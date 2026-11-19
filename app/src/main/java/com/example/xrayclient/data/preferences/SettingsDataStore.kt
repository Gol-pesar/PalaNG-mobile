package com.example.xrayclient.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.xrayclient.data.models.AppLanguage
import com.example.xrayclient.data.models.AppSettings
import com.example.xrayclient.data.models.DnsStrategy
import com.example.xrayclient.data.models.PerAppMode
import com.example.xrayclient.data.models.ThemeMode
import com.example.xrayclient.data.models.XrayLogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "xray_client_settings"
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val PRIMARY_DNS = stringPreferencesKey("primary_dns")
        val SECONDARY_DNS = stringPreferencesKey("secondary_dns")
        val DNS_STRATEGY = stringPreferencesKey("dns_strategy")
        val IPV4_ENABLED = booleanPreferencesKey("ipv4_enabled")
        val IPV6_ENABLED = booleanPreferencesKey("ipv6_enabled")
        val BYPASS_LAN = booleanPreferencesKey("bypass_lan")
        val PER_APP_MODE = stringPreferencesKey("per_app_mode")
        val PER_APP_PACKAGES = stringSetPreferencesKey("per_app_packages")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val RECONNECT_ON_NETWORK_CHANGE = booleanPreferencesKey("reconnect_on_network_change")
        val LOG_LEVEL = stringPreferencesKey("log_level")
        val SUB_UPDATE_INTERVAL_HOURS = intPreferencesKey("sub_update_interval_hours")
        val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        val DEFAULT_SERVERS_SEEDED = booleanPreferencesKey("default_servers_seeded")
        val DEFAULT_SERVERS_SEED_VERSION = intPreferencesKey("default_servers_seed_version")
        val CL2_ENABLED = booleanPreferencesKey("cl2_enabled")
        val CL2_UUID = stringPreferencesKey("cl2_uuid")
        val CL2_WORKER_HOST = stringPreferencesKey("cl2_worker_host")
        val CL2_CLEAN_IP = stringPreferencesKey("cl2_clean_ip")
        val CL2_LISTEN_HOST = stringPreferencesKey("cl2_listen_host")
        val CL2_LISTEN_PORT = intPreferencesKey("cl2_listen_port")
        val CL2_SUBPROTOCOL = stringPreferencesKey("cl2_subprotocol")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.DARK,
            language = prefs[Keys.LANGUAGE]?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: AppLanguage.PERSIAN,
            primaryDns = prefs[Keys.PRIMARY_DNS] ?: "1.1.1.1",
            secondaryDns = prefs[Keys.SECONDARY_DNS] ?: "8.8.8.8",
            dnsStrategy = prefs[Keys.DNS_STRATEGY]?.let {
                runCatching { DnsStrategy.valueOf(it) }.getOrNull()
            } ?: DnsStrategy.PREFER_IP_V4,
            ipv4Enabled = prefs[Keys.IPV4_ENABLED] ?: true,
            ipv6Enabled = prefs[Keys.IPV6_ENABLED] ?: false,
            bypassLan = prefs[Keys.BYPASS_LAN] ?: true,
            perAppMode = prefs[Keys.PER_APP_MODE]?.let {
                runCatching { PerAppMode.valueOf(it) }.getOrNull()
            } ?: PerAppMode.ALL_APPS,
            perAppPackages = prefs[Keys.PER_APP_PACKAGES] ?: emptySet(),
            autoConnect = prefs[Keys.AUTO_CONNECT] ?: false,
            reconnectOnNetworkChange = prefs[Keys.RECONNECT_ON_NETWORK_CHANGE] ?: true,
            logLevel = prefs[Keys.LOG_LEVEL]?.let {
                runCatching { XrayLogLevel.valueOf(it) }.getOrNull()
            } ?: XrayLogLevel.WARNING,
            subscriptionUpdateIntervalHours = prefs[Keys.SUB_UPDATE_INTERVAL_HOURS] ?: 12,
            selectedServerId = prefs[Keys.SELECTED_SERVER_ID],
            cl2Enabled = prefs[Keys.CL2_ENABLED] ?: true,
            cl2Uuid = prefs[Keys.CL2_UUID] ?: com.example.xrayclient.tunnel.Cl2Config.DEFAULT_UUID,
            cl2WorkerHost = prefs[Keys.CL2_WORKER_HOST] ?: com.example.xrayclient.tunnel.Cl2Config.DEFAULT_WORKER_HOST,
            cl2CleanIp = prefs[Keys.CL2_CLEAN_IP] ?: com.example.xrayclient.tunnel.Cl2Config.DEFAULT_CLEAN_IP,
            cl2ListenHost = prefs[Keys.CL2_LISTEN_HOST] ?: com.example.xrayclient.tunnel.Cl2Config.DEFAULT_LISTEN_HOST,
            cl2ListenPort = prefs[Keys.CL2_LISTEN_PORT] ?: com.example.xrayclient.tunnel.Cl2Config.DEFAULT_LISTEN_PORT,
            cl2Subprotocol = prefs[Keys.CL2_SUBPROTOCOL] ?: com.example.xrayclient.tunnel.Cl2Config.DEFAULT_SUBPROTOCOL
        )
    }

    suspend fun getSnapshot(): AppSettings = settingsFlow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = AppSettings(
                themeMode = prefs[Keys.THEME_MODE]?.let {
                    runCatching { ThemeMode.valueOf(it) }.getOrNull()
                } ?: ThemeMode.DARK,
                language = prefs[Keys.LANGUAGE]?.let {
                    runCatching { AppLanguage.valueOf(it) }.getOrNull()
                } ?: AppLanguage.PERSIAN,
                primaryDns = prefs[Keys.PRIMARY_DNS] ?: "1.1.1.1",
                secondaryDns = prefs[Keys.SECONDARY_DNS] ?: "8.8.8.8",
                dnsStrategy = prefs[Keys.DNS_STRATEGY]?.let {
                    runCatching { DnsStrategy.valueOf(it) }.getOrNull()
                } ?: DnsStrategy.PREFER_IP_V4,
                ipv4Enabled = prefs[Keys.IPV4_ENABLED] ?: true,
                ipv6Enabled = prefs[Keys.IPV6_ENABLED] ?: false,
                bypassLan = prefs[Keys.BYPASS_LAN] ?: true,
                perAppMode = prefs[Keys.PER_APP_MODE]?.let {
                    runCatching { PerAppMode.valueOf(it) }.getOrNull()
                } ?: PerAppMode.ALL_APPS,
                perAppPackages = prefs[Keys.PER_APP_PACKAGES] ?: emptySet(),
                autoConnect = prefs[Keys.AUTO_CONNECT] ?: false,
                reconnectOnNetworkChange = prefs[Keys.RECONNECT_ON_NETWORK_CHANGE] ?: true,
                logLevel = prefs[Keys.LOG_LEVEL]?.let {
                    runCatching { XrayLogLevel.valueOf(it) }.getOrNull()
                } ?: XrayLogLevel.WARNING,
                subscriptionUpdateIntervalHours = prefs[Keys.SUB_UPDATE_INTERVAL_HOURS] ?: 12,
                selectedServerId = prefs[Keys.SELECTED_SERVER_ID],
                cl2Enabled = prefs[Keys.CL2_ENABLED] ?: true,
                cl2Uuid = prefs[Keys.CL2_UUID] ?: com.example.xrayclient.tunnel.Cl2Config.DEFAULT_UUID,
                cl2WorkerHost = prefs[Keys.CL2_WORKER_HOST] ?: com.example.xrayclient.tunnel.Cl2Config.DEFAULT_WORKER_HOST,
                cl2CleanIp = prefs[Keys.CL2_CLEAN_IP] ?: com.example.xrayclient.tunnel.Cl2Config.DEFAULT_CLEAN_IP,
                cl2ListenHost = prefs[Keys.CL2_LISTEN_HOST] ?: com.example.xrayclient.tunnel.Cl2Config.DEFAULT_LISTEN_HOST,
                cl2ListenPort = prefs[Keys.CL2_LISTEN_PORT] ?: com.example.xrayclient.tunnel.Cl2Config.DEFAULT_LISTEN_PORT,
                cl2Subprotocol = prefs[Keys.CL2_SUBPROTOCOL] ?: com.example.xrayclient.tunnel.Cl2Config.DEFAULT_SUBPROTOCOL
            )
            val next = transform(current)
            prefs[Keys.THEME_MODE] = next.themeMode.name
            prefs[Keys.LANGUAGE] = next.language.name
            prefs[Keys.PRIMARY_DNS] = next.primaryDns
            prefs[Keys.SECONDARY_DNS] = next.secondaryDns
            prefs[Keys.DNS_STRATEGY] = next.dnsStrategy.name
            prefs[Keys.IPV4_ENABLED] = next.ipv4Enabled
            prefs[Keys.IPV6_ENABLED] = next.ipv6Enabled
            prefs[Keys.BYPASS_LAN] = next.bypassLan
            prefs[Keys.PER_APP_MODE] = next.perAppMode.name
            prefs[Keys.PER_APP_PACKAGES] = next.perAppPackages
            prefs[Keys.AUTO_CONNECT] = next.autoConnect
            prefs[Keys.RECONNECT_ON_NETWORK_CHANGE] = next.reconnectOnNetworkChange
            prefs[Keys.LOG_LEVEL] = next.logLevel.name
            prefs[Keys.SUB_UPDATE_INTERVAL_HOURS] = next.subscriptionUpdateIntervalHours
            if (next.selectedServerId != null) {
                prefs[Keys.SELECTED_SERVER_ID] = next.selectedServerId
            } else {
                prefs.remove(Keys.SELECTED_SERVER_ID)
            }
            prefs[Keys.CL2_ENABLED] = next.cl2Enabled
            prefs[Keys.CL2_UUID] = next.cl2Uuid
            prefs[Keys.CL2_WORKER_HOST] = next.cl2WorkerHost
            prefs[Keys.CL2_CLEAN_IP] = next.cl2CleanIp
            prefs[Keys.CL2_LISTEN_HOST] = next.cl2ListenHost
            prefs[Keys.CL2_LISTEN_PORT] = next.cl2ListenPort
            prefs[Keys.CL2_SUBPROTOCOL] = next.cl2Subprotocol
        }
    }

    suspend fun setSelectedServerId(serverId: String?) {
        update { it.copy(selectedServerId = serverId) }
    }

    suspend fun getDefaultServersSeedVersion(): Int {
        val prefs = context.settingsDataStore.data.first()
        val version = prefs[Keys.DEFAULT_SERVERS_SEED_VERSION]
        if (version != null) return version
        return if (prefs[Keys.DEFAULT_SERVERS_SEEDED] == true) 1 else 0
    }

    suspend fun setDefaultServersSeedVersion(version: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_SERVERS_SEEDED] = true
            prefs[Keys.DEFAULT_SERVERS_SEED_VERSION] = version
        }
    }
}
