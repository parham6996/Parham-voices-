package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.remote.ApiProviderTarget
import com.example.data.remote.ApiTestResult
import com.example.data.remote.UserApiKeysConfig
import com.example.domain.DubbingEngineMethod
import com.example.domain.IranGatewayPreset
import com.example.ui.components.OperationBanner
import com.example.viewmodel.AsyncOperationState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiKeysSettingsScreen(
    currentConfig: UserApiKeysConfig,
    selectedDubbingEngine: DubbingEngineMethod,
    keystoreSecurityStatus: String,
    apiTestResults: Map<ApiProviderTarget, ApiTestResult>,
    testingApiProvider: ApiProviderTarget?,
    isTestingAllApis: Boolean,
    operationState: AsyncOperationState,
    onSelectDubbingEngine: (DubbingEngineMethod) -> Unit,
    onTestSingleApi: (ApiProviderTarget, UserApiKeysConfig) -> Unit,
    onTestAllApis: (UserApiKeysConfig) -> Unit,
    onSaveKeys: (UserApiKeysConfig) -> Unit,
    onClearKeys: () -> Unit,
    onDismissBanner: () -> Unit,
    onBackToStudio: () -> Unit
) {
    BackHandler { onBackToStudio() }

    var elevenKey by remember(currentConfig) { mutableStateOf(currentConfig.elevenLabsApiKey) }
    var iranPresetId by remember(currentConfig) { mutableStateOf(currentConfig.iranGatewayPresetId) }
    var iranName by remember(currentConfig) { mutableStateOf(currentConfig.iranGatewayName) }
    var iranUrl by remember(currentConfig) { mutableStateOf(currentConfig.iranGatewayBaseUrl) }
    var iranKey by remember(currentConfig) { mutableStateOf(currentConfig.iranGatewayApiKey) }
    var iranModel by remember(currentConfig) { mutableStateOf(currentConfig.iranGatewayModel) }

    var geminiKey by remember(currentConfig) { mutableStateOf(currentConfig.geminiDirectApiKey) }
    var openAiKey by remember(currentConfig) { mutableStateOf(currentConfig.openAiApiKey) }
    var openAiBaseUrl by remember(currentConfig) { mutableStateOf(currentConfig.openAiBaseUrl) }
    var openAiModel by remember(currentConfig) { mutableStateOf(currentConfig.openAiModel) }
    var customName by remember(currentConfig) { mutableStateOf(currentConfig.customProviderName) }
    var customUrl by remember(currentConfig) { mutableStateOf(currentConfig.customProviderBaseUrl) }
    var customKey by remember(currentConfig) { mutableStateOf(currentConfig.customProviderApiKey) }
    var customModel by remember(currentConfig) { mutableStateOf(currentConfig.customProviderModel) }

    var revealKeys by remember { mutableStateOf(false) }

    fun buildConfig(): UserApiKeysConfig {
        val preset = IranGatewayPreset.fromId(iranPresetId)
        return UserApiKeysConfig(
            elevenLabsApiKey = elevenKey.trim(),
            geminiDirectApiKey = geminiKey.trim(),
            openAiApiKey = openAiKey.trim(),
            openAiBaseUrl = openAiBaseUrl.trim().ifBlank { "https://api.openai.com/v1" },
            openAiModel = openAiModel.trim().ifBlank { "gpt-4o-mini" },
            iranGatewayPresetId = preset.id,
            iranGatewayName = iranName.trim().ifBlank { preset.titleFa },
            iranGatewayBaseUrl = iranUrl.trim().ifBlank { preset.baseUrl },
            iranGatewayApiKey = iranKey.trim(),
            iranGatewayModel = iranModel.trim().ifBlank { preset.defaultModel },
            customProviderName = customName.trim(),
            customProviderBaseUrl = customUrl.trim(),
            customProviderApiKey = customKey.trim(),
            customProviderModel = customModel.trim()
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
                OperationBanner(state = operationState, onDismiss = onDismissBanner)
            }

            // Security & Encrypted Keystore Header
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
                    ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تنظیمات امن API و Keystore رمزنگاری‌شده",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "پشتیبانی از درگاه‌های داخلی بدون فیلترشکن (AvalAI، Liara، GapGPT، Metis) در کنار ElevenLabs، Gemini، OpenAI و موتور پنجم.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { revealKeys = !revealKeys },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("apikey_toggle_reveal")
                            ) {
                                Icon(
                                    imageVector = if (revealKeys) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "نمایش یا مخفی‌سازی کلیدها"
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = keystoreSecurityStatus,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onTestAllApis(buildConfig()) },
                                    enabled = !isTestingAllApis && testingApiProvider == null,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("test_all_apis_button")
                                ) {
                                    if (isTestingAllApis) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("تست همه APIها", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // Modular AI Engine Switcher
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "سوییچ ماژولار موتور هوش مصنوعی فعال",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "موتور اصلی پردازش و دوبله را بین درگاه بدون فیلترشکن ایران، ۵ موتور جهانی یا حالت ضدقطعی خودکار جابه‌جا کنید:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DubbingEngineMethod.entries.forEach { engine ->
                                val selected = selectedDubbingEngine == engine
                                val isReadyBadge = when (engine) {
                                    DubbingEngineMethod.AUTO_FAILOVER,
                                    DubbingEngineMethod.GEMINI_PRIMARY,
                                    DubbingEngineMethod.GEMINI_BACKUP -> true
                                    DubbingEngineMethod.IRAN_NO_VPN -> iranKey.isNotBlank() && iranUrl.isNotBlank()
                                    DubbingEngineMethod.OPENAI_WHISPER -> openAiKey.isNotBlank()
                                    DubbingEngineMethod.ELEVENLABS_SCRIBE -> elevenKey.isNotBlank()
                                    DubbingEngineMethod.CUSTOM_AI -> customKey.isNotBlank() && customUrl.isNotBlank()
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectDubbingEngine(engine) },
                                    label = {
                                        Text(
                                            text = if (isReadyBadge) "${engine.titleFa} ●" else engine.titleFa,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    modifier = Modifier.testTag("modular_engine_${engine.id}")
                                )
                            }
                        }
                    }
                }
            }

            // 0. Dedicated Iran No-VPN Gateways Card (AvalAI / Liara / GapGPT / Metis)
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "سرویس‌های هوش مصنوعی داخل ایران (بدون فیلترشکن)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onTestSingleApi(ApiProviderTarget.IRAN_GATEWAY, buildConfig()) },
                                enabled = testingApiProvider == null && !isTestingAllApis,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("test_api_iran_gateway_button")
                            ) {
                                if (testingApiProvider == ApiProviderTarget.IRAN_GATEWAY) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تست API", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Text(
                            text = "این درگاه‌ها با سرورهای داخلی ایران و IP ایران مستقیماً (حتی با پروکسی خاموش) به مدل‌های Whisper، GPT، Gemini و DeepSeek وصل می‌شوند:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IranGatewayPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = iranPresetId == preset.id,
                                    onClick = {
                                        iranPresetId = preset.id
                                        iranName = preset.titleFa
                                        iranUrl = preset.baseUrl
                                        iranModel = preset.defaultModel
                                    },
                                    label = { Text(preset.titleFa) },
                                    modifier = Modifier.testTag("iran_preset_${preset.id}")
                                )
                            }
                        }

                        val activePreset = IranGatewayPreset.fromId(iranPresetId)
                        Text(
                            text = buildString {
                                append("قابلیت‌های درگاه: ")
                                append(if (activePreset.supportsWhisperStt) "تشخیص گفتار صوتی (Whisper ✓)" else "تبدیل متن + چت (برای صوت کنار Scribe)")
                                append(" • ")
                                append(if (activePreset.supportsTts) "گویندگی چندشخصیتی ابری (TTS ✓)" else "دوبله با گوینده هوشمند")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )

                        SecretKeyField(
                            value = iranKey,
                            onValueChange = { iranKey = it },
                            label = "کلید درگاه ایرانی (${activePreset.titleFa})",
                            reveal = revealKeys,
                            testTag = "apikey_iran_gateway_input"
                        )
                        OutlinedTextField(
                            value = iranUrl,
                            onValueChange = { iranUrl = it },
                            label = { Text("آدرس سرور بدون فیلترشکن (Base URL)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = iranModel,
                            onValueChange = { iranModel = it },
                            label = { Text("نام مدل (مثلاً gpt-4o-mini یا deepseek-chat یا gemini-2.5-flash)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        apiTestResults[ApiProviderTarget.IRAN_GATEWAY]?.let { res ->
                            ApiTestResultBanner(res)
                        }
                    }
                }
            }

            // 1. ElevenLabs Card
            item {
                ApiProviderCard(
                    title = "۱. ElevenLabs (کلون صدا، خوانندگی و Scribe)",
                    subtitle = "برای ساخت صدای کلون‌شده، خوانندگی و تبدیل گفتار به متن با مدل eleven_v3 و scribe_v1.",
                    target = ApiProviderTarget.ELEVENLABS,
                    isTesting = testingApiProvider == ApiProviderTarget.ELEVENLABS,
                    testDisabled = testingApiProvider != null || isTestingAllApis,
                    testResult = apiTestResults[ApiProviderTarget.ELEVENLABS],
                    onTestClick = { onTestSingleApi(ApiProviderTarget.ELEVENLABS, buildConfig()) },
                    testButtonTag = "test_api_elevenlabs_button"
                ) {
                    SecretKeyField(
                        value = elevenKey,
                        onValueChange = { elevenKey = it },
                        label = "کلید ElevenLabs API (sk_...)",
                        reveal = revealKeys,
                        testTag = "apikey_elevenlabs_input"
                    )
                }
            }

            // 2. Gemini Direct API Card
            item {
                ApiProviderCard(
                    title = "۲. Google Gemini API (مستقیم با پروکسی)",
                    subtitle = "در صورت وارد کردن کلید شخصی جمینای، علاوه‌بر اتصال داخلی App Check، به‌صورت مستقیم با پروکسی هم فراخوانی می‌شود.",
                    target = ApiProviderTarget.GEMINI,
                    isTesting = testingApiProvider == ApiProviderTarget.GEMINI,
                    testDisabled = testingApiProvider != null || isTestingAllApis,
                    testResult = apiTestResults[ApiProviderTarget.GEMINI],
                    onTestClick = { onTestSingleApi(ApiProviderTarget.GEMINI, buildConfig()) },
                    testButtonTag = "test_api_gemini_button"
                ) {
                    SecretKeyField(
                        value = geminiKey,
                        onValueChange = { geminiKey = it },
                        label = "کلید Gemini API (AIza...)",
                        reveal = revealKeys,
                        testTag = "apikey_gemini_input"
                    )
                }
            }

            // 3. OpenAI / ChatGPT Card
            item {
                ApiProviderCard(
                    title = "۳. OpenAI / ChatGPT (Whisper + GPT)",
                    subtitle = "موتور Whisper برای تشخیص دقیق گفتار و GPT برای بازنویسی محاوره‌ای با لحن فارسی.",
                    target = ApiProviderTarget.OPENAI,
                    isTesting = testingApiProvider == ApiProviderTarget.OPENAI,
                    testDisabled = testingApiProvider != null || isTestingAllApis,
                    testResult = apiTestResults[ApiProviderTarget.OPENAI],
                    onTestClick = { onTestSingleApi(ApiProviderTarget.OPENAI, buildConfig()) },
                    testButtonTag = "test_api_openai_button"
                ) {
                    SecretKeyField(
                        value = openAiKey,
                        onValueChange = { openAiKey = it },
                        label = "کلید OpenAI API (sk-...)",
                        reveal = revealKeys,
                        testTag = "apikey_openai_input"
                    )
                    OutlinedTextField(
                        value = openAiBaseUrl,
                        onValueChange = { openAiBaseUrl = it },
                        label = { Text("آدرس سرور پایه (Base URL)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = openAiModel,
                        onValueChange = { openAiModel = it },
                        label = { Text("نام مدل (مثلاً gpt-4o-mini)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 4. Custom 5th AI Provider Card
            item {
                ApiProviderCard(
                    title = "۴. موتور پنجم دوبله و چت دلخواه (OpenRouter / Groq / DeepSeek)",
                    subtitle = "هر سرویس سازگار با OpenAI API را اینجا وارد کنید تا در سوییچ ضدقطعی فعال شود.",
                    target = ApiProviderTarget.CUSTOM,
                    isTesting = testingApiProvider == ApiProviderTarget.CUSTOM,
                    testDisabled = testingApiProvider != null || isTestingAllApis,
                    testResult = apiTestResults[ApiProviderTarget.CUSTOM],
                    onTestClick = { onTestSingleApi(ApiProviderTarget.CUSTOM, buildConfig()) },
                    testButtonTag = "test_api_custom_button"
                ) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("نام سرویس (مثلاً DeepSeek یا Groq)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("Base URL (مثلاً https://api.groq.com/openai/v1)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    SecretKeyField(
                        value = customKey,
                        onValueChange = { customKey = it },
                        label = "کلید API سرویس دلخواه",
                        reveal = revealKeys,
                        testTag = "apikey_custom_input"
                    )
                    OutlinedTextField(
                        value = customModel,
                        onValueChange = { customModel = it },
                        label = { Text("شناسه مدل (مثلاً llama-3.3-70b-versatile)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Save & Clear Buttons
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onSaveKeys(buildConfig()) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("apikey_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ذخیره امن در Keystore", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onClearKeys,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("apikey_clear_button")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("پاک‌کردن")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun ApiProviderCard(
    title: String,
    subtitle: String,
    target: ApiProviderTarget,
    isTesting: Boolean,
    testDisabled: Boolean,
    testResult: ApiTestResult?,
    onTestClick: () -> Unit,
    testButtonTag: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onTestClick,
                    enabled = !testDisabled,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag(testButtonTag)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.NetworkCheck, contentDescription = "تست ${target.titleFa}", modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تست API", style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            content()

            if (testResult != null) {
                ApiTestResultBanner(testResult)
            }
        }
    }
}

@Composable
private fun ApiTestResultBanner(testResult: ApiTestResult) {
    val ok = testResult.success
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (ok) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        },
        border = BorderStroke(
            1.dp,
            if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = buildString {
                    append(testResult.message)
                    if (testResult.latencyMs > 0L) {
                        append(" • ${testResult.latencyMs}ms")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (ok) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
            )
        }
    }
}

@Composable
private fun SecretKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    reveal: Boolean,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}
