package com.example.xrayclient.engine

sealed interface EngineAvailability {
    data class Ready(
        val backendName: String,
        val coreVersion: String,
        val supportsDirectTunFd: Boolean,
        val requiresTun2SocksBridge: Boolean
    ) : EngineAvailability

    data class NotInstalled(
        val reason: String,
        val checkedArtifacts: List<String>
    ) : EngineAvailability
}

enum class EngineRuntimeState {
    IDLE,
    INITIALIZING,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}

data class EngineTrafficStats(
    val uplinkBytes: Long,
    val downlinkBytes: Long
)

data class EngineDiagnosticLog(
    val timestampMs: Long = System.currentTimeMillis(),
    val level: String,
    val message: String
)

data class XrayStartConfig(
    val configJson: String,
    val tunFd: Int,
    val tunMtu: Int = 1500,
    val socksInboundPort: Int = 10808,
    val httpInboundPort: Int = 10809,
    val ipv4Address: String = "172.19.0.1",
    val ipv6Enabled: Boolean = false,
    val ipv6Address: String = "fd00::1",
    val assetDirPath: String
)

class EngineNotInstalledException(
    message: String = "هسته بومی Xray-core در این بسته نصب نشده است."
) : IllegalStateException(message)
