package com.example.xrayclient.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExtensionOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xrayclient.ui.theme.AmberWarning
import com.example.xrayclient.ui.theme.CrimsonError
import com.example.xrayclient.ui.theme.CyanAccent
import com.example.xrayclient.ui.theme.EmeraldCore
import com.example.xrayclient.ui.theme.EmeraldGlow
import com.example.xrayclient.vpn.ConnectionState

@Composable
fun PowerConnectButton(
    state: ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor by animateColorAsState(
        targetValue = when (state) {
            ConnectionState.CONNECTED -> EmeraldCore
            ConnectionState.CONNECTING,
            ConnectionState.RECONNECTING,
            ConnectionState.REQUESTING_PERMISSION -> CyanAccent
            ConnectionState.DISCONNECTING -> AmberWarning
            ConnectionState.ENGINE_NOT_INSTALLED -> AmberWarning
            ConnectionState.ERROR -> CrimsonError
            ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(300),
        label = "powerAccent"
    )

    val isTransitioning = state == ConnectionState.CONNECTING ||
        state == ConnectionState.RECONNECTING ||
        state == ConnectionState.DISCONNECTING ||
        state == ConnectionState.REQUESTING_PERMISSION

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(),
        label = "pressScale"
    )

    val icon = when (state) {
        ConnectionState.ENGINE_NOT_INSTALLED -> Icons.Rounded.ExtensionOff
        ConnectionState.ERROR -> Icons.Rounded.ErrorOutline
        ConnectionState.CONNECTING,
        ConnectionState.RECONNECTING,
        ConnectionState.DISCONNECTING -> Icons.Rounded.Sync
        else -> Icons.Rounded.PowerSettingsNew
    }

    val buttonDescription = when (state) {
        ConnectionState.CONNECTED -> "قطع اتصال VPN"
        ConnectionState.ENGINE_NOT_INSTALLED -> "هسته Xray نصب نشده است، نمایش وضعیت"
        else -> "اتصال به VPN"
    }

    Box(
        modifier = modifier.size(212.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer ambient halo
        Canvas(
            modifier = Modifier
                .size(204.dp)
                .scale(if (state == ConnectionState.CONNECTED || isTransitioning) pulseScale else 1f)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = if (state == ConnectionState.CONNECTED) 0.28f else 0.12f),
                        Color.Transparent
                    )
                )
            )
            drawCircle(
                color = accentColor.copy(alpha = 0.30f),
                radius = size.minDimension * 0.44f,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Core interactive button
        val coreBrush = if (state == ConnectionState.CONNECTED) {
            Brush.linearGradient(listOf(EmeraldCore.copy(alpha = 0.24f), EmeraldGlow.copy(alpha = 0.10f)))
        } else {
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.surface
                )
            )
        }

        Box(
            modifier = Modifier
                .size(152.dp)
                .scale(pressScale)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(coreBrush)
                .border(
                    width = 2.dp,
                    color = accentColor.copy(alpha = 0.78f),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(bounded = true),
                    role = Role.Button,
                    onClick = onClick
                )
                .semantics { contentDescription = buttonDescription }
                .testTag("connect_toggle_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (state == ConnectionState.DISCONNECTED) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        accentColor
                    },
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (state) {
                        ConnectionState.CONNECTED -> "قطع اتصال"
                        ConnectionState.CONNECTING -> "در حال اتصال"
                        ConnectionState.RECONNECTING -> "اتصال مجدد"
                        ConnectionState.DISCONNECTING -> "در حال قطع"
                        ConnectionState.REQUESTING_PERMISSION -> "مجوز VPN"
                        ConnectionState.ENGINE_NOT_INSTALLED -> "نیاز به هسته"
                        ConnectionState.ERROR -> "تلاش مجدد"
                        ConnectionState.DISCONNECTED -> "اتصال"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (state == ConnectionState.DISCONNECTED) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        accentColor
                    }
                )
            }
        }
    }
}
