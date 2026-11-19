package com.example.xrayclient.utils

import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin RFC 4648 Base64 codec supporting Standard, URL-safe, unpadded,
 * and whitespace-containing subscription/VMess/Shadowsocks payloads across API 24+ and JVM tests.
 */
object Base64Utils {

    private const val ENCODE_TABLE =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val ENCODE_URL_TABLE =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val DECODE_TABLE = IntArray(256) { -1 }.apply {
        for (i in ENCODE_TABLE.indices) {
            this[ENCODE_TABLE[i].code] = i
        }
        this['-'.code] = 62
        this['_'.code] = 63
    }

    fun decodeToBytes(input: String): ByteArray? {
        val cleaned = buildString(input.length) {
            for (ch in input) {
                if (!ch.isWhitespace()) append(ch)
            }
        }
        if (cleaned.isEmpty()) return ByteArray(0)

        // Strip trailing padding and validate length mod 4 != 1
        var end = cleaned.length
        while (end > 0 && cleaned[end - 1] == '=') {
            end--
        }
        val unpaddedLen = end
        if (unpaddedLen % 4 == 1) return null

        val outLen = (unpaddedLen * 3) / 4
        val out = ByteArray(outLen)
        var outIndex = 0
        var buffer = 0
        var bitsLeft = 0

        for (i in 0 until unpaddedLen) {
            val code = cleaned[i].code
            if (code >= 256) return null
            val value = DECODE_TABLE[code]
            if (value < 0) return null

            buffer = (buffer shl 6) or value
            bitsLeft += 6
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out[outIndex++] = ((buffer shr bitsLeft) and 0xFF).toByte()
            }
        }
        return if (outIndex == outLen) out else out.copyOf(outIndex)
    }

    fun decodeToString(input: String): String? {
        val bytes = decodeToBytes(input) ?: return null
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun encode(bytes: ByteArray, urlSafe: Boolean = false, pad: Boolean = true): String {
        if (bytes.isEmpty()) return ""
        val table = if (urlSafe) ENCODE_URL_TABLE else ENCODE_TABLE
        val sb = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val hasB1 = i + 1 < bytes.size
            val b1 = if (hasB1) bytes[i + 1].toInt() and 0xFF else 0
            val hasB2 = i + 2 < bytes.size
            val b2 = if (hasB2) bytes[i + 2].toInt() and 0xFF else 0

            sb.append(table[b0 shr 2])
            sb.append(table[((b0 and 0x03) shl 4) or (b1 shr 4)])
            if (hasB1) {
                sb.append(table[((b1 and 0x0F) shl 2) or (b2 shr 6)])
            } else if (pad) {
                sb.append('=')
            }
            if (hasB2) {
                sb.append(table[b2 and 0x3F])
            } else if (pad) {
                sb.append('=')
            }
            i += 3
        }
        return sb.toString()
    }

    fun encodeString(text: String, urlSafe: Boolean = false, pad: Boolean = true): String =
        encode(text.toByteArray(StandardCharsets.UTF_8), urlSafe = urlSafe, pad = pad)

    fun looksLikeBase64(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 8) return false
        if (trimmed.contains("://")) return false
        val noWs = trimmed.filterNot { it.isWhitespace() }
        if (noWs.length % 4 == 1) return false
        return noWs.all { ch ->
            ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                ch == '+' || ch == '/' || ch == '-' || ch == '_' || ch == '='
        }
    }
}
