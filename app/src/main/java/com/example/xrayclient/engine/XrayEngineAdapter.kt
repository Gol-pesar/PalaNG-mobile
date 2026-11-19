package com.example.xrayclient.engine

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Isolation boundary between the Android/Kotlin VPN architecture and the real native Xray-core AAR/JNI engine.
 *
 * No UI, ViewModel, or VpnService component should ever talk to gomobile or JNI directly.
 * All native operations are gated behind [detectAvailability] so the app never simulates
 * a working VPN when the real Xray-core binary is absent.
 */
interface XrayEngineAdapter {

    /**
     * Inspects the APK runtime for a real externally built Xray-core Android AAR/JNI library
     * and TUN bridge without crashing if missing.
     */
    fun detectAvailability(): EngineAvailability

    /**
     * Initializes Xray-core assets (`geoip.dat`, `geosite.dat`, runtime environment).
     */
    suspend fun initialize(appContext: Context, assetDir: File): Result<Unit>

    /**
     * Validates an Xray JSON configuration against the native Xray-core parser when installed,
     * or returns [EngineNotInstalledException] if the native library is absent.
     */
    suspend fun validateConfig(xrayJson: String): Result<Unit>

    /**
     * Starts the real Xray-core instance and attaches the TUN forwarding path.
     *
     * @param config Launch parameters including Xray JSON, TUN file descriptor, and local ports.
     * @param protectSocket Callback wrapping `VpnService.protect(fd)` to prevent upstream routing loops.
     */
    suspend fun start(
        config: XrayStartConfig,
        protectSocket: (Int) -> Boolean
    ): Result<Unit>

    /**
     * Stops the running Xray-core instance and TUN forwarding bridge, releasing native resources.
     */
    suspend fun stop(): Result<Unit>

    /**
     * Gracefully restarts the engine with a new configuration.
     */
    suspend fun restart(
        config: XrayStartConfig,
        protectSocket: (Int) -> Boolean
    ): Result<Unit>

    /**
     * Queries real cumulative uplink/downlink byte counters from Xray-core StatsService,
     * or returns `null` if unavailable or engine is not running.
     */
    fun queryTrafficStats(): EngineTrafficStats?

    /**
     * Measures real end-to-end outbound delay (in milliseconds) through the native core
     * without disrupting an active VPN session, or returns `null` if unavailable.
     */
    suspend fun measureOutboundDelay(
        xrayJson: String,
        testUrl: String = "https://www.gstatic.com/generate_204"
    ): Long? = null

    /**
     * Returns the native Xray-core version string if installed, or `null`.
     */
    fun getCoreVersion(): String?

    fun observeState(): StateFlow<EngineRuntimeState>

    fun observeLogs(): StateFlow<List<EngineDiagnosticLog>>
}
