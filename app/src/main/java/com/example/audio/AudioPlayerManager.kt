package com.example.audio

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin

private const val TAG = "AudioPlayerManager"
private const val LIVE_BAR_COUNT = 32

class AudioPlayerManager {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var waveformJob: Job? = null

    private val _playingItemId = MutableStateFlow<String?>(null)
    val playingItemId: StateFlow<String?> = _playingItemId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _waveformEnvelope = MutableStateFlow<List<Float>>(AudioWaveformExtractor.defaultEmptyWaveform().envelopeBars)
    val waveformEnvelope: StateFlow<List<Float>> = _waveformEnvelope.asStateFlow()

    private val _liveLevelBars = MutableStateFlow<List<Float>>(List(LIVE_BAR_COUNT) { 0.08f })
    val liveLevelBars: StateFlow<List<Float>> = _liveLevelBars.asStateFlow()

    private val _currentPlaybackLevel = MutableStateFlow(0f)
    val currentPlaybackLevel: StateFlow<Float> = _currentPlaybackLevel.asStateFlow()

    private val _currentPlaybackDb = MutableStateFlow(-60f)
    val currentPlaybackDb: StateFlow<Float> = _currentPlaybackDb.asStateFlow()

    private var detailedWaveform: List<Float> = AudioWaveformExtractor.defaultEmptyWaveform().detailedEnvelope
    private var activePitch: Float = 1.0f
    private var activeSpeed: Float = 1.0f
    private var activeVolume: Float = 1.0f

    fun togglePlay(
        itemId: String,
        filePath: String,
        pitch: Float = 1.0f,
        speed: Float = 1.0f,
        volume: Float = 1.0f,
        scope: CoroutineScope,
        onError: (String) -> Unit = {}
    ) {
        if (_playingItemId.value == itemId && mediaPlayer != null) {
            val mp = mediaPlayer!!
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
                resetLiveMeter()
            } else {
                applyPlaybackParams(pitch, speed)
                val v = volume.coerceIn(0f, 1f)
                activeVolume = v
                mp.setVolume(v, v)
                mp.start()
                _isPlaying.value = true
                startProgressTicker(scope)
            }
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            onError("فایل صوتی یافت نشد.")
            return
        }

        stop()

