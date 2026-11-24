package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.GeneratedSpeechEntity
import com.example.data.local.VoiceProfileEntity
import com.example.domain.PersianAccent
import com.example.domain.SpeechLanguage
import com.example.domain.VoiceEmotion
import com.example.ui.components.ApiKeyNoticeBanner
import com.example.ui.components.AudioPlaybackCard
import com.example.ui.components.OperationBanner
import com.example.viewmodel.AsyncOperationState
import com.example.viewmodel.StudioControlsState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioSpeakScreen(
    controls: StudioControlsState,
    voiceProfiles: List<VoiceProfileEntity>,
    latestSpeech: GeneratedSpeechEntity?,
    operationState: AsyncOperationState,
    isElevenLabsConfigured: Boolean,
    playingItemId: String?,
    isPlaying: Boolean,
    playbackProgress: Float,
    currentPosMs: Int,
    durationMs: Int,
    waveformEnvelope: List<Float>,
    liveBars: List<Float>,
    playbackLevel: Float,
    playbackDb: Float,
    onSelectVoice: (VoiceProfileEntity) -> Unit,
    onUpdateInputText: (String) -> Unit,
    onUpdateAdaptedText: (String) -> Unit,
    onToggleAutoAdapt: (Boolean) -> Unit,
    onSelectLanguage: (SpeechLanguage) -> Unit,
    onSelectAccent: (PersianAccent) -> Unit,
    onSelectEmotion: (VoiceEmotion) -> Unit,
    onPitchChange: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onStabilityChange: (Float) -> Unit,
    onSimilarityChange: (Float) -> Unit,
    onPreviewAdaptation: () -> Unit,
    onSynthesizeSpeech: () -> Unit,
    onTogglePlayLatest: (GeneratedSpeechEntity) -> Unit,
    onSeekPlayback: (Float) -> Unit,
    onShareSpeech: (GeneratedSpeechEntity) -> Unit,
    onSaveSpeech: (GeneratedSpeechEntity) -> Unit,
    onSyncVoices: () -> Unit,
    onGoToCloning: () -> Unit,
    onGoToSupport: () -> Unit,
    onDismissBanner: () -> Unit
) {
    var showAdvancedSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Acoustic Studio Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_studio_hero_1795502505590),
                        contentDescription = "استودیو هوشمند کلون صدای فارسی",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.25f),
                                        Color(0xFF0A111D).copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "استودیو گویندگی فارسی • کلونینگ طبیعی",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "ParHam VoiCe: صدای خودت، هر زبان و لهجه",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!isElevenLabsConfigured) {
                item {
                    ApiKeyNoticeBanner(onAskSupportClick = onGoToSupport)
                }
            }

            item {
                OperationBanner(
                    state = operationState,
                    onDismiss = onDismissBanner
                )
            }

            // Cloned Voice Selection
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "انتخاب صدای کلون‌شده",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = onSyncVoices,
                                modifier = Modifier.testTag("sync_voices_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("همگام‌سازی")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (voiceProfiles.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "هنوز صدای کلون‌شده‌ای ندارید",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "صدای خود را ضبط کنید یا فایل صوتی فارسی وارد کنید تا مدل اختصاصی شما ساخته شود.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = onGoToCloning,
                                        modifier = Modifier.testTag("empty_go_to_cloning_button")
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("شروع کلون صدای من")
                                    }
                                }
                            }
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(voiceProfiles, key = { it.id }) { profile ->
                                    val isSelected = profile.id == controls.selectedVoiceProfileId
                                    val profileAccent = PersianAccent.fromId(profile.accentId)
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                        },
                                        border = if (isSelected) {
                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        } else null,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onSelectVoice(profile) }
                                            .testTag("voice_profile_chip_${profile.id}")
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                            Text(
                                                text = "${profileAccent.titleFa} • ${profile.sampleCount} نمونه",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Text Input & AI Accent Script Adaptation
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "متن فارسی برای خواندن",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = controls.inputText,
                            onValueChange = onUpdateInputText,
                            placeholder = { Text("متن فارسی خود را اینجا بنویسید...") },
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tts_input_text")
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تطبیق هوشمند لهجه و لحن با جمینای",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "تبدیل خودکار متن به واژه‌گزینی لهجه و نشانه‌گذاری احساسی",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = controls.autoAdaptAccentWithAi,
                                onCheckedChange = onToggleAutoAdapt,
                                modifier = Modifier.testTag("auto_adapt_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onPreviewAdaptation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("preview_adaptation_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("پیش‌نمایش متن تطبیق‌یافته با لهجه ${controls.selectedAccent.titleFa}")
                        }

                        AnimatedVisibility(visible = controls.adaptedTextPreview.isNotBlank()) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "متن آماده گویندگی (قابل ویرایش):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = controls.adaptedTextPreview,
                                    onValueChange = onUpdateAdaptedText,
                                    minLines = 2,
                                    maxLines = 5,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("adapted_script_preview")
                                )
                            }
                        }
                    }
                }
            }

            // Output Language & Persian Accent Picker
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "زبان خروجی (چندزبانه با صدای خودت)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SpeechLanguage.entries.forEach { lang ->
                                val selected = lang == controls.selectedLanguage
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectLanguage(lang) },
                                    label = {
                                        Text(
                                            if (lang == SpeechLanguage.PERSIAN) lang.titleFa
                                            else "${lang.titleFa} (${lang.nativeName})"
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("language_chip_${lang.code}")
                                )
                            }
                        }

                        if (controls.selectedLanguage == SpeechLanguage.PERSIAN) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "لهجه زبان فارسی",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = controls.selectedAccent.subtitleFa,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PersianAccent.entries.forEach { accent ->
                                    val selected = accent == controls.selectedAccent
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectAccent(accent) },
                                        label = { Text(accent.titleFa) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.testTag("accent_chip_${accent.id}")
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "متن شما با همان تن صدای کلون‌شده خودتان به زبان ${controls.selectedLanguage.titleFa} (${controls.selectedLanguage.nativeName}) صحبت می‌شود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Emotion, Pitch & Speed Controls
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "احساسات، لحن (Pitch) و سرعت (Speed)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "احساس و حس‌وحال بیان",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VoiceEmotion.entries.forEach { emotion ->
                                val selected = emotion == controls.selectedEmotion
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectEmotion(emotion) },
                                    label = { Text(emotion.titleFa) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier.testTag("emotion_chip_${emotion.id}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Pitch slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("لحن و زیر و بمی (Pitch)", style = MaterialTheme.typography.labelLarge)
                            }
                            Text(
                                text = "${String.format(Locale.US, "%.2fx", controls.pitch)} (${
                                    when {
                                        controls.pitch < 0.92f -> "بم"
                                        controls.pitch > 1.08f -> "زیر"
                                        else -> "طبیعی"
                                    }
                                })",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = controls.pitch,
                            onValueChange = onPitchChange,
                            valueRange = 0.70f..1.35f,
                            modifier = Modifier.testTag("pitch_slider")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Speed slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("سرعت گفتار (Speed)", style = MaterialTheme.typography.labelLarge)
                            }
                            Text(
                                text = String.format(Locale.US, "%.2fx", controls.speed),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = controls.speed,
                            onValueChange = onSpeedChange,
                            valueRange = 0.65f..1.50f,
                            modifier = Modifier.testTag("speed_slider")
                        )

                        // Expandable advanced voice cloning parameters
                        TextButton(
                            onClick = { showAdvancedSettings = !showAdvancedSettings },
                            modifier = Modifier.testTag("advanced_params_toggle")
                        ) {
                            Text(if (showAdvancedSettings) "بستن تنظیمات پیشرفته مدل" else "تنظیمات پیشرفته شباهت و ثبات صدا")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (showAdvancedSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = showAdvancedSettings) {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("ثبات لحن (Stability)", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${(controls.stability * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Slider(
                                    value = controls.stability,
                                    onValueChange = onStabilityChange,
                                    valueRange = 0.1f..0.95f
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("شباهت به صدای اصلی (Similarity)", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${(controls.similarityBoost * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Slider(
                                    value = controls.similarityBoost,
                                    onValueChange = onSimilarityChange,
                                    valueRange = 0.2f..1.0f
                                )
                            }
                        }
                    }
                }
            }

            // Primary Synthesis CTA
            item {
                val isLoading = operationState is AsyncOperationState.Loading
                Button(
                    onClick = onSynthesizeSpeech,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("synthesize_speech_button")
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "تولید گفتار فارسی با صدای کلون‌شده",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Latest Generated Speech Card
            if (latestSpeech != null) {
                item {
                    Column {
                        Text(
                            text = "آخرین خروجی صوتی ساخته‌شده",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        AudioPlaybackCard(
                            speech = latestSpeech,
                            isActiveItem = playingItemId == "speech_${latestSpeech.id}",
                            isPlaying = isPlaying,
                            progress = playbackProgress,
                            currentPosMs = currentPosMs,
                            durationMs = durationMs,
                            waveformEnvelope = waveformEnvelope,
                            liveBars = liveBars,
                            playbackLevel = playbackLevel,
                            playbackDb = playbackDb,
                            onTogglePlay = { onTogglePlayLatest(latestSpeech) },
                            onSeek = onSeekPlayback,
                            onShare = { onShareSpeech(latestSpeech) },
                            onSaveFile = { onSaveSpeech(latestSpeech) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
