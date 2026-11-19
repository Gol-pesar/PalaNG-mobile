package com.example.xrayclient.subscriptions

import com.example.xrayclient.data.models.Subscription
import com.example.xrayclient.data.models.SubscriptionStatus
import com.example.xrayclient.data.preferences.SettingsDataStore
import com.example.xrayclient.data.repositories.ServerRepository
import com.example.xrayclient.data.repositories.SubscriptionRepository
import com.example.xrayclient.domain.parsers.ProxyLinkParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SubscriptionRefreshOutcome(
    val subscription: Subscription,
    val importedCount: Int,
    val errorMessage: String? = null
)

/**
 * Manages remote subscription URLs using OkHttp with strict TLS defaults,
 * size-bounded payload reads, Base64/plain-text decoding, and zero credential logging.
 */
class SubscriptionManager(
    private val subscriptionRepository: SubscriptionRepository,
    private val serverRepository: ServerRepository,
    private val settingsDataStore: SettingsDataStore,
    private val parser: ProxyLinkParser = ProxyLinkParser(),
    private val httpClient: OkHttpClient = defaultHttpClient()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicJob: Job? = null

    val subscriptionsFlow: Flow<List<Subscription>> = subscriptionRepository.subscriptionsFlow

    fun startPeriodicScheduler() {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            while (isActive) {
                val hours = settingsDataStore.getSnapshot().subscriptionUpdateIntervalHours.coerceAtLeast(1)
                delay(TimeUnit.HOURS.toMillis(hours.toLong()))
                val now = System.currentTimeMillis()
                val minAgeMs = TimeUnit.HOURS.toMillis(hours.toLong())
                subscriptionRepository.getAllOnce()
                    .filter { it.autoUpdateEnabled && (it.lastUpdatedAt == null || now - it.lastUpdatedAt >= minAgeMs) }
                    .forEach { updateSubscription(it.id) }
            }
        }
    }

    suspend fun addSubscription(name: String, rawUrl: String): Result<SubscriptionRefreshOutcome> {
        val url = rawUrl.trim()
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            return Result.failure(IllegalArgumentException("نشانی اشتراک باید با http:// یا https:// شروع شود."))
        }
        val existing = subscriptionRepository.getAllOnce().firstOrNull { it.url.equals(url, ignoreCase = false) }
        if (existing != null) {
            return Result.failure(IllegalArgumentException("این لینک اشتراک قبلاً اضافه شده است."))
        }

        val cleanName = name.trim().ifBlank {
            url.substringAfter("://").substringBefore('/').substringBefore(':').ifBlank { "اشتراک جدید" }
        }
        val sub = Subscription(
            name = cleanName,
            url = url,
            status = SubscriptionStatus.UPDATING
        )
        subscriptionRepository.save(sub)
        val outcome = updateSubscription(sub.id)
        return Result.success(outcome)
    }

    suspend fun renameSubscription(subscriptionId: String, newName: String) {
        val sub = subscriptionRepository.getById(subscriptionId) ?: return
        val clean = newName.trim()
        if (clean.isNotEmpty()) {
            subscriptionRepository.save(sub.copy(name = clean))
        }
    }

    suspend fun toggleAutoUpdate(subscriptionId: String, enabled: Boolean) {
        val sub = subscriptionRepository.getById(subscriptionId) ?: return
        subscriptionRepository.save(sub.copy(autoUpdateEnabled = enabled))
    }

    suspend fun deleteSubscription(subscriptionId: String) {
        serverRepository.deleteBySubscriptionId(subscriptionId)
        subscriptionRepository.deleteById(subscriptionId)
    }

    suspend fun updateAllSubscriptions(): List<SubscriptionRefreshOutcome> {
        return subscriptionRepository.getAllOnce().map { updateSubscription(it.id) }
    }

    suspend fun updateSubscription(subscriptionId: String): SubscriptionRefreshOutcome =
        withContext(Dispatchers.IO) {
            val sub = subscriptionRepository.getById(subscriptionId)
                ?: return@withContext SubscriptionRefreshOutcome(
                    subscription = Subscription(id = subscriptionId, name = "", url = "", status = SubscriptionStatus.ERROR),
                    importedCount = 0,
                    errorMessage = "اشتراک یافت نشد."
                )

            subscriptionRepository.save(sub.copy(status = SubscriptionStatus.UPDATING, errorMessage = null))

            return@withContext try {
                val payload = fetchPayload(sub.url)
                val parsed = parser.importBatch(
                    rawInput = payload,
                    existingFingerprints = emptySet(),
                    subscriptionId = sub.id
                )

                if (parsed.profiles.isEmpty()) {
                    val reason = parsed.errors.firstOrNull()
                        ?: "هیچ سرور معتبری در پاسخ اشتراک یافت نشد."
                    val failed = sub.copy(
                        status = SubscriptionStatus.ERROR,
                        errorMessage = reason
                    )
                    subscriptionRepository.save(failed)
                    SubscriptionRefreshOutcome(failed, 0, reason)
                } else {
                    serverRepository.replaceSubscriptionServers(sub.id, parsed.profiles)
                    val updated = sub.copy(
                        status = SubscriptionStatus.SUCCESS,
                        lastUpdatedAt = System.currentTimeMillis(),
                        errorMessage = null,
                        serverCount = parsed.profiles.size
                    )
                    subscriptionRepository.save(updated)
                    SubscriptionRefreshOutcome(updated, parsed.profiles.size)
                }
            } catch (e: Exception) {
                val msg = e.message ?: "خطا در ارتباط با سرور اشتراک"
                val failed = sub.copy(
                    status = SubscriptionStatus.ERROR,
                    errorMessage = msg
                )
                subscriptionRepository.save(failed)
                SubscriptionRefreshOutcome(failed, 0, msg)
            }
        }

    private fun fetchPayload(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "XrayClient-Android/1.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("خطای HTTP ${response.code} هنگام دریافت اشتراک")
            }
            val body = response.body ?: throw IllegalStateException("پاسخ سرور اشتراک خالی است.")
            val bytes = body.source().let { source ->
                source.request(MAX_SUBSCRIPTION_BYTES + 1)
                val buffer = source.buffer
                if (buffer.size > MAX_SUBSCRIPTION_BYTES) {
                    throw IllegalStateException("حجم پاسخ اشتراک بیش از حد مجاز (۵ مگابایت) است.")
                }
                buffer.readByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        }
    }

    companion object {
        private const val MAX_SUBSCRIPTION_BYTES = 5L * 1024L * 1024L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
