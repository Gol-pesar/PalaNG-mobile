package com.example.xrayclient

import android.content.Context
import com.example.xrayclient.data.database.XrayDatabase
import com.example.xrayclient.data.preferences.SettingsDataStore
import com.example.xrayclient.data.repositories.ServerRepository
import com.example.xrayclient.data.repositories.SubscriptionRepository
import com.example.xrayclient.domain.managers.ConfigurationManager
import com.example.xrayclient.domain.managers.ConnectionManager
import com.example.xrayclient.domain.parsers.ProxyLinkParser
import com.example.xrayclient.engine.EngineStateManager
import com.example.xrayclient.engine.NativeXrayBridge
import com.example.xrayclient.engine.XrayEngineAdapter
import com.example.xrayclient.statistics.StatisticsManager
import com.example.xrayclient.subscriptions.SubscriptionManager
import com.example.xrayclient.vpn.VpnController

/**
 * Lightweight constructor-injected dependency graph for the single-module app.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database = XrayDatabase.getInstance(appContext)

    val settingsDataStore = SettingsDataStore(appContext)
    val serverRepository = ServerRepository(database.serverDao())
    val subscriptionRepository = SubscriptionRepository(database.subscriptionDao())
    val proxyLinkParser = ProxyLinkParser()

    val engineAdapter: XrayEngineAdapter = NativeXrayBridge()
    val engineStateManager = EngineStateManager(engineAdapter)
    val statisticsManager = StatisticsManager(engineAdapter)
    val cl2ProxyServer = com.example.xrayclient.tunnel.Cl2ProxyServer()
    val vpnController = VpnController(appContext, engineStateManager)

    val configurationManager = ConfigurationManager(
        serverRepository = serverRepository,
        settingsDataStore = settingsDataStore,
        parser = proxyLinkParser
    )

    val connectionManager = ConnectionManager(
        appContext = appContext,
        vpnController = vpnController,
        serverRepository = serverRepository,
        settingsDataStore = settingsDataStore,
        engineAdapter = engineAdapter,
        engineStateManager = engineStateManager,
        statisticsManager = statisticsManager,
        cl2ProxyServer = cl2ProxyServer
    )

    val subscriptionManager = SubscriptionManager(
        subscriptionRepository = subscriptionRepository,
        serverRepository = serverRepository,
        settingsDataStore = settingsDataStore,
        parser = proxyLinkParser
    )
}
