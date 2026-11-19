package com.example.xrayclient.data.models

enum class TransportType(
    val xrayNetwork: String,
    val displayName: String
) {
    TCP("raw", "TCP / RAW"),
    WS("ws", "WebSocket"),
    GRPC("grpc", "gRPC"),
    XHTTP("xhttp", "XHTTP"),
    HTTPUPGRADE("httpupgrade", "HTTPUpgrade"),
    H2("h2", "HTTP/2"),
    QUIC("quic", "QUIC"),
    KCP("kcp", "mKCP");

    /** Network identifier compatible with both v2fly v5 (AndroidLibV2rayLite) and Xray-core. */
    val coreNetwork: String
        get() = if (this == TCP) "tcp" else xrayNetwork

    companion object {
        fun fromRaw(raw: String?): TransportType = when (raw?.trim()?.lowercase()) {
            null, "", "tcp", "raw" -> TCP
            "ws", "websocket" -> WS
            "grpc" -> GRPC
            "xhttp", "splithttp" -> XHTTP
            "httpupgrade" -> HTTPUPGRADE
            "h2", "http" -> H2
            "quic" -> QUIC
            "kcp", "mkcp" -> KCP
            else -> TCP
        }
    }
}
