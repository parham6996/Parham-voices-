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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.PodcastDialogueTurn
import com.example.domain.PersianAccent
import com.example.domain.VoiceCharacterArchetype
import com.example.podcast.PodcastEpisodeMode
import com.example.ui.components.OperationBanner
import com.example.ui.components.PlaybackWaveformVisualizer
import com.example.ui.components.formatMs
import com.example.viewmodel.AsyncOperationState
import com.example.viewmodel.PodcastWorkspaceState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PodcastStudioScreen(
    state: PodcastWorkspaceState,
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
    onSelectMode: (PodcastEpisodeMode) -> Unit,
    onSelectAccent: (PersianAccent) -> Unit,
    onUpdateTopicText: (String) -> Unit,
    onToggleAmbientMusic: (Boolean) -> Unit,
    onToggleClonedHost: (Boolean) -> Unit,
    onUpdateTurnText: (Int, String) -> Unit,
    onCycleTurnCharacter: (Int) -> Unit,
    onGenerateScript: () -> Unit,
    onRenderEpisodeAudio: () -> Unit,
    onTogglePlayEpisode: (String) -> Unit,
    onSeekPlayback: (Float) -> Unit,
    onSaveEpisodeWav: (String) -> Unit,
    onShareEpisodeWav: (String) -> Unit,
    onDismissBanner: () -> Unit,
    onBackToStudio: () -> Unit
) {
    BackHandler { onBackToStudio() }

    val isBusy = operationState is AsyncOperationState.Loading

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
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                    ),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "کارگردان پادکست و کتاب صوتی نمایشی",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "تبدیل ایده یا متن کتاب به گفتگوی چندشخصیتی (راوی، خانم، آقا، کودک) با موسیقی ملایم و خروجی مستر WAV",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Mode, Accent & Topic Prompt Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("قالب تولید برنامه:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            PodcastEpisodeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = state.episodeMode == mode,
                                    onClick = { onSelectMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index, PodcastEpisodeMode.entries.size),
                                    modifier = Modifier.testTag("podcast_mode_${mode.id}")
                                ) {
                                    Text(mode.titleFa, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        Text("گویش فارسی اجرا:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PersianAccent.entries.forEach { acc ->
                                FilterChip(
                                    selected = state.accent == acc,
                                    onClick = { onSelectAccent(acc) },
                                    label = { Text(acc.titleFa) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = state.topicOrBookText,
                            onValueChange = onUpdateTopicText,
                            label = { Text("موضوع پادکست یا پاراگراف کتاب / داستان") },
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("podcast_topic_input")
                        )

                        Button(
                            onClick = onGenerateScript,
                            enabled = !isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("podcast_generate_script_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تولید سناریوی چندشخصیتی با هوش مصنوعی", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Cast & Audio Mixing Switches + Render Button
            item {
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("حضور صدای کلون‌شده من به‌عنوان مجری/راوی", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "نوبت راوی/گوینده اول با صدای کلون‌شده شما خوانده می‌شود",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.useClonedVoiceForHost,
                                onCheckedChange = onToggleClonedHost
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("موسیقی زمینه ملایم با داکینگ خودکار", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = "پخش موزیک پس‌زمینه هماهنگ که زیر صدای گویندگان ملایم می‌شود",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.includeAmbientMusic,
                                onCheckedChange = onToggleAmbientMusic
                            )
                        }

                        Button(
                            onClick = onRenderEpisodeAudio,
                            enabled = !isBusy && state.turns.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("podcast_render_audio_button")
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تولید و مستر نهایی اپیزود صوتی (${state.turns.size} نوبت)", fontWeight = FontWeight.Bold)
                        }

                        val latestPath = state.latestRenderedWavPath
                        if (!latestPath.isNullOrBlank()) {
                            val isEpisodeActive = playingItemId == "podcast_episode_output"
                            if (isEpisodeActive) {
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
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(formatMs(currentPosMs), style = MaterialTheme.typography.labelSmall)
                                    Text(formatMs(durationMs), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onTogglePlayEpisode(latestPath) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isEpisodeActive && isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isEpisodeActive && isPlaying) "توقف" else "پخش اپیزود")
                                }
                                OutlinedButton(
                                    onClick = { onSaveEpisodeWav(latestPath) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ذخیره WAV")
                                }
                                OutlinedButton(
                                    onClick = { onShareEpisodeWav(latestPath) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "اشتراک", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Editable Multi-Character Script Turns
            if (state.turns.isNotEmpty()) {
                item {
                    Text(
                        text = "سناریوی قابل ویرایش (${state.turns.size} شخصیت و دیالوگ)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                itemsIndexed(state.turns) { index, turn ->
                    PodcastTurnCard(
                        index = index,
                        turn = turn,
                        onTextChange = { onUpdateTurnText(index, it) },
                        onCycleCharacter = { onCycleTurnCharacter(index) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }
        }
    }
}

@Composable
private fun PodcastTurnCard(
    index: Int,
    turn: PodcastDialogueTurn,
    onTextChange: (String) -> Unit,
    onCycleCharacter: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}. ${turn.speakerName}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                AssistChip(
                    onClick = onCycleCharacter,
                    label = { Text("${turn.archetype.titleFa} ↻") }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(turn.emotion.titleFa) }
                )
            }
            OutlinedTextField(
                value = turn.text,
                onValueChange = onTextChange,
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
