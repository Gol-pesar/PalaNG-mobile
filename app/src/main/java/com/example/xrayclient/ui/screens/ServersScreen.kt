package com.example.xrayclient.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xrayclient.data.models.ProxyProtocol
import com.example.xrayclient.data.models.SecurityType
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.tunnel.Cl2Config
import com.example.xrayclient.ui.ServerSortOrder
import com.example.xrayclient.ui.components.LatencyPill
import com.example.xrayclient.ui.components.ProtocolBadge
import com.example.xrayclient.ui.components.ServerEditorDialog
import com.example.xrayclient.ui.components.ShareServerDialog
import com.example.xrayclient.ui.theme.AmberWarning
import com.example.xrayclient.ui.theme.EmeraldCore
import com.example.xrayclient.ui.theme.JetBrainsMonoFamily

@Composable
fun ServersScreen(
    servers: List<ServerProfile>,
    selectedServerId: String?,
    searchQuery: String,
    protocolFilter: ProxyProtocol?,
    sortOrder: ServerSortOrder,
    testingServerIds: Set<String>,
    onSearchQueryChange: (String) -> Unit,
    onProtocolFilterChange: (ProxyProtocol?) -> Unit,
    onSortOrderChange: (ServerSortOrder) -> Unit,
    onSelectServer: (ServerProfile) -> Unit,
    onToggleFavorite: (ServerProfile) -> Unit,
    onTestLatency: (ServerProfile) -> Unit,
    onTestAllLatency: () -> Unit,
    onSaveProfile: (ServerProfile, (Result<Unit>) -> Unit) -> Unit,
    onDuplicateServer: (ServerProfile) -> Unit,
    onDeleteServer: (ServerProfile) -> Unit,
    exportShareUri: (ServerProfile) -> String,
    buildXrayJsonPreview: (ServerProfile) -> String,
    onCopiedMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingProfile by remember { mutableStateOf<ServerProfile?>(null) }
    var showManualCreator by remember { mutableStateOf(false) }
    var sharingProfile by remember { mutableStateOf<ServerProfile?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showManualCreator = true },
                containerColor = EmeraldCore,
                modifier = Modifier.testTag("add_server_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "افزودن دستی سرور")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp).fillMaxSize()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سرورها (${servers.size})",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    TextButton(onClick = onTestAllLatency) {
                        Icon(Icons.Rounded.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" تست پینگ همه", modifier = Modifier.padding(start = 4.dp))
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("جستجو بر اساس نام، آدرس یا پروتکل…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_search_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Protocol filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = protocolFilter == null,
                        onClick = { onProtocolFilterChange(null) },
                        label = { Text("همه") }
                    )
                    ProxyProtocol.entries.forEach { proto ->
                        FilterChip(
                            selected = protocolFilter == proto,
                            onClick = {
                                onProtocolFilterChange(if (protocolFilter == proto) null else proto)
                            },
                            label = { Text(proto.displayName) }
                        )
                    }
                }

                // Sort chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ServerSortOrder.entries.forEach { order ->
                        FilterChip(
                            selected = sortOrder == order,
                            onClick = { onSortOrderChange(order) },
                            label = { Text("مرتب‌سازی: ${order.persianLabel}") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (servers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "هنوز سروری وجود ندارد",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "از تب افزودن یا دکمه + یک سرور جدید اضافه کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(servers, key = { it.id }) { server ->
                            ServerCard(
                                profile = server,
                                isSelected = server.id == selectedServerId,
                                isTesting = server.id in testingServerIds,
                                onSelect = { onSelectServer(server) },
                                onToggleFavorite = { onToggleFavorite(server) },
                                onTestLatency = { onTestLatency(server) },
                                onEdit = { editingProfile = server },
                                onDuplicate = { onDuplicateServer(server) },
                                onShare = { sharingProfile = server },
                                onDelete = { onDeleteServer(server) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualCreator) {
        ServerEditorDialog(
            initialProfile = null,
            onSave = { created ->
                onSaveProfile(created) { res ->
                    if (res.isSuccess) showManualCreator = false
                }
            },
            onDismiss = { showManualCreator = false }
        )
    }

    editingProfile?.let { target ->
        ServerEditorDialog(
            initialProfile = target,
            onSave = { updated ->
                onSaveProfile(updated) { res ->
                    if (res.isSuccess) editingProfile = null
                }
            },
            onDismiss = { editingProfile = null }
        )
    }

    sharingProfile?.let { target ->
        ShareServerDialog(
            profile = target,
            shareUri = exportShareUri(target),
            xrayJson = buildXrayJsonPreview(target),
            onCopied = onCopiedMessage,
            onDismiss = { sharingProfile = null }
        )
    }
}

@Composable
private fun ServerCard(
    profile: ServerProfile,
    isSelected: Boolean,
    isTesting: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTestLatency: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val borderModifier = if (isSelected) {
        Modifier.border(1.5.dp, EmeraldCore, RoundedCornerShape(16.dp))
    } else {
        Modifier
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clickable(onClick = onSelect)
            .testTag("server_item_${profile.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                EmeraldCore.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val endpointText = if (profile.protocol == ProxyProtocol.AMIR) {
                        val workerHost = Cl2Config.parseWorkerUrl(profile.host).host.ifBlank { profile.host }
                        val cleanLabel = profile.address.ifBlank { "Auto" }
                        "$cleanLabel → $workerHost"
                    } else {
                        "${profile.address}:${profile.port}"
                    }
                    Text(
                        text = endpointText,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LatencyPill(latencyMs = profile.latencyMs, isTesting = isTesting)
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (profile.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "علاقه‌مندی",
                            tint = if (profile.isFavorite) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProtocolBadge(protocol = profile.protocol)
                    Text(
                        text = if (profile.protocol == ProxyProtocol.AMIR) {
                            "WS → SOCKS5 (${profile.port})"
                        } else {
                            buildString {
                                append(profile.transport.displayName)
                                if (profile.security != SecurityType.NONE) {
                                    append(" • ${profile.security.xrayValue.uppercase()}")
                                }
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onTestLatency) {
                    Icon(Icons.Rounded.NetworkCheck, contentDescription = "تست پینگ", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "ویرایش", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "کپی پیکربندی", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Rounded.Share, contentDescription = "اشتراک‌گذاری", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "حذف",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
