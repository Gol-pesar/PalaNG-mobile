package com.example.xrayclient.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.tunnel.Cl2Config
import com.example.xrayclient.engine.EngineAvailability
import com.example.xrayclient.statistics.TrafficStatisticsSnapshot
import com.example.xrayclient.ui.components.LatencyPill
import com.example.xrayclient.ui.components.PowerConnectButton
import com.example.xrayclient.ui.components.ProtocolBadge
import com.example.xrayclient.ui.theme.AmberWarning
import com.example.xrayclient.ui.theme.CrimsonError
import com.example.xrayclient.ui.theme.CyanAccent
import com.example.xrayclient.ui.theme.EmeraldCore
import com.example.xrayclient.ui.theme.JetBrainsMonoFamily
import com.example.xrayclient.utils.NetworkUtils
import com.example.xrayclient.vpn.ConnectionState
import com.example.xrayclient.vpn.VpnStatusSnapshot

@Composable
fun HomeScreen(
    vpnSnapshot: VpnStatusSnapshot,
    selectedServer: ServerProfile?,
    engineAvailability: EngineAvailability,
    trafficStats: TrafficStatisticsSnapshot,
    onToggleConnection: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenEngineDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.background
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Brand & Engine Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldCore.copy(alpha = 0.16f))
                            .border(1.dp, EmeraldCore.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "لوگوی ایکس‌ری کلاینت",
                            tint = EmeraldCore,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "ایکس‌ری کلاینت",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تونل امن Xray-core",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val isReady = engineAvailability is EngineAvailability.Ready
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isReady) EmeraldCore.copy(alpha = 0.14f) else AmberWarning.copy(alpha = 0.14f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onOpenEngineDiagnostics)
                        .testTag("engine_status_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isReady) EmeraldCore else AmberWarning)
                        )
                        Text(
                            text = if (isReady) "هسته آماده" else "نیاز به هسته",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isReady) EmeraldCore else AmberWarning
                        )
                    }
                }
            }

            // Explicit ENGINE_NOT_INSTALLED or ERROR Banner
            AnimatedVisibility(
                visible = engineAvailability is EngineAvailability.NotInstalled ||
                    vpnSnapshot.state == ConnectionState.ERROR ||
                    vpnSnapshot.state == ConnectionState.ENGINE_NOT_INSTALLED
            ) {
                val isEngineMissing = engineAvailability is EngineAvailability.NotInstalled ||
                    vpnSnapshot.state == ConnectionState.ENGINE_NOT_INSTALLED
                val bannerColor = if (isEngineMissing) AmberWarning else CrimsonError
                val message = vpnSnapshot.errorMessage ?: when (engineAvailability) {
                    is EngineAvailability.NotInstalled -> engineAvailability.reason
                    is EngineAvailability.Ready -> ""
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenEngineDiagnostics),
                    colors = CardDefaults.cardColors(
                        containerColor = bannerColor.copy(alpha = 0.10f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (isEngineMissing) Icons.Rounded.WarningAmber else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = bannerColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEngineMissing) "هسته بومی Xray نصب نشده است (ENGINE_NOT_INSTALLED)" else "خطا در برقراری اتصال",
                                style = MaterialTheme.typography.titleSmall,
                                color = bannerColor,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Connection State Pill + Central Power Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val stateColor = when (vpnSnapshot.state) {
                    ConnectionState.CONNECTED -> EmeraldCore
                    ConnectionState.CONNECTING,
                    ConnectionState.RECONNECTING,
                    ConnectionState.REQUESTING_PERMISSION -> CyanAccent
                    ConnectionState.DISCONNECTING,
                    ConnectionState.ENGINE_NOT_INSTALLED -> AmberWarning
                    ConnectionState.ERROR -> CrimsonError
                    ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = vpnSnapshot.state.persianLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = stateColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("connection_state_text")
                )

                PowerConnectButton(
                    state = vpnSnapshot.state,
                    onClick = {
                        if (engineAvailability is EngineAvailability.NotInstalled &&
                            vpnSnapshot.state == ConnectionState.ENGINE_NOT_INSTALLED
                        ) {
                            onOpenEngineDiagnostics()
                        } else {
                            onToggleConnection()
                        }
                    }
                )
            }

            // Selected Server Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = if (selectedServer != null) onOpenServers else onOpenImport)
                    .testTag("selected_server_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                if (selectedServer != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Dns,
                                    contentDescription = null,
                                    tint = EmeraldCore
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = selectedServer.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ProtocolBadge(protocol = selectedServer.protocol)
                                    val endpointLabel = if (selectedServer.protocol == ProxyProtocol.AMIR) {
                                        val workerHost = Cl2Config.parseWorkerUrl(selectedServer.host).host
                                            .ifBlank { selectedServer.host }
                                        val cleanLabel = selectedServer.address.ifBlank { "Auto" }
                                        "$cleanLabel → $workerHost"
                                    } else {
                                        "${selectedServer.address}:${selectedServer.port}"
                                    }
                                    Text(
                                        text = endpointLabel,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LatencyPill(latencyMs = selectedServer.latencyMs, isTesting = false)
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = "تغییر سرور",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "سروری انتخاب نشده است",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "یک لینک پروکسی، کد QR یا اشتراک اضافه کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onOpenImport) {
                            Text("افزودن سرور")
                        }
                    }
                }
            }

            // Real-Time Statistics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatMetricCard(
                    title = "سرعت دانلود",
                    value = NetworkUtils.formatSpeed(trafficStats.downloadSpeedBytesPerSec),
                    subValue = "کل: ${NetworkUtils.formatBytes(trafficStats.totalDownloadedBytes)}",
                    icon = Icons.Rounded.ArrowDownward,
                    accent = EmeraldCore,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "سرعت آپلود",
                    value = NetworkUtils.formatSpeed(trafficStats.uploadSpeedBytesPerSec),
                    subValue = "کل: ${NetworkUtils.formatBytes(trafficStats.totalUploadedBytes)}",
                    icon = Icons.Rounded.ArrowUpward,
                    accent = CyanAccent,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatMetricCard(
                    title = "مدت اتصال",
                    value = NetworkUtils.formatDuration(trafficStats.durationSeconds),
                    subValue = "زمان نشست فعلی",
                    icon = Icons.Rounded.Schedule,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "وضعیت تونل VPN",
                    value = if (vpnSnapshot.state == ConnectionState.CONNECTED) "TUN فعال" else "غیرفعال",
                    subValue = if (vpnSnapshot.state == ConnectionState.CONNECTED) {
                        if (trafficStats.usingKernelFallbackCounters) "شمارنده هسته: جایگزین سیستمی" else "شمارنده Xray فعال"
                    } else {
                        "تونل بسته است"
                    },
                    icon = Icons.Rounded.VpnLock,
                    accent = if (vpnSnapshot.state == ConnectionState.CONNECTED) EmeraldCore else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatMetricCard(
    title: String,
    value: String,
    subValue: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
