package com.example.xrayclient.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.xrayclient.XrayClientApplication
import com.example.xrayclient.data.models.AppSettings
import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.data.models.Subscription
import com.example.xrayclient.domain.parsers.BatchImportResult
import com.example.xrayclient.engine.EngineAvailability
import com.example.xrayclient.engine.EngineDiagnosticLog
import com.example.xrayclient.engine.XrayConfigBuilder
import com.example.xrayclient.statistics.TrafficStatisticsSnapshot
import com.example.xrayclient.vpn.ConnectionState
import com.example.xrayclient.vpn.VpnStatusSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ServerSortOrder(val persianLabel: String) {
    FAVORITE_FIRST("برگزیده‌ها"),
    NAME("نام"),
    LATENCY("کمترین پینگ")
}

data class InstalledAppInfo(
    val packageName: String,
    val label: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as XrayClientApplication).container

    val settings: StateFlow<AppSettings> = container.settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val allServers: StateFlow<List<ServerProfile>> = container.configurationManager.serversFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val subscriptions: StateFlow<List<Subscription>> = container.subscriptionManager.subscriptionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vpnSnapshot: StateFlow<VpnStatusSnapshot> = container.connectionManager.vpnSnapshot
    val engineAvailability: StateFlow<EngineAvailability> = container.connectionManager.engineAvailability
    val engineLogs: StateFlow<List<EngineDiagnosticLog>> = container.engineStateManager.diagnosticLogs
    val trafficStats: StateFlow<TrafficStatisticsSnapshot> = container.connectionManager.trafficStats
    val cl2IsRunning: StateFlow<Boolean> = container.cl2ProxyServer.isRunning

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _protocolFilter = MutableStateFlow<ProxyProtocol?>(null)
    val protocolFilter: StateFlow<ProxyProtocol?> = _protocolFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(ServerSortOrder.FAVORITE_FIRST)
    val sortOrder: StateFlow<ServerSortOrder> = _sortOrder.asStateFlow()

    val filteredServers: StateFlow<List<ServerProfile>> = combine(
        allServers,
        _searchQuery,
        _protocolFilter,
        _sortOrder
    ) { list, query, proto, sort ->
        val filtered = list.filter { server ->
            val matchesProto = proto == null || server.protocol == proto
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                server.name.contains(q, ignoreCase = true) ||
                server.address.contains(q, ignoreCase = true) ||
                server.protocol.displayName.contains(q, ignoreCase = true)
            matchesProto && matchesQuery
        }
        when (sort) {
            ServerSortOrder.FAVORITE_FIRST -> filtered.sortedWith(
                compareByDescending<ServerProfile> { it.isFavorite }
                    .thenByDescending { it.createdAt }
            )
            ServerSortOrder.NAME -> filtered.sortedBy { it.name.lowercase() }
            ServerSortOrder.LATENCY -> filtered.sortedBy {
                val ms = it.latencyMs
                if (ms == null || ms <= 0L) Long.MAX_VALUE else ms
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedServer: StateFlow<ServerProfile?> = combine(
        allServers,
        settings
    ) { servers, currentSettings ->
        val id = currentSettings.selectedServerId
        servers.firstOrNull { it.id == id } ?: servers.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _testingServerIds = MutableStateFlow<Set<String>>(emptySet())
    val testingServerIds: StateFlow<Set<String>> = _testingServerIds.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _vpnPermissionRequest = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val vpnPermissionRequest: SharedFlow<Intent> = _vpnPermissionRequest.asSharedFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    init {
        viewModelScope.launch {
            container.configurationManager.seedDefaultServersIfNeeded()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setProtocolFilter(protocol: ProxyProtocol?) {
        _protocolFilter.value = protocol
    }

    fun setSortOrder(order: ServerSortOrder) {
        _sortOrder.value = order
    }

    fun toggleConnection() {
        val currentState = vpnSnapshot.value.state
        when (currentState) {
            ConnectionState.CONNECTED,
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING -> {
                container.connectionManager.disconnect()
            }
            else -> {
                viewModelScope.launch {
                    val server = selectedServer.value
                    if (server == null) {
                        _snackbarMessages.emit("لطفاً ابتدا یک سرور اضافه و انتخاب کنید.")
                        return@launch
                    }
                    val permIntent = container.connectionManager.connectProfile(server)
                    if (permIntent != null) {
                        _vpnPermissionRequest.emit(permIntent)
                    }
                }
            }
        }
    }

    fun onVpnPermissionResult(granted: Boolean) {
        container.connectionManager.onVpnPermissionResult(granted)
    }

    fun selectServer(profile: ServerProfile) {
        viewModelScope.launch {
            container.configurationManager.selectServer(profile.id)
            _snackbarMessages.emit("سرور «${profile.name}» انتخاب شد.")
        }
    }

    fun testServerLatency(profile: ServerProfile) {
        if (profile.id in _testingServerIds.value) return
        viewModelScope.launch {
            _testingServerIds.value = _testingServerIds.value + profile.id
            try {
                val ms = container.connectionManager.testServerLatency(profile)
                val msg = if (ms > 0) {
                    "پینگ TCP سرور ${profile.name}: $ms میلی‌ثانیه"
                } else {
                    "سرور ${profile.name} پاسخ نداد (Timeout)"
                }
                _snackbarMessages.emit(msg)
            } finally {
                _testingServerIds.value = _testingServerIds.value - profile.id
            }
        }
    }

    fun testAllServersLatency() {
        val servers = allServers.value
        if (servers.isEmpty()) return
        viewModelScope.launch {
            servers.forEach { server ->
                launch {
                    _testingServerIds.value = _testingServerIds.value + server.id
                    try {
                        container.connectionManager.testServerLatency(server)
                    } finally {
                        _testingServerIds.value = _testingServerIds.value - server.id
                    }
                }
            }
        }
    }

    fun toggleFavorite(profile: ServerProfile) {
        viewModelScope.launch {
            container.configurationManager.toggleFavorite(profile)
        }
    }

    fun duplicateServer(profile: ServerProfile) {
        viewModelScope.launch {
            container.configurationManager.duplicateProfile(profile)
            _snackbarMessages.emit("پیکربندی کپی شد.")
        }
    }

    fun deleteServer(profile: ServerProfile) {
        viewModelScope.launch {
            container.configurationManager.deleteProfile(profile.id)
            _snackbarMessages.emit("سرور «${profile.name}» حذف شد.")
        }
    }

    fun saveProfile(profile: ServerProfile, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = container.configurationManager.saveManualOrEditedProfile(profile)
            res.onSuccess {
                _snackbarMessages.emit("پیکربندی «${profile.name}» ذخیره شد.")
            }.onFailure { err ->
                _snackbarMessages.emit(err.message ?: "خطا در ذخیره پیکربندی")
            }
            onResult(res)
        }
    }

    fun importFromText(rawText: String, onDone: (BatchImportResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = container.configurationManager.importFromText(rawText)
            var addedSubCount = 0
            if (result.subscriptionUrls.isNotEmpty()) {
                result.subscriptionUrls.forEach { url ->
                    val addRes = container.subscriptionManager.addSubscription("", url)
                    if (addRes.isSuccess) addedSubCount++
                }
            }
            val summary = buildString {
                if (result.profiles.isNotEmpty()) {
                    append("${result.profiles.size} سرور وارد شد")
                }
                if (addedSubCount > 0) {
                    if (isNotEmpty()) append(" • ")
                    append("$addedSubCount اشتراک اضافه شد")
                }
                if (result.duplicateCount > 0) {
                    if (isNotEmpty()) append(" • ")
                    append("${result.duplicateCount} مورد تکراری نادیده گرفته شد")
                }
                if (isEmpty() && result.errors.isNotEmpty()) {
                    append(result.errors.first())
                } else if (isEmpty()) {
                    append("پیکربندی معتبری یافت نشد.")
                }
            }
            _snackbarMessages.emit(summary)
            onDone(result)
        }
    }

    fun addSubscription(name: String, url: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val res = container.subscriptionManager.addSubscription(name, url)
            res.onSuccess { outcome ->
                val msg = if (outcome.errorMessage == null) {
                    "اشتراک «${outcome.subscription.name}» با ${outcome.importedCount} سرور ثبت شد."
                } else {
                    "اشتراک ثبت شد اما خطا رخ داد: ${outcome.errorMessage}"
                }
                _snackbarMessages.emit(msg)
                onDone(true)
            }.onFailure { err ->
                _snackbarMessages.emit(err.message ?: "خطا در افزودن اشتراک")
                onDone(false)
            }
        }
    }

    fun refreshSubscription(subscriptionId: String) {
        viewModelScope.launch {
            val outcome = container.subscriptionManager.updateSubscription(subscriptionId)
            val msg = if (outcome.errorMessage == null) {
                "اشتراک «${outcome.subscription.name}» به‌روزرسانی شد (${outcome.importedCount} سرور)."
            } else {
                "خطا در به‌روزرسانی: ${outcome.errorMessage}"
            }
            _snackbarMessages.emit(msg)
        }
    }

    fun refreshAllSubscriptions() {
        viewModelScope.launch {
            val outcomes = container.subscriptionManager.updateAllSubscriptions()
            if (outcomes.isEmpty()) {
                _snackbarMessages.emit("اشتراکی برای به‌روزرسانی وجود ندارد.")
            } else {
                val okCount = outcomes.count { it.errorMessage == null }
                _snackbarMessages.emit("$okCount از ${outcomes.size} اشتراک به‌روزرسانی شد.")
            }
        }
    }

    fun renameSubscription(id: String, newName: String) {
        viewModelScope.launch {
            container.subscriptionManager.renameSubscription(id, newName)
        }
    }

    fun toggleSubscriptionAutoUpdate(id: String, enabled: Boolean) {
        viewModelScope.launch {
            container.subscriptionManager.toggleAutoUpdate(id, enabled)
        }
    }

    fun deleteSubscription(id: String) {
        viewModelScope.launch {
            container.subscriptionManager.deleteSubscription(id)
            _snackbarMessages.emit("اشتراک و سرورهای آن حذف شدند.")
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            container.settingsDataStore.update(transform)
        }
    }

    fun refreshEngineAvailability() {
        container.engineStateManager.refreshAvailability()
    }

    fun exportShareUri(profile: ServerProfile): String =
        container.configurationManager.exportToShareUri(profile)

    fun buildXrayJsonPreview(profile: ServerProfile): String =
        XrayConfigBuilder.build(profile, settings.value)

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                val ownPkg = getApplication<Application>().packageName
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val resolved = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                resolved
                    .map { it.activityInfo.packageName }
                    .distinct()
                    .filter { it != ownPkg }
                    .mapNotNull { pkg ->
                        runCatching {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            val label = pm.getApplicationLabel(appInfo).toString()
                            InstalledAppInfo(packageName = pkg, label = label)
                        }.getOrNull()
                    }
                    .sortedBy { it.label.lowercase() }
            }
            _installedApps.value = apps
        }
    }
}
