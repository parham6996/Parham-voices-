package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.GeneratedSpeechEntity
import com.example.data.local.VoiceProfileEntity
import com.example.domain.PersianAccent
import com.example.ui.components.AudioPlaybackCard
import com.example.ui.components.OperationBanner
import com.example.viewmodel.AsyncOperationState

@Composable
fun VoiceLibraryScreen(
    voiceProfiles: List<VoiceProfileEntity>,
    generatedSpeeches: List<GeneratedSpeechEntity>,
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
    onSyncVoices: () -> Unit,
    onUseVoiceInStudio: (VoiceProfileEntity) -> Unit,
    onRetrainVoice: (VoiceProfileEntity) -> Unit,
    onDeleteVoiceProfile: (VoiceProfileEntity) -> Unit,
    onTogglePlaySpeech: (GeneratedSpeechEntity) -> Unit,
    onSeekPlayback: (Float) -> Unit,
    onShareSpeech: (GeneratedSpeechEntity) -> Unit,
    onSaveSpeech: (GeneratedSpeechEntity) -> Unit,
    onDeleteSpeech: (GeneratedSpeechEntity) -> Unit,
    onDismissBanner: () -> Unit,
    onBackToStudio: () -> Unit
) {
    BackHandler { onBackToStudio() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OperationBanner(state = operationState, onDismiss = onDismissBanner)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مدل‌های صدای کلون‌شده (${voiceProfiles.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onSyncVoices) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("همگام‌سازی ابری")
                    }
                }
            }

            if (voiceProfiles.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "هنوز مدل صدای کلون‌شده‌ای ذخیره نشده است. از تب «کلون و آموزش» اولین صدای خود را بسازید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(voiceProfiles, key = { it.id }) { profile ->
                    val accent = PersianAccent.fromId(profile.accentId)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "لهجه: ${accent.titleFa} • ${profile.sampleCount} نمونه آموزشی",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteVoiceProfile(profile) },
                                    modifier = Modifier.testTag("delete_voice_${profile.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.cd_delete_item),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onUseVoiceInStudio(profile) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("استفاده در استودیو")
                                }
                                OutlinedButton(
                                    onClick = { onRetrainVoice(profile) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ModelTraining, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("بازآموزی دیتاست")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "فایل‌های خروجی صدا و میکس (${generatedSpeeches.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (generatedSpeeches.isEmpty()) {
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
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "آرشیو خروجی‌ها خالی است",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "پس از تولید گفتار یا میکس و مستر، می‌توانید فایل صوتی را همین‌جا پخش، ذخیره یا اشتراک‌گذاری کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(generatedSpeeches, key = { it.id }) { speech ->
                    AudioPlaybackCard(
                        speech = speech,
                        isActiveItem = playingItemId == "speech_${speech.id}",
                        isPlaying = isPlaying,
                        progress = playbackProgress,
                        currentPosMs = currentPosMs,
                        durationMs = durationMs,
                        waveformEnvelope = waveformEnvelope,
                        liveBars = liveBars,
                        playbackLevel = playbackLevel,
                        playbackDb = playbackDb,
                        onTogglePlay = { onTogglePlaySpeech(speech) },
                        onSeek = onSeekPlayback,
                        onShare = { onShareSpeech(speech) },
                        onSaveFile = { onSaveSpeech(speech) },
                        onDelete = { onDeleteSpeech(speech) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
