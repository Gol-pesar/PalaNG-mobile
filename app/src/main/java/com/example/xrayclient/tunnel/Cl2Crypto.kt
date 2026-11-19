package com.example.xrayclient.tunnel

import com.example.xrayclient.utils.NetworkUtils
import java.net.InetAddress
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-Kotlin cryptographic & binary packet codec for the Cl2 WebSocket tunnel protocol.
 * Matches `Cl2_82497904.py` byte-for-byte:
 * - `deriveKey(nonce)`: `key[i] = uuidBytes[i % uuidBytes.size] xor nonce[i]` (16 bytes)
 * - `buildInitPacket(host, port)`: `nonce(16) + xor(body, key) + hmacSha256(key, enc)[0..7]`
 *   where `body = 0x01 + uint16_be(port) + at(1=IPv4, 2=Domain, 3=IPv6) + addr + payload`
 * - `verifyAck(ack, key)`: `xor(ack, key) == hmacSha256(key, "OK")[0..15]`
 */
object Cl2Crypto {

    private val random = SecureRandom()
    private val OK_BYTES = "OK".toByteArray(Charsets.US_ASCII)

    fun randomNonce(): ByteArray = ByteArray(16).also { random.nextBytes(it) }

    fun deriveKey(uuid: String, nonce: ByteArray): ByteArray {
        require(nonce.size == 16) { "Nonce must be 16 bytes" }
        val tokenBytes = uuid.trim().toByteArray(Charsets.UTF_8)
        require(tokenBytes.isNotEmpty()) { "UUID/token must not be empty" }
        return ByteArray(16) { i ->
            (tokenBytes[i % tokenBytes.size].toInt() xor nonce[i].toInt()).toByte()
        }
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun xorTransform(
        data: ByteArray,
        key: ByteArray,
        offset: Int = 0,
        length: Int = data.size - offset
    ): ByteArray {
        val out = ByteArray(length)
        val keyLen = key.size
        for (i in 0 until length) {
            out[i] = (data[offset + i].toInt() xor key[i % keyLen].toInt()).toByte()
        }
        return out
    }

    fun xorInPlace(
        data: ByteArray,
        length: Int,
        key: ByteArray
    ) {
        val keyLen = key.size
        if (keyLen == 16) {
            for (i in 0 until length) {
                data[i] = (data[i].toInt() xor key[i and 15].toInt()).toByte()
            }
        } else {
            for (i in 0 until length) {
                data[i] = (data[i].toInt() xor key[i % keyLen].toInt()).toByte()
            }
        }
    }

    fun buildInitPacket(
        uuid: String,
        host: String,
        port: Int,
        payload: ByteArray = ByteArray(0),
        nonce: ByteArray = randomNonce()
    ): Pair<ByteArray, ByteArray> {
        val key = deriveKey(uuid, nonce)
        val (at, addrBytes) = encodeAddress(host)

        val body = ByteArray(1 + 2 + 1 + addrBytes.size + payload.size)
        body[0] = 1
        body[1] = ((port ushr 8) and 0xFF).toByte()
        body[2] = (port and 0xFF).toByte()
        body[3] = at.toByte()
        System.arraycopy(addrBytes, 0, body, 4, addrBytes.size)
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, body, 4 + addrBytes.size, payload.size)
        }

        val enc = xorTransform(body, key)
        val mac8 = hmacSha256(key, enc).copyOfRange(0, 8)

        val packet = ByteArray(nonce.size + enc.size + mac8.size)
        System.arraycopy(nonce, 0, packet, 0, nonce.size)
        System.arraycopy(enc, 0, packet, nonce.size, enc.size)
        System.arraycopy(mac8, 0, packet, nonce.size + enc.size, mac8.size)
        return packet to key
    }

    fun verifyAck(ack: ByteArray, key: ByteArray): Boolean {
        if (ack.size < 16) return false
        val plain = xorTransform(ack, key)
        val expected = hmacSha256(key, OK_BYTES).copyOfRange(0, 16)
        return plain.contentEquals(expected)
    }

    private fun encodeAddress(rawHost: String): Pair<Int, ByteArray> {
        val host = rawHost.trim().removePrefix("[").removeSuffix("]")
        if (NetworkUtils.isValidIpv4(host)) {
            val parts = host.split('.').map { it.toInt().toByte() }.toByteArray()
            return 1 to parts
        }
        if (NetworkUtils.isValidIpv6Literal(host)) {
            val ipv6Bytes = InetAddress.getByName(host).address
            if (ipv6Bytes.size == 16) {
                return 3 to ipv6Bytes
            }
        }
        val domainBytes = host.toByteArray(Charsets.UTF_8)
        require(domainBytes.size <= 255) { "Domain name exceeds 255 bytes" }
        val prefixed = ByteArray(1 + domainBytes.size)
        prefixed[0] = domainBytes.size.toByte()
        System.arraycopy(domainBytes, 0, prefixed, 1, domainBytes.size)
        return 2 to prefixed
    }
}
