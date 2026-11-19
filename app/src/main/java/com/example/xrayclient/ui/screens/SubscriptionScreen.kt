package com.example.xrayclient.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xrayclient.data.models.Subscription
import com.example.xrayclient.data.models.SubscriptionStatus
import com.example.xrayclient.ui.theme.CrimsonError
import com.example.xrayclient.ui.theme.CyanAccent
import com.example.xrayclient.ui.theme.EmeraldCore
import com.example.xrayclient.ui.theme.JetBrainsMonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SubscriptionScreen(
    subscriptions: List<Subscription>,
    onAddSubscription: (String, String, (Boolean) -> Unit) -> Unit,
    onRefreshSubscription: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onRenameSubscription: (String, String) -> Unit,
    onToggleAutoUpdate: (String, Boolean) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var renamingSub by remember { mutableStateOf<Subscription?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = EmeraldCore
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "افزودن اشتراک")
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
                        text = "اشتراک‌ها (${subscriptions.size})",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    TextButton(onClick = onRefreshAll) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" بروزرسانی همه", modifier = Modifier.padding(start = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (subscriptions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Subscriptions,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text("هنوز اشتراکی ثبت نشده است", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "برای دریافت خودکار سرورها یک لینک اشتراک اضافه کنید.",
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
                        items(subscriptions, key = { it.id }) { sub ->
                            SubscriptionCard(
                                subscription = sub,
                                onRefresh = { onRefreshSubscription(sub.id) },
                                onRename = { renamingSub = sub },
                                onToggleAutoUpdate = { enabled -> onToggleAutoUpdate(sub.id, enabled) },
                                onDelete = { onDeleteSubscription(sub.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("افزودن اشتراک جدید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام دلخواه") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("نشانی URL اشتراک") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddSubscription(name, url) { ok ->
                            if (ok) showAddDialog = false
                        }
                    }
                ) {
                    Text("افزودن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("انصراف") }
            }
        )
    }

    renamingSub?.let { target ->
        var newName by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renamingSub = null },
            title = { Text("تغییر نام اشتراک") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("نام جدید") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameSubscription(target.id, newName)
                        renamingSub = null
                    }
                ) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { renamingSub = null }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onToggleAutoUpdate: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val hostOnly = subscription.url.substringAfter("://").substringBefore('/').substringBefore('?')
    val lastUpdatedStr = subscription.lastUpdatedAt?.let { ts ->
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(ts))
    } ?: "هرگز"

    val statusText = when (subscription.status) {
        SubscriptionStatus.IDLE -> "آماده"
        SubscriptionStatus.UPDATING -> "در حال بروزرسانی…"
        SubscriptionStatus.SUCCESS -> "بروزرسانی موفق"
        SubscriptionStatus.ERROR -> "خطا"
    }
    val statusColor = when (subscription.status) {
        SubscriptionStatus.SUCCESS -> EmeraldCore
        SubscriptionStatus.UPDATING -> CyanAccent
        SubscriptionStatus.ERROR -> CrimsonError
        SubscriptionStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        text = subscription.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = hostOnly,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (subscription.status == SubscriptionStatus.UPDATING) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "تعداد سرورها: ${subscription.serverCount}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "آخرین بروزرسانی: $lastUpdatedStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!subscription.errorMessage.isNullOrBlank()) {
                Text(
                    text = subscription.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = CrimsonError
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("بروزرسانی خودکار", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = subscription.autoUpdateEnabled,
                        onCheckedChange = onToggleAutoUpdate
                    )
                }
                Row {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "بروزرسانی")
                    }
                    IconButton(onClick = onRename) {
                        Icon(Icons.Rounded.Edit, contentDescription = "تغییر نام")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "حذف اشتراک",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
