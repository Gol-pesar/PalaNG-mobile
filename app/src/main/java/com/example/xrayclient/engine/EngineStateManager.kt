package com.example.xrayclient.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks the availability and runtime status of the Xray-core native engine for UI and VPN layers.
 */
class EngineStateManager(
    private val engineAdapter: XrayEngineAdapter
) {
    private val _availability = MutableStateFlow(engineAdapter.detectAvailability())
    val availability: StateFlow<EngineAvailability> = _availability.asStateFlow()

    val runtimeState: StateFlow<EngineRuntimeState> = engineAdapter.observeState()
    val diagnosticLogs: StateFlow<List<EngineDiagnosticLog>> = engineAdapter.observeLogs()

    fun refreshAvailability(): EngineAvailability {
        val detected = engineAdapter.detectAvailability()
        _availability.value = detected
        return detected
    }

    fun isEngineInstalled(): Boolean = refreshAvailability() is EngineAvailability.Ready
}
