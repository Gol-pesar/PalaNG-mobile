package com.example.xrayclient.engine

import com.example.xrayclient.data.models.AppSettings
import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.data.models.TransportType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Generates upstream-compatible Xray-core JSON configuration for all 6 protocols:
 * VLESS, VMess, Trojan, Shadowsocks, HTTP Proxy, and SOCKS5 Proxy.
 *
 * - Pure-Kotlin `kotlinx.serialization.json` builder (works on Android and JVM tests).
 * - HTTP and SOCKS5 are strictly built as Xray `http` and `socks` outbounds (never VLESS-style).
 * - Mux is disabled by default and only enabled if explicitly requested and protocol-compatible.
 * - Enables Xray `stats` + `policy` counters so real engine traffic stats can be queried.
 */
object XrayConfigBuilder {

    const val DEFAULT_SOCKS_PORT = 10808
    const val DEFAULT_HTTP_PORT = 10809

    private val prettyJson = Json { prettyPrint = true }

    fun build(
        profile: ServerProfile,
        settings: AppSettings,
        socksPort: Int = DEFAULT_SOCKS_PORT,
        httpPort: Int = DEFAULT_HTTP_PORT
    ): String {
        val tcpOnlyProxy = profile.protocol == ProxyProtocol.AMIR ||
            profile.protocol == ProxyProtocol.HTTP ||
            (profile.protocol == ProxyProtocol.SOCKS5 && !profile.socksUdpAssociate)

        val root = buildJsonObject {
            put("log", buildJsonObject {
                put("loglevel", settings.logLevel.xrayValue)
            })

            put("stats", buildJsonObject { })
            put("policy", buildJsonObject {
                put("system", buildJsonObject {
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                    put("statsInboundUplink", true)
                    put("statsInboundDownlink", true)
                })
            })

            put("dns", buildJsonObject {
                put("servers", buildJsonArray {
                    val primary = settings.primaryDns.trim().ifBlank { "1.1.1.1" }
                    add(JsonPrimitive(if (tcpOnlyProxy) toDohServer(primary) else primary))
                    val secondary = settings.secondaryDns.trim()
                    if (secondary.isNotBlank() && secondary != primary) {
                        add(JsonPrimitive(if (tcpOnlyProxy) toDohServer(secondary) else secondary))
                    }
                })
                put("queryStrategy", settings.dnsStrategy.xrayStrategy)
            })

            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("tag", "socks-in")
                    put("port", socksPort)
                    put("listen", "127.0.0.1")
                    put("protocol", "socks")
                    put("settings", buildJsonObject {
                        put("auth", "noauth")
                        put("udp", true)
                    })
                    put("sniffing", buildJsonObject {
                        put("enabled", true)
                        put("destOverride", buildJsonArray {
                            add(JsonPrimitive("http"))
                            add(JsonPrimitive("tls"))
                            add(JsonPrimitive("quic"))
                        })
                        put("routeOnly", false)
                    })
                })
                add(buildJsonObject {
                    put("tag", "http-in")
                    put("port", httpPort)
                    put("listen", "127.0.0.1")
                    put("protocol", "http")
                    put("settings", buildJsonObject { })
                })
            })

            put("outbounds", buildJsonArray {
                add(buildPrimaryOutbound(profile))
                add(buildJsonObject {
                    put("tag", "direct")
                    put("protocol", "freedom")
                    put("settings", buildJsonObject {
                        put("domainStrategy", settings.dnsStrategy.xrayStrategy)
                    })
                })
                if (tcpOnlyProxy) {
                    add(buildJsonObject {
                        put("tag", "dns-out")
                        put("protocol", "dns")
                    })
                }
                add(buildJsonObject {
                    put("tag", "block")
                    put("protocol", "blackhole")
                })
            })

            put("routing", buildJsonObject {
                put("domainStrategy", "AsIs")
                put("rules", buildJsonArray {
                    if (tcpOnlyProxy) {
                        add(buildJsonObject {
                            put("type", "field")
                            put("port", "53")
                            put("network", "udp")
                            put("outboundTag", "dns-out")
                        })
                    }
                    if (settings.bypassLan) {
                        add(buildJsonObject {
                            put("type", "field")
                            put("ip", buildJsonArray {
                                add(JsonPrimitive("10.0.0.0/8"))
                                add(JsonPrimitive("172.16.0.0/12"))
                                add(JsonPrimitive("192.168.0.0/16"))
                                add(JsonPrimitive("127.0.0.0/8"))
                                add(JsonPrimitive("169.254.0.0/16"))
                                add(JsonPrimitive("fc00::/7"))
                                add(JsonPrimitive("fe80::/10"))
                            })
                            put("outboundTag", "direct")
                        })
                    }
                    add(buildJsonObject {
                        put("type", "field")
                        put("inboundTag", buildJsonArray {
                            add(JsonPrimitive("socks-in"))
                            add(JsonPrimitive("http-in"))
                        })
                        put("outboundTag", "proxy")
                    })
                })
            })
        }

        return prettyJson.encodeToString(JsonObject.serializer(), root)
    }

    private fun toDohServer(rawDns: String): String {
        val trimmed = rawDns.trim()
        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("https+local://", ignoreCase = true)
        ) {
            return trimmed
        }
        return when (trimmed) {
            "1.1.1.1", "1.0.0.1" -> "https://$trimmed/dns-query"
            "8.8.8.8", "8.8.4.4" -> "https://$trimmed/dns-query"
            "9.9.9.9" -> "https://dns.quad9.net/dns-query"
            else -> "https://1.1.1.1/dns-query"
        }
    }

    fun buildPrimaryOutbound(profile: ServerProfile): JsonObject {
        if (profile.customOutboundJson.isNotBlank()) {
            try {
                val custom = Json.parseToJsonElement(profile.customOutboundJson) as? JsonObject
                if (custom != null && custom.containsKey("protocol") && custom.containsKey("settings")) {
                    return buildJsonObject {
                        custom.forEach { (k, v) -> if (k != "tag") put(k, v) }
                        put("tag", "proxy")
                    }
                }
            } catch (_: Exception) {
            }
        }

        return buildJsonObject {
            put("tag", "proxy")
            put("protocol", profile.protocol.xrayProtocol)

            when (profile.protocol) {
                ProxyProtocol.VLESS -> {
                    put("settings", buildJsonObject {
                        put("vnext", buildJsonArray {
                            add(buildJsonObject {
                                put("address", profile.address.trim())
                                put("port", profile.port)
                                put("users", buildJsonArray {
                                    add(buildJsonObject {
                                        put("id", profile.uuid.trim())
                                        put("encryption", profile.encryption.ifBlank { "none" })
                                        if (profile.flow.isNotBlank()) {
                                            put("flow", profile.flow.trim())
                                        }
                                    })
                                })
                            })
                        })
                    })
                }

                ProxyProtocol.VMESS -> {
                    put("settings", buildJsonObject {
                        put("vnext", buildJsonArray {
                            add(buildJsonObject {
                                put("address", profile.address.trim())
                                put("port", profile.port)
                                put("users", buildJsonArray {
                                    add(buildJsonObject {
                                        put("id", profile.uuid.trim())
                                        put("alterId", profile.alterId)
                                        put("security", profile.encryption.ifBlank { "auto" })
                                    })
                                })
                            })
                        })
                    })
                }

                ProxyProtocol.TROJAN -> {
                    put("settings", buildJsonObject {
                        put("servers", buildJsonArray {
                            add(buildJsonObject {
                                put("address", profile.address.trim())
                                put("port", profile.port)
                                put("password", profile.password)
                            })
                        })
                    })
                }

                ProxyProtocol.SHADOWSOCKS -> {
                    put("settings", buildJsonObject {
                        put("servers", buildJsonArray {
                            add(buildJsonObject {
                                put("address", profile.address.trim())
                                put("port", profile.port)
                                put("method", profile.ssMethod.trim())
                                put("password", profile.password)
                            })
                        })
                    })
                }

                ProxyProtocol.AMIR -> {
                    put("settings", buildJsonObject {
                        put("servers", buildJsonArray {
                            add(buildJsonObject {
                                put("address", "127.0.0.1")
                                put("port", profile.port.takeIf { it in 1..65535 } ?: 40446)
                            })
                        })
                    })
                }

                ProxyProtocol.HTTP, ProxyProtocol.SOCKS5 -> {
                    put("settings", buildJsonObject {
                        put("servers", buildJsonArray {
                            add(buildJsonObject {
                                put("address", profile.address.trim())
                                put("port", profile.port)
                                if (profile.username.isNotBlank() || profile.password.isNotBlank()) {
                                    put("users", buildJsonArray {
                                        add(buildJsonObject {
                                            put("user", profile.username)
                                            put("pass", profile.password)
                                        })
                                    })
                                }
                            })
                        })
                    })
                }
            }

            put("streamSettings", buildStreamSettings(profile))

            val muxCompatible = profile.protocol in setOf(
                ProxyProtocol.VLESS,
                ProxyProtocol.VMESS,
                ProxyProtocol.TROJAN,
                ProxyProtocol.SHADOWSOCKS
            ) && profile.flow.isBlank() && profile.transport != TransportType.XHTTP

            put("mux", buildJsonObject {
                if (profile.muxEnabled && muxCompatible) {
                    put("enabled", true)
                    put("concurrency", profile.muxConcurrency.coerceIn(1, 128))
                } else {
                    put("enabled", false)
                }
            })
        }
    }

    private fun buildStreamSettings(profile: ServerProfile): JsonObject {
        val isSimpleProxy = profile.protocol == ProxyProtocol.HTTP ||
            profile.protocol == ProxyProtocol.SOCKS5 ||
            profile.protocol == ProxyProtocol.AMIR
        val network = if (isSimpleProxy) "tcp" else profile.transport.coreNetwork
        val security = when {
            profile.protocol == ProxyProtocol.AMIR -> SecurityType.NONE
            isSimpleProxy && profile.security == SecurityType.TLS -> SecurityType.TLS
            isSimpleProxy -> SecurityType.NONE
            else -> profile.security
        }

        return buildJsonObject {
            put("network", network)
            put("security", security.xrayValue)

            when (security) {
                SecurityType.TLS -> {
                    put("tlsSettings", buildJsonObject {
                        val serverName = profile.sni.ifBlank { profile.host.ifBlank { profile.address } }
                        put("serverName", serverName)
                        put("allowInsecure", profile.allowInsecure)
                        if (profile.fingerprint.isNotBlank()) {
                            put("fingerprint", profile.fingerprint)
                        }
                        if (profile.alpn.isNotBlank()) {
                            val items = profile.alpn.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                            if (items.isNotEmpty()) {
                                put("alpn", buildJsonArray { items.forEach { add(JsonPrimitive(it)) } })
                            }
                        }
                    })
                }
                SecurityType.REALITY -> {
                    put("realitySettings", buildJsonObject {
                        put("serverName", profile.sni.ifBlank { profile.address })
                        put("publicKey", profile.realityPublicKey)
                        put("shortId", profile.realityShortId)
                        put("fingerprint", profile.fingerprint.ifBlank { "chrome" })
                        if (profile.realitySpiderX.isNotBlank()) {
                            put("spiderX", profile.realitySpiderX)
                        }
                    })
                }
                SecurityType.NONE -> {}
            }

            if (!isSimpleProxy) {
                when (profile.transport) {
                    TransportType.WS -> {
                        put("wsSettings", buildJsonObject {
                            put("path", profile.path.ifBlank { "/" })
                            if (profile.host.isNotBlank()) {
                                put("host", profile.host)
                                put("headers", buildJsonObject { put("Host", profile.host) })
                            }
                        })
                    }
                    TransportType.HTTPUPGRADE -> {
                        put("httpupgradeSettings", buildJsonObject {
                            put("path", profile.path.ifBlank { "/" })
                            if (profile.host.isNotBlank()) put("host", profile.host)
                        })
                    }
                    TransportType.XHTTP -> {
                        put("xhttpSettings", buildJsonObject {
                            put("path", profile.path.ifBlank { "/" })
                            put("mode", profile.xhttpMode.ifBlank { "auto" })
                            if (profile.host.isNotBlank()) put("host", profile.host)
                            if (profile.xhttpExtraJson.isNotBlank()) {
                                runCatching { Json.parseToJsonElement(profile.xhttpExtraJson) }.getOrNull()?.let {
                                    put("extra", it)
                                }
                            }
                        })
                    }
                    TransportType.GRPC -> {
                        put("grpcSettings", buildJsonObject {
                            put("serviceName", profile.serviceName)
                            put("multiMode", profile.grpcMode.equals("multi", ignoreCase = true))
                            if (profile.grpcAuthority.isNotBlank()) {
                                put("authority", profile.grpcAuthority)
                            }
                        })
                    }
                    TransportType.H2 -> {
                        put("httpSettings", buildJsonObject {
                            put("path", profile.path.ifBlank { "/" })
                            if (profile.host.isNotBlank()) {
                                put("host", buildJsonArray {
                                    profile.host.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                                        .forEach { add(JsonPrimitive(it)) }
                                })
                            }
                        })
                    }
                    TransportType.TCP -> {
                        if (profile.headerType.equals("http", ignoreCase = true)) {
                            put("tcpSettings", buildJsonObject {
                                put("header", buildJsonObject {
                                    put("type", "http")
                                    put("request", buildJsonObject {
                                        put("path", buildJsonArray { add(JsonPrimitive(profile.path.ifBlank { "/" })) })
                                        if (profile.host.isNotBlank()) {
                                            put("headers", buildJsonObject {
                                                put("Host", buildJsonArray { add(JsonPrimitive(profile.host)) })
                                            })
                                        }
                                    })
                                })
                            })
                        }
                    }
                    TransportType.QUIC, TransportType.KCP -> Unit
                }
            }
        }
    }
}
