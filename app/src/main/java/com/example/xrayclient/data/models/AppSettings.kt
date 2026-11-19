package com.example.xrayclient.data.models

enum class ThemeMode(val persianLabel: String) {
    DARK("تیره"),
    LIGHT("روشن"),
    SYSTEM("پیروی از سیستم")
}

enum class AppLanguage(val code: String, val label: String) {
    PERSIAN("fa", "فارسی"),
    ENGLISH("en", "English")
}

enum class DnsStrategy(val xrayStrategy: String, val persianLabel: String) {
    USE_IP("UseIP", "استفاده از IPv4 و IPv6"),
    USE_IP_V4("UseIPv4", "فقط IPv4"),
    PREFER_IP_V4("PreferIPv4", "اولویت با IPv4"),
    PREFER_IP_V6("PreferIPv6", "اولویت با IPv6")
}

enum class PerAppMode(val persianLabel: String) {
    ALL_APPS("همه برنامه‌ها از تونل عبور کنند"),
    BYPASS_SELECTED("دور زدن برنامه‌های انتخاب‌شده"),
    ALLOW_SELECTED("فقط برنامه‌های انتخاب‌شده")
}

enum class XrayLogLevel(val xrayValue: String, val persianLabel: String) {
    NONE("none", "غیرفعال"),
    ERROR("error", "فقط خطاها"),
    WARNING("warning", "هشدارها"),
    INFO("info", "اطلاعات"),
    DEBUG("debug", "اشکال‌زدایی کامل")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.PERSIAN,
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "8.8.8.8",
    val dnsStrategy: DnsStrategy = DnsStrategy.PREFER_IP_V4,
    val ipv4Enabled: Boolean = true,
    val ipv6Enabled: Boolean = false,
    val bypassLan: Boolean = true,
    val perAppMode: PerAppMode = PerAppMode.ALL_APPS,
    val perAppPackages: Set<String> = emptySet(),
    val autoConnect: Boolean = false,
    val reconnectOnNetworkChange: Boolean = true,
    val logLevel: XrayLogLevel = XrayLogLevel.WARNING,
    val subscriptionUpdateIntervalHours: Int = 12,
    val selectedServerId: String? = null,
    // Cl2 WebSocket -> SOCKS5 local proxy bridge (127.0.0.1:40446)
    val cl2Enabled: Boolean = true,
    val cl2Uuid: String = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd",
    val cl2WorkerHost: String = "twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev",
    val cl2CleanIp: String = "172.67.70.244",
    val cl2ListenHost: String = "127.0.0.1",
    val cl2ListenPort: Int = 40446,
    val cl2Subprotocol: String = "chat.v2"
) {
    fun toCl2Config(): com.example.xrayclient.tunnel.Cl2Config =
        com.example.xrayclient.tunnel.Cl2Config(
            enabled = cl2Enabled,
            uuid = cl2Uuid,
            workerHost = cl2WorkerHost,
            cleanIp = cl2CleanIp,
            listenHost = cl2ListenHost,
            listenPort = cl2ListenPort,
            subprotocol = cl2Subprotocol
        )
}
