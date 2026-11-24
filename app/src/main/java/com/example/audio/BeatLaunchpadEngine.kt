package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class LaunchpadPadType(
    val id: String,
    val titleFa: String,
    val shortCode: String,
    val colorHex: Long
) {
    KICK("kick", "کیک کوبنده", "KICK", 0xFFEF4444),
    SNARE("snare", "اسنیر پانچ", "SNR", 0xFFF97316),
    CLAP("clap", "کلپ استودیو", "CLP", 0xFFF59E0B),
    HIHAT_CLOSED("hh_closed", "های‌هت بسته", "CHH", 0xFF10B981),
    HIHAT_OPEN("hh_open", "های‌هت باز", "OHH", 0xFF06B6D4),
    BASS_808("bass_808", "بیس 808", "808", 0xFF8B5CF6),
    TOMBAK("tombak", "ضرب تنبک", "TMB", 0xFFEC4899),
    DAF_SNAP("daf", "دایره و دف", "DAF", 0xFF14B8A6),
    CHORD_1("chord_1", "آکورد اصلی", "CH1", 0xFF3B82F6),
    CHORD_2("chord_2", "آکورد دوم", "CH2", 0xFF6366F1),
    PLUCK("pluck", "پلاک ملودی", "PLK", 0xFF22C55E),
    RISER_FX("fx", "افکت فضا", "SFX", 0xFFD946EF)
}

enum class BackingBeatStyle(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val bpm: Int
) {
    NONE("none", "بدون بیت (آکاپلا)", "فقط صدای خواننده", 0),
    PERSIAN_6_8("persian_6_8", "ایرانی ۶/۸", "ریتم شاد و مجلسی ایرانی", 112),
    HIP_HOP_BOOM_BAP("hip_hop", "هیپ‌هاپ", "کیک و اسنیر کلاسیک کوبنده", 92),
    TRAP_808("trap", "ترپ ۸۰۸", "ساب‌بیس 808 و های‌هت سریع", 132),
    POP_MODERN("pop_4_4", "پاپ مدرن", "پاپ ۴/۴ خوش‌ریتم استودیویی", 104),
    LOFI_CHILL("lofi", "لو-فای آرام", "فضای گرم و ریلکس شبانه", 78),
    UK_DRILL("drill", "دریل", "کیک و اسنیر اسلایددار سنگین", 140),
    RNB_SOUL("rnb", "آر‌اند‌بی", "آکوردهای احساسی و نرم", 86),
    HOUSE_EDM("house", "هاوس / کلاب", "چهارضرب کوبنده الکترونیک", 122),
    ACOUSTIC_BALLAD("ambient_pad", "بالاد آکوستیک", "پد و آرپژ ملایم احساسی", 74),
    CUSTOM_LAUNCHPAD("custom_launchpad", "بیت لانچ‌پد من", "ساخته‌شده در لانچ‌پد", 108),
    CUSTOM_FILE("custom_file", "فایل موزیک دلخواه", "فایل وارد شده از گوشی", 0)
}

data class AutoBeatMatchResult(
    val recommendedStyle: BackingBeatStyle,
    val detectedBpm: Int,
    val detectedRootMidi: Int,
    val detectedKeyNameFa: String,
    val vocalRms: Float
)

class BeatLaunchpadEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        const val STEPS_COUNT = 16
    }

    private val padCache = mutableMapOf<Pair<LaunchpadPadType, Int>, ShortArray>()
    private val activeTracks = mutableListOf<AudioTrack>()

    fun defaultSequencerGrid(): Map<LaunchpadPadType, List<Boolean>> {
        return mapOf(
            LaunchpadPadType.KICK to listOf(true, false, false, false, true, false, false, false, true, false, false, false, true, false, true, false),
            LaunchpadPadType.SNARE to listOf(false, false, false, false, true, false, false, false, false, false, false, false, true, false, false, false),
            LaunchpadPadType.CLAP to listOf(false, false, false, false, true, false, false, false, false, false, false, false, true, false, false, true),
            LaunchpadPadType.HIHAT_CLOSED to listOf(true, false, true, false, true, false, true, false, true, false, true, false, true, false, true, true),
            LaunchpadPadType.BASS_808 to listOf(true, false, false, true, false, false, true, false, true, false, false, false, false, true, false, false),
            LaunchpadPadType.CHORD_1 to listOf(true, false, false, false, false, false, false, false, true, false, false, false, false, false, false, false),
            LaunchpadPadType.PLUCK to listOf(false, false, true, false, false, true, false, false, false, false, true, false, false, true, false, false),
            LaunchpadPadType.TOMBAK to listOf(false, false, false, true, false, false, true, false, false, false, false, true, false, false, false, false)
        )
    }

    fun playPad(pad: LaunchpadPadType, rootMidiNote: Int = 57) {
        val pcmShorts = getOrSynthesizePad(pad, rootMidiNote)
        playShortArrayOneShot(pcmShorts)
    }

    private fun playShortArrayOneShot(samples: ShortArray) {
        if (samples.isEmpty()) return
        try {
            cleanupFinishedTracks()
            val bufBytes = samples.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            synchronized(activeTracks) {
                activeTracks.add(track)
            }
        } catch (_: Exception) {
        }
    }

    private fun cleanupFinishedTracks() {
        synchronized(activeTracks) {
            val it = activeTracks.iterator()
            while (it.hasNext()) {
                val tr = it.next()
                if (tr.playState == AudioTrack.PLAYSTATE_STOPPED || tr.playbackHeadPosition >= tr.bufferSizeInFrames - 64) {
                    try {
                        tr.stop()
                        tr.release()
                    } catch (_: Exception) {
                    }
                    it.remove()
                } else if (activeTracks.size > 10) {
                    try {
                        tr.stop()
                        tr.release()
                    } catch (_: Exception) {
                    }
                    it.remove()
                }
            }
        }
    }

    fun releaseAllTracks() {
        synchronized(activeTracks) {
            for (tr in activeTracks) {
                try {
                    tr.stop()
                    tr.release()
                } catch (_: Exception) {
                }
            }
            activeTracks.clear()
        }
    }

    suspend fun renderSequencerToWav(
        bpm: Int,
        grid: Map<LaunchpadPadType, List<Boolean>>,
        rootMidiNote: Int,
        barsCount: Int,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val totalSamples = (barsCount * 4 * (60.0 / bpm.coerceIn(60, 180)) * SAMPLE_RATE).toInt()
        val mix = synthesizeSequencerFloatArray(bpm, grid, rootMidiNote, totalSamples, SAMPLE_RATE)
        writeFloatToWav(mix, outputFile, SAMPLE_RATE)
        outputFile
    }

    suspend fun renderGenreBeatPreviewToWav(
        style: BackingBeatStyle,
        bpmOverride: Int?,
        rootMidiNote: Int,
        durationSeconds: Int,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val totalSamples = durationSeconds * SAMPLE_RATE
        val beat = synthesizeGenreBeat(style, totalSamples, SAMPLE_RATE, bpmOverride, rootMidiNote)
        writeFloatToWav(beat, outputFile, SAMPLE_RATE)
        outputFile
    }

    fun synthesizeSequencerFloatArray(
        bpm: Int,
        grid: Map<LaunchpadPadType, List<Boolean>>,
        rootMidiNote: Int,
        targetSamples: Int,
        sampleRate: Int = SAMPLE_RATE
    ): FloatArray {
        val safeBpm = bpm.coerceIn(60, 180)
        val stepSamples = ((60.0 / safeBpm / 4.0) * sampleRate).toInt().coerceAtLeast(1)
        val loopSamples = stepSamples * STEPS_COUNT
        val loop = FloatArray(loopSamples)

        for ((pad, steps) in grid) {
            val padShorts = getOrSynthesizePad(pad, rootMidiNote)
            for (step in 0 until STEPS_COUNT) {
                if (steps.getOrNull(step) == true) {
                    val startOffset = step * stepSamples
                    for (j in padShorts.indices) {
                        val pos = (startOffset + j) % loopSamples
                        loop[pos] += padShorts[j] / 32768f * 0.55f
                    }
                }
            }
        }

        // Normalize loop softly
        var peak = 0.01f
        for (s in loop) {
            val a = abs(s)
            if (a > peak) peak = a
        }
        val gain = (0.88f / peak).coerceAtMost(1.3f)

        return FloatArray(targetSamples) { i ->
            (loop[i % loopSamples] * gain).coerceIn(-0.95f, 0.95f)
        }
    }

    fun synthesizeGenreBeat(
        style: BackingBeatStyle,
        length: Int,
        sampleRate: Int = SAMPLE_RATE,
        bpmOverride: Int? = null,
        rootMidiNote: Int = 57
    ): FloatArray {
        if (style == BackingBeatStyle.NONE || length <= 0) return FloatArray(length)
        val effectiveBpm = (bpmOverride?.takeIf { it in 55..180 } ?: style.bpm.takeIf { it > 0 } ?: 100)
        val rootFreq = midiToFreq(rootMidiNote)
        val subFreq = rootFreq * 0.25

        // Build chord tones adapted cleanly to detected root frequency
        val chords = arrayOf(
            doubleArrayOf(rootFreq * 0.5, rootFreq * 0.75, rootFreq, rootFreq * 1.189),      // Minor/7th root
            doubleArrayOf(rootFreq * 0.445, rootFreq * 0.667, rootFreq * 0.891, rootFreq),    // bVII major
            doubleArrayOf(rootFreq * 0.397, rootFreq * 0.595, rootFreq * 0.794, rootFreq),    // bVI
            doubleArrayOf(rootFreq * 0.375, rootFreq * 0.561, rootFreq * 0.749, rootFreq * 0.891) // v / sus
        )

        val stepSeconds = when (style) {
            BackingBeatStyle.PERSIAN_6_8 -> 60.0 / effectiveBpm / 2.0
            else -> 60.0 / effectiveBpm / 4.0
        }
        val stepSamples = (stepSeconds * sampleRate).toInt().coerceAtLeast(1)
        val chordSamples = if (style == BackingBeatStyle.PERSIAN_6_8) stepSamples * 12 else stepSamples * 16

        val out = FloatArray(length)
        for (i in 0 until length) {
            val t = i.toDouble() / sampleRate
            val chord = chords[(i / chordSamples) % chords.size]
            val localChordPos = (i % chordSamples).toDouble() / sampleRate
            val chordEnv = (1.0 - exp(-localChordPos * 10.0)) * exp(-localChordPos * 0.45)

            var harmonicPad = 0.0
            for (f in chord) {
                harmonicPad += sin(2.0 * PI * f * t)
                if (style == BackingBeatStyle.HOUSE_EDM || style == BackingBeatStyle.TRAP_808) {
                    harmonicPad += 0.22 * sin(4.0 * PI * f * t)
                }
            }
            harmonicPad *= 0.068 * chordEnv

            val stepIndex = i / stepSamples
            val localStepSec = (i % stepSamples).toDouble() / sampleRate
            var perc = 0.0

            when (style) {
                BackingBeatStyle.PERSIAN_6_8 -> {
                    val b = stepIndex % 6
                    if (b == 0 || b == 3) {
                        val kFreq = 125.0 * exp(-localStepSec * 28.0) + 48.0
                        perc += sin(2.0 * PI * kFreq * localStepSec) * exp(-localStepSec * 14.0) * 0.46
                    }
                    if (b == 1 || b == 2 || b == 4 || b == 5) {
                        val tombakTone = sin(2.0 * PI * 380.0 * localStepSec) * exp(-localStepSec * 36.0)
                        val snap = sin(2.0 * PI * 1420.0 * localStepSec) * exp(-localStepSec * 58.0)
                        val weight = if (b == 2 || b == 5) 0.26 else 0.14
                        perc += (tombakTone * 0.7 + snap * 0.35) * weight
                    }
                }
                BackingBeatStyle.HIP_HOP_BOOM_BAP -> {
                    val b = stepIndex % 16
                    if (b == 0 || b == 7 || b == 10) {
                        val kFreq = 135.0 * exp(-localStepSec * 32.0) + 45.0
                        perc += sin(2.0 * PI * kFreq * localStepSec) * exp(-localStepSec * 13.0) * 0.52
                    }
                    if (b == 4 || b == 12) {
                        val snr = sin(2.0 * PI * 195.0 * localStepSec) + 0.65 * pseudoNoise(i) * exp(-localStepSec * 16.0)
                        perc += snr * exp(-localStepSec * 22.0) * 0.32
                    }
                    if (b % 2 == 0) {
                        perc += pseudoNoise(i * 3) * exp(-localStepSec * 75.0) * 0.12
                    }
                }
                BackingBeatStyle.TRAP_808 -> {
                    val b = stepIndex % 16
                    if (b == 0 || b == 6 || b == 10) {
                        val freq808 = subFreq + 95.0 * exp(-localStepSec * 38.0)
                        perc += sin(2.0 * PI * freq808 * localStepSec) * exp(-localStepSec * 5.5) * 0.55
                    }
                    if (b == 8) {
                        val clap = pseudoNoise(i) * exp(-localStepSec * 24.0) + sin(2.0 * PI * 240.0 * localStepSec) * exp(-localStepSec * 28.0)
                        perc += clap * 0.34
                    }
                    // Fast hi-hat rolls on steps 13, 14, 15
                    if (b % 2 == 0 || b >= 13) {
                        val hatT = if (b >= 13) localStepSec % (stepSeconds * 0.5) else localStepSec
                        perc += pseudoNoise(i * 7) * exp(-hatT * 95.0) * 0.14
                    }
                }
                BackingBeatStyle.UK_DRILL -> {
                    val b = stepIndex % 16
                    if (b == 0 || b == 6 || b == 11) {
                        val glide = subFreq * (1.0 + 0.55 * exp(-localStepSec * 8.0)) + 100.0 * exp(-localStepSec * 40.0)
                        perc += sin(2.0 * PI * glide * localStepSec) * exp(-localStepSec * 6.5) * 0.54
                    }
                    if (b == 4 || b == 13) {
                        perc += (pseudoNoise(i) * exp(-localStepSec * 25.0) + sin(2.0 * PI * 220.0 * localStepSec) * exp(-localStepSec * 30.0)) * 0.34
                    }
                    if (b % 3 == 0 || b % 2 == 0) {
                        perc += pseudoNoise(i * 5) * exp(-localStepSec * 90.0) * 0.13
                    }
                }
                BackingBeatStyle.POP_MODERN -> {
                    val b = stepIndex % 16
                    if (b == 0 || b == 8 || b == 10) {
                        val kFreq = 128.0 * exp(-localStepSec * 30.0) + 48.0
                        perc += sin(2.0 * PI * kFreq * localStepSec) * exp(-localStepSec * 14.0) * 0.48
                    }
                    if (b == 4 || b == 12) {
                        perc += (sin(2.0 * PI * 210.0 * localStepSec) + 0.45 * pseudoNoise(i)) * exp(-localStepSec * 26.0) * 0.28
                    }
                    if (b % 2 == 1) {
                        perc += sin(2.0 * PI * chord[2] * 2.0 * t) * exp(-localStepSec * 18.0) * 0.08
                    }
                }
                BackingBeatStyle.LOFI_CHILL -> {
                    val b = stepIndex % 16
                    if (b == 0 || b == 10) {
                        val softKick = 95.0 * exp(-localStepSec * 22.0) + 46.0
                        perc += sin(2.0 * PI * softKick * localStepSec) * exp(-localStepSec * 12.0) * 0.34
                    }
                    if (b == 4 || b == 12) {
                        perc += pseudoNoise(i) * exp(-localStepSec * 38.0) * 0.14
                    }
                    harmonicPad *= 1.35
                }
                BackingBeatStyle.RNB_SOUL -> {
                    val b = stepIndex % 16
                    if (b == 0 || b == 6 || b == 9) {
                        val deepK = 108.0 * exp(-localStepSec * 25.0) + subFreq
                        perc += sin(2.0 * PI * deepK * localStepSec) * exp(-localStepSec * 10.0) * 0.40
                    }
                    if (b == 4 || b == 12) {
                        perc += (sin(2.0 * PI * 320.0 * localStepSec) + 0.35 * pseudoNoise(i)) * exp(-localStepSec * 32.0) * 0.20
                    }
                    harmonicPad *= 1.45
                }
                BackingBeatStyle.HOUSE_EDM -> {
                    val b = stepIndex % 16
                    if (b % 4 == 0) {
                        val houseK = 145.0 * exp(-localStepSec * 34.0) + 50.0
                        perc += sin(2.0 * PI * houseK * localStepSec) * exp(-localStepSec * 15.0) * 0.52
                    }
                    if (b % 4 == 2) {
                        perc += pseudoNoise(i * 9) * exp(-localStepSec * 45.0) * 0.16
                    }
                    if (b % 8 == 4) {
                        perc += pseudoNoise(i) * exp(-localStepSec * 22.0) * 0.25
                    }
                    // Offbeat bass pump
                    if (b % 4 == 2) {
                        perc += sin(2.0 * PI * subFreq * 2.0 * t) * exp(-localStepSec * 12.0) * 0.26
                    }
                }
                BackingBeatStyle.ACOUSTIC_BALLAD -> {
                    val b = stepIndex % 16
                    val arpFreq = chord[b % chord.size] * 2.0
                    perc += sin(2.0 * PI * arpFreq * localStepSec) * exp(-localStepSec * 7.5) * 0.14
                    harmonicPad *= 1.4
                }
                else -> Unit
            }

            val fadeSamples = (sampleRate * 0.7).toInt().coerceAtMost(length / 4)
            val fade = if (i > length - fadeSamples && fadeSamples > 0) {
                (length - i).toFloat() / fadeSamples.toFloat()
            } else {
                1f
            }

            out[i] = ((harmonicPad + perc).toFloat() * fade).coerceIn(-0.92f, 0.92f)
        }
        return out
    }

    /**
     * Analyzes an acapella vocal performance to extract:
     * - Musical pitch root note & scale key (autocorrelation across voiced frames)
     * - Tempo BPM (short-time energy onset autocorrelation)
     * - Dynamics & genre recommendation
     */
    fun analyzeVocalAndAutoMatchBeat(
        vocalSamples: FloatArray,
        sampleRate: Int = SAMPLE_RATE
    ): AutoBeatMatchResult {
        if (vocalSamples.isEmpty()) {
            return AutoBeatMatchResult(
                recommendedStyle = BackingBeatStyle.POP_MODERN,
                detectedBpm = 100,
                detectedRootMidi = 57,
                detectedKeyNameFa = "لا مینور (Am)",
                vocalRms = 0.15f
            )
        }

        // 1. RMS & Peak Crest Factor
        var sumSq = 0.0
        var peak = 0.001f
        for (s in vocalSamples) {
            sumSq += s * s
            val a = abs(s)
            if (a > peak) peak = a
        }
        val rms = sqrt(sumSq / vocalSamples.size).toFloat()

        // 2. Estimate dominant MIDI pitch class via frame-based autocorrelation
        val frameSize = (sampleRate * 0.04).toInt().coerceAtLeast(512)
        val hop = frameSize
        val minLag = (sampleRate / 380).coerceAtLeast(20) // up to 380Hz vocal fundamental
        val maxLag = (sampleRate / 75).coerceAtMost(frameSize - 1) // down to 75Hz
        val pitchClassHistogram = DoubleArray(12)
        var voicedFrames = 0

        var start = 0
        while (start + frameSize < vocalSamples.size && voicedFrames < 180) {
            var frameE = 0.0
            for (k in 0 until frameSize) {
                val x = vocalSamples[start + k]
                frameE += x * x
            }
            val frameRms = sqrt(frameE / frameSize)
            if (frameRms > rms * 0.55 && frameRms > 0.015) {
                var bestLag = -1
                var bestCorr = 0.0
                for (lag in minLag..maxLag) {
                    var ac = 0.0
                    var normA = 0.0
                    var normB = 0.0
                    val limit = frameSize - lag
                    var k = 0
                    while (k < limit) {
                        val a = vocalSamples[start + k].toDouble()
                        val b = vocalSamples[start + k + lag].toDouble()
                        ac += a * b
                        normA += a * a
                        normB += b * b
                        k += 2
                    }
                    val denom = sqrt(normA * normB) + 1e-9
                    val r = ac / denom
                    if (r > bestCorr) {
                        bestCorr = r
                        bestLag = lag
                    }
                }
                if (bestLag > 0 && bestCorr > 0.52) {
                    val hz = sampleRate.toDouble() / bestLag
                    val midi = (69.0 + 12.0 * (ln(hz / 440.0) / ln(2.0))).roundToInt()
                    val pitchClass = ((midi % 12) + 12) % 12
                    pitchClassHistogram[pitchClass] += bestCorr
                    voicedFrames++
                }
            }
            start += hop
        }

        var dominantPc = 9 // Default A (57)
        var maxPcScore = -1.0
        for (pc in 0 until 12) {
            if (pitchClassHistogram[pc] > maxPcScore) {
                maxPcScore = pitchClassHistogram[pc]
                dominantPc = pc
            }
        }
        val detectedRootMidi = 48 + dominantPc
        val keyNamesFa = arrayOf(
            "دو مینور (Cm)", "دو دیز مینور (C#m)", "ر مینور (Dm)", "می‌بمل مینور (Ebm)",
            "می مینور (Em)", "فا مینور (Fm)", "فا دیز مینور (F#m)", "سل مینور (Gm)",
            "سل دیز مینور (G#m)", "لا مینور (Am)", "سی‌بمل مینور (Bbm)", "سی مینور (Bm)"
        )

        // 3. Estimate Vocal Tempo (BPM) from syllable energy onset curve
        val envHop = (sampleRate / 50).coerceAtLeast(1) // 50 Hz envelope frames (20ms)
        val envLen = (vocalSamples.size / envHop).coerceAtLeast(1)
        val env = DoubleArray(envLen) { idx ->
            var s = 0.0
            val base = idx * envHop
            for (j in 0 until envHop) {
                val sample = vocalSamples[base + j]
                s += abs(sample)
            }
            s / envHop
        }
        // Compute onset difference curve
        val onset = DoubleArray(envLen)
        for (i in 1 until envLen) {
            onset[i] = (env[i] - env[i - 1]).coerceAtLeast(0.0)
        }
        // Check lags corresponding to 72..140 BPM (at 50 Hz envelope rate)
        var bestBpm = 100
        var bestBeatScore = -1.0
        for (bpm in 72..140 step 2) {
            val beatLagFrames = (60.0 / bpm) * 50.0
            var score = 0.0
            val l1 = beatLagFrames.roundToInt()
            val l2 = (beatLagFrames * 2.0).roundToInt()
            for (i in 0 until envLen - l2) {
                score += onset[i] * (onset[i + l1] + 0.7 * onset[i + l2])
            }
            if (score > bestBeatScore) {
                bestBeatScore = score
                bestBpm = bpm
            }
        }

        // Compute transient activity density for genre selection
        var sharpPeaks = 0
        val avgOnset = if (onset.isNotEmpty()) onset.average() else 0.0
        for (v in onset) {
            if (v > avgOnset * 3.2) sharpPeaks++
        }
        val durationSec = vocalSamples.size.toDouble() / sampleRate
        val syllablesPerSec = if (durationSec > 0.2) sharpPeaks / durationSec else 1.5

        val recommendedStyle = when {
            syllablesPerSec > 4.2 && bestBpm >= 115 -> BackingBeatStyle.TRAP_808
            syllablesPerSec > 3.5 && bestBpm in 84..104 -> BackingBeatStyle.HIP_HOP_BOOM_BAP
            bestBpm in 105..124 && syllablesPerSec >= 2.2 -> BackingBeatStyle.PERSIAN_6_8
            bestBpm >= 120 -> BackingBeatStyle.HOUSE_EDM
            rms < 0.08f || bestBpm < 82 -> BackingBeatStyle.LOFI_CHILL
            syllablesPerSec < 2.2 -> BackingBeatStyle.RNB_SOUL
            else -> BackingBeatStyle.POP_MODERN
        }

        return AutoBeatMatchResult(
            recommendedStyle = recommendedStyle,
            detectedBpm = bestBpm,
            detectedRootMidi = detectedRootMidi,
            detectedKeyNameFa = keyNamesFa[dominantPc],
            vocalRms = rms
        )
    }

    private fun getOrSynthesizePad(pad: LaunchpadPadType, rootMidiNote: Int): ShortArray {
        val key = pad to rootMidiNote
        padCache[key]?.let { return it }

        val durationSec = when (pad) {
            LaunchpadPadType.KICK, LaunchpadPadType.TOMBAK -> 0.28
            LaunchpadPadType.SNARE, LaunchpadPadType.CLAP, LaunchpadPadType.DAF_SNAP -> 0.26
            LaunchpadPadType.HIHAT_CLOSED -> 0.11
            LaunchpadPadType.HIHAT_OPEN -> 0.36
            LaunchpadPadType.BASS_808 -> 0.55
            LaunchpadPadType.CHORD_1, LaunchpadPadType.CHORD_2 -> 0.65
            LaunchpadPadType.PLUCK -> 0.35
            LaunchpadPadType.RISER_FX -> 0.55
        }
        val n = (durationSec * SAMPLE_RATE).toInt()
        val rootFreq = midiToFreq(rootMidiNote)
        val subFreq = rootFreq * 0.25

        val arr = ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val v = when (pad) {
                LaunchpadPadType.KICK -> {
                    val f = 148.0 * exp(-t * 28.0) + 46.0
                    sin(2.0 * PI * f * t) * exp(-t * 11.0) * 0.92
                }
                LaunchpadPadType.SNARE -> {
                    val body = sin(2.0 * PI * 192.0 * t) * exp(-t * 18.0)
                    val snap = pseudoNoise(i) * exp(-t * 15.0)
                    (body * 0.55 + snap * 0.55) * 0.85
                }
                LaunchpadPadType.CLAP -> {
                    val burst = if (t < 0.04) abs(sin(2.0 * PI * 90.0 * t)) else 1.0
                    pseudoNoise(i * 3) * exp(-t * 14.0) * burst * 0.82
                }
                LaunchpadPadType.HIHAT_CLOSED -> {
                    pseudoNoise(i * 11) * exp(-t * 48.0) * 0.55
                }
                LaunchpadPadType.HIHAT_OPEN -> {
                    pseudoNoise(i * 13) * exp(-t * 10.5) * 0.55
                }
                LaunchpadPadType.BASS_808 -> {
                    val f = subFreq + 110.0 * exp(-t * 36.0)
                    val raw = sin(2.0 * PI * f * t) + 0.18 * sin(4.0 * PI * f * t)
                    raw * exp(-t * 3.8) * 0.88
                }
                LaunchpadPadType.TOMBAK -> {
                    val f = 260.0 * exp(-t * 18.0) + 115.0
                    sin(2.0 * PI * f * t) * exp(-t * 14.0) * 0.85
                }
                LaunchpadPadType.DAF_SNAP -> {
                    val ring = sin(2.0 * PI * 520.0 * t) * exp(-t * 16.0)
                    val jingle = pseudoNoise(i * 7) * exp(-t * 24.0)
                    (ring * 0.65 + jingle * 0.45) * 0.8
                }
                LaunchpadPadType.CHORD_1 -> {
                    val c = (sin(2.0 * PI * rootFreq * t) +
                        sin(2.0 * PI * (rootFreq * 1.189) * t) +
                        sin(2.0 * PI * (rootFreq * 1.498) * t)) * 0.28
                    c * (1.0 - exp(-t * 45.0)) * exp(-t * 3.5)
                }
                LaunchpadPadType.CHORD_2 -> {
                    val base = rootFreq * 0.891
                    val c = (sin(2.0 * PI * base * t) +
                        sin(2.0 * PI * (base * 1.26) * t) +
                        sin(2.0 * PI * (base * 1.498) * t)) * 0.28
                    c * (1.0 - exp(-t * 45.0)) * exp(-t * 3.5)
                }
                LaunchpadPadType.PLUCK -> {
                    val f = rootFreq * 2.0
                    (sin(2.0 * PI * f * t) + 0.3 * sin(4.0 * PI * f * t)) * exp(-t * 9.5) * 0.62
                }
                LaunchpadPadType.RISER_FX -> {
                    val f = 240.0 + t * 1400.0
                    (sin(2.0 * PI * f * t) * 0.4 + pseudoNoise(i * 5) * 0.25) * (t / durationSec) * 0.65
                }
            }
            (v.coerceIn(-0.98, 0.98) * 32767.0).toInt().toShort()
        }

        padCache[key] = arr
        return arr
    }

    private fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun pseudoNoise(seed: Int): Double {
        var x = seed * 1664525 + 1013904223
        x = (x shl 13) xor x
        return ((x and 0x7fffffff).toDouble() / 0x3fffffff.toDouble()) - 1.0
    }

    private fun writeFloatToWav(samples: FloatArray, outFile: File, sampleRate: Int) {
        val byteData = ByteArray(samples.size * 2)
        val buf = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            buf.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        val totalDataLen = byteData.size + 36
        val byteRate = sampleRate * 2
        FileOutputStream(outFile).use { out ->
            val header = ByteArrayOutputStream(44)
            header.write("RIFF".toByteArray())
            header.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalDataLen).array())
            header.write("WAVE".toByteArray())
            header.write("fmt ".toByteArray())
            header.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(16).array())
            header.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array())
            header.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array())
            header.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate).array())
            header.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate).array())
            header.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(2).array())
            header.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(16).array())
            header.write("data".toByteArray())
            header.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteData.size).array())
            out.write(header.toByteArray())
            out.write(byteData)
        }
    }
}
