package com.example.xrayclient.data.models

enum class ProxyProtocol(
    val scheme: String,
    val xrayProtocol: String,
    val displayName: String,
    val persianName: String
) {
    VLESS("vless", "vless", "VLESS", "VLESS"),
    VMESS("vmess", "vmess", "VMess", "VMess"),
    TROJAN("trojan", "trojan", "Trojan", "Trojan"),
    SHADOWSOCKS("ss", "shadowsocks", "Shadowsocks", "شادوساکس"),
    HTTP("http", "http", "HTTP Proxy", "پروکسی HTTP"),
    SOCKS5("socks", "socks", "SOCKS5 Proxy", "پروکسی SOCKS5"),
    AMIR("amir", "socks", "AMIR", "تونل AMIR");

    companion object {
        fun fromScheme(scheme: String): ProxyProtocol? = when (scheme.lowercase()) {
            "vless" -> VLESS
            "vmess" -> VMESS
            "trojan" -> TROJAN
            "ss", "shadowsocks" -> SHADOWSOCKS
            "http", "https" -> HTTP
            "socks", "socks5" -> SOCKS5
            "amir" -> AMIR
            else -> null
        }
    }
}
