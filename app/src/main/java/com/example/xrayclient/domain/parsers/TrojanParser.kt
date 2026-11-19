package com.example.xrayclient.domain.parsers

import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.data.models.TransportType
import com.example.xrayclient.domain.validators.ConfigValidator
import com.example.xrayclient.domain.validators.ValidationResult
import com.example.xrayclient.utils.LinkUtils

/**
 * Parses Trojan share links (`trojan://password@host:port?params#remark`).
 */
class TrojanParser : ProxyUriParser {
    override val supportedSchemes: Set<String> = setOf("trojan")

    override fun canParse(raw: String): Boolean =
        raw.trim().startsWith("trojan://", ignoreCase = true)

    override fun parse(raw: String): ParseOutcome {
        val trimmed = raw.trim()
        if (!canParse(trimmed)) return ParseOutcome.Failure("فرمت لینک Trojan نیست.", raw)

        val body = trimmed.substringAfter("://")
        val hashIdx = body.indexOf('#')
        val withoutFragment = if (hashIdx >= 0) body.substring(0, hashIdx) else body
        val remark = if (hashIdx >= 0) LinkUtils.urlDecode(body.substring(hashIdx + 1)).trim() else ""

        val queryIdx = withoutFragment.indexOf('?')
        val authority = if (queryIdx >= 0) withoutFragment.substring(0, queryIdx) else withoutFragment
        val queryStr = if (queryIdx >= 0) withoutFragment.substring(queryIdx + 1) else null

        val atIdx = authority.lastIndexOf('@')
        if (atIdx <= 0) {
            return ParseOutcome.Failure("رمز عبور Trojan در لینک یافت نشد.", raw)
        }
        val password = LinkUtils.urlDecode(authority.substring(0, atIdx))
        val hostPort = LinkUtils.parseHostPort(authority.substring(atIdx + 1))
            ?: return ParseOutcome.Failure("آدرس یا پورت Trojan نامعتبر است.", raw)

        val params = LinkUtils.parseQueryParameters(queryStr)
        val knownKeys = setOf(
            "security", "sni", "peer", "alpn", "fp", "allowInsecure", "type",
            "path", "host", "headerType", "serviceName", "mode", "pbk", "sid", "spx"
        )
        val extraParams = params.filterKeys { it !in knownKeys }

        val transport = TransportType.fromRaw(params["type"])
        // Trojan defaults to TLS unless explicitly set to none or reality
        val securityRaw = params["security"]
        val security = if (securityRaw.isNullOrBlank()) {
            SecurityType.TLS
        } else {
            SecurityType.fromRaw(securityRaw)
        }

        val allowInsecure = params["allowInsecure"]?.let { it == "1" || it.equals("true", true) } ?: false

        val profile = ServerProfile(
            name = remark.ifBlank { "${hostPort.host}:${hostPort.port}" },
            protocol = ProxyProtocol.TROJAN,
            address = hostPort.host,
            port = hostPort.port,
            password = password,
            transport = transport,
            path = params["path"].orEmpty(),
            host = params["host"].orEmpty(),
            headerType = params["headerType"].orEmpty(),
            serviceName = params["serviceName"].orEmpty(),
            grpcMode = params["mode"]?.takeIf { transport == TransportType.GRPC } ?: "gun",
            security = security,
            sni = params["sni"]?.ifBlank { params["peer"].orEmpty() }.orEmpty(),
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
