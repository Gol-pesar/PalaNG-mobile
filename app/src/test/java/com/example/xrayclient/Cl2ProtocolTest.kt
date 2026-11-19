package com.example.xrayclient

import com.example.xrayclient.data.models.AppSettings
import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.tunnel.Cl2Config
import com.example.xrayclient.tunnel.Cl2Crypto
import com.example.xrayclient.tunnel.Cl2ProxyServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Cl2ProtocolTest {

    @Test
    fun `deriveKey xor matches Cl2_82497904 python specification`() {
        val uuid = Cl2Config.DEFAULT_UUID
        val nonce = ByteArray(16) { it.toByte() }
        val key = Cl2Crypto.deriveKey(uuid, nonce)
        val uuidBytes = uuid.toByteArray(Charsets.UTF_8)

        assertEquals(16, key.size)
        for (i in 0 until 16) {
            assertEquals((uuidBytes[i].toInt() xor i).toByte(), key[i])
        }
    }

    @Test
    fun `buildInitPacket encodes domain IPv4 IPv6 and 8-byte HMAC matching Python`() {
        val uuid = Cl2Config.DEFAULT_UUID
        val nonce = ByteArray(16) { (it + 1).toByte() }

        // Domain target -> at = 2
        val (domainPkt, key) = Cl2Crypto.buildInitPacket(uuid, "example.com", 443, nonce = nonce)
        val encLen = domainPkt.size - 16 - 8
        val enc = domainPkt.copyOfRange(16, 16 + encLen)
        val mac8 = domainPkt.copyOfRange(16 + encLen, domainPkt.size)
        val expectedMac8 = Cl2Crypto.hmacSha256(key, enc).copyOfRange(0, 8)
        assertTrue(mac8.contentEquals(expectedMac8))

        val plain = Cl2Crypto.xorTransform(enc, key)
        assertEquals(1, plain[0].toInt())
        assertEquals(0x01, plain[1].toInt() and 0xFF)
        assertEquals(0xBB, plain[2].toInt() and 0xFF) // 443
        assertEquals(2, plain[3].toInt()) // Domain at=2
        assertEquals(11, plain[4].toInt()) // "example.com".length
        assertEquals("example.com", String(plain, 5, 11, Charsets.UTF_8))

        // IPv4 target -> at = 1
        val (ipv4Pkt, _) = Cl2Crypto.buildInitPacket(uuid, "1.1.1.1", 80, nonce = nonce)
        val ipv4Plain = Cl2Crypto.xorTransform(ipv4Pkt.copyOfRange(16, ipv4Pkt.size - 8), key)
        assertEquals(1, ipv4Plain[3].toInt())
        assertEquals(8, ipv4Plain.size) // 1 + 2 + 1 + 4

        // IPv6 target -> at = 3
        val (ipv6Pkt, _) = Cl2Crypto.buildInitPacket(uuid, "2606:4700:4700::1111", 443, nonce = nonce)
        val ipv6Plain = Cl2Crypto.xorTransform(ipv6Pkt.copyOfRange(16, ipv6Pkt.size - 8), key)
        assertEquals(3, ipv6Plain[3].toInt())
        assertEquals(20, ipv6Plain.size) // 1 + 2 + 1 + 16
    }

    @Test
    fun `verifyAck validates encrypted HMAC-SHA256 OK tag`() {
        val key = Cl2Crypto.deriveKey(Cl2Config.DEFAULT_UUID, ByteArray(16) { 0x5A })
        val okDigest16 = Cl2Crypto.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII)).copyOfRange(0, 16)
        val encryptedAck = Cl2Crypto.xorTransform(okDigest16, key)

        assertTrue(Cl2Crypto.verifyAck(encryptedAck, key))
        val tampered = encryptedAck.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        assertFalse(Cl2Crypto.verifyAck(tampered, key))
        assertFalse(Cl2Crypto.verifyAck(ByteArray(10), key))
    }

    @Test
    fun `Cl2ProxyServer handles AMIR profiles with per-profile clean ip, worker url and uuid`() {
        val settings = AppSettings()
        val matchingSocks = ServerProfile(
            name = "Cl2",
            protocol = ProxyProtocol.SOCKS5,
            address = "127.0.0.1",
            port = 40446
        )
        val amirProfile = ServerProfile(
            name = "AMIR Custom",
            protocol = ProxyProtocol.AMIR,
            address = "104.16.1.2",
            port = 40446,
            uuid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            host = "https://custom-worker.workers.dev/ws"
        )
        val disabled = settings.copy(cl2Enabled = false)

        assertTrue(Cl2ProxyServer.shouldHandleProfile(matchingSocks, settings))
        assertFalse(Cl2ProxyServer.shouldHandleProfile(matchingSocks, disabled))
        assertTrue(Cl2ProxyServer.shouldHandleProfile(amirProfile, disabled))

        val resolved = Cl2ProxyServer.configForProfile(amirProfile, settings)
        assertEquals("104.16.1.2", resolved.cleanIp)
        assertEquals("https://custom-worker.workers.dev/ws", resolved.workerHost)
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", resolved.uuid)
        assertEquals("custom-worker.workers.dev", resolved.parsedEndpoint.host)
        assertEquals(443, resolved.parsedEndpoint.port)
        assertEquals("/ws", resolved.parsedEndpoint.path)
    }

    @Test
    fun `parseWorkerUrl and xorInPlace behave identically to xorTransform`() {
        val bare = Cl2Config.parseWorkerUrl("my.worker.dev")
        assertEquals("my.worker.dev", bare.host)
        assertEquals(443, bare.port)
        assertEquals("/", bare.path)

        val full = Cl2Config.parseWorkerUrl("https://twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev/")
        assertEquals("twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev", full.host)
        assertEquals(443, full.port)
        assertEquals("/", full.path)

        val key = Cl2Crypto.deriveKey(Cl2Config.DEFAULT_UUID, ByteArray(16) { (it * 3).toByte() })
        val payload = ByteArray(1025) { (it and 0xFF).toByte() }
        val expected = Cl2Crypto.xorTransform(payload, key)
        val inPlace = payload.copyOf()
        Cl2Crypto.xorInPlace(inPlace, inPlace.size, key)
        assertTrue(expected.contentEquals(inPlace))
    }

    @Test
    fun `Cl2ProxyServer handles half-close HTTP requests, fragmented frames, and PING control frames offline`() = kotlinx.coroutines.test.runTest {
        val uuid = Cl2Config.DEFAULT_UUID
        val expectedBody = ("HTTP/1.1 200 OK\r\nContent-Length: 210\r\nConnection: close\r\n\r\n" +
            "A".repeat(210)).toByteArray(Charsets.US_ASCII)

        // Mock remote WebSocket endpoint speaking Cl2 + RFC 6455 over localhost ServerSocket
        val mockWsServer = java.net.ServerSocket(0)
        val serverThread = Thread {
            mockWsServer.accept().use { peer ->
                peer.soTimeout = 5000
                val pIn = peer.getInputStream()
                val pOut = peer.getOutputStream()

                fun readMaskedClientFrame(): Pair<Int, ByteArray> {
                    val b0 = pIn.read()
                    val b1 = pIn.read()
                    val opcode = b0 and 0x0F
                    var len = (b1 and 0x7F)
                    if (len == 126) {
                        len = ((pIn.read() and 0xFF) shl 8) or (pIn.read() and 0xFF)
                    }
                    val mask = ByteArray(4) { pIn.read().toByte() }
                    val data = ByteArray(len)
                    var off = 0
                    while (off < len) {
                        val r = pIn.read(data, off, len - off)
                        if (r < 0) break
                        off += r
                    }
                    for (i in data.indices) {
                        data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
                    }
                    return opcode to data
                }

                fun writeServerFrame(fin: Boolean, opcode: Int, payload: ByteArray) {
                    val b0 = (if (fin) 0x80 else 0x00) or (opcode and 0x0F)
                    pOut.write(b0)
                    if (payload.size < 126) {
                        pOut.write(payload.size)
                    } else {
                        pOut.write(126)
                        pOut.write((payload.size ushr 8) and 0xFF)
                        pOut.write(payload.size and 0xFF)
                    }
                    pOut.write(payload)
                }

                // 1. Read Cl2 init packet
                val (initOp, initPkt) = readMaskedClientFrame()
                assertEquals(0x02, initOp)
                val nonce = initPkt.copyOfRange(0, 16)
                val key = Cl2Crypto.deriveKey(uuid, nonce)

                // 2. Send PING first, then encrypted ACK
                writeServerFrame(fin = true, opcode = 0x09, payload = "hi".toByteArray())
                val okTag = Cl2Crypto.hmacSha256(key, "OK".toByteArray(Charsets.US_ASCII)).copyOfRange(0, 16)
                writeServerFrame(fin = true, opcode = 0x02, payload = Cl2Crypto.xorTransform(okTag, key))
                pOut.flush()

                // 3. Expect PONG reply, then upstream HTTP request from client
                var reqText = ""
                while (true) {
                    val (op, payload) = readMaskedClientFrame()
                    if (op == 0x0A) continue // PONG
                    if (op == 0x02) {
                        reqText = String(Cl2Crypto.xorTransform(payload, key), Charsets.US_ASCII)
                        break
                    }
                }
                assertTrue(reqText.startsWith("GET /test HTTP/1.1"))

                // 4. Send response XORed as a single message, split across 2 fragmented WS frames
                // (unaligned first fragment length=37) with an interleaved PING in between!
                val encResp = Cl2Crypto.xorTransform(expectedBody, key)
                val part1 = encResp.copyOfRange(0, 37)
                val part2 = encResp.copyOfRange(37, encResp.size)
                writeServerFrame(fin = false, opcode = 0x02, payload = part1)
                writeServerFrame(fin = true, opcode = 0x09, payload = "mid".toByteArray())
                writeServerFrame(fin = true, opcode = 0x00, payload = part2)
                writeServerFrame(fin = true, opcode = 0x08, payload = ByteArray(0))
                pOut.flush()
            }
        }.apply { isDaemon = true; start() }

        val proxyPort = java.net.ServerSocket(0).use { it.localPort }
        val srv = Cl2ProxyServer {
            val s = java.net.Socket("127.0.0.1", mockWsServer.localPort)
            Cl2ProxyServer.RawWebSocket(s, java.io.BufferedInputStream(s.getInputStream()), s.getOutputStream())
        }
        val cfg = Cl2Config(listenPort = proxyPort, uuid = uuid)
        val startRes = srv.start(cfg)
        assertTrue("start failed: ${startRes.exceptionOrNull()}", startRes.isSuccess)
        try {
            java.net.Socket("127.0.0.1", proxyPort).use { client ->
                client.soTimeout = 5000
                val out = client.getOutputStream()
                val inp = client.getInputStream()

                // SOCKS5 Greeting
                out.write(byteArrayOf(5, 1, 0))
                out.flush()
                val greet = ByteArray(2)
                assertEquals(2, inp.read(greet))
                assertEquals(0, greet[1].toInt())

                // SOCKS5 Connect
                val host = "example.com".toByteArray()
                out.write(byteArrayOf(5, 1, 0, 3, host.size.toByte()) + host + byteArrayOf(0, 80))
                out.flush()
                val rep = ByteArray(10)
                assertEquals(10, inp.read(rep))
                assertEquals(0, rep[1].toInt())

                // Send HTTP request AND immediately TCP half-close (shutdownOutput)
                out.write("GET /test HTTP/1.1\r\nHost: example.com\r\n\r\n".toByteArray())
                out.flush()
                client.shutdownOutput()

                // Read complete fragmented response after half-close
                val received = inp.readBytes()
                assertTrue(
                    "Expected full HTTP 200 response after half-close & WS fragmentation, got ${received.size} bytes",
                    received.contentEquals(expectedBody)
                )
            }
        } finally {
            srv.stop()
            runCatching { mockWsServer.close() }
            serverThread.join(2000)
        }
    }
}
