package com.example.xrayclient.data.repositories

import com.example.xrayclient.data.database.ServerDao
import com.example.xrayclient.data.database.ServerEntity
import com.example.xrayclient.data.models.ServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ServerRepository(private val serverDao: ServerDao) {

    val serversFlow: Flow<List<ServerProfile>> = serverDao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getAllOnce(): List<ServerProfile> =
        serverDao.getAllOnce().map { it.toDomain() }

    suspend fun getById(id: String): ServerProfile? =
        serverDao.getById(id)?.toDomain()

    suspend fun existingFingerprints(): Set<String> =
        serverDao.getAllOnce().map { it.toDomain().duplicateFingerprint() }.toSet()

    suspend fun insertAll(profiles: List<ServerProfile>) {
        if (profiles.isEmpty()) return
        serverDao.insertAll(profiles.map { ServerEntity.fromDomain(it) })
    }

    suspend fun save(profile: ServerProfile) {
        serverDao.insert(ServerEntity.fromDomain(profile.copy(updatedAt = System.currentTimeMillis())))
    }

    suspend fun duplicate(profile: ServerProfile): ServerProfile {
        val copy = profile.copy(
            id = UUID.randomUUID().toString(),
            name = "${profile.name} (کپی)",
            isFavorite = false,
            latencyMs = null,
            subscriptionId = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        serverDao.insert(ServerEntity.fromDomain(copy))
        return copy
    }

    suspend fun deleteById(id: String) {
        serverDao.deleteById(id)
    }

    suspend fun replaceSubscriptionServers(subscriptionId: String, newProfiles: List<ServerProfile>) {
        // Never replace existing valid servers with an empty list unless parsing succeeded with >= 1 server
        if (newProfiles.isEmpty()) return
        serverDao.deleteBySubscriptionId(subscriptionId)
        serverDao.insertAll(newProfiles.map {
            ServerEntity.fromDomain(it.copy(subscriptionId = subscriptionId))
        })
    }

    suspend fun deleteBySubscriptionId(subscriptionId: String) {
        serverDao.deleteBySubscriptionId(subscriptionId)
    }

    suspend fun toggleFavorite(id: String, currentFavorite: Boolean) {
        serverDao.updateFavorite(id, !currentFavorite, System.currentTimeMillis())
    }

    suspend fun updateLatency(id: String, latencyMs: Long?) {
        serverDao.updateLatency(id, latencyMs)
    }
}
