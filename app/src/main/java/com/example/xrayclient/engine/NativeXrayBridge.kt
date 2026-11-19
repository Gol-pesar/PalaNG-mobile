package com.example.xrayclient.engine

import android.content.Context
import com.example.xrayclient.domain.validators.ConfigValidator
import com.example.xrayclient.domain.validators.ValidationResult
import go.Seq
import hev.htproxy.TProxyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Production [XrayEngineAdapter] wired to real prebuilt Android AAR binaries in `app/libs/`:
 * 1. `libv2ray.aar` (`2dust/AndroidLibV2rayLite` v5.56.0 — `libv2ray.Libv2ray`, `CoreController`, `CoreCallbackHandler`)
 * 2. `hev-socks5-tunnel.aar` (`heiher/hev-socks5-tunnel` v2.20.0 — `hev.htproxy.TProxyService` + `libhev-socks5-tunnel.so`)
 *
 * Routing-loop prevention:
 * `TunManager.buildRouteSpec` always excludes the host application's package via
 * `VpnService.Builder.addDisallowedApplication(ownPackageName)`, so all sockets opened by
 * `libgojni.so` inside this process bypass the TUN interface automatically.
 *
 * Note on JVM unit tests:
 * Host JVM environments cannot load Android ELF `.so` libraries (`libgojni.so`, `libhev-socks5-tunnel.so`).
 * [detectAvailability] verifies native library loadability safely via [canLoadNativeLibrary] without
 * invoking static JNI initializers when `.so` loading fails, returning [EngineAvailability.NotInstalled]
 * on non-Android JVMs instead of crashing.
 */
class NativeXrayBridge : XrayEngineAdapter {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(EngineRuntimeState.IDLE)
    private val _logs = MutableStateFlow<List<EngineDiagnosticLog>>(emptyList())

    private val cumulativeUplinkBytes = AtomicLong(0L)
    private val cumulativeDownlinkBytes = AtomicLong(0L)

    @Volatile
    private var initialized = false

    @Volatile
    private var lastAssetDir: File? = null

    @Volatile
    private var activeController: CoreController? = null

    @Volatile
    private var cachedCoreVersion: String? = null

    override fun detectAvailability(): EngineAvailability {
        val checkedArtifacts = mutableListOf<String>()

        checkedArtifacts += "libv2ray.Libv2ray + libgojni.so (libv2ray.aar)"
        val hasLibV2ray = hasClass("libv2ray.Libv2ray") && canLoadNativeLibrary("gojni")

        checkedArtifacts += "hev.htproxy.TProxyService + libhev-socks5-tunnel.so (hev-socks5-tunnel.aar)"
        val hasHevTunnel = hasClass("hev.htproxy.TProxyService") && canLoadNativeLibrary("hev-socks5-tunnel")

        return when {
            hasLibV2ray && hasHevTunnel -> {
                val version = cachedCoreVersion ?: queryLibV2rayVersion()?.also { cachedCoreVersion = it }
                    ?: "v2fly-core"
                EngineAvailability.Ready(
                    backendName = "AndroidLibV2rayLite + hev-socks5-tunnel",
                    coreVersion = version,
                    supportsDirectTunFd = false,
                    requiresTun2SocksBridge = true
                )
            }
            hasLibV2ray && !hasHevTunnel -> {
                EngineAvailability.NotInstalled(
                    reason = "کتابخانه libv2ray شناسایی شد اما پل TUN بومی (libhev-socks5-tunnel.so) قابل بارگذاری نیست.",
                    checkedArtifacts = checkedArtifacts
                )
            }
            else -> {
                EngineAvailability.NotInstalled(
                    reason = "باینری‌های بومی libv2ray.aar و hev-socks5-tunnel.aar در این محیط اجرایی قابل بارگذاری نیستند.",
                    checkedArtifacts = checkedArtifacts
                )
            }
        }
    }

    override suspend fun initialize(appContext: Context, assetDir: File): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val availability = detectAvailability()
                if (availability is EngineAvailability.NotInstalled) {
                    appendLog("WARN", availability.reason)
                    return@withContext Result.failure(EngineNotInstalledException(availability.reason))
                }

