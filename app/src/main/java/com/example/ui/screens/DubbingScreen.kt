package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.VoiceProfileEntity
import com.example.domain.CinemaDubbingStyle
import com.example.domain.DubbingEngineMethod
import com.example.domain.DubbingOutputMode
import com.example.domain.DubbingToneStyle
import com.example.domain.PersianAccent
import com.example.dubbing.DubbingSegment
import com.example.ui.components.OperationBanner
import com.example.ui.components.PlaybackWaveformVisualizer
import com.example.ui.components.formatMs
import com.example.viewmodel.AsyncOperationState
import java.util.Locale

@Composable
fun DubbingScreen(
    isServiceRunning: Boolean,
    isOverlayVisible: Boolean,
    isDubbingActive: Boolean,
    isProcessingSegment: Boolean,
    originalVolume: Float,
    dubVolume: Float,
    useClonedVoice: Boolean,
    dubOutputMode: DubbingOutputMode,
    currentSubtitleText: String,
    latestSrtFilePath: String?,
    latestDubbedAudioPath: String?,
    latestDubbedVideoPath: String?,
    latestDubOnlyStemPath: String?,
    latestSfxOnlyStemPath: String?,
    longFileProgressText: String?,
    mediaWorkloadSummary: String?,
    isPlayingDubbedFile: Boolean,
    playingItemId: String?,
    isPlaying: Boolean,
    playbackProgress: Float,
    currentPosMs: Int,
    durationMs: Int,
    waveformEnvelope: List<Float>,
    liveBars: List<Float>,
    playbackLevel: Float,
    playbackDb: Float,
    selectedEngineMethod: DubbingEngineMethod,
    selectedAccent: PersianAccent,
    selectedToneStyle: DubbingToneStyle,
    selectedCinemaStyle: CinemaDubbingStyle,
    dualLanguageSubtitles: Boolean,
    exportMp4Video: Boolean,
    clipStartSecText: String,
    clipEndSecText: String,
    dubExpressiveness: Float,
    dubPitch: Float,
    dubSpeed: Float,
    activeEngineLabel: String,
    voiceProfiles: List<VoiceProfileEntity>,
    selectedClonedProfileId: Long?,
    statusText: String?,
    segments: List<DubbingSegment>,
    operationState: AsyncOperationState,
    onRequestMediaProjectionCapture: () -> Unit,
    onShowFloatingOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onToggleDubbingActive: () -> Unit,
    onStopDubbingService: () -> Unit,
    onOriginalVolumeChange: (Float) -> Unit,
    onDubVolumeChange: (Float) -> Unit,
    onToggleUseClonedVoice: (Boolean) -> Unit,
    onSelectDubOutputMode: (DubbingOutputMode) -> Unit,
    onSelectEngineMethod: (DubbingEngineMethod) -> Unit,
    onSelectAccent: (PersianAccent) -> Unit,
    onSelectToneStyle: (DubbingToneStyle) -> Unit,
    onSelectCinemaStyle: (CinemaDubbingStyle) -> Unit,
    onToggleDualLanguageSubtitles: (Boolean) -> Unit,
    onToggleExportMp4Video: (Boolean) -> Unit,
    onClipRangeChange: (String, String) -> Unit,
    onExpressivenessChange: (Float) -> Unit,
    onDubPitchChange: (Float) -> Unit,
    onDubSpeedChange: (Float) -> Unit,
    onEstimateWorkloadUri: (android.net.Uri) -> Unit,
    onDubMediaFileUri: (android.net.Uri) -> Unit,
    onSaveSrtFile: (String) -> Unit,
    onShareSrtFile: (String) -> Unit,
    onTogglePlayDubbedFile: (String) -> Unit,
    onSeekPlayback: (Float) -> Unit,
    onSaveDubbedAudioFile: (String) -> Unit,
    onSaveDubbedVideoFile: (String) -> Unit,
    onShareDubbedVideoFile: (String) -> Unit,
    onSaveStemFile: (String, String) -> Unit,
    onClearSegments: () -> Unit,
    onDismissBanner: () -> Unit,
    onBackToStudio: () -> Unit
) {
    BackHandler { onBackToStudio() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var canDrawOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlay = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var notifWarning by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifWarning = !granted
        onRequestMediaProjectionCapture()
    }

    val mediaFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onDubMediaFileUri(uri)
    }

    val estimatePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onEstimateWorkloadUri(uri)
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

            // Header Status Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isDubbingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "دوبله زنده فارسی (ضدقطعی و غیررباتیک)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = statusText ?: "آماده ترجمه و دوبله صدای خروجی پخش‌کننده اندروید",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isProcessingSegment) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hub,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activeEngineLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    if (isServiceRunning) {
                                        onToggleDubbingActive()
                                    } else {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            onRequestMediaProjectionCapture()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDubbingActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("start_dubbing_capture_button")
                            ) {
                                Icon(
                                    imageVector = if (isDubbingActive) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        !isServiceRunning -> "اتصال به صدای پخش‌کننده"
                                        isDubbingActive -> "توقف موقت دوبله"
                                        else -> "ادامه دوبله زنده"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isServiceRunning) {
                                OutlinedButton(
                                    onClick = onStopDubbingService,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .height(50.dp)
                                        .testTag("stop_dubbing_service_button")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("قطع")
                                }
                            }
                        }

                        if (notifWarning) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "مجوز اعلان داده نشد؛ سرویس همچنان با اعلان سیستمی اجرا می‌شود.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Subtitle & Dubbing Output Mode Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "حالت زیرنویس و دوبله (زنده و فایل)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "انتخاب کنید فقط دوبله صوتی پخش شود، فقط زیرنویس فارسی نمایش داده و ساخته شود، یا هر دو هم‌زمان انجام شوند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            DubbingOutputMode.entries.forEachIndexed { idx, mode ->
                                SegmentedButton(
                                    selected = dubOutputMode == mode,
                                    onClick = { onSelectDubOutputMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(idx, DubbingOutputMode.entries.size),
                                    modifier = Modifier.testTag("dub_mode_${mode.id}")
                                ) {
                                    Text(
                                        text = mode.titleFa,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        if (dubOutputMode != DubbingOutputMode.SUBTITLE_ONLY || dubOutputMode != DubbingOutputMode.DUB_ONLY) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (dubOutputMode != DubbingOutputMode.DUB_ONLY) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("زیرنویس دوزبانه (اصلی + فارسی)", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = "مناسب آموزش زبان: نمایش همزمان جمله اصلی زیر ترجمه فارسی",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = dualLanguageSubtitles,
                                    onCheckedChange = onToggleDualLanguageSubtitles,
                                    modifier = Modifier.testTag("dual_subtitles_switch")
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.inverseSurface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ClosedCaption,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "نمایش زنده زیرنویس فارسی",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = currentSubtitleText.ifBlank {
                                            "با شروع پخش ویدیو یا فایل، زیرنویس فارسی همزمان اینجا و روی صفحه نمایش داده می‌شود..."
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.inverseOnSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Multi-Engine Failover Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "۵ موتور هوش مصنوعی دوبله + اتصال خودکار (Failover)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "در حالت خودکار، اگر هر موتور قطع شود، برنامه بلافاصله بین درگاه بدون فیلترشکن ایران (AvalAI/Liara/GapGPT)، Gemini، OpenAI، Scribe و موتور پنجم جابه‌جا می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DubbingEngineMethod.entries) { method ->
                                FilterChip(
                                    selected = selectedEngineMethod == method,
                                    onClick = { onSelectEngineMethod(method) },
                                    label = { Text(method.titleFa) },
                                    modifier = Modifier.testTag("dub_engine_${method.id}")
                                )
                            }
                        }
                    }
                }
            }

            // Expressive Non-Robotic Dialect, Tone & Emotion Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TheaterComedy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "گویش، لحن و احساسات غیررباتیک دوبله",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "گوینده احساس گوینده اصلی (هیجان، غم، شوخی، جدیت، نجوا) را می‌فهمد و با شکسته‌گویی طبیعی و مکث انسانی اجرا می‌کند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("گویش و لهجه دوبله:", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PersianAccent.entries) { accent ->
                                FilterChip(
                                    selected = selectedAccent == accent,
                                    onClick = { onSelectAccent(accent) },
                                    label = { Text(accent.titleFa) },
                                    modifier = Modifier.testTag("dub_accent_${accent.id}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("سبک بازیگری و لحن:", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DubbingToneStyle.entries) { style ->
                                FilterChip(
                                    selected = selectedToneStyle == style,
                                    onClick = { onSelectToneStyle(style) },
                                    label = { Text(style.titleFa) },
                                    modifier = Modifier.testTag("dub_tone_${style.id}")
                                )
                            }
                        }
                        Text(
                            text = selectedToneStyle.subtitleFa,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("سبک سینمایی دوبلاژ (Character Casting & Style):", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CinemaDubbingStyle.entries) { cStyle ->
                                FilterChip(
                                    selected = selectedCinemaStyle == cStyle,
                                    onClick = { onSelectCinemaStyle(cStyle) },
                                    label = { Text(cStyle.titleFa) },
                                    modifier = Modifier.testTag("dub_cinema_style_${cStyle.id}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("شدت احساس و بازیگری صدا", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "${(dubExpressiveness * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = dubExpressiveness,
                            onValueChange = onExpressivenessChange,
                            valueRange = 0f..1f,
                            modifier = Modifier.testTag("dub_expressiveness_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("بمی و زیری لحن (Pitch)", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "${"%.2f".format(dubPitch)}x",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = dubPitch,
                            onValueChange = onDubPitchChange,
                            valueRange = 0.75f..1.30f,
                            modifier = Modifier.testTag("dub_pitch_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("سرعت بیان دوبلور (Speed)", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "${"%.2f".format(dubSpeed)}x",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = dubSpeed,
                            onValueChange = onDubSpeedChange,
                            valueRange = 0.80f..1.25f,
                            modifier = Modifier.testTag("dub_speed_slider")
                        )
                    }
                }
            }

            // Floating Button Overlay Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "دکمه شناور روی صفحه (روی همه برنامه‌ها)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "دکمه شناور فقط یک آیکون کوچک Play/Pause است (بدون نوشته و بدون اشغال صفحه)؛ وقتی خاموش باشد کم‌رنگ می‌شود و بعد از رهاکردن خودکار به لبه چپ یا راست صفحه می‌چسبد. تمام تنظیمات زیرنویس و دوبله همین‌جا انجام می‌شوند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (!canDrawOverlay) {
                            OutlinedButton(
                                onClick = onRequestOverlayPermission,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("grant_overlay_button")
                            ) {
                                Text("فعال‌سازی دسترسی نمایش دکمه شناور روی صفحه")
                            }
                        } else {
                            Button(
                                onClick = onShowFloatingOverlay,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isOverlayVisible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("show_floating_button")
                            ) {
                                Text(
                                    text = if (isOverlayVisible) "دکمه شناور روی صفحه فعال است ✓" else "نمایش دکمه شناور روی صفحه",
                                    color = if (isOverlayVisible) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Dual Volume Control Card (Original vs Dubbed)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تنظیم ولوم صدای اصلی و صدای دوبله",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ولوم صدای اصلی (پخش‌کننده)", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "${(originalVolume * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = originalVolume,
                            onValueChange = onOriginalVolumeChange,
                            valueRange = 0f..1f,
                            modifier = Modifier.testTag("original_volume_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ولوم صدای دوبله فارسی", style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "${(dubVolume * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = dubVolume,
                            onValueChange = onDubVolumeChange,
                            valueRange = 0f..1f,
                            modifier = Modifier.testTag("dub_volume_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "دوبله با صدای کلون‌شده خودم",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val activeName = voiceProfiles.firstOrNull { it.id == selectedClonedProfileId }?.name
                                Text(
                                    text = if (useClonedVoice && activeName != null) {
                                        "فعال با صدای «$activeName» (حس‌وحال طبیعی eleven_v3)"
                                    } else {
                                        "در حالت خاموش از دوبلور هوشمند فارسی با تنظیم لحن پویا استفاده می‌شود"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useClonedVoice,
                                onCheckedChange = onToggleUseClonedVoice,
                                modifier = Modifier.testTag("dub_cloned_voice_switch")
                            )
                        }
                    }
                }
            }

            // Direct Long Movie / Audio File Dubbing & Auto-SRT Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "دوبله و زیرنویس خودکار فایل / فیلم (حتی چند ساعته)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "هر فیلم یا فایل صوتی (حتی چند ساعته با ذخیره پیشرفت) را بدهید: صدای انفجار/موسیقی اصلی کاملاً حفظ و رایگان رد می‌شود، جنسیت و سن کاراکتر (زن، مرد، دختربچه، پسر) با گریه/خنده طبیعی اجرا می‌گردد و همگام‌سازی لب (WSOLA) اعمال می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ساخت ویدئوی دوبله‌شده MP4 مستقیم", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "ترکیب تصویر اصلی فیلم با صدای دوبله + انفجارها (بدون کاهش کیفیت تصویر)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = exportMp4Video,
                                onCheckedChange = onToggleExportMp4Video,
                                modifier = Modifier.testTag("export_mp4_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("برش سکانس / ریلز (اختیاری - برحسب ثانیه):", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = clipStartSecText,
                                onValueChange = { s -> onClipRangeChange(s.filter { it.isDigit() }, clipEndSecText) },
                                label = { Text("شروع (ثانیه)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = clipEndSecText,
                                onValueChange = { e -> onClipRangeChange(clipStartSecText, e.filter { it.isDigit() }) },
                                label = { Text("پایان (ثانیه)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!mediaWorkloadSummary.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = mediaWorkloadSummary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (!longFileProgressText.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = longFileProgressText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { estimatePickerLauncher.launch(arrayOf("audio/*", "video/*")) },
                                enabled = operationState !is AsyncOperationState.Loading,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("estimate_workload_button")
                            ) {
                                Text("تخمین زمان")
                            }
                            Button(
                                onClick = { mediaFilePickerLauncher.launch(arrayOf("audio/*", "video/*")) },
                                enabled = operationState !is AsyncOperationState.Loading,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("dub_file_picker_button")
                            ) {
                                Icon(Icons.Default.AudioFile, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("شروع دوبله فیلم/صوت")
                            }
                        }

                        if (!latestSrtFilePath.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onSaveSrtFile(latestSrtFilePath) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("export_srt_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ذخیره زیرنویس (.srt)")
                                }
                                OutlinedButton(
                                    onClick = { onShareSrtFile(latestSrtFilePath) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("share_srt_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("اشتراک زیرنویس")
                                }
                            }
                        }

                        if (!latestDubbedAudioPath.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val isDubFileActive = playingItemId == "dub_file_output"
                            if (isDubFileActive) {
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
                                    Text(formatMs(currentPosMs), style = MaterialTheme.typography.labelSmall)
                                    Text(formatMs(durationMs), style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onTogglePlayDubbedFile(latestDubbedAudioPath) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("play_dubbed_audio_button")
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingDubbedFile) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isPlayingDubbedFile) "توقف دوبله" else "پخش فایل دوبله")
                                }
                                OutlinedButton(
                                    onClick = { onSaveDubbedAudioFile(latestDubbedAudioPath) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("save_dubbed_audio_button")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ذخیره صوت دوبله")
                                }
                            }
                        }

                        if (!latestDubbedVideoPath.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onSaveDubbedVideoFile(latestDubbedVideoPath) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("save_dubbed_mp4_button")
                                ) {
                                    Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ذخیره ویدئوی MP4")
                                }
                                OutlinedButton(
                                    onClick = { onShareDubbedVideoFile(latestDubbedVideoPath) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("share_dubbed_mp4_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "اشتراک MP4", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (!latestDubOnlyStemPath.isNullOrBlank() || !latestSfxOnlyStemPath.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                latestDubOnlyStemPath?.let { dubStem ->
                                    OutlinedButton(
                                        onClick = { onSaveStemFile(dubStem, "dub_only") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Text("استم فقط دیالوگ", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                latestSfxOnlyStemPath?.let { sfxStem ->
                                    OutlinedButton(
                                        onClick = { onSaveStemFile(sfxStem, "sfx_bgm") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Text("استم موسیقی+انفجار", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Live Dubbed Segments History
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "دیالوگ‌های ترجمه‌شده (${segments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (segments.isNotEmpty()) {
                        TextButton(onClick = onClearSegments) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("پاک‌کردن")
                        }
                    }
                }
            }

            if (segments.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "هنوز دیالوگی ترجمه نشده است. پخش‌کننده را باز کنید، روی «اتصال به صدای پخش‌کننده» یا دکمه شناور بزنید تا ترجمه و دوبله فارسی با احساس طبیعی اینجا نمایش داده و پخش شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(segments, key = { it.id }) { seg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(seg.speakerSlotLabel.ifBlank { seg.speakerCharacter.titleFa }) }
                                    )
                                }
                                item {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("مبدأ: ${seg.detectedLanguage}") }
                                    )
                                }
                                item {
                                    AssistChip(
                                        onClick = {},
                                        leadingIcon = {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                        },
                                        label = { Text(seg.detectedEmotion.titleFa) }
                                    )
                                }
                                item {
                                    AssistChip(
                                        onClick = {},
                                        label = {
                                            Text(
                                                if (seg.failoverOccurred) "جایگزین: ${seg.engineUsedName}" else seg.engineUsedName
                                            )
                                        }
                                    )
                                }
                                if (seg.endMs > seg.startMs) {
                                    item {
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(formatTimestampRange(seg.startMs, seg.endMs)) }
                                        )
                                    }
                                }
                            }
                            if (seg.originalTranscript.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = seg.originalTranscript,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = seg.persianTranslation,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

private fun formatTimestampRange(startMs: Long, endMs: Long): String {
    fun fmt(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }
    return "${fmt(startMs)} - ${fmt(endMs)}"
}
