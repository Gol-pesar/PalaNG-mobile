package com.example.xrayclient

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XrayClientApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            container.configurationManager.seedDefaultServersIfNeeded()
        }
        container.subscriptionManager.startPeriodicScheduler()
    }
}