                _state.value = EngineRuntimeState.INITIALIZING
                try {
                    if (!assetDir.exists()) {
                        assetDir.mkdirs()
                    }
                    extractBundledAssets(appContext.applicationContext, assetDir)
                    Seq.setContext(appContext.applicationContext)
                    Libv2ray.initCoreEnv(assetDir.absolutePath, "")
                    lastAssetDir = assetDir
                    initialized = true
                    _state.value = if (activeController?.isRunning == true) {
                        EngineRuntimeState.RUNNING
                    } else {
                        EngineRuntimeState.IDLE
                    }
                    val ready = availability as EngineAvailability.Ready
                    appendLog("INFO", "هسته آماده شد: ${ready.backendName} (${ready.coreVersion})")
                    Result.success(Unit)
                } catch (t: Throwable) {
                    _state.value = EngineRuntimeState.ERROR
                    val msg = "خطا در مقداردهی اولیه هسته بومی: ${t.message ?: t::class.java.simpleName}"
                    appendLog("ERROR", msg)
                    Result.failure(IllegalStateException(msg, t))
                }
            }
        }

    override suspend fun validateConfig(xrayJson: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            when (val structural = ConfigValidator.validateXrayJson(xrayJson)) {
                is ValidationResult.Invalid -> {
                    return@withContext Result.failure(IllegalArgumentException(structural.message))
                }
                ValidationResult.Valid -> Unit
            }

            val v2flyCompatibilityError = checkV2flyCompatibility(xrayJson)
            if (v2flyCompatibilityError != null) {
                appendLog("WARN", v2flyCompatibilityError)
                return@withContext Result.failure(IllegalArgumentException(v2flyCompatibilityError))
            }

            when (detectAvailability()) {
                is EngineAvailability.NotInstalled -> {
                    Result.failure(
                        EngineNotInstalledException(
                            "اعتبارسنجی ساختاری JSON موفق بود، اما هسته بومی در این محیط قابل بارگذاری نیست."
                        )
                    )
                }
                is EngineAvailability.Ready -> Result.success(Unit)
            }
        }

    override suspend fun start(
        config: XrayStartConfig,
        protectSocket: (Int) -> Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val availability = detectAvailability()
            if (availability is EngineAvailability.NotInstalled) {
                _state.value = EngineRuntimeState.ERROR
                appendLog("ERROR", availability.reason)
                return@withContext Result.failure(EngineNotInstalledException(availability.reason))
            }

            if (config.tunFd < 0) {
                _state.value = EngineRuntimeState.ERROR
                return@withContext Result.failure(IllegalArgumentException("شناسه فایل دیسکریپتور TUN نامعتبر است."))
            }

            val compatError = checkV2flyCompatibility(config.configJson)
            if (compatError != null) {
                _state.value = EngineRuntimeState.ERROR
                appendLog("ERROR", compatError)
                return@withContext Result.failure(IllegalArgumentException(compatError))
            }

            stopInternalLocked()
            _state.value = EngineRuntimeState.STARTING
            cumulativeUplinkBytes.set(0L)
            cumulativeDownlinkBytes.set(0L)

            var createdController: CoreController? = null
            try {
                val assetDir = File(
                    config.assetDirPath.ifBlank { lastAssetDir?.absolutePath ?: "." }
                ).apply { mkdirs() }
                lastAssetDir = assetDir

                val handler = object : CoreCallbackHandler {
                    override fun startup(): Long {
                        appendLog("INFO", "هسته v2fly-core استارت خورد.")
                        return 0L
                    }

                    override fun shutdown(): Long {
                        appendLog("INFO", "هسته v2fly-core متوقف شد.")
                        return 0L
                    }

                    override fun onEmitStatus(code: Long, status: String?): Long {
                        if (!status.isNullOrBlank()) {
                            appendLog("CORE[$code]", status)
                        }
                        return 0L
                    }
                }

                val controller = Libv2ray.newCoreController(handler)
                createdController = controller
                appendLog("INFO", "شروع حلقه هسته با پورت SOCKS=${config.socksInboundPort}")
                controller.startLoop(config.configJson)

                if (!controller.isRunning) {
                    throw IllegalStateException("CoreController پس از startLoop در وضعیت اجرا قرار نگرفت.")
                }

                val tunnelConfigFile = writeHevTunnelYaml(assetDir, config)
                appendLog("INFO", "راه‌اندازی پل TUN-to-SOCKS5 با fd=${config.tunFd}")
                val tunStarted = TProxyService.TProxyStartService(
                    tunnelConfigFile.absolutePath,
                    config.tunFd
                )
                if (!tunStarted) {
                    throw IllegalStateException("راه‌اندازی پل بومی hev-socks5-tunnel (TProxyStartService) ناموفق بود.")
                }

                activeController = controller
                _state.value = EngineRuntimeState.RUNNING
                appendLog("INFO", "اتصال هسته و پل TUN با موفقیت برقرار شد.")
                Result.success(Unit)
            } catch (t: Throwable) {
                runCatching {
                    if (TProxyService.TProxyIsRunning()) {
                        TProxyService.TProxyStopService()
                    }
                }
                runCatching { createdController?.stopLoop() }
                activeController = null
                _state.value = EngineRuntimeState.ERROR
                val msg = "خطا در راه‌اندازی هسته بومی: ${t.message ?: t::class.java.simpleName}"
                appendLog("ERROR", msg)
                Result.failure(IllegalStateException(msg, t))
            }
        }
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        mutex.withLock {
            stopInternalLocked()
            Result.success(Unit)
        }
    }

    override suspend fun restart(
        config: XrayStartConfig,
        protectSocket: (Int) -> Boolean
    ): Result<Unit> {
        stop()
        return start(config, protectSocket)
    }

    override fun queryTrafficStats(): EngineTrafficStats? {
        if (_state.value != EngineRuntimeState.RUNNING) return null
        val controller = activeController ?: return null
        return try {
            // CoreController.queryStats(tag, direct) returns delta bytes and resets the counter.
            val upDelta = (controller.queryStats("proxy", "uplink") +
                controller.queryStats("direct", "uplink")).coerceAtLeast(0L)
            val downDelta = (controller.queryStats("proxy", "downlink") +
                controller.queryStats("direct", "downlink")).coerceAtLeast(0L)

            val totalUp = cumulativeUplinkBytes.addAndGet(upDelta)
            val totalDown = cumulativeDownlinkBytes.addAndGet(downDelta)
            EngineTrafficStats(
                uplinkBytes = totalUp,
                downlinkBytes = totalDown
            )
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun measureOutboundDelay(
        xrayJson: String,
        testUrl: String
    ): Long? = withContext(Dispatchers.IO) {
        if (detectAvailability() !is EngineAvailability.Ready) {
            return@withContext null
        }
        if (checkV2flyCompatibility(xrayJson) != null) {
            return@withContext -1L
        }
        try {
            val rtt = Libv2ray.measureOutboundDelay(xrayJson, testUrl)
            if (rtt >= 0L) rtt else -1L
        } catch (t: Throwable) {
            appendLog("WARN", "خطا در تست تاخیر هسته: ${t.message ?: t::class.java.simpleName}")
            -1L
        }
    }

    override fun getCoreVersion(): String? = when (val av = detectAvailability()) {
        is EngineAvailability.Ready -> av.coreVersion
        is EngineAvailability.NotInstalled -> null
    }

    override fun observeState(): StateFlow<EngineRuntimeState> = _state.asStateFlow()

    override fun observeLogs(): StateFlow<List<EngineDiagnosticLog>> = _logs.asStateFlow()

    private fun stopInternalLocked() {
        val hadActive = activeController != null || _state.value == EngineRuntimeState.RUNNING
        if (hadActive) {
            _state.value = EngineRuntimeState.STOPPING
            appendLog("INFO", "توقف پل TUN و هسته v2fly-core...")
        }
        runCatching {
            if (hasClass("hev.htproxy.TProxyService") && TProxyService.TProxyIsRunning()) {
                TProxyService.TProxyStopService()
            }
        }
        runCatching {
            activeController?.stopLoop()
        }
        activeController = null
        cumulativeUplinkBytes.set(0L)
        cumulativeDownlinkBytes.set(0L)
        _state.value = EngineRuntimeState.IDLE
    }

    private fun extractBundledAssets(context: Context, assetDir: File) {
        listOf("geoip.dat", "geosite.dat").forEach { fileName ->
            val outFile = File(assetDir, fileName)
            if (outFile.exists() && outFile.length() > 0L) return@forEach
            runCatching {
                context.assets.open(fileName).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun writeHevTunnelYaml(assetDir: File, config: XrayStartConfig): File {
        val yamlFile = File(assetDir, "hev-socks5-tunnel.yml")
        val content = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: ${config.tunMtu}")
            appendLine("  ipv4: ${config.ipv4Address}")
            if (config.ipv6Enabled && config.ipv6Address.isNotBlank()) {
                appendLine("  ipv6: '${config.ipv6Address}'")
            }
            appendLine("socks5:")
            appendLine("  port: ${config.socksInboundPort}")
            appendLine("  address: 127.0.0.1")
            appendLine("  udp: 'udp'")
            appendLine("misc:")
            appendLine("  task-stack-size: 81920")
            appendLine("  connect-timeout: 10000")
            appendLine("  tcp-read-write-timeout: 300000")
            appendLine("  udp-read-write-timeout: 60000")
            appendLine("  log-level: warn")
        }
        yamlFile.writeText(content)
        return yamlFile
    }

    private fun checkV2flyCompatibility(configJson: String): String? {
        val root = runCatching { Json.parseToJsonElement(configJson) as? JsonObject }.getOrNull()
            ?: return null
        val outbounds = root["outbounds"]?.jsonArray ?: return null
        for (item in outbounds) {
            val outbound = item as? JsonObject ?: continue
            val stream = outbound["streamSettings"] as? JsonObject
            val network = stream?.get("network")?.jsonPrimitive?.content?.lowercase()
            val security = stream?.get("security")?.jsonPrimitive?.content?.lowercase()
            if (security == "reality" || stream?.containsKey("realitySettings") == true) {
                return "هسته فعلی (AndroidLibV2rayLite / v2fly v5) از امنیت Xray REALITY پشتیبانی نمی‌کند."
            }
            if (network == "xhttp" || network == "splithttp" || stream?.containsKey("xhttpSettings") == true) {
                return "هسته فعلی (AndroidLibV2rayLite / v2fly v5) از انتقال XHTTP پشتیبانی نمی‌کند."
            }
            if (network == "httpupgrade" || stream?.containsKey("httpupgradeSettings") == true) {
                return "هسته فعلی (AndroidLibV2rayLite / v2fly v5) از انتقال HTTPUpgrade پشتیبانی نمی‌کند."
            }
            val users = (outbound["settings"] as? JsonObject)?.get("vnext")
                ?.jsonArray?.firstOrNull()?.jsonObject?.get("users")?.jsonArray
            val flow = users?.firstOrNull()?.jsonObject?.get("flow")?.jsonPrimitive?.content.orEmpty()
            if (flow.startsWith("xtls", ignoreCase = true)) {
                return "هسته فعلی (AndroidLibV2rayLite / v2fly v5) از flow=$flow پشتیبانی نمی‌کند."
            }
        }
        return null
    }

    private fun hasClass(fqcn: String): Boolean = try {
        Class.forName(fqcn, false, this::class.java.classLoader)
        true
    } catch (_: Throwable) {
        false
    }

    private fun canLoadNativeLibrary(name: String): Boolean = try {
        System.loadLibrary(name)
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: Throwable) {
        false
    }

    private fun queryLibV2rayVersion(): String? = try {
        Libv2ray.checkVersionX()
    } catch (_: Throwable) {
        null
    }

    private fun appendLog(level: String, message: String) {
        val entry = EngineDiagnosticLog(level = level, message = message)
        val current = _logs.value
        _logs.value = (current + entry).takeLast(150)
    }
}
