package com.example.xrayclient.domain.parsers

import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.data.models.TransportType
import com.example.xrayclient.domain.validators.ConfigValidator
import com.example.xrayclient.domain.validators.ValidationResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Parses full Xray-core JSON files (`{ "outbounds": [...] }`) or standalone outbound objects
 * into validated [ServerProfile] instances without losing custom fields.
 */
object XrayJsonConfigParser {

    private val prettyJson = Json { prettyPrint = true }

    fun looksLikeJson(raw: String): Boolean {
        val t = raw.trim()
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))
    }

    fun parse(jsonText: String, fallbackName: String = "پیکربندی JSON"): List<ParseOutcome> {
        val trimmed = jsonText.trim()
        if (!looksLikeJson(trimmed)) {
            return listOf(ParseOutcome.Failure("متن ورودی ساختار JSON معتبر ندارد.", jsonText))
        }

        return try {
            val element = Json.parseToJsonElement(trimmed)
            when (element) {
                is JsonArray -> {
                    val results = mutableListOf<ParseOutcome>()
                    element.forEachIndexed { idx, item ->
                        val obj = item as? JsonObject ?: return@forEachIndexed
                        results += parseRootObject(obj, "$fallbackName #${idx + 1}")
                    }
                    if (results.isEmpty()) {
                        listOf(ParseOutcome.Failure("هیچ پیکربندی معتبری در آرایه JSON یافت نشد.", jsonText))
                    } else {
                        results
                    }
                }
                is JsonObject -> parseRootObject(element, fallbackName)
                else -> listOf(ParseOutcome.Failure("ساختار ریشه JSON پشتیبانی نمی‌شود.", jsonText))
            }
        } catch (e: Exception) {
            listOf(ParseOutcome.Failure("خطا در تجزیه JSON: ${e.message ?: "ساختار نامعتبر"}", jsonText))
        }
    }

    private fun parseRootObject(root: JsonObject, fallbackName: String): List<ParseOutcome> {
        val outbounds = root["outbounds"] as? JsonArray
        if (outbounds != null) {
            val outcomes = mutableListOf<ParseOutcome>()
            for (item in outbounds) {
                val ob = item as? JsonObject ?: continue
                val proto = ob.optString("protocol").lowercase()
                if (proto in setOf("freedom", "blackhole", "dns", "loopback")) continue
                val remark = root.optString("remarks")
                    .ifBlank { ob.optString("tag").takeUnless { it == "proxy" }.orEmpty() }
                    .ifBlank { fallbackName }
                outcomes += parseOutbound(ob, remark, prettyJson.encodeToString(JsonObject.serializer(), ob))
            }
            return if (outcomes.isEmpty()) {
                listOf(ParseOutcome.Failure("هیچ outbound پروکسی در بخش outbounds یافت نشد.", root.toString()))
            } else {
                outcomes
            }
        }

        if (root.containsKey("protocol") && root.containsKey("settings")) {
            val remark = root.optString("tag").ifBlank { fallbackName }
            return listOf(parseOutbound(root, remark, prettyJson.encodeToString(JsonObject.serializer(), root)))
        }

        return listOf(ParseOutcome.Failure("کلید outbounds یا protocol در JSON یافت نشد.", root.toString()))
    }

    private fun parseOutbound(
        ob: JsonObject,
        remark: String,
        customFullOrOutbound: String
    ): ParseOutcome {
        val protocolStr = ob.optString("protocol").lowercase()
        val protocol = when (protocolStr) {
            "vless" -> ProxyProtocol.VLESS
            "vmess" -> ProxyProtocol.VMESS
            "trojan" -> ProxyProtocol.TROJAN
            "shadowsocks" -> ProxyProtocol.SHADOWSOCKS
            "http" -> ProxyProtocol.HTTP
            "socks" -> ProxyProtocol.SOCKS5
            else -> return ParseOutcome.Failure("پروتکل پشتیبانی‌نشده در JSON: $protocolStr", customFullOrOutbound)
        }

        val settings = ob.optObject("settings")
            ?: return ParseOutcome.Failure("بخش settings در outbound یافت نشد.", customFullOrOutbound)
        val stream = ob.optObject("streamSettings")
        val mux = ob.optObject("mux")

        var address = ""
        var port = 0
        var uuid = ""
        var encryption = "none"
        var alterId = 0
        var flow = ""
        var password = ""
        var ssMethod = "aes-256-gcm"
        var username = ""

        when (protocol) {
            ProxyProtocol.VLESS, ProxyProtocol.VMESS -> {
                val vnext = settings.optArray("vnext")?.firstOrNull() as? JsonObject
                    ?: return ParseOutcome.Failure("بخش vnext در JSON یافت نشد.", customFullOrOutbound)
                address = vnext.optString("address").trim()
                port = vnext.optInt("port", 0)
                val user = vnext.optArray("users")?.firstOrNull() as? JsonObject
                    ?: return ParseOutcome.Failure("بخش users در vnext یافت نشد.", customFullOrOutbound)
                uuid = user.optString("id").trim()
                if (protocol == ProxyProtocol.VLESS) {
                    encryption = user.optString("encryption").ifBlank { "none" }
                    flow = user.optString("flow").trim()
                } else {
                    encryption = user.optString("security").ifBlank { "auto" }
                    alterId = user.optInt("alterId", 0)
                }
            }
            ProxyProtocol.TROJAN -> {
                val server = settings.optArray("servers")?.firstOrNull() as? JsonObject
                    ?: return ParseOutcome.Failure("بخش servers برای Trojan یافت نشد.", customFullOrOutbound)
                address = server.optString("address").trim()
                port = server.optInt("port", 0)
                password = server.optString("password")
            }
            ProxyProtocol.SHADOWSOCKS -> {
                val server = settings.optArray("servers")?.firstOrNull() as? JsonObject
                    ?: return ParseOutcome.Failure("بخش servers برای Shadowsocks یافت نشد.", customFullOrOutbound)
                address = server.optString("address").trim()
                port = server.optInt("port", 0)
                password = server.optString("password")
                ssMethod = server.optString("method").ifBlank { "aes-256-gcm" }
            }
            ProxyProtocol.HTTP, ProxyProtocol.SOCKS5, ProxyProtocol.AMIR -> {
                val server = settings.optArray("servers")?.firstOrNull() as? JsonObject
                    ?: return ParseOutcome.Failure("بخش servers برای ${protocol.displayName} یافت نشد.", customFullOrOutbound)
                address = server.optString("address").trim()
                port = server.optInt("port", 0)
                val user = server.optArray("users")?.firstOrNull() as? JsonObject
                username = user?.optString("user").orEmpty()
                password = user?.optString("pass").orEmpty()
            }
        }

        val transport = TransportType.fromRaw(stream?.optString("network"))
        val security = SecurityType.fromRaw(stream?.optString("security"))
        val tlsObj = stream?.optObject("tlsSettings")
        val realityObj = stream?.optObject("realitySettings")

        var path = ""
        var host = ""
        var headerType = ""
        var serviceName = ""
        var grpcMode = "gun"
        var grpcAuthority = ""
        var xhttpMode = "auto"

        when (transport) {
            TransportType.WS -> {
                val ws = stream?.optObject("wsSettings")
                path = ws?.optString("path").orEmpty()
                host = ws?.optString("host").orEmpty()
                    .ifBlank { ws?.optObject("headers")?.optString("Host").orEmpty() }
            }
            TransportType.HTTPUPGRADE -> {
                val hu = stream?.optObject("httpupgradeSettings")
                path = hu?.optString("path").orEmpty()
                host = hu?.optString("host").orEmpty()
            }
            TransportType.XHTTP -> {
                val xh = stream?.optObject("xhttpSettings") ?: stream?.optObject("splithttpSettings")
                path = xh?.optString("path").orEmpty()
                host = xh?.optString("host").orEmpty()
                xhttpMode = xh?.optString("mode")?.ifBlank { "auto" } ?: "auto"
            }
            TransportType.GRPC -> {
                val grpc = stream?.optObject("grpcSettings")
                serviceName = grpc?.optString("serviceName").orEmpty()
                grpcAuthority = grpc?.optString("authority").orEmpty()
                grpcMode = if (grpc?.optBoolean("multiMode") == true) "multi" else "gun"
            }
            TransportType.H2 -> {
                val h2 = stream?.optObject("httpSettings")
                path = h2?.optString("path").orEmpty()
                host = (h2?.optArray("host")?.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
            TransportType.TCP -> {
                val tcp = stream?.optObject("tcpSettings") ?: stream?.optObject("rawSettings")
                val header = tcp?.optObject("header")
                headerType = header?.optString("type").orEmpty()
                val req = header?.optObject("request")
                path = (req?.optArray("path")?.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
                host = (req?.optObject("headers")?.optArray("Host")?.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
            else -> {}
        }

        val alpnList = tlsObj?.optArray("alpn")?.mapNotNull { el ->
            (el as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }.orEmpty()

        val profile = ServerProfile(
            name = remark.ifBlank { "$address:$port" },
            protocol = protocol,
            address = address,
            port = port,
            uuid = uuid,
            encryption = encryption,
            alterId = alterId,
            flow = flow,
            password = password,
            ssMethod = ssMethod,
            username = username,
            transport = transport,
            path = path,
            host = host,
            headerType = headerType,
            serviceName = serviceName,
            grpcMode = grpcMode,
            grpcAuthority = grpcAuthority,
            xhttpMode = xhttpMode,
            security = security,
            sni = tlsObj?.optString("serverName")
                ?.ifBlank { realityObj?.optString("serverName").orEmpty() }
                .orEmpty(),
            alpn = alpnList.joinToString(","),
            fingerprint = tlsObj?.optString("fingerprint")
                ?.ifBlank { realityObj?.optString("fingerprint").orEmpty() }
                .orEmpty(),
            allowInsecure = tlsObj?.optBoolean("allowInsecure") ?: false,
            realityPublicKey = realityObj?.optString("publicKey").orEmpty(),
            realityShortId = realityObj?.optString("shortId").orEmpty(),
            realitySpiderX = realityObj?.optString("spiderX").orEmpty(),
            muxEnabled = mux?.optBoolean("enabled") ?: false,
            muxConcurrency = mux?.optInt("concurrency", 8) ?: 8,
            customOutboundJson = customFullOrOutbound
        )

        return when (val v = ConfigValidator.validateProfile(profile)) {
            ValidationResult.Valid -> ParseOutcome.Success(profile)
            is ValidationResult.Invalid -> ParseOutcome.Failure(v.message, customFullOrOutbound)
        }
    }

    private fun JsonObject.optString(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.optInt(key: String, default: Int): Int =
        (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() } ?: default

    private fun JsonObject.optBoolean(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    private fun JsonObject.optObject(key: String): JsonObject? =
        this[key] as? JsonObject

    private fun JsonObject.optArray(key: String): JsonArray? =
        this[key] as? JsonArray
}
