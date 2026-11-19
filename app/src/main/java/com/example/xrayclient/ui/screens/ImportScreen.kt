package com.example.xrayclient.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.xrayclient.data.models.ServerProfile
import com.example.xrayclient.domain.parsers.BatchImportResult
import com.example.xrayclient.ui.components.QrScannerDialog
import com.example.xrayclient.ui.components.ServerEditorDialog
import com.example.xrayclient.ui.theme.JetBrainsMonoFamily

@Composable
fun ImportScreen(
    onImportText: (String, (BatchImportResult) -> Unit) -> Unit,
    onAddSubscription: (String, String, (Boolean) -> Unit) -> Unit,
    onSaveManualProfile: (ServerProfile, (Result<Unit>) -> Unit) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var rawConfigInput by remember { mutableStateOf("") }
    var subName by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var lastErrors by remember { mutableStateOf<List<String>>(emptyList()) }

    // Storage Access Framework (SAF) JSON file importer
    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("فایل قابل خواندن نیست.")
            }.onSuccess { content ->
                onImportText(content) { res ->
                    lastErrors = res.errors
                }
            }.onFailure { err ->
                onMessage("خطا در خواندن فایل JSON: ${err.message}")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "افزودن پیکربندی",
                style = MaterialTheme.typography.headlineSmall
            )

            // Action 1: Direct Link / Clipboard / Base64 / Multi-line Import
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "۱. وارد کردن لینک مستقیم یا از کلیپ‌بورد",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "پشتیبانی از amir://، vless://، vmess://، trojan://، ss://، socks5://، http(s)://، لیست متنی، Base64 و Xray JSON",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = rawConfigInput,
                        onValueChange = { rawConfigInput = it },
                        placeholder = { Text("لینک‌ها یا JSON را اینجا جای‌گذاری کنید…") },
                        minLines = 3,
                        maxLines = 6,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_text_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clip = clipboard.getText()?.text.orEmpty()
                                if (clip.isBlank()) {
                                    onMessage("کلیپ‌بورد خالی است.")
                                } else {
                                    rawConfigInput = clip
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(" جای‌گذاری", modifier = Modifier.padding(start = 4.dp))
                        }
                        Button(
                            onClick = {
                                onImportText(rawConfigInput) { res ->
                                    lastErrors = res.errors
                                    if (res.profiles.isNotEmpty() || res.subscriptionUrls.isNotEmpty()) {
                                        rawConfigInput = ""
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_submit_button")
                        ) {
                            Text("افزودن پیکربندی")
                        }
                    }
                    if (lastErrors.isNotEmpty()) {
                        Text(
                            text = lastErrors.take(3).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Action 2: Scan QR Code & JSON File / Manual Config
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text("۲. اسکن کد QR", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = "دوربین زنده یا تصویر گالری",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { showQrScanner = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_qr_scanner_button")
                        ) {
                            Text("شروع اسکن")
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text("فایل Xray JSON", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = "ورود از حافظه دستگاه",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                jsonPickerLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("انتخاب فایل")
                        }
                    }
                }
            }

            // Action 3: Add Subscription URL
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "۳. افزودن لینک اشتراک (Subscription)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    OutlinedTextField(
                        value = subName,
                        onValueChange = { subName = it },
                        label = { Text("نام اشتراک (اختیاری)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subUrl,
                        onValueChange = { subUrl = it },
                        label = { Text("نشانی اشتراک (https://...)") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subscription_url_input")
                    )
                    Button(
                        onClick = {
                            onAddSubscription(subName, subUrl) { ok ->
                                if (ok) {
                                    subName = ""
                                    subUrl = ""
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_subscription_button")
                    ) {
                        Text("ثبت و دریافت اشتراک")
                    }
                }
            }

            // Manual Configuration Fallback (for HTTP, SOCKS5, VLESS, VMess, Trojan, SS)
            OutlinedButton(
                onClick = { showManualDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(" ساخت دستی پیکربندی (HTTP / SOCKS5 / Xray)", modifier = Modifier.padding(start = 6.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showQrScanner) {
        QrScannerDialog(
            onQrDecoded = { decoded ->
                showQrScanner = false
                onImportText(decoded) { res ->
                    lastErrors = res.errors
                }
            },
            onError = { msg -> onMessage(msg) },
            onDismiss = { showQrScanner = false }
        )
    }

    if (showManualDialog) {
        ServerEditorDialog(
            initialProfile = null,
            onSave = { profile ->
                onSaveManualProfile(profile) { res ->
                    if (res.isSuccess) showManualDialog = false
                }
            },
            onDismiss = { showManualDialog = false }
        )
    }
}
