package com.example.xrayclient.domain.parsers

import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.domain.validators.ConfigValidator
import com.example.xrayclient.domain.validators.ValidationResult
import com.example.xrayclient.utils.LinkUtils

/**
 * Parses unambiguous HTTP/HTTPS proxy URIs (`http://[user:pass@]host:port[#remark]` or `https://[user:pass@]host:port[?sni=...][#remark]`)
 * into Xray `http` outbound profiles (NOT VLESS-style schemes).
 *
 * Subscription URLs (e.g., `https://panel.example.com/sub/token`) are intentionally rejected here
 * so [BatchConfigImporter] routes them to subscription management instead of misclassifying them as proxies.
 */
class HttpProxyParser : ProxyUriParser {
    override val supportedSchemes: Set<String> = setOf("http", "https")

    override fun canParse(raw: String): Boolean {
        val trimmed = raw.trim()
        return (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)) &&
            !looksLikeSubscriptionUrl(trimmed)
    }

    override fun parse(raw: String): ParseOutcome {
        val trimmed = raw.trim()
        val scheme = trimmed.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") {
            return ParseOutcome.Failure("فرمت لینک پروکسی HTTP/HTTPS نیست.", raw)
        }
        if (looksLikeSubscriptionUrl(trimmed)) {
            return ParseOutcome.Failure("این نشانی به نظر یک لینک اشتراک است نه پروکسی HTTP.", raw)
        }

        val body = trimmed.substringAfter("://")
        val hashIdx = body.indexOf('#')
        val withoutFragment = if (hashIdx >= 0) body.substring(0, hashIdx) else body
        val remark = if (hashIdx >= 0) LinkUtils.urlDecode(body.substring(hashIdx + 1)).trim() else ""

        val queryIdx = withoutFragment.indexOf('?')
        val beforeQuery = if (queryIdx >= 0) withoutFragment.substring(0, queryIdx) else withoutFragment
        val queryStr = if (queryIdx >= 0) withoutFragment.substring(queryIdx + 1) else null

        val authority = beforeQuery.trimEnd('/')
        val atIdx = authority.lastIndexOf('@')
        val (username, password, hostPortStr) = if (atIdx >= 0) {
            val userInfo = authority.substring(0, atIdx)
            val colonIdx = userInfo.indexOf(':')
            val user = if (colonIdx >= 0) LinkUtils.urlDecode(userInfo.substring(0, colonIdx)) else LinkUtils.urlDecode(userInfo)
            val pass = if (colonIdx >= 0) LinkUtils.urlDecode(userInfo.substring(colonIdx + 1)) else ""
            Triple(user, pass, authority.substring(atIdx + 1))
        } else {
            Triple("", "", authority)
        }

        val hostPort = LinkUtils.parseHostPort(hostPortStr)
            ?: return ParseOutcome.Failure("آدرس یا پورت پروکسی HTTP باید صراحتاً مشخص باشد (host:port).", raw)

        val params = LinkUtils.parseQueryParameters(queryStr)
        val security = if (scheme == "https" || params["security"].equals("tls", true)) {
            SecurityType.TLS
        } else {
            SecurityType.NONE
        }

        val profile = ServerProfile(
            name = remark.ifBlank { "${hostPort.host}:${hostPort.port}" },
            protocol = ProxyProtocol.HTTP,
            address = hostPort.host,
            port = hostPort.port,
            username = username,
            password = password,
            security = security,
            sni = params["sni"].orEmpty(),
            alpn = params["alpn"].orEmpty(),
            allowInsecure = params["allowInsecure"] == "1" || params["allowInsecure"].equals("true", true),
            rawUri = trimmed,
            extraParams = params.filterKeys { it !in setOf("security", "sni", "alpn", "allowInsecure") }
        )

        return when (val v = ConfigValidator.validateProfile(profile)) {
            ValidationResult.Valid -> ParseOutcome.Success(profile)
            is ValidationResult.Invalid -> ParseOutcome.Failure(v.message, raw)
        }
    }

    companion object {
        fun looksLikeSubscriptionUrl(uri: String): Boolean {
            val body = uri.substringAfter("://", "")
            if (body.isEmpty()) return false
            val withoutFrag = body.substringBefore('#').substringBefore('?')
            val slashIdx = withoutFrag.indexOf('/')
            // If there is a non-empty path (e.g., /sub/xyz or /api/v1/client), it's a web/subscription URL
            if (slashIdx >= 0 && withoutFrag.substring(slashIdx).trimEnd('/').isNotEmpty()) {
                return true
            }
            val authority = withoutFrag.trimEnd('/')
            val hostPortPart = authority.substringAfterLast('@')
            // Unambiguous HTTP proxy URIs must specify an explicit :port
            return LinkUtils.parseHostPort(hostPortPart) == null
        }
    }
}
