package com.example.xrayclient.vpn

import com.example.xrayclient.data.models.ProxyProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionState(val persianLabel: String) {
    DISCONNECTED("قطع شده"),
    REQUESTING_PERMISSION("در حال دریافت مجوز VPN"),
    CONNECTING("در حال اتصال…"),
    CONNECTED("متصل"),
    RECONNECTING("در حال اتصال مجدد…"),
    DISCONNECTING("در حال قطع اتصال…"),
    ERROR("خطا در اتصال"),
    ENGINE_NOT_INSTALLED("هسته Xray نصب نشده")
}

data class VpnStatusSnapshot(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val activeServerId: String? = null,
    val activeServerName: String? = null,
    val activeProtocol: ProxyProtocol? = null,
    val errorMessage: String? = null,
    val connectedAtElapsedRealtimeMs: Long? = null
)

/**
 * Process-wide state bridge between [XrayVpnService] and [VpnController] / UI ViewModels.
 */
object VpnStateHolder {
    private val _snapshot = MutableStateFlow(VpnStatusSnapshot())
    val snapshot: StateFlow<VpnStatusSnapshot> = _snapshot.asStateFlow()

    fun update(transform: (VpnStatusSnapshot) -> VpnStatusSnapshot) {
        _snapshot.value = transform(_snapshot.value)
    }

    fun setState(
        state: ConnectionState,
        serverId: String? = _snapshot.value.activeServerId,
        serverName: String? = _snapshot.value.activeServerName,
        protocol: ProxyProtocol? = _snapshot.value.activeProtocol,
        errorMessage: String? = null,
        connectedAtMs: Long? = if (state == ConnectionState.CONNECTED) _snapshot.value.connectedAtElapsedRealtimeMs else null
    ) {
        _snapshot.value = VpnStatusSnapshot(
            state = state,
            activeServerId = serverId,
            activeServerName = serverName,
            activeProtocol = protocol,
            errorMessage = errorMessage,
            connectedAtElapsedRealtimeMs = connectedAtMs
        )
    }
}
