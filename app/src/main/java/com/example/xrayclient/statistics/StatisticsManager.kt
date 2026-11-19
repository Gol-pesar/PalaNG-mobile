package com.example.xrayclient.statistics

import android.os.Process
import android.os.SystemClock
import android.net.TrafficStats
import com.example.xrayclient.engine.XrayEngineAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TrafficStatisticsSnapshot(
    val downloadSpeedBytesPerSec: Long = 0L,
    val uploadSpeedBytesPerSec: Long = 0L,
    val totalDownloadedBytes: Long = 0L,
    val totalUploadedBytes: Long = 0L,
    val durationSeconds: Long = 0L,
    val usingKernelFallbackCounters: Boolean = false
)

/**
 * Tracks real connection traffic statistics.
 *
 * - Queries [XrayEngineAdapter.queryTrafficStats] first when the native Xray engine is running.
 * - If the active core build does not expose Xray StatsService counters, falls back to
 *   Android [TrafficStats] UID counters (`getUidRxBytes` / `getUidTxBytes`) delta during active VPN.
 * - Never generates fake or random speeds; remains at 0 when disconnected or not installed.
 */
class StatisticsManager(
    private val engineAdapter: XrayEngineAdapter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    private val _stats = MutableStateFlow(TrafficStatisticsSnapshot())
    val stats: StateFlow<TrafficStatisticsSnapshot> = _stats.asStateFlow()

    fun startTracking() {
        if (pollingJob?.isActive == true) return
        val startedElapsedMs = SystemClock.elapsedRealtime()
        val uid = Process.myUid()
        val baselineUidRx = TrafficStats.getUidRxBytes(uid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() }
        val baselineUidTx = TrafficStats.getUidTxBytes(uid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() }

        var lastDownTotal = 0L
        var lastUpTotal = 0L
        var lastSampleMs = startedElapsedMs

        pollingJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val nowMs = SystemClock.elapsedRealtime()
                val elapsedDeltaSec = ((nowMs - lastSampleMs).coerceAtLeast(1L)) / 1000.0
                val durationSec = ((nowMs - startedElapsedMs).coerceAtLeast(0L)) / 1000L

                val engineStats = engineAdapter.queryTrafficStats()
                val (totalDown, totalUp, isFallback) = if (engineStats != null) {
                    Triple(engineStats.downlinkBytes, engineStats.uplinkBytes, false)
                } else {
                    val curRx = TrafficStats.getUidRxBytes(uid)
                    val curTx = TrafficStats.getUidTxBytes(uid)
                    val down = if (baselineUidRx != null && curRx >= baselineUidRx) curRx - baselineUidRx else 0L
                    val up = if (baselineUidTx != null && curTx >= baselineUidTx) curTx - baselineUidTx else 0L
                    Triple(down, up, true)
                }

                val downSpeed = ((totalDown - lastDownTotal).coerceAtLeast(0L) / elapsedDeltaSec).toLong()
                val upSpeed = ((totalUp - lastUpTotal).coerceAtLeast(0L) / elapsedDeltaSec).toLong()

                lastDownTotal = totalDown
                lastUpTotal = totalUp
                lastSampleMs = nowMs

                _stats.value = TrafficStatisticsSnapshot(
                    downloadSpeedBytesPerSec = downSpeed,
                    uploadSpeedBytesPerSec = upSpeed,
                    totalDownloadedBytes = totalDown,
                    totalUploadedBytes = totalUp,
                    durationSeconds = durationSec,
                    usingKernelFallbackCounters = isFallback
                )
            }
        }
    }

    fun stopAndReset() {
        pollingJob?.cancel()
        pollingJob = null
        _stats.value = TrafficStatisticsSnapshot()
    }
}
