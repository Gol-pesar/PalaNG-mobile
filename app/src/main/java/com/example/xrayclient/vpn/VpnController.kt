package com.example.xrayclient.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.engine.EngineAvailability
import com.example.xrayclient.engine.EngineStateManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinates VPN permission checks (`VpnService.prepare`), service start/stop intents,
 * and strict validation of native engine availability before claiming connectivity.
 */
class VpnController(
    private val context: Context,
    private val engineStateManager: EngineStateManager
) {
    val vpnSnapshot: StateFlow<VpnStatusSnapshot> = VpnStateHolder.snapshot

    private var pendingServer: ServerProfile? = null

    fun prepareVpnIntent(): Intent? = VpnService.prepare(context)

    /**
     * Initiates VPN connection. Returns an [Intent] if system VPN permission is required,
     * or `null` if permission was already granted or engine is not installed.
     */
    fun connect(profile: ServerProfile): Intent? {
        val availability = engineStateManager.refreshAvailability()
        if (availability is EngineAvailability.NotInstalled) {
            VpnStateHolder.setState(
                state = ConnectionState.ENGINE_NOT_INSTALLED,
                serverId = profile.id,
                serverName = profile.name,
                protocol = profile.protocol,
                errorMessage = availability.reason
            )
            return null
        }

        val permissionIntent = prepareVpnIntent()
        if (permissionIntent != null) {
            pendingServer = profile
            VpnStateHolder.setState(
                state = ConnectionState.REQUESTING_PERMISSION,
                serverId = profile.id,
                serverName = profile.name,
                protocol = profile.protocol,
                errorMessage = null
            )
            return permissionIntent
        }

        startVpnService(profile.id, profile)
        return null
    }

    fun onPermissionResult(granted: Boolean) {
        val target = pendingServer
        pendingServer = null
        if (!granted) {
            VpnStateHolder.setState(
                state = ConnectionState.DISCONNECTED,
                errorMessage = "مجوز سرویس VPN توسط کاربر رد شد."
            )
            return
        }
        if (target != null) {
            startVpnService(target.id, target)
        } else {
            VpnStateHolder.setState(state = ConnectionState.DISCONNECTED)
        }
    }

    fun startVpnService(serverId: String?, profile: ServerProfile? = null) {
        VpnStateHolder.setState(
            state = ConnectionState.CONNECTING,
            serverId = serverId ?: profile?.id,
            serverName = profile?.name ?: VpnStateHolder.snapshot.value.activeServerName,
            protocol = profile?.protocol ?: VpnStateHolder.snapshot.value.activeProtocol,
            errorMessage = null
        )
        val intent = Intent(context, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_CONNECT
            if (serverId != null) putExtra(XrayVpnService.EXTRA_SERVER_ID, serverId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun disconnect() {
        val current = VpnStateHolder.snapshot.value.state
        if (current == ConnectionState.DISCONNECTED) return
        if (current == ConnectionState.ENGINE_NOT_INSTALLED || current == ConnectionState.ERROR) {
            VpnStateHolder.setState(state = ConnectionState.DISCONNECTED, errorMessage = null)
            return
        }
        VpnStateHolder.setState(state = ConnectionState.DISCONNECTING)
        val intent = Intent(context, XrayVpnService::class.java).apply {
            action = XrayVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }
}
