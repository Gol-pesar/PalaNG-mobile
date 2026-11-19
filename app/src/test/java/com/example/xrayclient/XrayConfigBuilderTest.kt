package com.example.xrayclient

import com.example.xrayclient.data.models.AppSettings
import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.data.models.TransportType
import com.example.xrayclient.domain.validators.ConfigValidator
import com.example.xrayclient.domain.validators.ValidationResult
import com.example.xrayclient.engine.XrayConfigBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {

    @Test
    fun `http and socks5 generate proper xray outbound schemas rather than vless`() {
        val httpProfile = ServerProfile(
            name = "HTTP Out",
            protocol = ProxyProtocol.HTTP,
            address = "proxy.example.com",
            port = 8080,
            username = "u1",
            password = "p1"
        )
        val httpJsonStr = XrayConfigBuilder.build(httpProfile, AppSettings())
        assertTrue(ConfigValidator.validateXrayJson(httpJsonStr) is ValidationResult.Valid)

        val httpJson = Json.parseToJsonElement(httpJsonStr).jsonObject
        val httpOutbound = httpJson["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("http", httpOutbound["protocol"]!!.jsonPrimitive.content)
        val httpServer = httpOutbound["settings"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject
        assertEquals("proxy.example.com", httpServer["address"]!!.jsonPrimitive.content)
        assertEquals(8080, httpServer["port"]!!.jsonPrimitive.int)
        assertEquals("u1", httpServer["users"]!!.jsonArray[0].jsonObject["user"]!!.jsonPrimitive.content)

        val socksProfile = ServerProfile(
            name = "SOCKS Out",
            protocol = ProxyProtocol.SOCKS5,
            address = "192.0.2.5",
            port = 1080,
            username = "suser",
            password = "spass"
        )
        val socksJson = Json.parseToJsonElement(XrayConfigBuilder.build(socksProfile, AppSettings())).jsonObject
        val socksOutbound = socksJson["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("socks", socksOutbound["protocol"]!!.jsonPrimitive.content)
        assertFalse(socksOutbound["settings"]!!.jsonObject.containsKey("vnext"))
    }

    @Test
    fun `vless reality disables mux by default and configures realitySettings and stats`() {
        val profile = ServerProfile(
            name = "VLESS Reality",
            protocol = ProxyProtocol.VLESS,
            address = "198.51.100.2",
            port = 443,
            uuid = "11111111-2222-3333-4444-555555555555",
            flow = "xtls-rprx-vision",
            transport = TransportType.TCP,
            security = SecurityType.REALITY,
            sni = "www.cloudflare.com",
            realityPublicKey = "PUBKEY123",
            realityShortId = "abcd",
            fingerprint = "chrome"
        )

        val jsonStr = XrayConfigBuilder.build(profile, AppSettings())
        val root = Json.parseToJsonElement(jsonStr).jsonObject
        assertTrue(root.containsKey("stats"))
        assertTrue(root.containsKey("policy"))
        assertTrue(root.containsKey("dns"))

        val outbound = root["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("vless", outbound["protocol"]!!.jsonPrimitive.content)
        assertFalse(outbound["mux"]!!.jsonObject["enabled"]!!.jsonPrimitive.boolean)

        val reality = outbound["streamSettings"]!!.jsonObject["realitySettings"]!!.jsonObject
        assertEquals("PUBKEY123", reality["publicKey"]!!.jsonPrimitive.content)
        assertEquals("abcd", reality["shortId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `default Mahdi4 Mahdi5 and AMIR configs build fast v2fly v5 json`() {
        val parser = com.example.xrayclient.domain.parsers.ProxyLinkParser()
        val batch = parser.importBatch(
            com.example.xrayclient.domain.managers.ConfigurationManager.DEFAULT_CONFIG_URIS.joinToString("\n")
        )
        assertEquals(3, batch.profiles.size)

        batch.profiles.take(2).forEach { profile ->
            val jsonStr = XrayConfigBuilder.build(profile, AppSettings())
            assertTrue(ConfigValidator.validateXrayJson(jsonStr) is ValidationResult.Valid)
            val root = Json.parseToJsonElement(jsonStr).jsonObject
            val outbound = root["outbounds"]!!.jsonArray[0].jsonObject
            val stream = outbound["streamSettings"]!!.jsonObject
            assertEquals("ws", stream["network"]!!.jsonPrimitive.content)
            assertEquals("tls", stream["security"]!!.jsonPrimitive.content)
            assertEquals(
                "admin.ariamusic.workers.dev",
                stream["wsSettings"]!!.jsonObject["headers"]!!.jsonObject["Host"]!!.jsonPrimitive.content
            )
            assertEquals(
                "admin.ariamusic.workers.dev",
                stream["tlsSettings"]!!.jsonObject["serverName"]!!.jsonPrimitive.content
            )
        }

        val amirProfile = batch.profiles[2]
        assertEquals(ProxyProtocol.AMIR, amirProfile.protocol)
        val amirJsonStr = XrayConfigBuilder.build(amirProfile, AppSettings())
        assertTrue(ConfigValidator.validateXrayJson(amirJsonStr) is ValidationResult.Valid)
        val amirRoot = Json.parseToJsonElement(amirJsonStr).jsonObject
        val amirOutbound = amirRoot["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("socks", amirOutbound["protocol"]!!.jsonPrimitive.content)
        val server = amirOutbound["settings"]!!.jsonObject["servers"]!!.jsonArray[0].jsonObject
        assertEquals("127.0.0.1", server["address"]!!.jsonPrimitive.content)
        assertEquals(40446, server["port"]!!.jsonPrimitive.int)
        assertTrue(amirRoot["outbounds"]!!.jsonArray.any { it.jsonObject["tag"]!!.jsonPrimitive.content == "dns-out" })
        assertTrue(amirRoot["dns"]!!.jsonObject["servers"]!!.jsonArray[0].jsonPrimitive.content.startsWith("https://"))

        val routing = amirRoot["routing"]!!.jsonObject
        assertEquals("AsIs", routing["domainStrategy"]!!.jsonPrimitive.content)
        assertFalse(amirJsonStr.contains("geoip:private"))
    }
}
