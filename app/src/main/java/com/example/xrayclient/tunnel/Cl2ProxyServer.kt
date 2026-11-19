package com.example.xrayclient.tunnel

import android.os.Build
import com.example.xrayclient.data.models.AppSettings
import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.utils.Base64Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket

data class Cl2Config(
    val enabled: Boolean = true,
    val uuid: String = DEFAULT_UUID,
    val workerHost: String = DEFAULT_WORKER_HOST,
    val cleanIp: String = DEFAULT_CLEAN_IP,
    val listenHost: String = DEFAULT_LISTEN_HOST,
    val listenPort: Int = DEFAULT_LISTEN_PORT,
    val subprotocol: String = DEFAULT_SUBPROTOCOL
) {
    val parsedEndpoint: ParsedWorkerEndpoint
        get() = parseWorkerUrl(workerHost)

    companion object {
        const val DEFAULT_UUID = "63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd"
        const val DEFAULT_WORKER_HOST = "https://twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev/"
        const val DEFAULT_CLEAN_IP = "172.67.70.244"
        const val DEFAULT_LISTEN_HOST = "127.0.0.1"
        const val DEFAULT_LISTEN_PORT = 40446
        const val DEFAULT_SUBPROTOCOL = "chat.v2"

        data class ParsedWorkerEndpoint(
            val host: String,
            val port: Int,
            val path: String
        )

        fun parseWorkerUrl(raw: String): ParsedWorkerEndpoint {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                return ParsedWorkerEndpoint("", 443, "/")
            }
            val normalized = if ("://" in trimmed) trimmed else "https://$trimmed"
            val parsed = runCatching { URI(normalized) }.getOrNull()
            val host = parsed?.host?.takeIf { it.isNotBlank() }
                ?: trimmed.substringAfter("://").substringBefore('/').substringBefore(':')
            val port = parsed?.port?.takeIf { it in 1..65535 } ?: 443
            val rawPath = parsed?.rawPath?.takeIf { it.isNotBlank() } ?: "/"
            val rawQuery = parsed?.rawQuery?.takeIf { it.isNotBlank() }
            val path = if (rawQuery != null) "$rawPath?$rawQuery" else rawPath
            return ParsedWorkerEndpoint(host = host, port = port, path = path)
        }
    }
}

/**
 * High-speed local SOCKS5 server (`127.0.0.1:40446`) implementing the **AMIR** (`Cl2`)
 * WebSocket-encrypted tunnel directly over `SSLSocket` + raw RFC 6455 binary framing:
 *
 * - **Zero `permessage-deflate` compression overhead** (matches `compression=None` in Python)
 * - **TLS Session Resumption** via shared `SSLContext` session cache (`256` entries)
 * - **Zero-allocation in-place XOR + combined RFC 6455 masking** on reusable 64KB buffers
 * - **No HTTP client connection caps** and `TCP_NODELAY` + 512KB socket buffers
 * - Direct IP dialing via **CLEAN IP** with **WORKER URL** SNI/Host and automatic DNS fallback
 */
