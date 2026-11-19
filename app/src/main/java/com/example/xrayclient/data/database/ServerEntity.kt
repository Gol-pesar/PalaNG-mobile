package com.example.xrayclient.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.data.models.TransportType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val uuid: String,
    val encryption: String,
    val alterId: Int,
    val flow: String,
    val password: String,
    val ssMethod: String,
    val username: String,
    val socksUdpAssociate: Boolean,
    val transport: String,
    val path: String,
    val host: String,
    val headerType: String,
    val serviceName: String,
    val grpcMode: String,
    val grpcAuthority: String,
    val xhttpMode: String,
    val xhttpExtraJson: String,
    val security: String,
    val sni: String,
    val alpn: String,
    val fingerprint: String,
    val allowInsecure: Boolean,
    val realityPublicKey: String,
    val realityShortId: String,
    val realitySpiderX: String,
    val muxEnabled: Boolean,
    val muxConcurrency: Int,
    val subscriptionId: String?,
    val isFavorite: Boolean,
    val latencyMs: Long?,
    val rawUri: String,
    val customOutboundJson: String,
    val extraParamsJson: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toDomain(): ServerProfile {
        val extraMap = LinkedHashMap<String, String>()
        if (extraParamsJson.isNotBlank()) {
            try {
                val obj = Json.parseToJsonElement(extraParamsJson).jsonObject
                obj.forEach { (key, el) -> extraMap[key] = el.jsonPrimitive.contentOrNull.orEmpty() }
            } catch (_: Exception) {
            }
        }
        return ServerProfile(
            id = id,
            name = name,
            protocol = runCatching { ProxyProtocol.valueOf(protocol) }.getOrDefault(ProxyProtocol.VLESS),
            address = address,
            port = port,
            uuid = uuid,
            encryption = encryption,
            alterId = alterId,
            flow = flow,
            password = password,
            ssMethod = ssMethod,
            username = username,
            socksUdpAssociate = socksUdpAssociate,
            transport = runCatching { TransportType.valueOf(transport) }.getOrDefault(TransportType.TCP),
            path = path,
            host = host,
            headerType = headerType,
            serviceName = serviceName,
            grpcMode = grpcMode,
            grpcAuthority = grpcAuthority,
            xhttpMode = xhttpMode,
            xhttpExtraJson = xhttpExtraJson,
            security = runCatching { SecurityType.valueOf(security) }.getOrDefault(SecurityType.NONE),
            sni = sni,
            alpn = alpn,
            fingerprint = fingerprint,
            allowInsecure = allowInsecure,
            realityPublicKey = realityPublicKey,
            realityShortId = realityShortId,
            realitySpiderX = realitySpiderX,
            muxEnabled = muxEnabled,
            muxConcurrency = muxConcurrency,
            subscriptionId = subscriptionId,
            isFavorite = isFavorite,
            latencyMs = latencyMs,
            rawUri = rawUri,
            customOutboundJson = customOutboundJson,
            extraParams = extraMap,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(profile: ServerProfile): ServerEntity {
            val extraJson = if (profile.extraParams.isEmpty()) {
                ""
            } else {
                buildJsonObject {
                    profile.extraParams.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
                }.toString()
            }
            return ServerEntity(
                id = profile.id,
                name = profile.name,
                protocol = profile.protocol.name,
                address = profile.address,
                port = profile.port,
                uuid = profile.uuid,
                encryption = profile.encryption,
                alterId = profile.alterId,
                flow = profile.flow,
                password = profile.password,
                ssMethod = profile.ssMethod,
                username = profile.username,
                socksUdpAssociate = profile.socksUdpAssociate,
                transport = profile.transport.name,
                path = profile.path,
                host = profile.host,
                headerType = profile.headerType,
                serviceName = profile.serviceName,
                grpcMode = profile.grpcMode,
                grpcAuthority = profile.grpcAuthority,
                xhttpMode = profile.xhttpMode,
                xhttpExtraJson = profile.xhttpExtraJson,
                security = profile.security.name,
                sni = profile.sni,
                alpn = profile.alpn,
                fingerprint = profile.fingerprint,
                allowInsecure = profile.allowInsecure,
                realityPublicKey = profile.realityPublicKey,
                realityShortId = profile.realityShortId,
                realitySpiderX = profile.realitySpiderX,
                muxEnabled = profile.muxEnabled,
                muxConcurrency = profile.muxConcurrency,
                subscriptionId = profile.subscriptionId,
                isFavorite = profile.isFavorite,
                latencyMs = profile.latencyMs,
                rawUri = profile.rawUri,
                customOutboundJson = profile.customOutboundJson,
                extraParamsJson = extraJson,
                createdAt = profile.createdAt,
                updatedAt = profile.updatedAt
            )
        }
    }
}
