package com.example.xrayclient.domain.parsers

import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.data.models.TransportType
import com.example.xrayclient.domain.validators.ConfigValidator
import com.example.xrayclient.domain.validators.ValidationResult
import com.example.xrayclient.utils.LinkUtils

/**
 * Parses VLESS share links (`vless://uuid@host:port?params#remark`).
 * Preserves unknown query parameters without silently discarding them.
 */
class VlessParser : ProxyUriParser {
    override val supportedSchemes: Set<String> = setOf("vless")

    override fun canParse(raw: String): Boolean =
        raw.trim().startsWith("vless://", ignoreCase = true)

    override fun parse(raw: String): ParseOutcome {
        val trimmed = raw.trim()
        if (!canParse(trimmed)) return ParseOutcome.Failure("فرمت لینک VLESS نیست.", raw)

        val body = trimmed.substringAfter("://")
        val hashIdx = body.indexOf('#')
        val withoutFragment = if (hashIdx >= 0) body.substring(0, hashIdx) else body
        val remark = if (hashIdx >= 0) LinkUtils.urlDecode(body.substring(hashIdx + 1)).trim() else ""

        val queryIdx = withoutFragment.indexOf('?')
        val authority = if (queryIdx >= 0) withoutFragment.substring(0, queryIdx) else withoutFragment
        val queryStr = if (queryIdx >= 0) withoutFragment.substring(queryIdx + 1) else null

        val atIdx = authority.lastIndexOf('@')
        if (atIdx <= 0) {
            return ParseOutcome.Failure("شناسه UUID در لینک VLESS یافت نشد.", raw)
        }
        val uuid = LinkUtils.urlDecode(authority.substring(0, atIdx)).trim()
        val hostPort = LinkUtils.parseHostPort(authority.substring(atIdx + 1))
            ?: return ParseOutcome.Failure("آدرس یا پورت VLESS نامعتبر است.", raw)

        val params = LinkUtils.parseQueryParameters(queryStr)
        val knownKeys = setOf(
            "encryption", "flow", "security", "sni", "pbk", "sid", "spx", "fp", "alpn",
            "allowInsecure", "type", "path", "host", "headerType", "serviceName", "mode",
            "authority", "extra"
        )
        val extraParams = params.filterKeys { it !in knownKeys }

        val transport = TransportType.fromRaw(params["type"])
        val security = SecurityType.fromRaw(params["security"])
        val allowInsecure = params["allowInsecure"]?.let { it == "1" || it.equals("true", true) } ?: false

        val profile = ServerProfile(
            name = remark.ifBlank { "${hostPort.host}:${hostPort.port}" },
            protocol = ProxyProtocol.VLESS,
            address = hostPort.host,
            port = hostPort.port,
            uuid = uuid,
            encryption = params["encryption"]?.ifBlank { "none" } ?: "none",
            flow = params["flow"].orEmpty(),
            transport = transport,
            path = params["path"].orEmpty(),
            host = params["host"].orEmpty(),
            headerType = params["headerType"].orEmpty(),
            serviceName = params["serviceName"].orEmpty(),
            grpcMode = params["mode"]?.takeIf { transport == TransportType.GRPC } ?: "gun",
            grpcAuthority = params["authority"].orEmpty(),
            xhttpMode = params["mode"]?.takeIf { transport == TransportType.XHTTP } ?: "auto",
            xhttpExtraJson = params["extra"].orEmpty(),
            security = security,
            sni = params["sni"].orEmpty(),
            alpn = params["alpn"].orEmpty(),
            fingerprint = params["fp"].orEmpty(),
            allowInsecure = allowInsecure,
            realityPublicKey = params["pbk"].orEmpty(),
            realityShortId = params["sid"].orEmpty(),
            realitySpiderX = params["spx"].orEmpty(),
            rawUri = trimmed,
            extraParams = extraParams
        )

        return when (val v = ConfigValidator.validateProfile(profile)) {
            ValidationResult.Valid -> ParseOutcome.Success(profile)
            is ValidationResult.Invalid -> ParseOutcome.Failure(v.message, raw)
        }
    }
}