class Cl2ProxyServer internal constructor(
    private val customWebSocketOpener: ((Cl2Config) -> RawWebSocket)?
) {
    constructor() : this(null)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptJob: Job? = null

    private var serverScope: CoroutineScope? = null

    private val relayExecutor = Executors.newCachedThreadPool()
    private val random = SecureRandom()

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, null, null)
            runCatching {
                clientSessionContext.sessionCacheSize = 256
                clientSessionContext.sessionTimeout = 3600
            }
        }
    }

    suspend fun start(config: Cl2Config): Result<Unit> = withContext(Dispatchers.IO) {
        stop()
        lastError = null
        val endpoint = config.parsedEndpoint
        if (endpoint.host.isBlank() || config.uuid.isBlank() || config.listenPort !in 1..65535) {
            return@withContext Result.failure(
                IllegalArgumentException("تنظیمات AMIR (UUID، WORKER URL یا پورت) نامعتبر است.")
            )
        }

        try {
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName(config.listenHost), config.listenPort), 256)
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            serverSocket = socket
            serverScope = scope
            _isRunning.value = true

            acceptJob = scope.launch {
                while (isActive && !socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: Throwable) {
                        break
                    }
                    relayExecutor.execute {
                        handleClient(client, config)
                    }
                }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            lastError = t.message
            stop()
            Result.failure(
                IllegalStateException(
                    "عدم امکان راه‌اندازی سرور SOCKS5 محلی AMIR روی ${config.listenHost}:${config.listenPort}: ${t.message}",
                    t
                )
            )
        }
    }

    fun stop() {
        _isRunning.value = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        serverScope?.cancel()
        serverScope = null
    }

    suspend fun measureHandshakeLatencyMs(
        config: Cl2Config,
        targetHost: String = "www.gstatic.com",
        targetPort: Int = 443
    ): Long = withContext(Dispatchers.IO) {
        val startedNs = System.nanoTime()
        var ws: RawWebSocket? = null
        try {
            ws = openWebSocketWithFallback(config)
            val (initPacket, key) = Cl2Crypto.buildInitPacket(config.uuid, targetHost, targetPort)
            val writeBuf = ByteArray(initPacket.size + 14)
            val ctrlBuf = ByteArray(256)
            writeMaskedBinaryFrame(ws.output, writeBuf, initPacket, initPacket.size, keyXor = null)
            ws.output.flush()

            val ackBuf = ByteArray(256)
            val ackFrame = readNextDataFrame(ws.input, ws.output, ctrlBuf, ackBuf)
            if (ackFrame.length < 16 || !Cl2Crypto.verifyAck(ackFrame.bytes.copyOfRange(0, ackFrame.length), key)) {
                lastError = "AMIR ACK verification failed"
                return@withContext -1L
            }
            lastError = null
            ((System.nanoTime() - startedNs) / 1_000_000L).coerceAtLeast(1L)
        } catch (t: Throwable) {
            lastError = t.message ?: t::class.java.simpleName
            -1L
        } finally {
            runCatching { ws?.socket?.close() }
        }
    }

    private fun handleClient(client: Socket, config: Cl2Config) {
        var ws: RawWebSocket? = null
        try {
            runCatching {
                client.tcpNoDelay = true
                client.receiveBufferSize = SOCKET_BUFFER_BYTES
                client.sendBufferSize = SOCKET_BUFFER_BYTES
                client.soTimeout = 5_000
            }

            val clientIn = BufferedInputStream(client.getInputStream(), 16384)
            val clientOut = client.getOutputStream()

            // SOCKS5 Greeting: VER(1) + NMETHODS(1) + METHODS(NMETHODS)
            val header = readExactly(clientIn, 2)
            if (header[0].toInt() != 5) return
            val nMethods = header[1].toInt() and 0xFF
            if (nMethods > 0) {
                readExactly(clientIn, nMethods)
            }
            clientOut.write(byteArrayOf(0x05, 0x00))
            clientOut.flush()

            // SOCKS5 Connect Request: VER(1) + CMD(1) + RSV(1) + ATYP(1)
            val req = readExactly(clientIn, 4)
            if (req[0].toInt() != 5 || req[1].toInt() != 1) return

            val atyp = req[3].toInt() and 0xFF
            val targetHost = when (atyp) {
                1 -> {
                    val ipBytes = readExactly(clientIn, 4)
                    InetAddress.getByAddress(ipBytes).hostAddress ?: return
                }
                3 -> {
                    val len = readExactly(clientIn, 1)[0].toInt() and 0xFF
                    String(readExactly(clientIn, len), Charsets.UTF_8)
                }
                4 -> {
                    val ip6Bytes = readExactly(clientIn, 16)
                    InetAddress.getByAddress(ip6Bytes).hostAddress ?: return
                }
                else -> return
            }

            val portBytes = readExactly(clientIn, 2)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            ws = openWebSocketWithFallback(config)
            val (initPacket, key) = Cl2Crypto.buildInitPacket(config.uuid, targetHost, targetPort)

            val upWriteBuf = ByteArray(MAX_CHUNK_BYTES + 14)
            writeMaskedBinaryFrame(ws.output, upWriteBuf, initPacket, initPacket.size, keyXor = null)
            ws.output.flush()

            // Expect encrypted 16-byte HMAC("OK") ACK within 10s
            ws.socket.soTimeout = 10_000
            val downReadBuf = ByteArray(MAX_CHUNK_BYTES)
            val pongWriteBuf = ByteArray(256)
            val ackFrame = readNextDataFrame(ws.input, ws.output, pongWriteBuf, downReadBuf)
            if (ackFrame.length < 16) return
            val ackBytes = ackFrame.bytes.copyOfRange(0, ackFrame.length)
            if (!Cl2Crypto.verifyAck(ackBytes, key)) return

            // SOCKS5 Success Reply
            clientOut.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            clientOut.flush()

            // Unlimited stream timeouts during active relay
            runCatching {
                client.soTimeout = 0
                ws.socket.soTimeout = 0
            }

            val closed = AtomicBoolean(false)
            val activeWs = ws

            // Upstream thread: SOCKS5 client -> XOR + RFC6455 masked frame -> TLS WebSocket.
            // Note: Do NOT close activeWs when clientIn reaches clean EOF (TCP half-close / shutdownOutput),
            // because HTTP/1.1 clients commonly half-close while still reading the server's response.
            val upFuture = relayExecutor.submit {
                val readBuf = ByteArray(MAX_CHUNK_BYTES)
                try {
                    while (!closed.get()) {
                        val n = clientIn.read(readBuf)
                        if (n <= 0) break
                        synchronized(activeWs.writeLock) {
                            writeMaskedBinaryFrame(
                                out = activeWs.output,
                                frameBuf = upWriteBuf,
                                payload = readBuf,
                                payloadLen = n,
                                keyXor = key
                            )
                            if (clientIn.available() == 0) {
                                activeWs.output.flush()
                            }
                        }
                    }
                    // Apply a generous idle read timeout after client stops sending so orphaned
                    // half-closed sockets cannot stay open indefinitely.
                    if (!closed.get()) {
                        runCatching { activeWs.socket.soTimeout = HALF_CLOSE_IDLE_TIMEOUT_MS }
                    }
                } catch (_: Throwable) {
                    if (!closed.get()) {
                        closed.set(true)
                        runCatching { activeWs.socket.close() }
                    }
                }
            }

            // Downstream on current thread: TLS WebSocket -> XOR in-place -> SOCKS5 client
            try {
                while (!closed.get()) {
                    val frame = readNextDataFrame(
                        inp = activeWs.input,
                        out = activeWs.output,
                        ctrlWriteBuf = pongWriteBuf,
                        reusableBuf = downReadBuf,
                        writeLock = activeWs.writeLock
                    )
                    if (frame.length < 0) break
                    if (frame.length == 0) continue
                    Cl2Crypto.xorInPlace(frame.bytes, frame.length, key)
                    clientOut.write(frame.bytes, 0, frame.length)
                    if (activeWs.input.available() == 0) {
                        clientOut.flush()
                    }
                }
            } catch (_: Throwable) {
            } finally {
                closed.set(true)
                runCatching { clientOut.flush() }
                runCatching {
                    synchronized(activeWs.writeLock) {
                        writeControlFrame(activeWs.output, pongWriteBuf, 0x08, ByteArray(0), 0)
                        activeWs.output.flush()
                    }
                }
                runCatching { client.close() }
                runCatching { activeWs.socket.close() }
            }

            runCatching { upFuture.get() }
        } catch (t: Throwable) {
            lastError = t.message ?: t::class.java.simpleName
        } finally {
            runCatching { ws?.socket?.close() }
            runCatching { client.close() }
        }
    }

    private fun openWebSocketWithFallback(config: Cl2Config): RawWebSocket {
        customWebSocketOpener?.let { return it(config) }
        val cleanIp = config.cleanIp.trim()
        if (cleanIp.isNotEmpty()) {
            try {
                return openSingleWebSocket(config, connectIp = cleanIp)
            } catch (t: Throwable) {
                lastError = "Clean IP failed (${t.message}), trying domain fallback"
            }
        }
        return openSingleWebSocket(config, connectIp = null)
    }

    private fun openSingleWebSocket(config: Cl2Config, connectIp: String?): RawWebSocket {
        val endpoint = config.parsedEndpoint
        val workerHost = endpoint.host
        val workerPort = endpoint.port
        val wsPath = endpoint.path
        val dialTarget = connectIp ?: workerHost

        val tcpSocket = Socket()
        try {
            tcpSocket.tcpNoDelay = true
            runCatching {
                tcpSocket.receiveBufferSize = SOCKET_BUFFER_BYTES
                tcpSocket.sendBufferSize = SOCKET_BUFFER_BYTES
            }
            val connectTimeoutMs = if (connectIp != null) 4_000 else 10_000
            tcpSocket.connect(InetSocketAddress(InetAddress.getByName(dialTarget), workerPort), connectTimeoutMs)

            val sslSocket = sslContext.socketFactory.createSocket(
                tcpSocket,
                workerHost,
                workerPort,
                true
            ) as SSLSocket

            val sslParams = sslSocket.sslParameters
            sslParams.serverNames = listOf(SNIHostName(workerHost))
            runCatching { sslParams.endpointIdentificationAlgorithm = "HTTPS" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { sslParams.applicationProtocols = arrayOf("http/1.1") }
            }
            sslSocket.sslParameters = sslParams
            sslSocket.tcpNoDelay = true
            sslSocket.soTimeout = 10_000
            sslSocket.startHandshake()

            val peerCert = sslSocket.session.peerCertificates.firstOrNull() as? java.security.cert.X509Certificate
                ?: throw SSLPeerUnverifiedException("Missing X509 peer certificate for $workerHost")
            if (!verifyCertificateHostname(workerHost, peerCert)) {
                throw SSLPeerUnverifiedException("Hostname verification failed for $workerHost")
            }

            val wsIn = BufferedInputStream(sslSocket.inputStream, 65536)
            val wsOut = sslSocket.outputStream

            val keyBytes = ByteArray(16).also { random.nextBytes(it) }
            val secKey = Base64Utils.encode(keyBytes, urlSafe = false, pad = true)
            val hostHeader = if (workerPort == 443) workerHost else "$workerHost:$workerPort"

            val reqText = buildString {
                append("GET ").append(wsPath).append(" HTTP/1.1\r\n")
                append("Host: ").append(hostHeader).append("\r\n")
                append("User-Agent: XrayClient/1.0\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: ").append(secKey).append("\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                if (config.subprotocol.isNotBlank()) {
                    append("Sec-WebSocket-Protocol: ").append(config.subprotocol.trim()).append("\r\n")
                }
                append("\r\n")
            }
            wsOut.write(reqText.toByteArray(Charsets.US_ASCII))
            wsOut.flush()

            // Verify HTTP 101 Switching Protocols + Sec-WebSocket-Accept
            val statusLine = readHttpLine(wsIn)
            if (!statusLine.startsWith("HTTP/1.1 101") && !statusLine.startsWith("HTTP/1.0 101")) {
                throw IOException("WebSocket upgrade failed: $statusLine")
            }
            var acceptHeader: String? = null
            while (true) {
                val line = readHttpLine(wsIn)
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val k = line.substring(0, colon).trim()
                    if (k.equals("Sec-WebSocket-Accept", ignoreCase = true)) {
                        acceptHeader = line.substring(colon + 1).trim()
                    }
                }
            }
            val expectedAccept = computeWebSocketAccept(secKey)
            if (acceptHeader != expectedAccept) {
                throw IOException("Invalid Sec-WebSocket-Accept header")
            }

            return RawWebSocket(socket = sslSocket, input = wsIn, output = wsOut)
        } catch (t: Throwable) {
            runCatching { tcpSocket.close() }
            throw t
        }
    }

    internal fun verifyCertificateHostname(host: String, cert: java.security.cert.X509Certificate): Boolean {
        val normalizedHost = host.trim().removePrefix("[").removeSuffix("]").lowercase()
        if (normalizedHost.isEmpty()) return false

        val isIp = com.example.xrayclient.utils.NetworkUtils.isValidIpv4(normalizedHost) ||
            com.example.xrayclient.utils.NetworkUtils.isValidIpv6Literal(normalizedHost)

        val sans = runCatching { cert.subjectAlternativeNames }.getOrNull().orEmpty()
        var hasSan = false
        for (entry in sans) {
            if (entry.size < 2) continue
            val type = (entry[0] as? Int) ?: continue
            val value = (entry[1] as? String)?.trim()?.lowercase() ?: continue
            if (type == 2 && !isIp) {
                hasSan = true
                if (matchesDnsPattern(normalizedHost, value)) return true
            } else if (type == 7 && isIp) {
                hasSan = true
                if (normalizedHost == value) return true
            }
        }
        if (hasSan) return false

        // RFC 2818 CN fallback only when no SAN entries exist
        val dn = cert.subjectX500Principal.name
        val cn = dn.split(',')
            .map { it.trim() }
            .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
            ?.substring(3)
            ?.trim()
            ?.lowercase()
            ?: return false
        return if (isIp) normalizedHost == cn else matchesDnsPattern(normalizedHost, cn)
    }

    private fun matchesDnsPattern(hostname: String, pattern: String): Boolean {
        if (hostname == pattern) return true
        if (!pattern.startsWith("*.")) return false
        val suffix = pattern.substring(1) // ".example.com"
        if (!hostname.endsWith(suffix)) return false
        val prefixLen = hostname.length - suffix.length
        if (prefixLen <= 0) return false
        // Wildcard matches a single DNS label only
        return hostname.indexOf('.', 0) !in 0 until prefixLen
    }

    private fun readHttpLine(inp: InputStream): String {
        val sb = StringBuilder(128)
        while (true) {
            val b = inp.read()
            if (b < 0) throw EOFException("Unexpected EOF reading WebSocket HTTP response")
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                sb.append(b.toChar())
            }
            if (sb.length > 8192) throw IOException("HTTP header line too long")
        }
        return sb.toString()
    }

    private fun computeWebSocketAccept(secKey: String): String {
        val magic = (secKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)
        val sha1 = MessageDigest.getInstance("SHA-1").digest(magic)
        return Base64Utils.encode(sha1, urlSafe = false, pad = true)
    }

    /**
     * Writes an RFC 6455 masked binary frame in a single pass with zero heap allocations.
     */
    private fun writeMaskedBinaryFrame(
        out: OutputStream,
        frameBuf: ByteArray,
        payload: ByteArray,
        payloadLen: Int,
        keyXor: ByteArray?
    ) {
        frameBuf[0] = 0x82.toByte() // FIN=1, opcode=BINARY (0x02)
        val hdrLen: Int
        when {
            payloadLen < 126 -> {
                frameBuf[1] = (0x80 or payloadLen).toByte()
                hdrLen = 2
            }
            payloadLen <= 65535 -> {
                frameBuf[1] = (0x80 or 126).toByte()
                frameBuf[2] = ((payloadLen ushr 8) and 0xFF).toByte()
                frameBuf[3] = (payloadLen and 0xFF).toByte()
                hdrLen = 4
            }
            else -> {
                frameBuf[1] = (0x80 or 127).toByte()
                val len64 = payloadLen.toLong()
                for (i in 0 until 8) {
                    frameBuf[2 + i] = ((len64 ushr ((7 - i) * 8)) and 0xFFL).toByte()
                }
                hdrLen = 10
            }
        }

        val m0 = random.nextInt(256)
        val m1 = random.nextInt(256)
        val m2 = random.nextInt(256)
        val m3 = random.nextInt(256)
        frameBuf[hdrLen] = m0.toByte()
        frameBuf[hdrLen + 1] = m1.toByte()
        frameBuf[hdrLen + 2] = m2.toByte()
        frameBuf[hdrLen + 3] = m3.toByte()
        val dataStart = hdrLen + 4

        if (keyXor == null) {
            for (i in 0 until payloadLen) {
                val m = when (i and 3) {
                    0 -> m0
                    1 -> m1
                    2 -> m2
                    else -> m3
                }
                frameBuf[dataStart + i] = (payload[i].toInt() xor m).toByte()
            }
        } else if (keyXor.size == 16) {
            for (i in 0 until payloadLen) {
                val m = when (i and 3) {
                    0 -> m0
                    1 -> m1
                    2 -> m2
                    else -> m3
                }
                val enc = payload[i].toInt() xor keyXor[i and 15].toInt()
                frameBuf[dataStart + i] = (enc xor m).toByte()
            }
        } else {
            val kLen = keyXor.size
            for (i in 0 until payloadLen) {
                val m = when (i and 3) {
                    0 -> m0
                    1 -> m1
                    2 -> m2
                    else -> m3
                }
                val enc = payload[i].toInt() xor keyXor[i % kLen].toInt()
                frameBuf[dataStart + i] = (enc xor m).toByte()
            }
        }

        out.write(frameBuf, 0, dataStart + payloadLen)
    }

    private fun writeControlFrame(
        out: OutputStream,
        frameBuf: ByteArray,
        opcode: Int,
        payload: ByteArray,
        payloadLen: Int
    ) {
        val len = payloadLen.coerceIn(0, 125)
        frameBuf[0] = (0x80 or opcode).toByte()
        frameBuf[1] = (0x80 or len).toByte()
        val m0 = random.nextInt(256)
        val m1 = random.nextInt(256)
        val m2 = random.nextInt(256)
        val m3 = random.nextInt(256)
        frameBuf[2] = m0.toByte()
        frameBuf[3] = m1.toByte()
        frameBuf[4] = m2.toByte()
        frameBuf[5] = m3.toByte()
        for (i in 0 until len) {
            val m = when (i and 3) {
                0 -> m0
                1 -> m1
                2 -> m2
                else -> m3
            }
            frameBuf[6 + i] = (payload[i].toInt() xor m).toByte()
        }
        out.write(frameBuf, 0, 6 + len)
    }

    private data class FrameData(val bytes: ByteArray, val length: Int)

    private fun readNextDataFrame(
        inp: InputStream,
        out: OutputStream,
        ctrlWriteBuf: ByteArray,
        reusableBuf: ByteArray,
        writeLock: Any = Any()
    ): FrameData {
        var fragBuffer: java.io.ByteArrayOutputStream? = null
        while (true) {
            val b0 = inp.read()
            if (b0 < 0) return FrameData(reusableBuf, -1)
            val b1 = inp.read()
            if (b1 < 0) return FrameData(reusableBuf, -1)

            val fin = (b0 and 0x80) != 0
            val opcode = b0 and 0x0F
            val masked = (b1 and 0x80) != 0
            var payloadLen = (b1 and 0x7F).toLong()

            if (payloadLen == 126L) {
                val lenBytes = readExactly(inp, 2)
                payloadLen = (((lenBytes[0].toInt() and 0xFF) shl 8) or (lenBytes[1].toInt() and 0xFF)).toLong()
            } else if (payloadLen == 127L) {
                val lenBytes = readExactly(inp, 8)
                payloadLen = 0L
                for (i in 0 until 8) {
                    payloadLen = (payloadLen shl 8) or (lenBytes[i].toLong() and 0xFFL)
                }
            }

            if (payloadLen < 0 || payloadLen > MAX_FRAME_BYTES) {
                throw IOException("Invalid WebSocket frame length: $payloadLen")
            }

            val mask = if (masked) readExactly(inp, 4) else null
            val lenInt = payloadLen.toInt()
            val targetBuf = if (lenInt <= reusableBuf.size) reusableBuf else ByteArray(lenInt)
            readExactlyInto(inp, targetBuf, lenInt)

            if (mask != null) {
                for (i in 0 until lenInt) {
                    targetBuf[i] = (targetBuf[i].toInt() xor mask[i and 3].toInt()).toByte()
                }
            }

            when (opcode) {
                0x08 -> return FrameData(reusableBuf, -1) // CLOSE
                0x09 -> {
                    // PING -> reply PONG
                    runCatching {
                        synchronized(writeLock) {
                            writeControlFrame(out, ctrlWriteBuf, 0x0A, targetBuf, lenInt)
                            out.flush()
                        }
                    }
                    continue
                }
                0x0A -> continue // PONG
                0x01, 0x02 -> {
                    if (fin && fragBuffer == null) {
                        return FrameData(targetBuf, lenInt)
                    }
                    val acc = fragBuffer ?: java.io.ByteArrayOutputStream(lenInt.coerceAtLeast(4096)).also {
                        fragBuffer = it
                    }
                    acc.write(targetBuf, 0, lenInt)
                    if (fin) {
                        val assembled = acc.toByteArray()
                        return FrameData(assembled, assembled.size)
                    }
                }
                0x00 -> {
                    val acc = fragBuffer
                    if (acc != null) {
                        acc.write(targetBuf, 0, lenInt)
                        if (fin) {
                            val assembled = acc.toByteArray()
                            return FrameData(assembled, assembled.size)
                        }
                    } else if (fin) {
                        return FrameData(targetBuf, lenInt)
                    }
                }
                else -> continue
            }
        }
    }

    private fun readExactly(input: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        readExactlyInto(input, buf, count)
        return buf
    }

    private fun readExactlyInto(input: InputStream, buf: ByteArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val read = input.read(buf, offset, count - offset)
            if (read < 0) throw EOFException("Unexpected EOF while reading stream")
            offset += read
        }
    }

    internal class RawWebSocket(
        val socket: Socket,
        val input: InputStream,
        val output: OutputStream,
        val writeLock: Any = Any()
    )

    companion object {
        private const val MAX_CHUNK_BYTES = 65536
        private const val MAX_FRAME_BYTES = 16 * 1024 * 1024
        private const val SOCKET_BUFFER_BYTES = 512 * 1024
        private const val HALF_CLOSE_IDLE_TIMEOUT_MS = 120_000

        fun shouldHandleProfile(profile: ServerProfile, settings: AppSettings): Boolean {
            if (profile.protocol == ProxyProtocol.AMIR) return true
            if (!settings.cl2Enabled) return false
            if (profile.protocol != ProxyProtocol.SOCKS5) return false
            val host = profile.address.trim()
            val matchesLocal = host == "127.0.0.1" ||
                host.equals("localhost", ignoreCase = true) ||
                host == settings.cl2ListenHost.trim()
            return matchesLocal && profile.port == settings.cl2ListenPort
        }

        fun configForProfile(profile: ServerProfile, settings: AppSettings): Cl2Config {
            return if (profile.protocol == ProxyProtocol.AMIR) {
                Cl2Config(
                    enabled = true,
                    uuid = profile.uuid.ifBlank { settings.cl2Uuid },
                    workerHost = profile.host.ifBlank { settings.cl2WorkerHost },
                    cleanIp = profile.address.trim(),
                    listenHost = "127.0.0.1",
                    listenPort = profile.port.takeIf { it in 1..65535 } ?: Cl2Config.DEFAULT_LISTEN_PORT,
                    subprotocol = profile.extraParams["subproto"] ?: settings.cl2Subprotocol
                )
            } else {
                settings.toCl2Config()
            }
        }
    }
}
