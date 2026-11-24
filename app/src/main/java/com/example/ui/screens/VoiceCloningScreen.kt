package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.AudioSampleEntity
import com.example.data.local.VoiceProfileEntity
import com.example.domain.PersianAccent
import com.example.domain.PersianCalibrationCorpus
import com.example.ui.components.ApiKeyNoticeBanner
import com.example.ui.components.OperationBanner
import com.example.ui.components.PlaybackWaveformVisualizer
import com.example.ui.components.RecordingWaveformVisualizer
import com.example.ui.components.formatMs
import com.example.viewmodel.AsyncOperationState
import com.example.viewmodel.CloningWorkspaceState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceCloningScreen(
    cloningState: CloningWorkspaceState,
    voiceProfiles: List<VoiceProfileEntity>,
    audioSamples: List<AudioSampleEntity>,
    isRecording: Boolean,
    elapsedSeconds: Int,
    waveBars: List<Float>,
    recordingLevel: Float,
    recordingPeakDb: Float,
    playingItemId: String?,
    isPlaying: Boolean,
    playbackProgress: Float,
    currentPosMs: Int,
    durationMs: Int,
    waveformEnvelope: List<Float>,
    liveBars: List<Float>,
    playbackLevel: Float,
    playbackDb: Float,
    operationState: AsyncOperationState,
    isElevenLabsConfigured: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onImportAudioFiles: (List<android.net.Uri>) -> Unit,
    onNextCalibrationPrompt: () -> Unit,
    onToggleSampleSelection: (Long) -> Unit,
    onPlaySample: (AudioSampleEntity) -> Unit,
    onSeekPlayback: (Float) -> Unit,
    onAnalyzeSample: (AudioSampleEntity) -> Unit,
    onDeleteSample: (AudioSampleEntity) -> Unit,
    onUpdateVoiceName: (String) -> Unit,
    onSelectAccent: (PersianAccent) -> Unit,
    onToggleNoiseRemoval: (Boolean) -> Unit,
    onSelectRetrainTarget: (Long?) -> Unit,
    onSubmitCloneOrRetrain: () -> Unit,
    onGoToSupport: () -> Unit,
    onDismissBanner: () -> Unit,
    onBackToStudio: () -> Unit
) {
    BackHandler { onBackToStudio() }

    val context = LocalContext.current
    var showPermissionNotice by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showPermissionNotice = false
            onStartRecording()
        } else {
            showPermissionNotice = true
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportAudioFiles(uris)
        }
    }

    val currentPrompt = PersianCalibrationCorpus.prompts[
        cloningState.calibrationPromptIndex.coerceIn(0, PersianCalibrationCorpus.prompts.lastIndex)
    ]

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isElevenLabsConfigured) {
                item {
                    ApiKeyNoticeBanner(onAskSupportClick = onGoToSupport)
                }
            }

            item {
                OperationBanner(state = operationState, onDismiss = onDismissBanner)
            }

            // Mode Switcher: New Clone vs Retrain Existing with Extra Persian Dataset
            item {
                val isRetrainMode = cloningState.retrainTargetVoiceId != null
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isRetrainMode,
                        onClick = { onSelectRetrainTarget(null) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("mode_new_clone")
                    ) {
                        Text("کلون صدای جدید")
                    }
                    SegmentedButton(
                        selected = isRetrainMode,
                        onClick = {
                            val firstId = voiceProfiles.firstOrNull()?.id
                            onSelectRetrainTarget(firstId)
                        },
                        enabled = voiceProfiles.isNotEmpty(),
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("mode_retrain_voice")
                    ) {
                        Text("بازآموزی با دیتاست جدید")
                    }
                }
            }

            // Live Persian Voice Recorder Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ضبط مستقیم نمونه صدای فارسی",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = onNextCalibrationPrompt,
                                modifier = Modifier.testTag("next_prompt_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("متن تمرینی بعدی")
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${currentPrompt.title} • ${currentPrompt.focusArea}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentPrompt.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        RecordingWaveformVisualizer(
                            bars = waveBars,
                            currentLevel = recordingLevel,
                            peakDb = recordingPeakDb,
                            isRecording = isRecording,
                            title = "موج زنده صدای کلونینگ",
                            primaryColor = if (isRecording) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val min = elapsedSeconds / 60
                            val sec = elapsedSeconds % 60
                            Text(
                                text = String.format(Locale.US, "%02d:%02d", min, sec),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isRecording) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = {
                                    if (isRecording) {
                                        onStopRecording()
                                    } else {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            onStartRecording()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .size(72.dp)
                                    .testTag("record_voice_button")
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = stringResource(R.string.cd_record_voice),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        if (showPermissionNotice) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "برای ضبط مستقیم صدای خودتان، دسترسی میکروفون لازم است. همچنین می‌توانید فایل‌های صوتی فارسی آماده را از دکمه زیر وارد کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("import_dataset_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("افزودن مجموعه داده صوتی فارسی (MP3 / WAV / M4A)")
                        }
                    }
                }
            }

            // Dataset Samples Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مجموعه داده‌های صوتی فارسی (${audioSamples.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${cloningState.selectedSampleIds.size} انتخاب‌شده",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (audioSamples.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "هنوز نمونه صدایی ثبت نشده است",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "حداقل ۳۰ تا ۶۰ ثانیه صدای شفاف فارسی ضبط یا وارد کنید تا کلونینگ با طبیعی‌ترین کیفیت انجام شود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(audioSamples, key = { it.id }) { sample ->
                    val isSelected = sample.id in cloningState.selectedSampleIds
                    val isSamplePlaying = playingItemId == "sample_${sample.id}" && isPlaying
                    val isAnalyzing = cloningState.analyzingSampleId == sample.id

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleSampleSelection(sample.id) },
                                    modifier = Modifier.testTag("sample_checkbox_${sample.id}")
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sample.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${sample.durationSeconds} ثانیه • ${if (sample.sourceType == "RECORDED") "ضبط میکروفون" else "دیتاست واردشده"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onPlaySample(sample) },
                                    modifier = Modifier.testTag("play_sample_${sample.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isSamplePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.cd_play_audio)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteSample(sample) },
                                    modifier = Modifier.testTag("delete_sample_${sample.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.cd_delete_item),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }

                            val isActiveSample = playingItemId == "sample_${sample.id}"
                            if (isActiveSample) {
                                Spacer(modifier = Modifier.height(6.dp))
                                PlaybackWaveformVisualizer(
                                    envelopeBars = waveformEnvelope,
                                    liveBars = liveBars,
                                    playbackLevel = playbackLevel,
                                    playbackDb = playbackDb,
                                    progress = playbackProgress,
                                    isActiveItem = true,
                                    isPlaying = isPlaying,
                                    onSeek = onSeekPlayback
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatMs(currentPosMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatMs(durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isAnalyzing) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "در حال تحلیل هوشمند کیفیت و لهجه با جمینای...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else if (sample.qualityScore != null) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AssistChip(
                                                onClick = {},
                                                label = { Text("کیفیت: ${sample.qualityScore}/100") }
                                            )
                                            if (!sample.detectedAccent.isNullOrBlank()) {
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text("لهجه: ${sample.detectedAccent}") }
                                                )
                                            }
                                            if (!sample.naturalPitchLabel.isNullOrBlank()) {
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text("لحن: ${sample.naturalPitchLabel}") }
                                                )
                                            }
                                        }
                                        if (!sample.clarityNote.isNullOrBlank()) {
                                            Text(
                                                text = sample.clarityNote,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (!sample.improvementTip.isNullOrBlank()) {
                                            Text(
                                                text = "پیشنهاد: ${sample.improvementTip}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            } else {
                                TextButton(
                                    onClick = { onAnalyzeSample(sample) },
                                    modifier = Modifier.testTag("analyze_sample_${sample.id}")
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تحلیل کیفیت و لهجه با جمینای")
                                }
                            }
                        }
                    }
                }
            }

            // Training & Cloning Configuration Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val targetId = cloningState.retrainTargetVoiceId
                        Text(
                            text = if (targetId == null) "تنظیمات ساخت کلون صدای جدید" else "انتخاب صدای کلون‌شده برای بازآموزی",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (targetId == null) {
                            OutlinedTextField(
                                value = cloningState.newVoiceName,
                                onValueChange = onUpdateVoiceName,
                                label = { Text("نام پروفایل صدا (مثلاً: صدای اصلی من)") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("new_voice_name_input")
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                voiceProfiles.forEach { profile ->
                                    val selected = profile.id == targetId
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectRetrainTarget(profile.id) },
                                        label = { Text("${profile.name} (${profile.sampleCount} نمونه)") }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لهجه پایه مدل کلون‌شده",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PersianAccent.entries.forEach { accent ->
                                FilterChip(
                                    selected = accent == cloningState.cloningAccent,
                                    onClick = { onSelectAccent(accent) },
                                    label = { Text(accent.titleFa) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "حذف هوشمند نویز پس‌زمینه",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "پالایش صدای محیط پیش از آموزش مدل",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = cloningState.removeBackgroundNoise,
                                onCheckedChange = onToggleNoiseRemoval
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        val isBusy = operationState is AsyncOperationState.Loading
                        Button(
                            onClick = onSubmitCloneOrRetrain,
                            enabled = !isBusy,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_cloning_button")
                        ) {
                            Icon(Icons.Default.ModelTraining, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (targetId == null) {
                                    "ساخت کلون صدای فارسی من"
                                } else {
                                    "آموزش مجدد با دیتاست‌های انتخابی"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
