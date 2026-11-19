package com.example.xrayclient.domain.parsers

import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.domain.validators.ConfigValidator
import com.example.xrayclient.domain.validators.ValidationResult
import com.example.xrayclient.tunnel.Cl2Config
import com.example.xrayclient.utils.LinkUtils

/**
 * Parses `amir://` URIs carrying the 3 configurable AMIR tunnel fields (`UUID`, `CLEAN IP`, `WORKER URL`):
 * `amir://<uuid>@<clean_ip>[:port]?worker=<worker_url>[&subproto=chat.v2][#<name>]`
 */
class AmirUriParser : ProxyUriParser {
    override val supportedSchemes: Set<String> = setOf("amir")

    override fun canParse(raw: String): Boolean =
        raw.trim().startsWith("amir://", ignoreCase = true)

    override fun parse(raw: String): ParseOutcome {
        val trimmed = raw.trim()
        if (!canParse(trimmed)) return ParseOutcome.Failure("فرمت لینک AMIR نیست.", raw)

        val body = trimmed.substringAfter("://")
        val hashIdx = body.indexOf('#')
        val withoutFragment = if (hashIdx >= 0) body.substring(0, hashIdx) else body
        val remark = if (hashIdx >= 0) LinkUtils.urlDecode(body.substring(hashIdx + 1)).trim() else ""

        val queryIdx = withoutFragment.indexOf('?')
        val authority = (if (queryIdx >= 0) withoutFragment.substring(0, queryIdx) else withoutFragment).trimEnd('/')
        val queryStr = if (queryIdx >= 0) withoutFragment.substring(queryIdx + 1) else null
        val params = LinkUtils.parseQueryParameters(queryStr)

        val atIdx = authority.lastIndexOf('@')
        if (atIdx <= 0) {
            return ParseOutcome.Failure("شناسه UUID در لینک AMIR یافت نشد.", raw)
        }
        val uuid = LinkUtils.urlDecode(authority.substring(0, atIdx)).trim()
        val cleanEndpoint = authority.substring(atIdx + 1).trim()

        val (cleanIp, port) = parseOptionalHostPort(cleanEndpoint)
            ?: return ParseOutcome.Failure("CLEAN IP یا پورت محلی AMIR نامعتبر است.", raw)

        val workerUrl = (params["worker"] ?: params["workerUrl"] ?: params["host"]).orEmpty().trim()
        val extraParams = buildMap {
            params["subproto"]?.trim()?.takeIf { it.isNotEmpty() }?.let { put("subproto", it) }
            val ignored = setOf("worker", "workerUrl", "host", "subproto")
            params.forEach { (k, v) -> if (k !in ignored) put(k, v) }
        }

        val profile = ServerProfile(
            name = remark.ifBlank { "AMIR" },
            protocol = ProxyProtocol.AMIR,
            address = cleanIp,
            port = port,
            uuid = uuid,
            host = workerUrl,
            socksUdpAssociate = false,
            rawUri = trimmed,
            extraParams = extraParams
        )

        return when (val v = ConfigValidator.validateProfile(profile)) {
            ValidationResult.Valid -> ParseOutcome.Success(profile)
            is ValidationResult.Invalid -> ParseOutcome.Failure(v.message, raw)
        }
    }

    private fun parseOptionalHostPort(raw: String): Pair<String, Int>? {
        if (raw.isEmpty()) return "" to Cl2Config.DEFAULT_LISTEN_PORT
        if (raw.startsWith(":")) {
            val port = raw.substring(1).toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return "" to port
        }
        if (raw.startsWith("[")) {
            val closeIdx = raw.indexOf(']')
            if (closeIdx <= 1) return null
            val host = raw.substring(1, closeIdx)
            val rest = raw.substring(closeIdx + 1)
            if (rest.isEmpty()) return host to Cl2Config.DEFAULT_LISTEN_PORT
            if (!rest.startsWith(":")) return null
            val port = rest.substring(1).toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            return host to port
        }
        val colonIdx = raw.lastIndexOf(':')
        return if (colonIdx < 0) {
            raw to Cl2Config.DEFAULT_LISTEN_PORT
        } else {
            val host = raw.substring(0, colonIdx)
            if (host.contains(':')) return null
            val port = raw.substring(colonIdx + 1).toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            host to port
        }
    }
}
