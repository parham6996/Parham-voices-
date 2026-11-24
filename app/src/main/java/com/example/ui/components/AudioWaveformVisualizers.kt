package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun RecordingWaveformVisualizer(
    bars: List<Float>,
    currentLevel: Float,
    peakDb: Float,
    isRecording: Boolean,
    title: String = "موج زنده میکروفون",
    primaryColor: Color = MaterialTheme.colorScheme.tertiary,
    secondaryColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_dot_alpha"
    )

    val safeBars = if (bars.isEmpty()) List(48) { 0.08f } else bars
    val dbClamped = peakDb.coerceIn(-60f, 0f)
    val levelClamped = currentLevel.coerceIn(0f, 1f)
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val accentSuccess = Color(0xFF10B981)
    val accentWarn = Color(0xFFF59E0B)
    val accentClip = Color(0xFFEF4444)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = surfaceBg,
        border = BorderStroke(
            width = 1.dp,
            color = if (isRecording) primaryColor.copy(alpha = 0.55f) else gridColor
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("recording_waveform_visualizer")
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // Top Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecording) primaryColor.copy(alpha = pulseAlpha)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRecording) "$title • در حال ضبط" else "$title • آماده ضبط",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isRecording) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = if (isRecording) {
                        String.format(Locale.US, "%.0f dBFS", dbClamped)
                    } else {
                        "-60 dBFS"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        !isRecording -> MaterialTheme.colorScheme.onSurfaceVariant
                        dbClamped > -3f -> accentClip
                        dbClamped > -12f -> accentWarn
                        else -> secondaryColor
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Symmetrical Waveform Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
            ) {
                val centerY = size.height / 2f
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                // Subtle studio reference lines
                drawLine(
                    color = gridColor,
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = gridColor.copy(alpha = 0.10f),
                    start = Offset(0f, size.height * 0.2f),
                    end = Offset(size.width, size.height * 0.2f),
                    strokeWidth = 1f,
                    pathEffect = dashEffect
                )
                drawLine(
                    color = gridColor.copy(alpha = 0.10f),
                    start = Offset(0f, size.height * 0.8f),
                    end = Offset(size.width, size.height * 0.8f),
                    strokeWidth = 1f,
                    pathEffect = dashEffect
                )

                val count = safeBars.size.coerceAtLeast(1)
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.56f).coerceAtLeast(3f)

                safeBars.forEachIndexed { index, rawAmp ->
                    val amp = if (isRecording) rawAmp.coerceIn(0.06f, 1f) else 0.08f
                    val barHeight = (size.height * 0.90f * amp).coerceAtLeast(6f)
                    val x = index * slotWidth + (slotWidth - barWidth) / 2f
                    val y = centerY - barHeight / 2f

                    val barBrush = if (isRecording) {
                        Brush.verticalGradient(
                            colors = listOf(
                                if (amp > 0.86f) accentClip else primaryColor,
                                secondaryColor,
                                if (amp > 0.86f) accentClip else primaryColor
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                secondaryColor.copy(alpha = 0.28f),
                                secondaryColor.copy(alpha = 0.18f)
                            )
                        )
                    }

                    drawRoundRect(
                        brush = barBrush,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Real-Time Segmented VU Meter Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "سطح ورودی:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                ) {
                    val segments = 24
                    val gap = 3f
                    val segW = ((size.width - gap * (segments - 1)) / segments).coerceAtLeast(2f)
                    val activeSegments = if (isRecording) (levelClamped * segments).toInt() else 0

                    for (s in 0 until segments) {
                        val ratio = s.toFloat() / segments.toFloat()
                        val baseCol = when {
                            ratio >= 0.85f -> accentClip
                            ratio >= 0.65f -> accentWarn
                            else -> accentSuccess
                        }
                        val col = if (s < activeSegments) baseCol else gridColor.copy(alpha = 0.25f)
                        drawRoundRect(
                            color = col,
                            topLeft = Offset(s * (segW + gap), 0f),
                            size = Size(segW, size.height),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRecording) "${(levelClamped * 100).toInt()}%" else "0%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecording) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PlaybackWaveformVisualizer(
    envelopeBars: List<Float>,
    liveBars: List<Float>,
    playbackLevel: Float,
    playbackDb: Float,
    progress: Float,
    isActiveItem: Boolean,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val safeEnvelope = if (envelopeBars.isEmpty()) List(52) { 0.14f } else envelopeBars
    val safeLive = if (liveBars.isEmpty()) List(32) { 0.08f } else liveBars
    val effectiveProgress = if (isActiveItem) progress.coerceIn(0f, 1f) else 0f

    val mutedTrack = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)
    val surfaceCard = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    val accentWarn = Color(0xFFF59E0B)
    val accentClip = Color(0xFFEF4444)

    fun mapOffsetToFraction(x: Float, width: Float): Float {
        if (width <= 0f) return 0f
        val raw = (x / width).coerceIn(0f, 1f)
        return if (isRtl) 1f - raw else raw
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceCard)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("playback_waveform_visualizer")
    ) {
        // 1. Scrubbable Full-Track Waveform Envelope
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .pointerInput(isActiveItem) {
                    detectTapGestures { offset ->
                        if (isActiveItem) {
                            onSeek(mapOffsetToFraction(offset.x, size.width.toFloat()))
                        }
                    }
                }
                .pointerInput(isActiveItem) {
                    detectHorizontalDragGestures { change, _ ->
                        if (isActiveItem) {
                            change.consume()
                            onSeek(mapOffsetToFraction(change.position.x, size.width.toFloat()))
                        }
                    }
                }
        ) {
            val count = safeEnvelope.size.coerceAtLeast(1)
            val slot = size.width / count
            val barW = (slot * 0.58f).coerceAtLeast(2.5f)
            val centerY = size.height / 2f

            safeEnvelope.forEachIndexed { idx, amp ->
                val barFrac = (idx.toFloat() + 0.5f) / count.toFloat()
                val drawIdx = if (isRtl) (count - 1 - idx) else idx
                val x = drawIdx * slot + (slot - barW) / 2f
                val barH = (size.height * 0.88f * amp.coerceIn(0.08f, 1f)).coerceAtLeast(5f)
                val y = centerY - barH / 2f

                val isPlayed = isActiveItem && barFrac <= effectiveProgress
                val brush = if (isPlayed) {
                    Brush.verticalGradient(listOf(primaryColor, secondaryColor))
                } else {
                    Brush.verticalGradient(listOf(mutedTrack, mutedTrack.copy(alpha = 0.16f)))
                }

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(x, y),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                )
            }

            if (isActiveItem) {
                val headX = if (isRtl) {
                    size.width * (1f - effectiveProgress)
                } else {
                    size.width * effectiveProgress
                }
                drawLine(
                    color = secondaryColor,
                    start = Offset(headX, 2f),
                    end = Offset(headX, size.height - 2f),
                    strokeWidth = 3f
                )
            }
        }

        // 2. Real-Time Live Playback Audio Level Spectrum
        AnimatedVisibility(visible = isActiveItem && isPlaying) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سطح لحظه‌ای خروجی صدا",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.0f dBFS • %d%%",
                            playbackDb.coerceIn(-60f, 0f),
                            (playbackLevel.coerceIn(0f, 1f) * 100).toInt()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            playbackDb > -3f -> accentClip
                            playbackDb > -10f -> accentWarn
                            else -> secondaryColor
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                ) {
                    val count = safeLive.size.coerceAtLeast(1)
                    val slot = size.width / count
                    val barW = (slot * 0.62f).coerceAtLeast(2.5f)
                    safeLive.forEachIndexed { idx, amp ->
                        val clamped = amp.coerceIn(0.06f, 1f)
                        val barH = (size.height * clamped).coerceAtLeast(3f)
                        val x = idx * slot + (slot - barW) / 2f
                        val y = size.height - barH
                        val topColor = when {
                            clamped > 0.85f -> accentClip
                            clamped > 0.65f -> accentWarn
                            else -> primaryColor
                        }
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(topColor, secondaryColor)),
                            topLeft = Offset(x, y),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }
            }
        }
    }
}
