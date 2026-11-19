package com.example.xrayclient.domain.parsers

import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.domain.validators.ConfigValidator
import com.example.xrayclient.domain.validators.ValidationResult
import com.example.xrayclient.utils.Base64Utils
import com.example.xrayclient.utils.LinkUtils

/**
 * Parses Shadowsocks SIP002 (`ss://base64(method:password)@host:port?plugin=...#remark`),
 * plain userinfo (`ss://method:password@host:port#remark`), and legacy (`ss://base64(method:password@host:port)#remark`).
 */
class ShadowsocksParser : ProxyUriParser {
    override val supportedSchemes: Set<String> = setOf("ss")

    override fun canParse(raw: String): Boolean =
        raw.trim().startsWith("ss://", ignoreCase = true)

    override fun parse(raw: String): ParseOutcome {
        val trimmed = raw.trim()
        if (!canParse(trimmed)) return ParseOutcome.Failure("فرمت لینک Shadowsocks نیست.", raw)

        val body = trimmed.substringAfter("://")
        val hashIdx = body.indexOf('#')
        val withoutFragment = if (hashIdx >= 0) body.substring(0, hashIdx) else body
        val remark = if (hashIdx >= 0) LinkUtils.urlDecode(body.substring(hashIdx + 1)).trim() else ""

        val queryIdx = withoutFragment.indexOf('?')
        val mainPart = if (queryIdx >= 0) withoutFragment.substring(0, queryIdx) else withoutFragment
        val queryStr = if (queryIdx >= 0) withoutFragment.substring(queryIdx + 1) else null
        val params = LinkUtils.parseQueryParameters(queryStr)

        val parsed = if (mainPart.contains('@')) {
            parseSip002(mainPart)
        } else {
            parseLegacy(mainPart)
        } ?: return ParseOutcome.Failure("ساختار لینک Shadowsocks نامعتبر است.", raw)

        val (method, password, hostPort) = parsed
        val profile = ServerProfile(
            name = remark.ifBlank { "${hostPort.host}:${hostPort.port}" },
            protocol = ProxyProtocol.SHADOWSOCKS,
            address = hostPort.host,
            port = hostPort.port,
            password = password,
            ssMethod = method,
            rawUri = trimmed,
            extraParams = params
        )

        return when (val v = ConfigValidator.validateProfile(profile)) {
            ValidationResult.Valid -> ParseOutcome.Success(profile)
            is ValidationResult.Invalid -> ParseOutcome.Failure(v.message, raw)
        }
    }

    private fun parseSip002(mainPart: String): Triple<String, String, LinkUtils.HostPort>? {
        val atIdx = mainPart.lastIndexOf('@')
        if (atIdx <= 0) return null
        val userInfoRaw = mainPart.substring(0, atIdx)
        val hostPartRaw = mainPart.substring(atIdx + 1).substringBefore('/')
        val hostPort = LinkUtils.parseHostPort(hostPartRaw) ?: return null

        // UserInfo can be URL-encoded "method:password" or Base64("method:password")
        val methodPass = if (userInfoRaw.contains(':')) {
            LinkUtils.urlDecode(userInfoRaw)
        } else {
            Base64Utils.decodeToString(LinkUtils.urlDecode(userInfoRaw)) ?: return null
        }

        val colonIdx = methodPass.indexOf(':')
        if (colonIdx <= 0) return null
        val method = methodPass.substring(0, colonIdx).trim()
        val password = methodPass.substring(colonIdx + 1)
        return Triple(method, password, hostPort)
    }

    private fun parseLegacy(encodedMain: String): Triple<String, String, LinkUtils.HostPort>? {
        val decoded = Base64Utils.decodeToString(encodedMain.substringBefore('/')) ?: return null
        val atIdx = decoded.lastIndexOf('@')
        if (atIdx <= 0) return null
        val methodPass = decoded.substring(0, atIdx)
        val hostPort = LinkUtils.parseHostPort(decoded.substring(atIdx + 1)) ?: return null
        val colonIdx = methodPass.indexOf(':')
        if (colonIdx <= 0) return null
        val method = methodPass.substring(0, colonIdx).trim()
        val password = methodPass.substring(colonIdx + 1)
        return Triple(method, password, hostPort)
    }
}