        try {
            activePitch = pitch
            activeSpeed = speed
            val v = volume.coerceIn(0f, 1f)
            activeVolume = v
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setVolume(v, v)
                prepare()
                playbackParams = PlaybackParams()
                    .setPitch(pitch.coerceIn(0.6f, 1.5f))
                    .setSpeed(speed.coerceIn(0.6f, 1.6f))
                setOnCompletionListener {
                    _isPlaying.value = false
                    _progress.value = 0f
                    _currentPositionMs.value = 0
                    resetLiveMeter()
                    progressJob?.cancel()
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
            }
            mediaPlayer = mp
            _playingItemId.value = itemId
            _durationMs.value = mp.duration.coerceAtLeast(1)
            _currentPositionMs.value = 0
            _progress.value = 0f

            waveformJob?.cancel()
            waveformJob = scope.launch {
                val extracted = AudioWaveformExtractor.extractWaveform(file.absolutePath)
                if (_playingItemId.value == itemId) {
                    detailedWaveform = extracted.detailedEnvelope
                    _waveformEnvelope.value = extracted.envelopeBars
                }
            }

            mp.start()
            _isPlaying.value = true
            startProgressTicker(scope)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play audio", e)
            stop()
            onError("خطا در پخش فایل صوتی: ${e.localizedMessage ?: "نامشخص"}")
        }
    }

    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        activeVolume = v
        try {
            mediaPlayer?.setVolume(v, v)
        } catch (_: Exception) {
        }
    }

    fun applyPlaybackParams(pitch: Float, speed: Float) {
        activePitch = pitch
        activeSpeed = speed
        val mp = mediaPlayer ?: return
        try {
            val wasPlaying = mp.isPlaying
            val params = PlaybackParams()
                .setPitch(pitch.coerceIn(0.6f, 1.5f))
                .setSpeed(speed.coerceIn(0.6f, 1.6f))
            mp.playbackParams = params
            if (!wasPlaying && mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
                resetLiveMeter()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply PlaybackParams live", e)
        }
    }

    fun seekTo(fraction: Float) {
        val mp = mediaPlayer ?: return
        val dur = mp.duration.takeIf { it > 0 } ?: return
        val clamped = fraction.coerceIn(0f, 1f)
        val target = (dur * clamped).toInt()
        mp.seekTo(target)
        _currentPositionMs.value = target
        _progress.value = clamped
        updateLiveBarsAtFraction(clamped)
    }

    private fun startProgressTicker(scope: CoroutineScope) {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive && mediaPlayer != null && _isPlaying.value) {
                val mp = mediaPlayer ?: break
                val dur = mp.duration.coerceAtLeast(1)
                val pos = mp.currentPosition.coerceIn(0, dur)
                val frac = pos.toFloat() / dur.toFloat()
                _durationMs.value = dur
                _currentPositionMs.value = pos
                _progress.value = frac
                updateLiveBarsAtFraction(frac)
                delay(50L)
            }
        }
    }

    private fun updateLiveBarsAtFraction(fraction: Float) {
        val points = detailedWaveform
        if (points.isEmpty()) return

        val centerIdx = (fraction * points.lastIndex).toInt().coerceIn(0, points.lastIndex)
        val currentEnergy = (points[centerIdx] * activeVolume.coerceAtLeast(0.25f)).coerceIn(0.05f, 1.0f)

        val prev = _currentPlaybackLevel.value
        val smoothedLevel = if (currentEnergy > prev) currentEnergy else (prev * 0.72f + currentEnergy * 0.28f)
        _currentPlaybackLevel.value = smoothedLevel

        val db = if (smoothedLevel > 0.01f) {
            (20.0 * ln(smoothedLevel.toDouble()) / ln(10.0)).toFloat().coerceIn(-60f, 0f)
        } else {
            -60f
        }
        _currentPlaybackDb.value = db

        val prevBars = _liveLevelBars.value
        val newBars = List(LIVE_BAR_COUNT) { i ->
            val offset = i - LIVE_BAR_COUNT / 2
            val sampleIdx = (centerIdx + offset / 2).coerceIn(0, points.lastIndex)
            val baseEnv = points[sampleIdx]
            val centerWeight = 1.0f - (abs(offset).toFloat() / (LIVE_BAR_COUNT / 1.6f)).coerceIn(0f, 0.55f)
            val harmonicMod = 0.78f + 0.22f * abs(sin((centerIdx * 0.45 + i * 0.65) * PI / 6.0)).toFloat()
            val targetBar = (baseEnv * centerWeight * harmonicMod * activeVolume.coerceAtLeast(0.3f)).coerceIn(0.06f, 1.0f)
            val oldBar = prevBars.getOrElse(i) { 0.08f }
            if (targetBar > oldBar) targetBar else (oldBar * 0.68f + targetBar * 0.32f)
        }
        _liveLevelBars.value = newBars
    }

    private fun resetLiveMeter() {
        _currentPlaybackLevel.value = 0f
        _currentPlaybackDb.value = -60f
        _liveLevelBars.value = List(LIVE_BAR_COUNT) { 0.08f }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        waveformJob?.cancel()
        waveformJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _playingItemId.value = null
        _isPlaying.value = false
        _progress.value = 0f
        _currentPositionMs.value = 0
        _durationMs.value = 0
        detailedWaveform = AudioWaveformExtractor.defaultEmptyWaveform().detailedEnvelope
        _waveformEnvelope.value = AudioWaveformExtractor.defaultEmptyWaveform().envelopeBars
        resetLiveMeter()
    }
}
