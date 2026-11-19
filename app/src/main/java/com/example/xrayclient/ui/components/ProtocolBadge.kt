package com.example.xrayclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.ui.theme.AmberWarning
import com.example.xrayclient.ui.theme.CrimsonError
import com.example.xrayclient.ui.theme.CyanAccent
import com.example.xrayclient.ui.theme.EmeraldCore
import com.example.xrayclient.ui.theme.JetBrainsMonoFamily

@Composable
fun ProtocolBadge(
    protocol: ProxyProtocol,
    modifier: Modifier = Modifier
) {
    val tint = when (protocol) {
        ProxyProtocol.VLESS -> EmeraldCore
        ProxyProtocol.VMESS -> CyanAccent
        ProxyProtocol.TROJAN -> Color(0xFFA78BFA)
        ProxyProtocol.SHADOWSOCKS -> AmberWarning
        ProxyProtocol.HTTP -> Color(0xFFFB7185)
        ProxyProtocol.SOCKS5 -> Color(0xFF60A5FA)
        ProxyProtocol.AMIR -> Color(0xFF14B8A6)
    }

    Text(
        text = protocol.displayName.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold
        ),
        color = tint,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun LatencyPill(
    latencyMs: Long?,
    isTesting: Boolean,
    modifier: Modifier = Modifier
) {
    val (label, color) = when {
        isTesting -> "…" to CyanAccent
        latencyMs == null -> "نامشخص" to MaterialTheme.colorScheme.onSurfaceVariant
        latencyMs <= 0L -> "Timeout" to CrimsonError
        latencyMs < 250L -> "${latencyMs} ms" to EmeraldCore
        latencyMs < 600L -> "${latencyMs} ms" to AmberWarning
        else -> "${latencyMs} ms" to CrimsonError
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium
            ),
            color = color
        )
    }
}
