package com.example.xrayclient.data.models

import java.util.UUID

/**
 * Unified domain model for a proxy server configuration across all 6 supported protocols.
 * Preserves unknown/advanced parameters in [extraParams] and supports raw Xray JSON override when imported.
 */
data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: ProxyProtocol,
    val address: String,
    val port: Int,
    // Auth / protocol credentials
    val uuid: String = "",
    val encryption: String = "none", // VLESS encryption ("none") or VMess security ("auto", "aes-128-gcm", ...)
    val alterId: Int = 0,
    val flow: String = "",
    val password: String = "",
    val ssMethod: String = "aes-256-gcm",
    val username: String = "",
    val socksUdpAssociate: Boolean = true,
    // Transport
    val transport: TransportType = TransportType.TCP,
    val path: String = "",
    val host: String = "",
    val headerType: String = "",
    val serviceName: String = "",
    val grpcMode: String = "gun",
    val grpcAuthority: String = "",
    val xhttpMode: String = "auto",
    val xhttpExtraJson: String = "",
    // Security (TLS / REALITY)
    val security: SecurityType = SecurityType.NONE,
    val sni: String = "",
    val alpn: String = "",
    val fingerprint: String = "",
    val allowInsecure: Boolean = false,
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val realitySpiderX: String = "",
    // Mux (explicitly disabled by default per performance & compatibility rules)
    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8,
    // Metadata & unknown parameters preservation
    val subscriptionId: String? = null,
    val isFavorite: Boolean = false,
    val latencyMs: Long? = null,
    val rawUri: String = "",
    val customOutboundJson: String = "",
    val extraParams: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Canonical deduplication key comparing protocol, server endpoint, credentials, and transport/security.
     */
    fun duplicateFingerprint(): String {
        val credential = when (protocol) {
            ProxyProtocol.VLESS, ProxyProtocol.VMESS -> uuid.lowercase()
            ProxyProtocol.TROJAN -> password
            ProxyProtocol.SHADOWSOCKS -> "$ssMethod:$password"
            ProxyProtocol.HTTP, ProxyProtocol.SOCKS5 -> "$username:$password"
            ProxyProtocol.AMIR -> "${uuid.trim().lowercase()}:${host.trim().lowercase()}"
        }
        return listOf(
            protocol.name,
            address.trim().lowercase(),
            port.toString(),
            credential,
            transport.name,
            security.name,
            sni.trim().lowercase(),
            path.trim(),
            serviceName.trim(),
            realityPublicKey.trim()
        ).joinToString("|")
    }
}
