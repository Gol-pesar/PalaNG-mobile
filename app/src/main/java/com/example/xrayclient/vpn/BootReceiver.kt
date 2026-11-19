package com.example.xrayclient.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.example.xrayclient.XrayClientApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val app = context.applicationContext as? XrayClientApplication
        if (app == null) {
            pendingResult.finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = app.container.settingsDataStore.getSnapshot()
                if (settings.autoConnect && VpnService.prepare(context) == null) {
                    app.container.vpnController.startVpnService(settings.selectedServerId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
