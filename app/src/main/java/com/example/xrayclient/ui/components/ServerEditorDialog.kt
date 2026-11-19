package com.example.xrayclient.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.data.models.TransportType
import com.example.xrayclient.tunnel.Cl2Config

@Composable
fun ServerEditorDialog(
    initialProfile: ServerProfile? = null,
    onSave: (ServerProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialProfile?.name.orEmpty()) }
    var protocol by remember { mutableStateOf(initialProfile?.protocol ?: ProxyProtocol.VLESS) }
    var address by remember { mutableStateOf(initialProfile?.address.orEmpty()) }
    var portText by remember { mutableStateOf(initialProfile?.port?.toString() ?: "443") }
    var uuid by remember { mutableStateOf(initialProfile?.uuid.orEmpty()) }
    var alterIdText by remember { mutableStateOf((initialProfile?.alterId ?: 0).toString()) }
    var encryption by remember { mutableStateOf(initialProfile?.encryption ?: "none") }
    var flow by remember { mutableStateOf(initialProfile?.flow.orEmpty()) }
    var password by remember { mutableStateOf(initialProfile?.password.orEmpty()) }
    var ssMethod by remember { mutableStateOf(initialProfile?.ssMethod ?: "aes-256-gcm") }
    var username by remember { mutableStateOf(initialProfile?.username.orEmpty()) }
    var socksUdp by remember { mutableStateOf(initialProfile?.socksUdpAssociate ?: true) }
    var transport by remember { mutableStateOf(initialProfile?.transport ?: TransportType.TCP) }
    var path by remember { mutableStateOf(initialProfile?.path.orEmpty()) }
    var host by remember { mutableStateOf(initialProfile?.host.orEmpty()) }
    var serviceName by remember { mutableStateOf(initialProfile?.serviceName.orEmpty()) }
    var security by remember { mutableStateOf(initialProfile?.security ?: SecurityType.NONE) }
    var sni by remember { mutableStateOf(initialProfile?.sni.orEmpty()) }
    var alpn by remember { mutableStateOf(initialProfile?.alpn.orEmpty()) }
    var fingerprint by remember { mutableStateOf(initialProfile?.fingerprint.orEmpty()) }
    var realityPublicKey by remember { mutableStateOf(initialProfile?.realityPublicKey.orEmpty()) }
    var realityShortId by remember { mutableStateOf(initialProfile?.realityShortId.orEmpty()) }
    var allowInsecure by remember { mutableStateOf(initialProfile?.allowInsecure ?: false) }
    var muxEnabled by remember { mutableStateOf(initialProfile?.muxEnabled ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialProfile == null) "ایجاد پیکربندی دستی" else "ویرایش پیکربندی")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("پروتکل", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProxyProtocol.entries.forEach { proto ->
                        FilterChip(
                            selected = protocol == proto,
                            onClick = {
                                val prev = protocol
                                protocol = proto
                                if (proto == ProxyProtocol.AMIR) {
                                    if (portText == "443" || portText.isBlank()) {
                                        portText = Cl2Config.DEFAULT_LISTEN_PORT.toString()
                                    }
                                    if (name.isBlank()) name = "AMIR"
                                } else if (prev == ProxyProtocol.AMIR && portText == Cl2Config.DEFAULT_LISTEN_PORT.toString()) {
                                    portText = "443"
                                }
                            },
                            label = { Text(proto.displayName) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام سرور (Remark)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (protocol != ProxyProtocol.AMIR) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("آدرس سرور / IP") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("پورت") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                when (protocol) {
                    ProxyProtocol.VLESS -> {
                        OutlinedTextField(
                            value = uuid,
                            onValueChange = { uuid = it },
                            label = { Text("UUID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = flow,
                            onValueChange = { flow = it },
                            label = { Text("Flow (مثلاً xtls-rprx-vision)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ProxyProtocol.VMESS -> {
                        OutlinedTextField(
                            value = uuid,
                            onValueChange = { uuid = it },
                            label = { Text("UUID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = alterIdText,
                                onValueChange = { alterIdText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("AlterID") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = encryption,
                                onValueChange = { encryption = it },
                                label = { Text("Security (auto)") },
                                singleLine = true,
                                modifier = Modifier.weight(1.5f)
                            )
                        }
                    }
                    ProxyProtocol.TROJAN -> {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("رمز عبور Trojan") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ProxyProtocol.SHADOWSOCKS -> {
                        OutlinedTextField(
                            value = ssMethod,
                            onValueChange = { ssMethod = it },
                            label = { Text("روش رمزنگاری (aes-256-gcm, ...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("رمز عبور") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ProxyProtocol.HTTP, ProxyProtocol.SOCKS5 -> {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("نام کاربری (اختیاری)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("رمز عبور (اختیاری)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (protocol == ProxyProtocol.SOCKS5) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("فعال‌سازی UDP ASSOCIATE", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = socksUdp, onCheckedChange = { socksUdp = it })
                            }
                        }
                    }
                    ProxyProtocol.AMIR -> {
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("WORKER URL") },
                            placeholder = { Text("https://example.workers.dev/") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("CLEAN IP (اختیاری)") },
                            placeholder = { Text(Cl2Config.DEFAULT_CLEAN_IP) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = uuid,
                            onValueChange = { uuid = it },
                            label = { Text("UUID") },
                            placeholder = { Text(Cl2Config.DEFAULT_UUID) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("پورت محلی SOCKS5") },
                            placeholder = { Text(Cl2Config.DEFAULT_LISTEN_PORT.toString()) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (protocol in setOf(ProxyProtocol.VLESS, ProxyProtocol.VMESS, ProxyProtocol.TROJAN)) {
                    Text("نوع انتقال (Transport)", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            TransportType.TCP,
                            TransportType.WS,
                            TransportType.GRPC,
                            TransportType.XHTTP,
                            TransportType.HTTPUPGRADE,
                            TransportType.H2
                        ).forEach { t ->
                            FilterChip(
                                selected = transport == t,
                                onClick = { transport = t },
                                label = { Text(t.displayName) }
                            )
                        }
                    }

                    if (transport == TransportType.GRPC) {
                        OutlinedTextField(
                            value = serviceName,
                            onValueChange = { serviceName = it },
                            label = { Text("Service Name (gRPC)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (transport != TransportType.TCP) {
                        OutlinedTextField(
                            value = path,
                            onValueChange = { path = it },
                            label = { Text("مسیر (Path)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("میزبان (Host Header)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (protocol != ProxyProtocol.AMIR) {
                    Text("امنیت لایه انتقال", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val allowedSecurity = if (protocol in setOf(ProxyProtocol.HTTP, ProxyProtocol.SOCKS5, ProxyProtocol.SHADOWSOCKS)) {
                            listOf(SecurityType.NONE, SecurityType.TLS)
                        } else {
                            SecurityType.entries
                        }
                        allowedSecurity.forEach { sec ->
                            FilterChip(
                                selected = security == sec,
                                onClick = { security = sec },
                                label = { Text(sec.displayName) }
                            )
                        }
                    }

                    if (security != SecurityType.NONE) {
                        OutlinedTextField(
                            value = sni,
                            onValueChange = { sni = it },
                            label = { Text("SNI") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = fingerprint,
                            onValueChange = { fingerprint = it },
                            label = { Text("Fingerprint (مثلاً chrome)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = alpn,
                            onValueChange = { alpn = it },
                            label = { Text("ALPN (مثلاً h2,http/1.1)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (security == SecurityType.REALITY) {
                        OutlinedTextField(
                            value = realityPublicKey,
                            onValueChange = { realityPublicKey = it },
                            label = { Text("Public Key (pbk)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = realityShortId,
                            onValueChange = { realityShortId = it },
                            label = { Text("Short ID (sid)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (security == SecurityType.TLS) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Allow Insecure (غیرتوصیه‌شده)", style = MaterialTheme.typography.bodySmall)
                            Switch(checked = allowInsecure, onCheckedChange = { allowInsecure = it })
                        }
                    }
                }

                if (protocol in setOf(ProxyProtocol.VLESS, ProxyProtocol.VMESS, ProxyProtocol.TROJAN, ProxyProtocol.SHADOWSOCKS)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("فعال‌سازی Mux", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = muxEnabled, onCheckedChange = { muxEnabled = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val port = portText.toIntOrNull()
                        ?: if (protocol == ProxyProtocol.AMIR) Cl2Config.DEFAULT_LISTEN_PORT else 0
                    val defaultName = if (protocol == ProxyProtocol.AMIR) "AMIR" else "${address.trim()}:$port"
                    val base = initialProfile ?: ServerProfile(
                        name = name.ifBlank { defaultName },
                        protocol = protocol,
                        address = address.trim(),
                        port = port
                    )
                    val updated = base.copy(
                        name = name.trim().ifBlank { defaultName },
                        protocol = protocol,
                        address = address.trim(),
                        port = port,
                        uuid = uuid.trim(),
                        alterId = alterIdText.toIntOrNull() ?: 0,
                        encryption = if (protocol == ProxyProtocol.VMESS) {
                            encryption.ifBlank { "auto" }
                        } else {
                            "none"
                        },
                        flow = flow.trim(),
                        password = password,
                        ssMethod = ssMethod.trim(),
                        username = username.trim(),
                        socksUdpAssociate = if (protocol == ProxyProtocol.AMIR) false else socksUdp,
                        transport = if (protocol == ProxyProtocol.AMIR) TransportType.TCP else transport,
                        path = path.trim(),
                        host = host.trim(),
                        serviceName = serviceName.trim(),
                        security = if (protocol == ProxyProtocol.AMIR) SecurityType.NONE else security,
                        sni = sni.trim(),
                        alpn = alpn.trim(),
                        fingerprint = fingerprint.trim(),
                        realityPublicKey = realityPublicKey.trim(),
                        realityShortId = realityShortId.trim(),
                        allowInsecure = allowInsecure,
                        muxEnabled = if (protocol == ProxyProtocol.AMIR) false else muxEnabled,
                        customOutboundJson = ""
                    )
                    onSave(updated)
                }
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
