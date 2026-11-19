package com.example.xrayclient.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xrayclient.data.models.AppLanguage
import com.example.xrayclient.data.models.AppSettings
import com.example.xrayclient.data.models.DnsStrategy
import com.example.xrayclient.data.models.PerAppMode
import com.example.xrayclient.data.models.ThemeMode
import com.example.xrayclient.data.models.XrayLogLevel
import com.example.xrayclient.engine.EngineAvailability
import com.example.xrayclient.ui.InstalledAppInfo
import com.example.xrayclient.ui.theme.JetBrainsMonoFamily

@Composable
fun SettingsScreen(
    settings: AppSettings,
    engineAvailability: EngineAvailability,
    cl2IsRunning: Boolean = false,
    installedApps: List<InstalledAppInfo>,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onLoadInstalledApps: () -> Unit,
    onOpenEngineDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPerAppDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("تنظیمات", style = MaterialTheme.typography.headlineSmall)

            // 1. Appearance & Language
            SettingsSectionCard(title = "ظاهر و زبان", icon = Icons.Rounded.Palette) {
                Text("تم برنامه", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onUpdateSettings { it.copy(themeMode = mode) } },
                            label = { Text(mode.persianLabel) }
                        )
                    }
                }
                Text("زبان و جهت چیدمان", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = settings.language == lang,
                            onClick = { onUpdateSettings { it.copy(language = lang) } },
                            label = { Text(lang.label) }
                        )
                    }
                }
            }

            // 2. Network, DNS & IPv4/IPv6
            SettingsSectionCard(title = "شبکه، DNS و مسیریابی IP", icon = Icons.Rounded.Dns) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settings.primaryDns,
                        onValueChange = { v -> onUpdateSettings { it.copy(primaryDns = v) } },
                        label = { Text("DNS اصلی") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = settings.secondaryDns,
                        onValueChange = { v -> onUpdateSettings { it.copy(secondaryDns = v) } },
                        label = { Text("DNS دوم") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("استراتژی DNS هسته Xray", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DnsStrategy.entries.forEach { strategy ->
                        FilterChip(
                            selected = settings.dnsStrategy == strategy,
                            onClick = { onUpdateSettings { it.copy(dnsStrategy = strategy) } },
                            label = { Text(strategy.persianLabel) }
                        )
                    }
                }

                SettingsSwitchRow(
                    title = "فعال‌سازی IPv4",
                    checked = settings.ipv4Enabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(ipv4Enabled = enabled || !it.ipv6Enabled) }
                    }
                )
                SettingsSwitchRow(
                    title = "فعال‌سازی IPv6 در تونل",
                    checked = settings.ipv6Enabled,
                    onCheckedChange = { enabled -> onUpdateSettings { it.copy(ipv6Enabled = enabled) } }
                )
                SettingsSwitchRow(
                    title = "دور زدن آدرس‌های شبکه محلی (LAN)",
                    checked = settings.bypassLan,
                    onCheckedChange = { enabled -> onUpdateSettings { it.copy(bypassLan = enabled) } }
                )
            }

            // 3. Per-App VPN Routing
            SettingsSectionCard(title = "فیلتر برنامه‌ها (Per-App VPN)", icon = Icons.Rounded.Apps) {
                PerAppMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.perAppMode == mode,
                        onClick = { onUpdateSettings { it.copy(perAppMode = mode) } },
                        label = { Text(mode.persianLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (settings.perAppMode != PerAppMode.ALL_APPS) {
                    OutlinedButton(
                        onClick = {
                            onLoadInstalledApps()
                            showPerAppDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("انتخاب برنامه‌ها (${settings.perAppPackages.size} انتخاب‌شده)")
                    }
                }
            }

            // 4. Connection Lifecycle & Battery Optimization
            SettingsSectionCard(title = "پایداری اتصال و باتری", icon = Icons.Rounded.PowerSettingsNew) {
                SettingsSwitchRow(
                    title = "اتصال خودکار پس از راه‌اندازی دستگاه",
                    checked = settings.autoConnect,
                    onCheckedChange = { enabled -> onUpdateSettings { it.copy(autoConnect = enabled) } }
                )
                SettingsSwitchRow(
                    title = "اتصال مجدد هنگام تغییر شبکه (Wi-Fi / موبایل)",
                    checked = settings.reconnectOnNetworkChange,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(reconnectOnNetworkChange = enabled) }
                    }
                )
                Text(
                    text = "برای جلوگیری از قطع شدن تونل در پس‌زمینه توسط سیستم اندروید، برنامه را از بهینه‌سازی باتری مستثنا کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.BatterySaver, contentDescription = null)
                    Text(" باز کردن تنظیمات بهینه‌سازی باتری", modifier = Modifier.padding(start = 6.dp))
                }
            }

            // 5. Subscription Interval, Log Level & Engine Inspector
            SettingsSectionCard(title = "هسته Xray و نگهداری", icon = Icons.Rounded.BugReport) {
                Text("سطح ثبت گزارش (Log Level)", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    XrayLogLevel.entries.forEach { lvl ->
                        FilterChip(
                            selected = settings.logLevel == lvl,
                            onClick = { onUpdateSettings { it.copy(logLevel = lvl) } },
                            label = { Text(lvl.persianLabel) }
                        )
                    }
                }

                Text("بازه بروزرسانی خودکار اشتراک‌ها", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(6, 12, 24, 48).forEach { hrs ->
                        FilterChip(
                            selected = settings.subscriptionUpdateIntervalHours == hrs,
                            onClick = {
                                onUpdateSettings { it.copy(subscriptionUpdateIntervalHours = hrs) }
                            },
                            label = { Text("هر $hrs ساعت") }
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenEngineDiagnostics,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val statusLabel = when (engineAvailability) {
                        is EngineAvailability.Ready -> "وضعیت هسته: ${engineAvailability.coreVersion}"
                        is EngineAvailability.NotInstalled -> "مشاهده وضعیت و مستندات هسته Xray"
                    }
                    Text(statusLabel)
                }
            }

            // 6. AMIR WebSocket -> Local SOCKS5 Bridge (127.0.0.1:40446)
            SettingsSectionCard(title = "پروکسی AMIR (WebSocket → SOCKS5 127.0.0.1:40446)", icon = Icons.Rounded.SwapHoriz) {
                Text(
                    text = if (cl2IsRunning) {
                        "وضعیت: فعال و در حال گوش دادن روی ${settings.cl2ListenHost}:${settings.cl2ListenPort}"
                    } else {
                        "وضعیت: آماده (هنگام اتصال به کانفیگ AMIR خودکار اجرا می‌شود)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (cl2IsRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsSwitchRow(
                    title = "اجرای خودکار سرور محلی SOCKS5 برای AMIR",
                    checked = settings.cl2Enabled,
                    onCheckedChange = { enabled -> onUpdateSettings { it.copy(cl2Enabled = enabled) } }
                )
                OutlinedTextField(
                    value = settings.cl2WorkerHost,
                    onValueChange = { v -> onUpdateSettings { it.copy(cl2WorkerHost = v) } },
                    label = { Text("WORKER URL") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = settings.cl2CleanIp,
                        onValueChange = { v -> onUpdateSettings { it.copy(cl2CleanIp = v) } },
                        label = { Text("CLEAN IP") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = settings.cl2ListenPort.toString(),
                        onValueChange = { v ->
                            val parsed = v.filter { it.isDigit() }.toIntOrNull()
                            if (parsed != null && parsed in 1..65535) {
                                onUpdateSettings { it.copy(cl2ListenPort = parsed) }
                            }
                        },
                        label = { Text("پورت محلی") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.weight(0.6f)
                    )
                }
                OutlinedTextField(
                    value = settings.cl2Uuid,
                    onValueChange = { v -> onUpdateSettings { it.copy(cl2Uuid = v) } },
                    label = { Text("UUID") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = settings.cl2Subprotocol,
                    onValueChange = { v -> onUpdateSettings { it.copy(cl2Subprotocol = v) } },
                    label = { Text("Subprotocol") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        onUpdateSettings {
                            it.copy(
                                cl2Enabled = true,
                                cl2Uuid = com.example.xrayclient.tunnel.Cl2Config.DEFAULT_UUID,
                                cl2WorkerHost = com.example.xrayclient.tunnel.Cl2Config.DEFAULT_WORKER_HOST,
                                cl2CleanIp = com.example.xrayclient.tunnel.Cl2Config.DEFAULT_CLEAN_IP,
                                cl2ListenHost = com.example.xrayclient.tunnel.Cl2Config.DEFAULT_LISTEN_HOST,
                                cl2ListenPort = com.example.xrayclient.tunnel.Cl2Config.DEFAULT_LISTEN_PORT,
                                cl2Subprotocol = com.example.xrayclient.tunnel.Cl2Config.DEFAULT_SUBPROTOCOL
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("بازنشانی تنظیمات پیش‌فرض AMIR")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPerAppDialog) {
        var search by remember { mutableStateOf("") }
        val filtered = remember(installedApps, search) {
            if (search.isBlank()) installedApps else installedApps.filter {
                it.label.contains(search, true) || it.packageName.contains(search, true)
            }
        }
        AlertDialog(
            onDismissRequest = { showPerAppDialog = false },
            title = { Text("انتخاب برنامه‌ها") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("جستجوی برنامه…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            val checked = app.packageName in settings.perAppPackages
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onUpdateSettings { cur ->
                                            val next = if (checked) {
                                                cur.perAppPackages - app.packageName
                                            } else {
                                                cur.perAppPackages + app.packageName
                                            }
                                            cur.copy(perAppPackages = next)
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        app.packageName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPerAppDialog = false }) { Text("تایید") }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
