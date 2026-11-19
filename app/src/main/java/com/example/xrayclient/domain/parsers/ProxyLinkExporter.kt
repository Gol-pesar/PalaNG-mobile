package com.example.xrayclient.domain.parsers

import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.data.models.TransportType
import com.example.xrayclient.utils.Base64Utils
import com.example.xrayclient.utils.LinkUtils
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Exports any [ServerProfile] back to its canonical shareable URI, preserving advanced and extra parameters.
 */
object ProxyLinkExporter {

    fun export(profile: ServerProfile): String = when (profile.protocol) {
        ProxyProtocol.VLESS -> exportVless(profile)
        ProxyProtocol.VMESS -> exportVmess(profile)
        ProxyProtocol.TROJAN -> exportTrojan(profile)
        ProxyProtocol.SHADOWSOCKS -> exportShadowsocks(profile)
        ProxyProtocol.HTTP -> exportHttp(profile)
        ProxyProtocol.SOCKS5 -> exportSocks5(profile)
        ProxyProtocol.AMIR -> exportAmir(profile)
    }

    private fun exportVless(p: ServerProfile): String {
        val params = linkedMapOf<String, String>()
        params["encryption"] = p.encryption.ifBlank { "none" }
        if (p.flow.isNotBlank()) params["flow"] = p.flow
        params["security"] = p.security.xrayValue
        params["type"] = p.transport.xrayNetwork
        appendTransportAndSecurityParams(p, params)
        p.extraParams.forEach { (k, v) -> params.putIfAbsent(k, v) }

        val query = buildQuery(params)
        val host = LinkUtils.formatHostForUri(p.address)
        val remark = LinkUtils.urlEncode(p.name)
        return "vless://${p.uuid}@$host:${p.port}?$query#$remark"
    }

    private fun exportVmess(p: ServerProfile): String {
        val obj = buildJsonObject {
            put("v", JsonPrimitive("2"))
            put("ps", JsonPrimitive(p.name))
            put("add", JsonPrimitive(p.address))
            put("port", JsonPrimitive(p.port.toString()))
            put("id", JsonPrimitive(p.uuid))
            put("aid", JsonPrimitive(p.alterId.toString()))
            put("scy", JsonPrimitive(p.encryption.ifBlank { "auto" }))
            put("net", JsonPrimitive(if (p.transport == TransportType.TCP) "tcp" else p.transport.xrayNetwork))
            put("type", JsonPrimitive(p.headerType.ifBlank { "none" }))
            if (p.host.isNotBlank()) put("host", JsonPrimitive(p.host))
            if (p.transport == TransportType.GRPC && p.serviceName.isNotBlank()) {
                put("path", JsonPrimitive(p.serviceName))
            } else if (p.path.isNotBlank()) {
                put("path", JsonPrimitive(p.path))
            }
            put("tls", JsonPrimitive(if (p.security == SecurityType.NONE) "" else p.security.xrayValue))
            if (p.sni.isNotBlank()) put("sni", JsonPrimitive(p.sni))
            if (p.alpn.isNotBlank()) put("alpn", JsonPrimitive(p.alpn))
            if (p.fingerprint.isNotBlank()) put("fp", JsonPrimitive(p.fingerprint))
            if (p.allowInsecure) put("allowInsecure", JsonPrimitive(1))
            val usedKeys = setOf("v", "ps", "add", "port", "id", "aid", "scy", "net", "type", "host", "path", "tls", "sni", "alpn", "fp", "allowInsecure")
            p.extraParams.forEach { (k, v) -> if (k !in usedKeys) put(k, JsonPrimitive(v)) }
        }
        return "vmess://${Base64Utils.encodeString(obj.toString())}"
    }

    private fun exportTrojan(p: ServerProfile): String {
        val params = linkedMapOf<String, String>()
        params["security"] = p.security.xrayValue
        params["type"] = p.transport.xrayNetwork
        appendTransportAndSecurityParams(p, params)
        p.extraParams.forEach { (k, v) -> params.putIfAbsent(k, v) }

        val query = buildQuery(params)
        val host = LinkUtils.formatHostForUri(p.address)
        val pass = LinkUtils.urlEncode(p.password)
        val remark = LinkUtils.urlEncode(p.name)
        return "trojan://$pass@$host:${p.port}?$query#$remark"
    }

    private fun exportShadowsocks(p: ServerProfile): String {
        val creds = Base64Utils.encodeString("${p.ssMethod}:${p.password}", urlSafe = true, pad = false)
        val host = LinkUtils.formatHostForUri(p.address)
        val query = if (p.extraParams.isNotEmpty()) "?${buildQuery(p.extraParams)}" else ""
        val remark = LinkUtils.urlEncode(p.name)
        return "ss://$creds@$host:${p.port}$query#$remark"
    }

