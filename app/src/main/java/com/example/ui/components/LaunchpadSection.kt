package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.BackingBeatStyle
import com.example.audio.BeatLaunchpadEngine
import com.example.audio.LaunchpadPadType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LaunchpadAndGenreBeatsCard(
    selectedBeatStyle: BackingBeatStyle,
    launchpadBpm: Int,
    sequencerGrid: Map<LaunchpadPadType, List<Boolean>>,
    isSequencerLoopPlaying: Boolean,
    previewingGenreStyle: BackingBeatStyle?,
    onTapPad: (LaunchpadPadType) -> Unit,
    onToggleSequencerStep: (LaunchpadPadType, Int) -> Unit,
    onLaunchpadBpmChange: (Int) -> Unit,
    onToggleSequencerLoop: () -> Unit,
    onUseLaunchpadBeatInMix: () -> Unit,
    onSelectGenreBeat: (BackingBeatStyle) -> Unit,
    onTogglePreviewGenreBeat: (BackingBeatStyle) -> Unit
) {
    val scope = rememberCoroutineScope()
    var activePad by remember { mutableStateOf<LaunchpadPadType?>(null) }
    var showSequencer by remember { mutableStateOf(true) }

    val pads = remember { LaunchpadPadType.entries }
    val seqRows = remember {
        listOf(
            LaunchpadPadType.KICK,
            LaunchpadPadType.SNARE,
            LaunchpadPadType.HIHAT_CLOSED,
            LaunchpadPadType.BASS_808,
            LaunchpadPadType.TOMBAK,
            LaunchpadPadType.CHORD_1,
            LaunchpadPadType.PLUCK
        )
    }

    val readyGenreBeats = remember {
        BackingBeatStyle.entries.filter {
            it != BackingBeatStyle.NONE &&
                it != BackingBeatStyle.CUSTOM_FILE &&
                it != BackingBeatStyle.CUSTOM_LAUNCHPAD
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Live Launchpad & 16-Step Sequencer
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "لانچ‌پد و بیت‌ساز اختصاصی",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "پدها را زنده بزنید یا الگوی ۱۶ گامی خودتان را بچینید",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { showSequencer = !showSequencer },
                        modifier = Modifier.testTag("toggle_sequencer_button")
                    ) {
                        Text(if (showSequencer) "بستن گام‌شمار" else "گام‌شمار ۱۶تایی")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4 columns x 3 rows interactive launchpad grid
                val chunkedPads = pads.chunked(4)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunkedPads.forEach { rowPads ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPads.forEach { pad ->
                                val baseColor = Color(pad.colorHex)
                                val isFlashing = activePad == pad
                                val bgColor by animateColorAsState(
                                    targetValue = if (isFlashing) {
                                        baseColor.copy(alpha = 0.92f)
                                    } else {
                                        baseColor.copy(alpha = 0.22f)
                                    },
                                    label = "pad_${pad.id}"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(62.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(bgColor, baseColor.copy(alpha = if (isFlashing) 0.7f else 0.12f))
                                            )
                                        )
                                        .border(
                                            width = if (isFlashing) 2.dp else 1.dp,
                                            color = baseColor.copy(alpha = if (isFlashing) 1f else 0.55f),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            activePad = pad
                                            onTapPad(pad)
                                            scope.launch {
                                                delay(140)
                                                if (activePad == pad) activePad = null
                                            }
                                        }
                                        .testTag("launchpad_pad_${pad.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = pad.shortCode,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isFlashing) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = pad.titleFa,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isFlashing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showSequencer) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("سرعت ریتم (BPM): $launchpadBpm", style = MaterialTheme.typography.labelLarge)
                        Text("۱۶ ضربه استودیویی", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = launchpadBpm.toFloat(),
                        onValueChange = { onLaunchpadBpmChange(it.toInt()) },
                        valueRange = 65f..160f,
                        modifier = Modifier.testTag("launchpad_bpm_slider")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        seqRows.forEach { pad ->
                            val rowColor = Color(pad.colorHex)
                            val steps = sequencerGrid[pad] ?: List(BeatLaunchpadEngine.STEPS_COUNT) { false }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = pad.shortCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = rowColor,
                                    modifier = Modifier.width(34.dp)
                                )
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    for (step in 0 until BeatLaunchpadEngine.STEPS_COUNT) {
                                        val active = steps.getOrNull(step) == true
                                        val groupAccent = (step / 4) % 2 == 0
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(26.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    when {
                                                        active -> rowColor
                                                        groupAccent -> MaterialTheme.colorScheme.surfaceVariant
                                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                                    }
                                                )
                                                .clickable { onToggleSequencerStep(pad, step) }
                                                .testTag("seq_${pad.id}_$step")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onToggleSequencerLoop,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("launchpad_play_loop_button")
                        ) {
                            Icon(
                                imageVector = if (isSequencerLoopPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isSequencerLoopPlaying) "توقف پخش بیت" else "پخش لوپ لانچ‌پد")
                        }
                        Button(
                            onClick = onUseLaunchpadBeatInMix,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedBeatStyle == BackingBeatStyle.CUSTOM_LAUNCHPAD) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("use_launchpad_beat_button")
                        ) {
                            Text(
                                text = if (selectedBeatStyle == BackingBeatStyle.CUSTOM_LAUNCHPAD) {
                                    "بیت لانچ‌پد انتخاب شد ✓"
                                } else {
                                    "انتخاب برای خوانندگی"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 2. Ready-Made Genre Beats Library
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "بانک بیت‌های آماده در هر ژانر (پخش و خوانندگی)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "بیت دلخواهتان را پلی کنید تا رویش بخوانید یا مستقیم برای میکس انتخابش کنید",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    readyGenreBeats.forEach { genre ->
                        val isSelected = selectedBeatStyle == genre
                        val isPreviewing = previewingGenreStyle == genre
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.48f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectGenreBeat(genre) }
                                .testTag("genre_beat_${genre.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onTogglePreviewGenreBeat(genre) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("preview_genre_${genre.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = "پخش نمونه ${genre.titleFa}",
                                        tint = if (isPreviewing) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = genre.titleFa,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${genre.bpm} BPM • ${genre.subtitleFa}",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
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
}
