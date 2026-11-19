package com.example.xrayclient.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.xrayclient.XrayClientApplication
import com.example.xrayclient.data.models.AppSettings
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.engine.EngineAvailability
import com.example.xrayclient.engine.EngineNotInstalledException
import com.example.xrayclient.engine.XrayConfigBuilder
import com.example.xrayclient.engine.XrayStartConfig
import com.example.xrayclient.tunnel.Cl2ProxyServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real Android [VpnService] managing the TUN interface, foreground service lifecycle,
 * upstream socket protection (`protect()`), network change reconnection, and [XrayEngineAdapter].
 *
 * CRITICAL INVARIANT:
 * Never establishes a dead-end TUN or reports [ConnectionState.CONNECTED] unless the real
 * native Xray-core engine is installed and successfully starts with the TUN file descriptor.
 */
class XrayVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isActiveLock = AtomicBoolean(false)

    private var connectionJob: Job? = null
    private var tunInterface: ParcelFileDescriptor? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastServerId: String? = null

    private val appContainer by lazy {
        (application as XrayClientApplication).container
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopVpnGracefully()
                return START_NOT_STICKY
            }
            ACTION_CONNECT, ACTION_RECONNECT, null -> {
                val serverId = intent?.getStringExtra(EXTRA_SERVER_ID)
                // Always enter foreground promptly on modern Android before async work
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(
                        title = getString(R.string.vpn_notification_title_connecting),
                        text = "در حال آماده‌سازی تونل Xray…"
                    )
                )
                startOrRestartConnection(serverId)
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    private fun startOrRestartConnection(requestedServerId: String?) {
        connectionJob?.cancel()
        connectionJob = serviceScope.launch {
            try {
                // Ensure previous engine/TUN resources are released before reconnecting
                releaseTunnelAndEngine()

                val settings = appContainer.settingsDataStore.getSnapshot()
                val targetId = requestedServerId ?: settings.selectedServerId ?: lastServerId
                val profile = targetId?.let { appContainer.serverRepository.getById(it) }
                    ?: appContainer.serverRepository.getAllOnce().firstOrNull()

                if (profile == null) {
                    VpnStateHolder.setState(
                        state = ConnectionState.ERROR,
                        errorMessage = "هیچ سروری برای اتصال انتخاب نشده است."
                    )
                    stopForegroundAndSelf()
                    return@launch
                }

                lastServerId = profile.id
                VpnStateHolder.setState(
                    state = ConnectionState.CONNECTING,
                    serverId = profile.id,
                    serverName = profile.name,
                    protocol = profile.protocol,
                    errorMessage = null
                )

                // Verify real native engine availability BEFORE creating TUN interface
                val availability = appContainer.engineStateManager.refreshAvailability()
                if (availability is EngineAvailability.NotInstalled) {
                    VpnStateHolder.setState(
                        state = ConnectionState.ENGINE_NOT_INSTALLED,
                        serverId = profile.id,
                        serverName = profile.name,
                        protocol = profile.protocol,
                        errorMessage = availability.reason
                    )
                    stopForegroundAndSelf()
                    return@launch
                }

                establishTunnelAndStartEngine(profile, settings)
            } catch (_: CancellationException) {
                // Cancelled cleanly by disconnect or new connect attempt
            } catch (e: EngineNotInstalledException) {
                VpnStateHolder.setState(
                    state = ConnectionState.ENGINE_NOT_INSTALLED,
                    errorMessage = e.message
                )
                stopForegroundAndSelf()
            } catch (e: Exception) {
                VpnStateHolder.setState(
                    state = ConnectionState.ERROR,
                    errorMessage = e.message ?: "خطای غیرمنتظره در راه‌اندازی سرویس VPN"
                )
                releaseTunnelAndEngine()
                stopForegroundAndSelf()
            }
        }
    }

    private suspend fun establishTunnelAndStartEngine(
        profile: ServerProfile,
        settings: AppSettings
    ) {
        if (!isActiveLock.compareAndSet(false, true)) {
            releaseTunnelAndEngine()
            isActiveLock.set(true)
        }

        val assetDir = File(filesDir, "xray_assets").apply { mkdirs() }
        val initResult = appContainer.engineAdapter.initialize(applicationContext, assetDir)
        if (initResult.isFailure) {
            val err = initResult.exceptionOrNull()
            isActiveLock.set(false)
            if (err is EngineNotInstalledException) {
                VpnStateHolder.setState(
                    state = ConnectionState.ENGINE_NOT_INSTALLED,
                    serverId = profile.id,
                    serverName = profile.name,
                    protocol = profile.protocol,
                    errorMessage = err.message
                )
            } else {
                VpnStateHolder.setState(
                    state = ConnectionState.ERROR,
                    serverId = profile.id,
                    serverName = profile.name,
                    protocol = profile.protocol,
                    errorMessage = err?.message ?: "خطا در آماده‌سازی هسته"
                )
            }
            stopForegroundAndSelf()
            return
        }

        val xrayJson = XrayConfigBuilder.build(profile, settings)
        val validation = appContainer.engineAdapter.validateConfig(xrayJson)
        if (validation.isFailure) {
            isActiveLock.set(false)
            val err = validation.exceptionOrNull()
            VpnStateHolder.setState(
                state = if (err is EngineNotInstalledException) {
                    ConnectionState.ENGINE_NOT_INSTALLED
                } else {
                    ConnectionState.ERROR
                },
                serverId = profile.id,
                serverName = profile.name,
                protocol = profile.protocol,
                errorMessage = err?.message ?: "پیکربندی Xray نامعتبر است."
            )
            stopForegroundAndSelf()
            return
        }

        val pfd = TunManager.establishTun(
            service = this,
            context = applicationContext,
            settings = settings,
            sessionName = "${getString(R.string.app_name)} - ${profile.name}"
        )
        if (pfd == null) {
            isActiveLock.set(false)
            VpnStateHolder.setState(
                state = ConnectionState.ERROR,
                serverId = profile.id,
                serverName = profile.name,
                protocol = profile.protocol,
                errorMessage = "ایجاد رابط شبکه TUN ناموفق بود. مجوز VPN را بررسی کنید."
            )
            stopForegroundAndSelf()
            return
        }

        tunInterface = pfd
        val startConfig = XrayStartConfig(
            configJson = xrayJson,
            tunFd = pfd.fd,
            tunMtu = TunManager.DEFAULT_MTU,
            socksInboundPort = XrayConfigBuilder.DEFAULT_SOCKS_PORT,
            httpInboundPort = XrayConfigBuilder.DEFAULT_HTTP_PORT,
            ipv4Address = TunManager.TUN_IPV4_ADDRESS,
            ipv6Enabled = settings.ipv6Enabled,
            ipv6Address = TunManager.TUN_IPV6_ADDRESS,
            assetDirPath = assetDir.absolutePath
        )

        if (Cl2ProxyServer.shouldHandleProfile(profile, settings)) {
            val cl2Result = appContainer.cl2ProxyServer.start(Cl2ProxyServer.configForProfile(profile, settings))
            if (cl2Result.isFailure) {
                releaseTunnelAndEngine()
                VpnStateHolder.setState(
                    state = ConnectionState.ERROR,
                    serverId = profile.id,
                    serverName = profile.name,
                    protocol = profile.protocol,
                    errorMessage = cl2Result.exceptionOrNull()?.message
                        ?: "راه‌اندازی سرور محلی AMIR ناموفق بود."
                )
                stopForegroundAndSelf()
                return
            }
        }

        val startResult = appContainer.engineAdapter.start(
            config = startConfig,
            protectSocket = { socketFd -> protect(socketFd) }
        )

        if (startResult.isFailure) {
            val err = startResult.exceptionOrNull()
            releaseTunnelAndEngine()
            VpnStateHolder.setState(
                state = if (err is EngineNotInstalledException) {
                    ConnectionState.ENGINE_NOT_INSTALLED
                } else {
                    ConnectionState.ERROR
                },
                serverId = profile.id,
                serverName = profile.name,
                protocol = profile.protocol,
                errorMessage = err?.message ?: "شروع هسته Xray ناموفق بود."
            )
            stopForegroundAndSelf()
            return
        }

        // Only reached when BOTH TUN interface and real Xray-core engine succeeded
        if (settings.reconnectOnNetworkChange) {
            registerNetworkCallback()
        }

        updateNotification(
            title = getString(R.string.vpn_notification_title_connected),
            text = "${profile.name} (${profile.protocol.displayName})"
        )

        VpnStateHolder.setState(
            state = ConnectionState.CONNECTED,
            serverId = profile.id,
            serverName = profile.name,
            protocol = profile.protocol,
            errorMessage = null,
            connectedAtMs = SystemClock.elapsedRealtime()
        )
        appContainer.statisticsManager.startTracking()
    }

    private fun stopVpnGracefully() {
        connectionJob?.cancel()
        VpnStateHolder.setState(state = ConnectionState.DISCONNECTING)
        serviceScope.launch {
            releaseTunnelAndEngine()
            VpnStateHolder.setState(
                state = ConnectionState.DISCONNECTED,
                errorMessage = null,
                connectedAtMs = null
            )
            stopForegroundAndSelf()
        }
    }

    private suspend fun releaseTunnelAndEngine() {
        appContainer.statisticsManager.stopAndReset()
        unregisterNetworkCallback()
        runCatching { appContainer.engineAdapter.stop() }
        runCatching { appContainer.cl2ProxyServer.stop() }
        try {
            tunInterface?.close()
        } catch (_: Exception) {
        } finally {
            tunInterface = null
            isActiveLock.set(false)
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            private var seenFirst = false
            override fun onAvailable(network: Network) {
                if (!seenFirst) {
                    seenFirst = true
                    return
                }
                if (VpnStateHolder.snapshot.value.state == ConnectionState.CONNECTED) {
                    VpnStateHolder.setState(state = ConnectionState.RECONNECTING)
                    startOrRestartConnection(lastServerId)
                }
            }
        }
        networkCallback = cb
        runCatching { cm.registerNetworkCallback(request, cb) }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    override fun onRevoke() {
        stopVpnGracefully()
        super.onRevoke()
    }

    override fun onDestroy() {
        connectionJob?.cancel()
        appContainer.statisticsManager.stopAndReset()
        unregisterNetworkCallback()
        runCatching {
            kotlinx.coroutines.runBlocking {
                appContainer.engineAdapter.stop()
            }
        }
        runCatching { appContainer.cl2ProxyServer.stop() }
        try {
            tunInterface?.close()
        } catch (_: Exception) {
        }
        tunInterface = null
        isActiveLock.set(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_notification_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, XrayVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                0,
                getString(R.string.vpn_notification_action_disconnect),
                disconnectIntent
            )
            .build()
    }

    companion object {
        const val ACTION_CONNECT = "com.example.xrayclient.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.xrayclient.vpn.ACTION_DISCONNECT"
        const val ACTION_RECONNECT = "com.example.xrayclient.vpn.ACTION_RECONNECT"
        const val EXTRA_SERVER_ID = "extra_server_id"
        private const val CHANNEL_ID = "xray_vpn_status"
        private const val NOTIFICATION_ID = 1001
    }
}
