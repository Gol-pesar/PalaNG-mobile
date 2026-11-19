package com.example.xrayclient.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

object NetworkUtils {

    private val IPV4_REGEX = Regex(
        "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
    )

    private val DOMAIN_REGEX = Regex(
        "^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9-]{1,63}$"
    )

    fun isValidHostOrIp(host: String): Boolean {
        val clean = host.trim().removePrefix("[").removeSuffix("]")
        if (clean.isEmpty()) return false
        if (IPV4_REGEX.matches(clean)) return true
        if (clean.contains(':') && isValidIpv6Literal(clean)) return true
        return DOMAIN_REGEX.matches(clean)
    }

    fun isValidIpv4(ip: String): Boolean = IPV4_REGEX.matches(ip.trim())

    fun isValidIpv6Literal(ip: String): Boolean {
        val clean = ip.trim().removePrefix("[").removeSuffix("]")
        if (!clean.contains(':')) return false
        return try {
            // Avoid DNS lookup by checking hex/colon structure before InetAddress
            if (!clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }) {
                return false
            }
            InetAddress.getByName(clean).address.size == 16
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Measures real TCP handshake latency to the server endpoint off the main thread.
     * Returns elapsed milliseconds or -1L on timeout/error.
     */
    suspend fun measureTcpLatencyMs(
        host: String,
        port: Int,
        timeoutMs: Int = 3500
    ): Long = withContext(Dispatchers.IO) {
        if (host.isBlank() || port !in 1..65535) return@withContext -1L
        val socket = Socket()
        try {
            val startNs = System.nanoTime()
            socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
            elapsedMs.coerceAtLeast(1L)
        } catch (_: Exception) {
            -1L
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    fun formatBytes(bytes: Long): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe < 1024L -> "$safe B"
            safe < 1024L * 1024L -> String.format("%.1f KB", safe / 1024.0)
            safe < 1024L * 1024L * 1024L -> String.format("%.2f MB", safe / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", safe / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        val safe = bytesPerSec.coerceAtLeast(0L)
        return when {
            safe < 1024L -> "$safe B/s"
            safe < 1024L * 1024L -> String.format("%.1f KB/s", safe / 1024.0)
            else -> String.format("%.2f MB/s", safe / (1024.0 * 1024.0))
        }
    }

    fun formatDuration(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val seconds = safe % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
