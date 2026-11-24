package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.remote.AutoProxyDiscoveryReport
import com.example.data.remote.ProxyBenchmarkCandidate
import com.example.data.remote.ProxyProtocol
import com.example.data.remote.ProxySettings

@Composable
fun ProxySettingsScreen(
    currentSettings: ProxySettings,
    isTestingConnection: Boolean,
    isAutoDiscovering: Boolean,
    testResult: Pair<Boolean, String>?,
    autoDiscoveryReport: AutoProxyDiscoveryReport?,
    onSaveSettings: (ProxySettings) -> Unit,
    onTestConnection: (ProxySettings) -> Unit,
    onAutoDiscoverBestProxy: (customListText: String, includePublicPool: Boolean) -> Unit,
    onApplyDiscoveredCandidate: (ProxyBenchmarkCandidate, customListText: String) -> Unit,
    onBackToStudio: () -> Unit
) {
    BackHandler { onBackToStudio() }

    var enabled by remember(currentSettings) { mutableStateOf(currentSettings.enabled) }
    var protocol by remember(currentSettings) { mutableStateOf(currentSettings.protocol) }
    var host by remember(currentSettings) { mutableStateOf(currentSettings.host) }
    var portText by remember(currentSettings) { mutableStateOf(currentSettings.port.toString()) }
    var username by remember(currentSettings) { mutableStateOf(currentSettings.username) }
    var password by remember(currentSettings) { mutableStateOf(currentSettings.password) }
    var customListText by remember(currentSettings) { mutableStateOf(currentSettings.customProxyListText) }
    var includePublicPool by remember { mutableStateOf(false) }

    fun buildDraft(): ProxySettings {
        val parsedPort = portText.toIntOrNull()?.coerceIn(1, 65535) ?: 10808
        return ProxySettings(
            enabled = enabled,
            protocol = protocol,
            host = host.trim(),
            port = parsedPort,
            username = username.trim(),
            password = password,
            customProxyListText = customListText
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnLock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "تونل پروکسی اختصاصی (فقط برای ParHam VoiCe)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "این بخش فقط ترافیک همین برنامه را از پروکسی (HTTP / SOCKS5 یا پورت محلی v2rayNG / Nekobox / Hiddify در حالت Proxy-Only) عبور می‌دهد و VPN کل گوشی را تغییر نمی‌دهد.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Auto-Find & Benchmark App-Only Split-Tunnel Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "پیداکردن خودکار سریع‌ترین پروکسی",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "پورت‌های محلی فعال روی گوشی (10808, 10809, 1080, 2080, 7890) و لیست پروکسی شخصی خودتان را به‌صورت موازی تست سرعت می‌گیرد و بهترین پروکسی متصل را خودکار روی برنامه ست می‌کند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = customListText,
                            onValueChange = { customListText = it },
                            label = { Text("لیست پروکسی شخصی شما (هر خط یکی)") },
                            placeholder = { Text("مثال:\nsocks5://127.0.0.1:10808\nhttp://127.0.0.1:10809\n192.168.1.5:8080:user:pass") },
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("proxy_custom_list_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("اسکن پروکسی‌های عمومی آنلاین (ProxyScrape)", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "اختیاری؛ دریافت فهرست عمومی برای تست سرعت همزمان",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = includePublicPool,
                                onCheckedChange = { includePublicPool = it },
                                modifier = Modifier.testTag("proxy_public_pool_switch")
                            )
                        }

                        Button(
                            onClick = { onAutoDiscoverBestProxy(customListText, includePublicPool) },
                            enabled = !isAutoDiscovering && !isTestingConnection,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("proxy_auto_find_button")
                        ) {
                            if (isAutoDiscovering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("در حال تست سرعت و انتخاب بهترین پروکسی...")
                            } else {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("اسکن خودکار و اتصال به بهترین پروکسی", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (autoDiscoveryReport != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (autoDiscoveryReport.bestCandidate != null) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = autoDiscoveryReport.summaryMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Discovered / Benchmarked Candidates List
            if (autoDiscoveryReport != null && autoDiscoveryReport.testedCandidates.isNotEmpty()) {
                item {
                    Text(
                        text = "نتیجه تست پروکسی‌ها (${autoDiscoveryReport.testedCandidates.size} مورد):",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(autoDiscoveryReport.testedCandidates.take(8)) { cand ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (cand.reachable) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (cand.reachable) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (cand.reachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = cand.displayEndpoint(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (cand.reachable) "${cand.sourceLabel} • پینگ ${cand.latencyMs}ms" else "${cand.sourceLabel} • بدون پاسخ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (cand.reachable) {
                                OutlinedButton(
                                    onClick = { onApplyDiscoveredCandidate(cand, customListText) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("اعمال")
                                }
                            }
                        }
                    }
                }
            }

            // Manual Proxy Settings Card (Preserved completely)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "فعال‌سازی دستی پروکسی",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (enabled) "ترافیک API فقط از این پروکسی عبور می‌کند" else "اتصال مستقیم بدون پروکسی",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = { enabled = it },
                                modifier = Modifier.testTag("proxy_enable_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("نوع پروتکل پروکسی:", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ProxyProtocol.entries.forEachIndexed { index, item ->
                                SegmentedButton(
                                    selected = protocol == item,
                                    onClick = { protocol = item },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ProxyProtocol.entries.size
                                    ),
                                    modifier = Modifier.testTag("proxy_protocol_${item.id}")
                                ) {
                                    Text(item.titleFa)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = { Text("آدرس سرور / IP (مثال: 127.0.0.1)") },
                            leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("proxy_host_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { input ->
                                portText = input.filter { it.isDigit() }.take(5)
                            },
                            label = { Text("پورت (Port - مثال: 10808 یا 10809)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("proxy_port_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("نام کاربری (اختیاری)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("proxy_username_input")
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("رمز عبور (اختیاری)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("proxy_password_input")
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { onSaveSettings(buildDraft()) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("proxy_save_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ذخیره تنظیمات", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { onTestConnection(buildDraft()) },
                                enabled = !isTestingConnection && !isAutoDiscovering,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("proxy_test_button")
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تست اتصال")
                            }
                        }
                    }
                }
            }

            if (testResult != null) {
                item {
                    val (success, msg) = testResult
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (success) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
