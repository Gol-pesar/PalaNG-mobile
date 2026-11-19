package com.example.xrayclient.domain.managers

import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.data.preferences.SettingsDataStore
import com.example.xrayclient.data.repositories.ServerRepository
import com.example.xrayclient.domain.parsers.BatchImportResult
import com.example.xrayclient.domain.parsers.ProxyLinkExporter
import com.example.xrayclient.domain.parsers.ProxyLinkParser
import com.example.xrayclient.domain.validators.ConfigValidator
import com.example.xrayclient.domain.validators.ValidationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConfigurationManager(
    private val serverRepository: ServerRepository,
    private val settingsDataStore: SettingsDataStore,
    private val parser: ProxyLinkParser = ProxyLinkParser()
) {
    private val seedMutex = Mutex()

    val serversFlow: Flow<List<ServerProfile>> = serverRepository.serversFlow

    suspend fun seedDefaultServersIfNeeded() {
        seedMutex.withLock {
            val seedVersion = settingsDataStore.getDefaultServersSeedVersion()
            if (seedVersion >= DEFAULT_SERVERS_SEED_VERSION) return

            var replacedSelectedLegacyId: String? = null
            if (seedVersion in 1 until DEFAULT_SERVERS_SEED_VERSION) {
                val legacyCl2 = serverRepository.getAllOnce().firstOrNull {
                    it.rawUri == LEGACY_CL2_CONFIG_URI
                }
                if (legacyCl2 != null) {
                    val curSelected = settingsDataStore.getSnapshot().selectedServerId
                    if (curSelected == legacyCl2.id) {
                        replacedSelectedLegacyId = legacyCl2.id
                    }
                    serverRepository.deleteById(legacyCl2.id)
                }
            }

            val urisToSeed = when {
                seedVersion == 0 -> DEFAULT_CONFIG_URIS
                else -> listOf(DEFAULT_AMIR_CONFIG_URI)
            }
            val existingFingerprints = serverRepository.existingFingerprints()
            val result = parser.importBatch(
                rawInput = urisToSeed.joinToString("\n"),
                existingFingerprints = existingFingerprints
            )
            if (result.profiles.isNotEmpty()) {
                serverRepository.insertAll(result.profiles)
                val currentSettings = settingsDataStore.getSnapshot()
                if (currentSettings.selectedServerId == null || replacedSelectedLegacyId != null) {
                    settingsDataStore.setSelectedServerId(result.profiles.first().id)
                }
            }
            settingsDataStore.setDefaultServersSeedVersion(DEFAULT_SERVERS_SEED_VERSION)
        }
    }

    suspend fun importFromText(rawText: String): BatchImportResult {
        val existingFingerprints = serverRepository.existingFingerprints()
        val result = parser.importBatch(rawText, existingFingerprints)
        if (result.profiles.isNotEmpty()) {
            serverRepository.insertAll(result.profiles)
            val currentSettings = settingsDataStore.getSnapshot()
            if (currentSettings.selectedServerId == null) {
                settingsDataStore.setSelectedServerId(result.profiles.first().id)
            }
        }
        return result
    }

    suspend fun saveManualOrEditedProfile(profile: ServerProfile): Result<Unit> {
        return when (val v = ConfigValidator.validateProfile(profile)) {
            is ValidationResult.Invalid -> Result.failure(IllegalArgumentException(v.message))
            ValidationResult.Valid -> {
                serverRepository.save(profile)
                val currentSettings = settingsDataStore.getSnapshot()
                if (currentSettings.selectedServerId == null) {
                    settingsDataStore.setSelectedServerId(profile.id)
                }
                Result.success(Unit)
            }
        }
    }

    suspend fun duplicateProfile(profile: ServerProfile): ServerProfile =
        serverRepository.duplicate(profile)

    suspend fun deleteProfile(profileId: String) {
        serverRepository.deleteById(profileId)
        val current = settingsDataStore.getSnapshot()
        if (current.selectedServerId == profileId) {
            val remaining = serverRepository.getAllOnce().firstOrNull()?.id
            settingsDataStore.setSelectedServerId(remaining)
        }
    }

    suspend fun toggleFavorite(profile: ServerProfile) {
        serverRepository.toggleFavorite(profile.id, profile.isFavorite)
    }

    suspend fun selectServer(profileId: String) {
        settingsDataStore.setSelectedServerId(profileId)
    }

    fun exportToShareUri(profile: ServerProfile): String = ProxyLinkExporter.export(profile)

    companion object {
        const val DEFAULT_SERVERS_SEED_VERSION = 3
        private const val LEGACY_CL2_CONFIG_URI = "socks://127.0.0.1:40446?udp=0#Cl2%20Worker%20(127.0.0.1:40446)"
        const val DEFAULT_AMIR_CONFIG_URI =
            "amir://63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd@172.67.70.244:40446?worker=https%3A%2F%2Ftwilight-dream-9e3c.aliahmadiphotoofficial.workers.dev%2F#AMIR"

        val DEFAULT_CONFIG_URIS = listOf(
            "vless://63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd@[2606:4700:9b05:3153:5a5f:e811:da0e:11d8]:443?path=%2F&security=tls&encryption=none&insecure=0&host=admin.ariamusic.workers.dev&fp=chrome&type=ws&allowInsecure=0&sni=admin.ariamusic.workers.dev#Mahdi4",
            "vless://63ae8279-fafa-4dd0-b2ef-9d6a4ec3ffdd@www.speedtest.net:443?path=%2F&security=tls&encryption=none&insecure=0&host=admin.ariamusic.workers.dev&fp=chrome&type=ws&allowInsecure=0&sni=admin.ariamusic.workers.dev#Mahdi5",
            DEFAULT_AMIR_CONFIG_URI
        )
    }
}
