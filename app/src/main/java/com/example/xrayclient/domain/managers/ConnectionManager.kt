package com.example.xrayclient.domain.managers

import android.content.Context
import android.content.Intent
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.data.preferences.SettingsDataStore
import com.example.xrayclient.data.repositories.ServerRepository
import com.example.xrayclient.engine.EngineAvailability
import com.example.xrayclient.engine.EngineStateManager
import com.example.xrayclient.engine.XrayConfigBuilder
import com.example.xrayclient.engine.XrayEngineAdapter
import com.example.xrayclient.statistics.StatisticsManager
import com.example.xrayclient.statistics.TrafficStatisticsSnapshot
import com.example.xrayclient.tunnel.Cl2ProxyServer
import com.example.xrayclient.utils.NetworkUtils
import com.example.xrayclient.vpn.VpnController
import com.example.xrayclient.vpn.VpnStatusSnapshot
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class ConnectionManager(
    private val appContext: Context,
    private val vpnController: VpnController,
    private val serverRepository: ServerRepository,
    private val settingsDataStore: SettingsDataStore,
    private val engineAdapter: XrayEngineAdapter,
    private val engineStateManager: EngineStateManager,
    private val statisticsManager: StatisticsManager,
    private val cl2ProxyServer: Cl2ProxyServer
) {
    val vpnSnapshot: StateFlow<VpnStatusSnapshot> = vpnController.vpnSnapshot
    val engineAvailability: StateFlow<EngineAvailability> = engineStateManager.availability
    val trafficStats: StateFlow<TrafficStatisticsSnapshot> = statisticsManager.stats

    suspend fun connectSelectedOrFirst(): Intent? {
        val settings = settingsDataStore.getSnapshot()
        val selected = settings.selectedServerId?.let { serverRepository.getById(it) }
            ?: serverRepository.getAllOnce().firstOrNull()
        if (selected == null) {
            return null
        }
        return vpnController.connect(selected)
    }

    fun connectProfile(profile: ServerProfile): Intent? = vpnController.connect(profile)

    fun onVpnPermissionResult(granted: Boolean) = vpnController.onPermissionResult(granted)

    fun disconnect() = vpnController.disconnect()

    /**
     * Measures real proxy round-trip delay via the Cl2 WebSocket handshake or native core,
     * falling back to direct TCP endpoint latency when the native core cannot be loaded.
     */
    suspend fun testServerLatency(profile: ServerProfile): Long {
        val settings = settingsDataStore.getSnapshot()
        if (Cl2ProxyServer.shouldHandleProfile(profile, settings)) {
            val latency = cl2ProxyServer.measureHandshakeLatencyMs(Cl2ProxyServer.configForProfile(profile, settings))
            serverRepository.updateLatency(profile.id, if (latency > 0) latency else -1L)
            return latency
        }
        val availability = engineStateManager.refreshAvailability()
        val latency = if (availability is EngineAvailability.Ready) {
            val assetDir = File(appContext.filesDir, "xray_assets").apply { mkdirs() }
            engineAdapter.initialize(appContext, assetDir)
            val configJson = XrayConfigBuilder.build(profile, settings)
            val coreDelay = engineAdapter.measureOutboundDelay(configJson)
            coreDelay ?: NetworkUtils.measureTcpLatencyMs(profile.address, profile.port)
        } else {
            NetworkUtils.measureTcpLatencyMs(profile.address, profile.port)
        }
        serverRepository.updateLatency(profile.id, if (latency > 0) latency else -1L)
        return latency
    }
}
