package com.example.xrayclient.domain.parsers

import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.data.models.TransportType
import com.example.xrayclient.domain.validators.ConfigValidator
import com.example.xrayclient.domain.validators.ValidationResult
import com.example.xrayclient.utils.Base64Utils
import com.example.xrayclient.utils.LinkUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Parses both v2rayN Base64-JSON (`vmess://<base64>`) and URI-style VMess links.
 */
class VmessParser : ProxyUriParser {
    override val supportedSchemes: Set<String> = setOf("vmess")

    override fun canParse(raw: String): Boolean =
        raw.trim().startsWith("vmess://", ignoreCase = true)

    override fun parse(raw: String): ParseOutcome {
        val trimmed = raw.trim()
        if (!canParse(trimmed)) return ParseOutcome.Failure("فرمت لینک VMess نیست.", raw)

        val payload = trimmed.substringAfter("://").trim()
        if (payload.isEmpty()) return ParseOutcome.Failure("بدنه لینک VMess خالی است.", raw)

        val decodedJson = Base64Utils.decodeToString(payload)
        if (decodedJson != null && decodedJson.trimStart().startsWith("{")) {
            return parseV2RayNJson(decodedJson, trimmed)
        }

        if (payload.contains("@")) {
            return parseUriStyle(payload, trimmed)
        }

        return ParseOutcome.Failure("ساختار Base64 یا JSON لینک VMess نامعتبر است.", raw)
    }

    private fun parseV2RayNJson(jsonText: String, rawUri: String): ParseOutcome {
        return try {
            val obj = Json.parseToJsonElement(jsonText) as? JsonObject
                ?: return ParseOutcome.Failure("ساختار JSON لینک VMess نامعتبر است.", rawUri)

            fun str(key: String): String =
                (obj[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

            val address = str("add")
            val port = (obj["port"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
                ?: return ParseOutcome.Failure("پورت VMess نامعتبر است.", rawUri)
            val uuid = str("id")
            val alterId = (obj["aid"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() } ?: 0
            val scy = str("scy").ifBlank { "auto" }
            val transport = TransportType.fromRaw(str("net"))
            val tlsRaw = str("tls").lowercase()
            val security = when (tlsRaw) {
                "tls" -> SecurityType.TLS
                "reality" -> SecurityType.REALITY
                else -> SecurityType.NONE
            }
            val sni = str("sni").ifBlank { str("host") }
            val allowInsecure = (obj["allowInsecure"] as? JsonPrimitive)?.let { prim ->
                prim.booleanOrNull ?: (prim.intOrNull == 1) || prim.contentOrNull.equals("true", true) || prim.contentOrNull == "1"
            } ?: false

            val knownKeys = setOf(
                "v", "ps", "add", "port", "id", "aid", "scy", "net", "type",
                "host", "path", "tls", "sni", "alpn", "fp", "allowInsecure"
            )
            val extra = LinkedHashMap<String, String>()
            obj.forEach { (key, el) ->
                if (key !in knownKeys) {
                    extra[key] = (el as? JsonPrimitive)?.contentOrNull ?: el.toString()
                }
            }

            val remark = str("ps").ifBlank { "$address:$port" }
            val profile = ServerProfile(
                name = remark,
                protocol = ProxyProtocol.VMESS,
                address = address,
                port = port,
                uuid = uuid,
                encryption = scy,
                alterId = alterId,
                transport = transport,
                path = str("path"),
                host = str("host"),
                headerType = str("type"),
                serviceName = if (transport == TransportType.GRPC) str("path") else "",
                security = security,
                sni = sni,
                alpn = str("alpn"),
                fingerprint = str("fp"),
                allowInsecure = allowInsecure,
                rawUri = rawUri,
                extraParams = extra
            )

            when (val v = ConfigValidator.validateProfile(profile)) {
                ValidationResult.Valid -> ParseOutcome.Success(profile)
                is ValidationResult.Invalid -> ParseOutcome.Failure(v.message, rawUri)
            }
        } catch (e: Exception) {
            ParseOutcome.Failure("خطا در خواندن JSON لینک VMess: ${e.message}", rawUri)
        }
    }

    private fun parseUriStyle(body: String, rawUri: String): ParseOutcome {
        val hashIdx = body.indexOf('#')
        val withoutFragment = if (hashIdx >= 0) body.substring(0, hashIdx) else body
        val remark = if (hashIdx >= 0) LinkUtils.urlDecode(body.substring(hashIdx + 1)).trim() else ""

        val queryIdx = withoutFragment.indexOf('?')
        val authority = if (queryIdx >= 0) withoutFragment.substring(0, queryIdx) else withoutFragment
        val queryStr = if (queryIdx >= 0) withoutFragment.substring(queryIdx + 1) else null

        val atIdx = authority.lastIndexOf('@')
        if (atIdx <= 0) return ParseOutcome.Failure("شناسه UUID در لینک VMess یافت نشد.", rawUri)

        val uuid = LinkUtils.urlDecode(authority.substring(0, atIdx)).trim()
        val hostPort = LinkUtils.parseHostPort(authority.substring(atIdx + 1))
            ?: return ParseOutcome.Failure("آدرس یا پورت VMess نامعتبر است.", rawUri)

        val params = LinkUtils.parseQueryParameters(queryStr)
        val transport = TransportType.fromRaw(params["type"])
        val security = SecurityType.fromRaw(params["security"])

        val profile = ServerProfile(
            name = remark.ifBlank { "${hostPort.host}:${hostPort.port}" },
            protocol = ProxyProtocol.VMESS,
            address = hostPort.host,
            port = hostPort.port,
            uuid = uuid,
            encryption = params["encryption"]?.ifBlank { "auto" } ?: "auto",
            alterId = params["aid"]?.toIntOrNull() ?: 0,
            transport = transport,
            path = params["path"].orEmpty(),
            host = params["host"].orEmpty(),
            headerType = params["headerType"].orEmpty(),
            serviceName = params["serviceName"].orEmpty(),
            security = security,
            sni = params["sni"].orEmpty(),
            alpn = params["alpn"].orEmpty(),
            fingerprint = params["fp"].orEmpty(),
            allowInsecure = params["allowInsecure"] == "1" || params["allowInsecure"].equals("true", true),
            rawUri = rawUri
        )

        return when (val v = ConfigValidator.validateProfile(profile)) {
            ValidationResult.Valid -> ParseOutcome.Success(profile)
            is ValidationResult.Invalid -> ParseOutcome.Failure(v.message, rawUri)
        }
    }
}
