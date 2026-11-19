package com.example.xrayclient.utils

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Helpers for URI percent-encoding/decoding, IPv6 bracketed host:port splitting,
 * and preserving unknown query parameters.
 */
object LinkUtils {

    data class HostPort(val host: String, val port: Int)

    fun urlDecode(value: String): String {
        return try {
            // Preserve literal '+' if not form-encoded? In proxy URIs remarks/paths often use %20 or +
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }

    fun urlDecodeQueryValue(value: String): String {
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }

    fun urlEncode(value: String): String {
        return try {
            URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
        } catch (_: Exception) {
            value
        }
    }

    /**
     * Parses `host:port` or `[ipv6]:port` safely.
     */
    fun parseHostPort(authorityHostPort: String): HostPort? {
        val trimmed = authorityHostPort.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("[")) {
            val closeIdx = trimmed.indexOf(']')
            if (closeIdx <= 1) return null
            val host = trimmed.substring(1, closeIdx)
            val rest = trimmed.substring(closeIdx + 1)
            if (!rest.startsWith(":")) return null
            val port = rest.substring(1).toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            HostPort(host, port)
        } else {
            val colonIdx = trimmed.lastIndexOf(':')
            if (colonIdx <= 0 || colonIdx == trimmed.length - 1) return null
            val host = trimmed.substring(0, colonIdx)
            if (host.contains(':')) return null // Unbracketed IPv6 with port is ambiguous
            val port = trimmed.substring(colonIdx + 1).toIntOrNull() ?: return null
            if (port !in 1..65535) return null
            HostPort(host, port)
        }
    }

    fun formatHostForUri(host: String): String {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        return if (clean.contains(':')) "[$clean]" else clean
    }

    fun parseQueryParameters(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        rawQuery.split('&').forEach { pair ->
            if (pair.isEmpty()) return@forEach
            val eqIdx = pair.indexOf('=')
            if (eqIdx <= 0) {
                val key = urlDecodeQueryValue(pair)
                if (key.isNotEmpty()) result[key] = ""
            } else {
                val key = urlDecodeQueryValue(pair.substring(0, eqIdx))
                val value = urlDecodeQueryValue(pair.substring(eqIdx + 1))
                if (key.isNotEmpty()) result[key] = value
            }
        }
        return result
    }

    fun splitSchemeRest(uri: String): Pair<String, String>? {
        val idx = uri.indexOf("://")
        if (idx <= 0) return null
        return uri.substring(0, idx).lowercase() to uri.substring(idx + 3)
    }

    /**
     * Masks sensitive secrets for safe diagnostic display/logging.
     */
    fun redactSecret(value: String): String {
        if (value.length <= 4) return "****"
        return "${value.take(2)}***${value.takeLast(2)}"
    }
}
