package com.example.xrayclient

import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.TransportType
import com.example.xrayclient.domain.parsers.ParseOutcome
import com.example.xrayclient.domain.parsers.ProxyLinkExporter
import com.example.xrayclient.domain.parsers.ProxyLinkParser
import com.example.xrayclient.utils.Base64Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyLinkParserTest {

    private val parser = ProxyLinkParser()

    @Test
    fun `parse vless reality xhttp link and preserve unknown parameters`() {
        val uri = "vless://11111111-2222-3333-4444-555555555555@example.com:443" +
            "?encryption=none&security=reality&sni=cdn.example.org&fp=chrome" +
            "&pbk=AbCdEfGhIjKlMnOpQrStUvWxYz0123456789&sid=a1b2&type=xhttp&mode=packet-up" +
            "&path=%2Fxhttp-path&customKey=customVal#Tehran%20Node"

        val outcome = parser.parseSingle(uri)
        assertTrue(outcome is ParseOutcome.Success)
        val p = (outcome as ParseOutcome.Success).profile

        assertEquals(ProxyProtocol.VLESS, p.protocol)
        assertEquals("Tehran Node", p.name)
        assertEquals("11111111-2222-3333-4444-555555555555", p.uuid)
        assertEquals("example.com", p.address)
        assertEquals(443, p.port)
        assertEquals(SecurityType.REALITY, p.security)
        assertEquals(TransportType.XHTTP, p.transport)
        assertEquals("packet-up", p.xhttpMode)
        assertEquals("/xhttp-path", p.path)
        assertEquals("AbCdEfGhIjKlMnOpQrStUvWxYz0123456789", p.realityPublicKey)
        assertEquals("a1b2", p.realityShortId)
        assertEquals("customVal", p.extraParams["customKey"])

        // Export & re-parse round-trip
        val exported = ProxyLinkExporter.export(p)
        val reParsed = parser.parseSingle(exported) as ParseOutcome.Success
        assertEquals(p.duplicateFingerprint(), reParsed.profile.duplicateFingerprint())
        assertEquals("customVal", reParsed.profile.extraParams["customKey"])
    }

    @Test
    fun `parse vmess base64 json link with url-safe unpadded encoding`() {
        val json = """{"v":"2","ps":"Germany VMess","add":"de.example.net","port":"8443","id":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","aid":"0","scy":"aes-128-gcm","net":"ws","type":"none","host":"ws.example.net","path":"/ws","tls":"tls","sni":"ws.example.net"}"""
        val b64 = Base64Utils.encodeString(json, urlSafe = true, pad = false)
        val outcome = parser.parseSingle("vmess://$b64")

        assertTrue(outcome is ParseOutcome.Success)
        val p = (outcome as ParseOutcome.Success).profile
        assertEquals(ProxyProtocol.VMESS, p.protocol)
        assertEquals("Germany VMess", p.name)
        assertEquals("de.example.net", p.address)
        assertEquals(8443, p.port)
        assertEquals(TransportType.WS, p.transport)
        assertEquals(SecurityType.TLS, p.security)
        assertEquals("/ws", p.path)
    }

    @Test
    fun `parse trojan grpc tls link`() {
        val uri = "trojan://secret%40pass@tr.example.com:443?security=tls&sni=tr.example.com&alpn=h2,http/1.1&type=grpc&serviceName=myGrpc#TrojanNode"
        val outcome = parser.parseSingle(uri)

        assertTrue(outcome is ParseOutcome.Success)
        val p = (outcome as ParseOutcome.Success).profile
        assertEquals(ProxyProtocol.TROJAN, p.protocol)
        assertEquals("secret@pass", p.password)
        assertEquals(TransportType.GRPC, p.transport)
        assertEquals("myGrpc", p.serviceName)
        assertEquals("h2,http/1.1", p.alpn)
    }

    @Test
    fun `parse shadowsocks sip002 and legacy links`() {
        val userInfo = Base64Utils.encodeString("aes-256-gcm:MySecretKey!", urlSafe = true, pad = false)
        val sip002 = "ss://$userInfo@198.51.100.10:8388#SS-Node"
        val res1 = parser.parseSingle(sip002) as ParseOutcome.Success
        assertEquals(ProxyProtocol.SHADOWSOCKS, res1.profile.protocol)
        assertEquals("aes-256-gcm", res1.profile.ssMethod)
        assertEquals("MySecretKey!", res1.profile.password)
        assertEquals(8388, res1.profile.port)

        val legacyPayload = Base64Utils.encodeString("chacha20-ietf-poly1305:LegacyPass@203.0.113.5:9000")
        val res2 = parser.parseSingle("ss://$legacyPayload#Legacy") as ParseOutcome.Success
        assertEquals("chacha20-ietf-poly1305", res2.profile.ssMethod)
        assertEquals("203.0.113.5", res2.profile.address)
        assertEquals(9000, res2.profile.port)
    }

    @Test
    fun `parse http and socks5 proxy uris without confusing with subscription urls`() {
        val httpRes = parser.parseSingle("https://alice:pw123@proxy.example.com:8443?sni=proxy.example.com#HTTPSProxy")
        assertTrue(httpRes is ParseOutcome.Success)
        val hp = (httpRes as ParseOutcome.Success).profile
        assertEquals(ProxyProtocol.HTTP, hp.protocol)
        assertEquals(SecurityType.TLS, hp.security)
        assertEquals("alice", hp.username)

        val socksCreds = Base64Utils.encodeString("bob:socksPass")
        val socksRes = parser.parseSingle("socks://$socksCreds@[2001:db8::1]:1080#IPv6Socks")
        assertTrue(socksRes is ParseOutcome.Success)
        val sp = (socksRes as ParseOutcome.Success).profile
        assertEquals(ProxyProtocol.SOCKS5, sp.protocol)
        assertEquals("2001:db8::1", sp.address)
        assertEquals(1080, sp.port)
        assertEquals("bob", sp.username)
        assertEquals("socksPass", sp.password)
    }

    @Test
    fun `batch import decodes base64 subscription, extracts sub urls, detects duplicates, and rejects invalid`() {
        val vless1 = "vless://11111111-2222-3333-4444-555555555555@a.example.com:443?security=tls#S1"
        val vless1Dup = "vless://11111111-2222-3333-4444-555555555555@a.example.com:443?security=tls#S1-Copy"
        val invalid = "vless://not-a-uuid@a.example.com:99999#Bad"
        val subUrl = "https://panel.example.com/sub/token123"

        val plainList = listOf(vless1, vless1Dup, invalid, subUrl).joinToString("\n")
        val b64Payload = Base64Utils.encodeString(plainList, urlSafe = false, pad = false)

        val batch = parser.importBatch(b64Payload)
        assertEquals(1, batch.profiles.size)
        assertEquals(1, batch.duplicateCount)
        assertEquals(1, batch.subscriptionUrls.size)
        assertEquals(subUrl, batch.subscriptionUrls.first())
        assertEquals(1, batch.errors.size)
    }

    @Test
    fun `import xray json configuration file`() {
        val json = """
            {
              "remarks": "Imported JSON",
              "outbounds": [
                {
                  "tag": "proxy",
                  "protocol": "vless",
                  "settings": {
                    "vnext": [{
                      "address": "json.example.com",
                      "port": 443,
                      "users": [{"id": "11111111-2222-3333-4444-555555555555", "encryption": "none"}]
                    }]
                  },
                  "streamSettings": {
                    "network": "ws",
                    "security": "tls",
                    "wsSettings": {"path": "/vless", "host": "json.example.com"},
                    "tlsSettings": {"serverName": "json.example.com"}
                  }
                }
              ]
            }
        """.trimIndent()

        val batch = parser.importBatch(json)
        assertEquals(1, batch.profiles.size)
        assertEquals("Imported JSON", batch.profiles.first().name)
        assertEquals(TransportType.WS, batch.profiles.first().transport)
    }

    @Test
    fun `parse amir uri with clean ip, worker url, uuid and round-trip export`() {
        val uri = "amir://63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd@172.67.70.244:40446" +
            "?worker=https%3A%2F%2Ftwilight-dream-9e3c.aliahmadiphotoofficial.workers.dev%2F&subproto=chat.v2#AMIR"

        val outcome = parser.parseSingle(uri)
        assertTrue(outcome is ParseOutcome.Success)
        val p = (outcome as ParseOutcome.Success).profile

        assertEquals(ProxyProtocol.AMIR, p.protocol)
        assertEquals("AMIR", p.name)
        assertEquals("172.67.70.244", p.address)
        assertEquals(40446, p.port)
        assertEquals("63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd", p.uuid)
        assertEquals("https://twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev/", p.host)
        assertEquals("chat.v2", p.extraParams["subproto"])
        assertEquals(false, p.socksUdpAssociate)

        val exported = ProxyLinkExporter.export(p)
        val reparsed = parser.parseSingle(exported) as ParseOutcome.Success
        assertEquals(p.duplicateFingerprint(), reparsed.profile.duplicateFingerprint())
        assertEquals(p.address, reparsed.profile.address)
        assertEquals(p.host, reparsed.profile.host)
        assertEquals(p.uuid, reparsed.profile.uuid)

        // Optional empty clean IP still parses cleanly
        val noCleanIp = parser.parseSingle("amir://63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd@:40446?worker=example.workers.dev#AMIR-Auto")
        assertTrue(noCleanIp is ParseOutcome.Success)
        assertEquals("", (noCleanIp as ParseOutcome.Success).profile.address)
    }

    @Test
    fun `parse default Mahdi4 Mahdi5 and AMIR configs`() {
        val batch = parser.importBatch(
            com.example.xrayclient.domain.managers.ConfigurationManager.DEFAULT_CONFIG_URIS.joinToString("\n")
        )
        assertEquals(3, batch.profiles.size)
        assertTrue(batch.errors.isEmpty())

        val mahdi4 = batch.profiles[0]
        assertEquals("Mahdi4", mahdi4.name)
        assertEquals("2606:4700:9b05:3153:5a5f:e811:da0e:11d8", mahdi4.address)
        assertEquals(443, mahdi4.port)
        assertEquals(TransportType.WS, mahdi4.transport)
        assertEquals(SecurityType.TLS, mahdi4.security)
        assertEquals("admin.ariamusic.workers.dev", mahdi4.host)
        assertEquals("admin.ariamusic.workers.dev", mahdi4.sni)
        assertEquals("chrome", mahdi4.fingerprint)

        val mahdi5 = batch.profiles[1]
        assertEquals("Mahdi5", mahdi5.name)
        assertEquals("www.speedtest.net", mahdi5.address)
        assertEquals(443, mahdi5.port)
        assertEquals(TransportType.WS, mahdi5.transport)
        assertEquals(SecurityType.TLS, mahdi5.security)

        val amir = batch.profiles[2]
        assertEquals(ProxyProtocol.AMIR, amir.protocol)
        assertEquals("AMIR", amir.name)
        assertEquals("172.67.70.244", amir.address)
        assertEquals("https://twilight-dream-9e3c.aliahmadiphotoofficial.workers.dev/", amir.host)
        assertEquals("63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd", amir.uuid)
        assertEquals(40446, amir.port)
        assertEquals(false, amir.socksUdpAssociate)
    }
}
