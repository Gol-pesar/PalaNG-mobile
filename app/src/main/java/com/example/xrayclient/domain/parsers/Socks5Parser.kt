package com.example.xrayclient.domain.parsers

import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.domain.validators.ConfigValidator
import com.example.xrayclient.domain.validators.ValidationResult
import com.example.xrayclient.utils.Base64Utils
import com.example.xrayclient.utils.LinkUtils

/**
 * Parses SOCKS5 proxy URIs (`socks5://[user:pass@]host:port[#remark]` and `socks://[base64(user:pass)|user:pass@]host:port[#remark]`)
 * into Xray `socks` outbound profiles.
 */
class Socks5Parser : ProxyUriParser {
    override val supportedSchemes: Set<String> = setOf("socks", "socks5")

    override fun canParse(raw: String): Boolean {
        val trimmed = raw.trim()
        return trimmed.startsWith("socks5://", ignoreCase = true) ||
            trimmed.startsWith("socks://", ignoreCase = true)
    }

    override fun parse(raw: String): ParseOutcome {
        val trimmed = raw.trim()
        if (!canParse(trimmed)) return ParseOutcome.Failure("فرمت لینک SOCKS5 نیست.", raw)

        val body = trimmed.substringAfter("://")
        val hashIdx = body.indexOf('#')
        val withoutFragment = if (hashIdx >= 0) body.substring(0, hashIdx) else body
        val remark = if (hashIdx >= 0) LinkUtils.urlDecode(body.substring(hashIdx + 1)).trim() else ""

        val queryIdx = withoutFragment.indexOf('?')
        val authority = (if (queryIdx >= 0) withoutFragment.substring(0, queryIdx) else withoutFragment).trimEnd('/')
        val queryStr = if (queryIdx >= 0) withoutFragment.substring(queryIdx + 1) else null
        val params = LinkUtils.parseQueryParameters(queryStr)

        val atIdx = authority.lastIndexOf('@')
        val (username, password, hostPortStr) = if (atIdx >= 0) {
            val rawUserInfo = authority.substring(0, atIdx)
            val decodedUserPass = if (rawUserInfo.contains(':')) {
                val idx = rawUserInfo.indexOf(':')
                LinkUtils.urlDecode(rawUserInfo.substring(0, idx)) to
                    LinkUtils.urlDecode(rawUserInfo.substring(idx + 1))
            } else {
                val b64 = Base64Utils.decodeToString(LinkUtils.urlDecode(rawUserInfo))
                if (b64 != null && b64.contains(':')) {
                    val idx = b64.indexOf(':')
                    b64.substring(0, idx) to b64.substring(idx + 1)
                } else {
                    LinkUtils.urlDecode(rawUserInfo) to ""
                }
            }
            Triple(decodedUserPass.first, decodedUserPass.second, authority.substring(atIdx + 1))
        } else {
            Triple("", "", authority)
        }

        val hostPort = LinkUtils.parseHostPort(hostPortStr)
            ?: return ParseOutcome.Failure("آدرس یا پورت SOCKS5 نامعتبر است.", raw)

        val udpEnabled = params["udp"]?.let { it != "0" && !it.equals("false", true) } ?: true
        val security = SecurityType.fromRaw(params["security"])

        val profile = ServerProfile(
            name = remark.ifBlank { "${hostPort.host}:${hostPort.port}" },
            protocol = ProxyProtocol.SOCKS5,
            address = hostPort.host,
            port = hostPort.port,
            username = username,
            password = password,
            socksUdpAssociate = udpEnabled,
            security = security,
            sni = params["sni"].orEmpty(),
            rawUri = trimmed,
            extraParams = params.filterKeys { it !in setOf("udp", "security", "sni") }
        )

        return when (val v = ConfigValidator.validateProfile(profile)) {
            ValidationResult.Valid -> ParseOutcome.Success(profile)
            is ValidationResult.Invalid -> ParseOutcome.Failure(v.message, raw)
        }
    }
}
