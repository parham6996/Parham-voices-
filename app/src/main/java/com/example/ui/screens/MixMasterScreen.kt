package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audio.AutoTuneScale
import com.example.audio.BackingBeatStyle
import com.example.audio.DspEffectPreset
import com.example.audio.LaunchpadPadType
import com.example.audio.MixMasterConfig
import com.example.data.local.AudioSampleEntity
import com.example.data.local.GeneratedSpeechEntity
import com.example.domain.SpeechLanguage
import com.example.ui.components.AudioPlaybackCard
import com.example.ui.components.LaunchpadAndGenreBeatsCard
import com.example.ui.components.OperationBanner
import com.example.viewmodel.AsyncOperationState
import com.example.viewmodel.SingingMixState
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MixMasterScreen(
    mixState: SingingMixState,
    selectedLanguage: SpeechLanguage,
    audioSamples: List<AudioSampleEntity>,
    operationState: AsyncOperationState,
    playingItemId: String?,
    isPlaying: Boolean,
    playbackProgress: Float,
    currentPosMs: Int,
    durationMs: Int,
    waveformEnvelope: List<Float>,
    liveBars: List<Float>,
    playbackLevel: Float,
    playbackDb: Float,
    onSelectLanguage: (SpeechLanguage) -> Unit,
    onUpdateLyricsTopic: (String) -> Unit,
    onUpdateLyrics: (String) -> Unit,
    onGenerateAiLyrics: () -> Unit,
    onToggleVocalSource: (Boolean) -> Unit,
    onSelectVocalSample: (Long) -> Unit,
    onUpdateMixConfig: ((MixMasterConfig) -> MixMasterConfig) -> Unit,
    onImportCustomBeat: (android.net.Uri) -> Unit,
    onTapLaunchpadPad: (LaunchpadPadType) -> Unit,
    onToggleSequencerStep: (LaunchpadPadType, Int) -> Unit,
    onLaunchpadBpmChange: (Int) -> Unit,
    onToggleSequencerLoop: () -> Unit,
    onUseLaunchpadBeatInMix: () -> Unit,
    onTogglePreviewGenreBeat: (BackingBeatStyle) -> Unit,
    onAutoMatchBeatToVocal: () -> Unit,
    onSynthesizeAndMaster: () -> Unit,
    onRemasterExisting: () -> Unit,
    onTogglePlayMastered: (GeneratedSpeechEntity) -> Unit,
    onSeekPlayback: (Float) -> Unit,
    onShareSpeech: (GeneratedSpeechEntity) -> Unit,
    onSaveSpeech: (GeneratedSpeechEntity) -> Unit,
    onDismissBanner: () -> Unit,
    onBackToStudio: () -> Unit
) {
    BackHandler { onBackToStudio() }

    val beatPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportCustomBeat(uri)
    }

    val cfg = mixState.mixConfig

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

            // Header Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "استودیو خوانندگی، لانچ‌پد و مسترینگ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "بیت‌سازی زنده با لانچ‌پد، بانک بیت ژانرها، تشخیص خودکار بیت هماهنگ با صدا و تنظیم مستقل بم/نازک و سرعت",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Vocal Source Selector & Lyrics
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "منبع صدای خواننده (Vocal Track)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !mixState.useRecordedSampleInsteadOfClone,
                                onClick = { onToggleVocalSource(false) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                            ) {
                                Text("خوانندگی صدای کلون‌شده")
                            }
                            SegmentedButton(
                                selected = mixState.useRecordedSampleInsteadOfClone,
                                onClick = { onToggleVocalSource(true) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                            ) {
                                Text("وکال ضبط‌شده خودم")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (mixState.useRecordedSampleInsteadOfClone) {
                            if (audioSamples.isEmpty()) {
                                Text(
                                    text = "هیچ نمونه صدای ضبط‌شده‌ای ندارید؛ در تب کلون صدا یک نمونه ضبط یا وارد کنید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            } else {
                                Text("انتخاب فایل وکال برای خوانندگی و میکس:", style = MaterialTheme.typography.labelMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    audioSamples.forEach { sample ->
                                        val selected = (mixState.selectedVocalSampleId ?: audioSamples.first().id) == sample.id
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onSelectVocalSample(sample.id) },
                                            label = { Text(sample.title.take(24)) }
                                        )
                                    }
                                }
                            }
                        } else {
                            Text("زبان خوانندگی:", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SpeechLanguage.entries.forEach { lang ->
                                    FilterChip(
                                        selected = lang == selectedLanguage,
                                        onClick = { onSelectLanguage(lang) },
                                        label = { Text(lang.titleFa) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = mixState.lyricsTopic,
                                    onValueChange = onUpdateLyricsTopic,
                                    label = { Text("موضوع ترانه") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = onGenerateAiLyrics,
                                    modifier = Modifier
                                        .height(54.dp)
                                        .testTag("generate_lyrics_button")
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("شعر بساز")
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = mixState.lyrics,
                                onValueChange = onUpdateLyrics,
                                label = { Text("متن ترانه برای خوانندگی") },
                                minLines = 3,
                                maxLines = 6,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("singing_lyrics_input")
                            )
                        }
                    }
                }
            }

            // Auto-Beat Finder + Auto-Engineered Mix Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "یابنده خودکار بیت + میکس مهندسی‌شده هوشمند",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "اگر بیتی انتخاب نکرده‌اید یا می‌خواهید برنامه بر اساس گام، تمپو و انرژی صدای خودتان بهترین بیت را بسازد و روی صدایتان بنشاند، این دکمه را بزنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        mixState.autoMatchSummary?.let { summary ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onAutoMatchBeatToVocal,
                            enabled = operationState !is AsyncOperationState.Loading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("auto_match_beat_button")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("پیدا کردن خودکار بیت هماهنگ با صدای من و میکس آهنگ", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "میکس مهندسی‌شده خودکار (تراز RMS و داکینگ)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "هماهنگی تراز بلندی وکال و بیت، حذف فرکانس‌های مزاحم و کاهش خودکار بیت زیر صدای خواننده",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = cfg.autoAlignAndDuck,
                                onCheckedChange = { enabled ->
                                    onUpdateMixConfig { it.copy(autoAlignAndDuck = enabled) }
                                },
                                modifier = Modifier.testTag("auto_pro_mix_switch")
                            )
                        }
                    }
                }
            }

            // Interactive Launchpad + Ready-Made Genre Beats Library
            item {
                LaunchpadAndGenreBeatsCard(
                    selectedBeatStyle = cfg.beatStyle,
                    launchpadBpm = cfg.launchpadBpm,
                    sequencerGrid = cfg.launchpadGrid,
                    isSequencerLoopPlaying = mixState.isLaunchpadLoopPlaying,
                    previewingGenreStyle = mixState.previewingGenreStyle,
                    onTapPad = onTapLaunchpadPad,
                    onToggleSequencerStep = onToggleSequencerStep,
                    onLaunchpadBpmChange = onLaunchpadBpmChange,
                    onToggleSequencerLoop = onToggleSequencerLoop,
                    onUseLaunchpadBeatInMix = onUseLaunchpadBeatInMix,
                    onSelectGenreBeat = { style ->
                        onUpdateMixConfig { it.copy(beatStyle = style, detectedBpmOverride = null) }
                    },
                    onTogglePreviewGenreBeat = onTogglePreviewGenreBeat
                )
            }

            // Custom Audio Beat File & Acapella option
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = cfg.beatStyle == BackingBeatStyle.NONE,
                                onClick = { onUpdateMixConfig { it.copy(beatStyle = BackingBeatStyle.NONE) } },
                                label = { Text("بدون بیت (آکاپلا)") }
                            )
                            if (mixState.customBeatName != null) {
                                FilterChip(
                                    selected = cfg.beatStyle == BackingBeatStyle.CUSTOM_FILE,
                                    onClick = { onUpdateMixConfig { it.copy(beatStyle = BackingBeatStyle.CUSTOM_FILE) } },
                                    label = { Text("فایل: ${mixState.customBeatName.take(18)}") }
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { beatPickerLauncher.launch(arrayOf("audio/*")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("import_custom_beat_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = mixState.customBeatName?.let { "تغییر فایل بیت دلخواه ($it)" }
                                    ?: "بارگذاری بیت یا موزیک بی‌کلام دلخواه از حافظه (MP3/WAV)"
                            )
                        }
                    }
                }
            }

            // Separate Speed & Pitch (بم / نازک کردن) for Vocal AND Beat independently
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "تنظیم مستقل سرعت و بم/نازکی خواننده و بیت",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        MixSliderRow(
                            label = "سرعت صدای خواننده (Vocal Speed)",
                            valueText = "${"%.2f".format(cfg.vocalSpeed)}x",
                            value = cfg.vocalSpeed,
                            range = 0.75f..1.30f,
                            testTag = "vocal_speed_slider",
                            onChange = { v -> onUpdateMixConfig { it.copy(vocalSpeed = v) } }
                        )
                        MixSliderRow(
                            label = "بم یا نازک کردن صدای خواننده (Pitch)",
                            valueText = formatSemitones(cfg.vocalPitchSemitones),
                            value = cfg.vocalPitchSemitones,
                            range = -7f..7f,
                            testTag = "vocal_pitch_slider",
                            onChange = { v -> onUpdateMixConfig { it.copy(vocalPitchSemitones = v) } }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        MixSliderRow(
                            label = "سرعت ریتم و بیت (Beat Speed)",
                            valueText = "${"%.2f".format(cfg.beatSpeed)}x",
                            value = cfg.beatSpeed,
                            range = 0.75f..1.30f,
                            testTag = "beat_speed_slider",
                            onChange = { v -> onUpdateMixConfig { it.copy(beatSpeed = v) } }
                        )
                        MixSliderRow(
                            label = "بم یا زیر کردن گام بیت (Beat Pitch)",
                            valueText = formatSemitones(cfg.beatPitchSemitones),
                            value = cfg.beatPitchSemitones,
                            range = -7f..7f,
                            testTag = "beat_pitch_slider",
                            onChange = { v -> onUpdateMixConfig { it.copy(beatPitchSemitones = v) } }
                        )
                    }
                }
            }

            // Studio Vocal Booth & DSP Effects Rack (Auto-Tune, Doubler, De-Esser, Echo & Studio FX)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "اتاق ضبط حرفه‌ای (Auto-Tune و افکت‌های وکال)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تیونینگ خودکار صدا (Auto-Tune روی گام)", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "اصلاح فالش بودن نت‌های خوانندگی هماهنگ با گام آهنگ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = cfg.enableAutoTune,
                                onCheckedChange = { enabled ->
                                    onUpdateMixConfig { it.copy(enableAutoTune = enabled) }
                                },
                                modifier = Modifier.testTag("autotune_switch")
                            )
                        }

                        if (cfg.enableAutoTune) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AutoTuneScale.entries.forEach { scale ->
                                    FilterChip(
                                        selected = cfg.autoTuneScale == scale,
                                        onClick = { onUpdateMixConfig { it.copy(autoTuneScale = scale) } },
                                        label = { Text(scale.titleFa) },
                                        modifier = Modifier.testTag("autotune_scale_${scale.id}")
                                    )
                                }
                            }
                            MixSliderRow(
                                label = "شدت اصلاح گام (Auto-Tune Retune)",
                                valueText = "${(cfg.autoTuneAmount * 100).toInt()}%",
                                value = cfg.autoTuneAmount,
                                range = 0f..1f,
                                testTag = "autotune_amount_slider",
                                onChange = { v -> onUpdateMixConfig { it.copy(autoTuneAmount = v) } }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("دوبله وکال و هارمونی استودیویی (Vocal Doubler)", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "حجم‌دهی لایه دوم صدا + همخوانی هارمونیک ظریف",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = cfg.enableVocalDoubler,
                                onCheckedChange = { enabled ->
                                    onUpdateMixConfig { it.copy(enableVocalDoubler = enabled) }
                                },
                                modifier = Modifier.testTag("vocal_doubler_switch")
                            )
                        }

                        if (cfg.enableVocalDoubler) {
                            MixSliderRow(
                                label = "میزان هارمونی (+۵ پنجم درست)",
                                valueText = "${(cfg.harmonyAmount * 100).toInt()}%",
                                value = cfg.harmonyAmount,
                                range = 0f..1f,
                                onChange = { v -> onUpdateMixConfig { it.copy(harmonyAmount = v) } }
                            )
                        }

                        MixSliderRow(
                            label = "حذف سوت حروف «س/ش» (De-Esser)",
                            valueText = "${(cfg.deEsserAmount * 100).toInt()}%",
                            value = cfg.deEsserAmount,
                            range = 0f..1f,
                            testTag = "deesser_slider",
                            onChange = { v -> onUpdateMixConfig { it.copy(deEsserAmount = v) } }
                        )

                        MixSliderRow(
                            label = "اکو و پژواک ریتمیک (Echo Delay)",
                            valueText = "${(cfg.echoAmount * 100).toInt()}%",
                            value = cfg.echoAmount,
                            range = 0f..1f,
                            testTag = "echo_slider",
                            onChange = { v -> onUpdateMixConfig { it.copy(echoAmount = v) } }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("پریست افکت استودیویی / کاراکتری:", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DspEffectPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = cfg.effectPreset == preset,
                                    onClick = { onUpdateMixConfig { it.copy(effectPreset = preset) } },
                                    label = { Text(preset.titleFa) },
                                    modifier = Modifier.testTag("dsp_preset_${preset.id}")
                                )
                            }
                        }
                    }
                }
            }

            // Vocal/Beat Mix Gain, Reverb & Mastering EQ Rack
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Equalizer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "بالانس صدا، ریورب و زنجیره مسترینگ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        MixSliderRow(
                            label = "حجم صدای خواننده (Vocal Gain)",
                            valueText = "${(cfg.vocalGain * 100).toInt()}%",
                            value = cfg.vocalGain,
                            range = 0.5f..1.8f,
                            onChange = { v -> onUpdateMixConfig { it.copy(vocalGain = v) } }
                        )
                        MixSliderRow(
                            label = "حجم بیت (Beat Level)",
                            valueText = "${(cfg.beatGain * 100).toInt()}%",
                            value = cfg.beatGain,
                            range = 0f..1.2f,
                            onChange = { v -> onUpdateMixConfig { it.copy(beatGain = v) } }
                        )
                        MixSliderRow(
                            label = "فضاسازی و ریورب استودیویی (Reverb)",
                            valueText = "${(cfg.reverbAmount * 100).toInt()}%",
                            value = cfg.reverbAmount,
                            range = 0f..1f,
                            onChange = { v -> onUpdateMixConfig { it.copy(reverbAmount = v) } }
                        )
                        MixSliderRow(
                            label = "گرمی و بیس (Low 220Hz)",
                            valueText = String.format(Locale.US, "%+.1f dB", cfg.bassBoostDb),
                            value = cfg.bassBoostDb,
                            range = -6f..8f,
                            onChange = { v -> onUpdateMixConfig { it.copy(bassBoostDb = v) } }
                        )
                        MixSliderRow(
                            label = "شفافیت وکال (Presence Mid)",
                            valueText = String.format(Locale.US, "%+.1f dB", cfg.presenceBoostDb),
                            value = cfg.presenceBoostDb,
                            range = -6f..8f,
                            onChange = { v -> onUpdateMixConfig { it.copy(presenceBoostDb = v) } }
                        )
                        MixSliderRow(
                            label = "درخشندگی و هوا (Air 3.2kHz+)",
                            valueText = String.format(Locale.US, "%+.1f dB", cfg.airTrebleDb),
                            value = cfg.airTrebleDb,
                            range = -6f..8f,
                            onChange = { v -> onUpdateMixConfig { it.copy(airTrebleDb = v) } }
                        )
                        MixSliderRow(
                            label = "فشرده‌سازی و حجم مستر (Compressor)",
                            valueText = "${(cfg.compressionAmount * 100).toInt()}%",
                            value = cfg.compressionAmount,
                            range = 0f..1f,
                            onChange = { v -> onUpdateMixConfig { it.copy(compressionAmount = v) } }
                        )
                    }
                }
            }

            // Action Buttons
            item {
                val isBusy = operationState is AsyncOperationState.Loading
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onSynthesizeAndMaster,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("synthesize_mix_master_button")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تولید خوانندگی + میکس و مستر نهایی", fontWeight = FontWeight.Bold)
                    }

                    if (mixState.rawVocalFilePath != null) {
                        OutlinedButton(
                            onClick = onRemasterExisting,
                            enabled = !isBusy,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("remaster_existing_button")
                        ) {
                            Text("اعمال تغییرات جدید سرعت/گام/EQ/بیت روی همین وکال")
                        }
                    }
                }
            }

            // Mastered Output Player & Export
            val latestMastered = mixState.latestMasteredSpeech
            if (latestMastered != null) {
                item {
                    Column {
                        Text(
                            text = "خروجی مسترشده آماده دانلود و پخش",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        AudioPlaybackCard(
                            speech = latestMastered,
                            isActiveItem = playingItemId == "speech_${latestMastered.id}",
                            isPlaying = isPlaying,
                            progress = playbackProgress,
                            currentPosMs = currentPosMs,
                            durationMs = durationMs,
                            waveformEnvelope = waveformEnvelope,
                            liveBars = liveBars,
                            playbackLevel = playbackLevel,
                            playbackDb = playbackDb,
                            onTogglePlay = { onTogglePlayMastered(latestMastered) },
                            onSeek = onSeekPlayback,
                            onShare = { onShareSpeech(latestMastered) },
                            onSaveFile = { onSaveSpeech(latestMastered) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

private fun formatSemitones(semitones: Float): String {
    val rounded = semitones.roundToInt()
    return when {
        rounded < 0 -> "$rounded نیم‌پرده (بم‌تر)"
        rounded > 0 -> "+$rounded نیم‌پرده (نازک‌تر)"
        else -> "۰ (طبیعی)"
    }
}

@Composable
private fun MixSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    testTag: String? = null,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}