    private fun exportHttp(p: ServerProfile): String {
        val scheme = if (p.security == SecurityType.TLS) "https" else "http"
        val auth = if (p.username.isNotBlank() || p.password.isNotBlank()) {
            "${LinkUtils.urlEncode(p.username)}:${LinkUtils.urlEncode(p.password)}@"
        } else {
            ""
        }
        val params = linkedMapOf<String, String>()
        if (p.sni.isNotBlank()) params["sni"] = p.sni
        if (p.allowInsecure) params["allowInsecure"] = "1"
        p.extraParams.forEach { (k, v) -> params.putIfAbsent(k, v) }
        val query = if (params.isNotEmpty()) "?${buildQuery(params)}" else ""
        val host = LinkUtils.formatHostForUri(p.address)
        val remark = LinkUtils.urlEncode(p.name)
        return "$scheme://$auth$host:${p.port}$query#$remark"
    }

    private fun exportSocks5(p: ServerProfile): String {
        val auth = if (p.username.isNotBlank() || p.password.isNotBlank()) {
            "${LinkUtils.urlEncode(p.username)}:${LinkUtils.urlEncode(p.password)}@"
        } else {
            ""
        }
        val params = linkedMapOf<String, String>()
        if (!p.socksUdpAssociate) params["udp"] = "0"
        if (p.security == SecurityType.TLS) params["security"] = "tls"
        if (p.sni.isNotBlank()) params["sni"] = p.sni
        p.extraParams.forEach { (k, v) -> params.putIfAbsent(k, v) }
        val query = if (params.isNotEmpty()) "?${buildQuery(params)}" else ""
        val host = LinkUtils.formatHostForUri(p.address)
        val remark = LinkUtils.urlEncode(p.name)
        return "socks5://$auth$host:${p.port}$query#$remark"
    }

    private fun exportAmir(p: ServerProfile): String {
        val params = linkedMapOf<String, String>()
        params["worker"] = p.host.trim()
        p.extraParams["subproto"]?.trim()?.takeIf { it.isNotEmpty() }?.let { params["subproto"] = it }
        p.extraParams.forEach { (k, v) ->
            if (k !in setOf("worker", "workerUrl", "host", "subproto")) {
                params.putIfAbsent(k, v)
            }
        }
        val cleanHost = if (p.address.isBlank()) "" else LinkUtils.formatHostForUri(p.address)
        val remark = LinkUtils.urlEncode(p.name.ifBlank { "AMIR" })
        return "amir://${LinkUtils.urlEncode(p.uuid.trim())}@$cleanHost:${p.port}?${buildQuery(params)}#$remark"
    }

    private fun appendTransportAndSecurityParams(p: ServerProfile, params: MutableMap<String, String>) {
        if (p.path.isNotBlank()) params["path"] = p.path
        if (p.host.isNotBlank()) params["host"] = p.host
        if (p.headerType.isNotBlank()) params["headerType"] = p.headerType
        if (p.transport == TransportType.GRPC) {
            if (p.serviceName.isNotBlank()) params["serviceName"] = p.serviceName
            if (p.grpcMode.isNotBlank()) params["mode"] = p.grpcMode
            if (p.grpcAuthority.isNotBlank()) params["authority"] = p.grpcAuthority
        }
        if (p.transport == TransportType.XHTTP) {
            if (p.xhttpMode.isNotBlank()) params["mode"] = p.xhttpMode
            if (p.xhttpExtraJson.isNotBlank()) params["extra"] = p.xhttpExtraJson
        }
        if (p.sni.isNotBlank()) params["sni"] = p.sni
        if (p.alpn.isNotBlank()) params["alpn"] = p.alpn
        if (p.fingerprint.isNotBlank()) params["fp"] = p.fingerprint
        if (p.allowInsecure) params["allowInsecure"] = "1"
        if (p.security == SecurityType.REALITY) {
            if (p.realityPublicKey.isNotBlank()) params["pbk"] = p.realityPublicKey
            if (p.realityShortId.isNotBlank()) params["sid"] = p.realityShortId
            if (p.realitySpiderX.isNotBlank()) params["spx"] = p.realitySpiderX
        }
    }

    private fun buildQuery(params: Map<String, String>): String =
        params.entries.joinToString("&") { (k, v) ->
            "${LinkUtils.urlEncode(k)}=${LinkUtils.urlEncode(v)}"
        }
}
