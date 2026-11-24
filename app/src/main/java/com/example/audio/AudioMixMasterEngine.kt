package com.example.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

enum class AutoTuneScale(
    val id: String,
    val titleFa: String,
    val pitchClasses: IntArray
) {
    CHROMATIC("chromatic", "کروماتیک (همه نت‌ها)", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
    MINOR("minor", "مینور احساسی / ایرانی", intArrayOf(0, 2, 3, 5, 7, 8, 10)),
    MAJOR("major", "ماژور شاد", intArrayOf(0, 2, 4, 5, 7, 9, 11))
}

enum class DspEffectPreset(
    val id: String,
    val titleFa: String
) {
    NONE("none", "استودیو شفاف"),
    HALL_REVERB("hall", "سالن کنسرت (Hall)"),
    ECHO_DELAY("echo", "اکو و دیلی ریتمیک"),
    TUBE_WARMTH("tube", "گرمای لامپ استودیو"),
    RADIO_VINTAGE("radio", "رادیو قدیمی / Lo-Fi"),
    TELEPHONE("telephone", "افکت تلفن سینمایی"),
    MEGAPHONE("megaphone", "بلندگو / مگافون")
}

data class MixMasterConfig(
    val vocalGain: Float = 1.15f,
    val beatGain: Float = 0.50f,
    val reverbAmount: Float = 0.28f,
    val bassBoostDb: Float = 2.0f,
    val presenceBoostDb: Float = 3.0f,
    val airTrebleDb: Float = 2.5f,
    val compressionAmount: Float = 0.60f,
    val vocalSpeed: Float = 1.0f,
    val vocalPitchSemitones: Float = 0f,
    val beatSpeed: Float = 1.0f,
    val beatPitchSemitones: Float = 0f,
    val enableAutoTune: Boolean = false,
    val autoTuneAmount: Float = 0.65f,
    val autoTuneScale: AutoTuneScale = AutoTuneScale.MINOR,
    val enableVocalDoubler: Boolean = false,
    val harmonyAmount: Float = 0.30f,
    val deEsserAmount: Float = 0.35f,
    val echoAmount: Float = 0.15f,
    val effectPreset: DspEffectPreset = DspEffectPreset.NONE,
    val autoAlignAndDuck: Boolean = true,
    val beatStyle: BackingBeatStyle = BackingBeatStyle.PERSIAN_6_8,
    val detectedBpmOverride: Int? = null,
    val detectedRootMidi: Int = 57,
    val launchpadBpm: Int = 108,
    val launchpadGrid: Map<LaunchpadPadType, List<Boolean>> = emptyMap(),
    val customBeatFilePath: String? = null
)

class AudioMixMasterEngine(
    private val launchpadEngine: BeatLaunchpadEngine = BeatLaunchpadEngine()
) {

    companion object {
        const val SAMPLE_RATE = 44100

        /**
         * Scale-locked vocal pitch correction (Auto-Tune) using frame autocorrelation + overlap-add resampling.
         */
        fun applyAutoTunePitchCorrection(
            samples: FloatArray,
            sampleRate: Int,
            scale: AutoTuneScale,
            rootMidi: Int,
            amount: Float
        ): FloatArray {
            if (samples.size < 2048 || amount <= 0.02f) return samples
            val strength = amount.coerceIn(0f, 1f)
            val winSize = 1536
            val hop = winSize / 2
            val hann = FloatArray(winSize) { i ->
                (0.5 * (1.0 - cos(2.0 * PI * i / (winSize - 1)))).toFloat()
            }
            val out = FloatArray(samples.size)
            val norm = FloatArray(samples.size)

            val minLag = (sampleRate / 480).coerceAtLeast(24)
            val maxLag = (sampleRate / 78).coerceAtMost(winSize / 2)
            val rootPc = ((rootMidi % 12) + 12) % 12

            var pos = 0
            while (pos + winSize <= samples.size) {
                var energy = 0.0
                for (i in 0 until winSize) {
                    val s = samples[pos + i]
                    energy += s * s
                }
                val rms = sqrt(energy / winSize).toFloat()

                var pitchRatio = 1.0f
                if (rms > 0.012f && maxLag > minLag + 4) {
                    var bestLag = -1
                    var bestCorr = 0.0
                    var r0 = 1e-9
                    for (i in 0 until winSize - maxLag step 2) {
                        val v = samples[pos + i]
                        r0 += v * v
                    }
                    for (lag in minLag..maxLag step 2) {
                        var corr = 0.0
                        for (i in 0 until winSize - lag step 2) {
                            corr += samples[pos + i] * samples[pos + i + lag]
                        }
                        val normCorr = corr / r0
                        if (normCorr > bestCorr) {
                            bestCorr = normCorr
                            bestLag = lag
                        }
                    }

                    if (bestLag > 0 && bestCorr > 0.42) {
                        val f0 = sampleRate.toDouble() / bestLag.toDouble()
                        val detectedMidi = 69.0 + 12.0 * (ln(f0 / 440.0) / ln(2.0))
                        val nearestTargetMidi = findNearestScaleMidi(detectedMidi, rootPc, scale)
                        val deltaSemi = ((nearestTargetMidi - detectedMidi) * strength).coerceIn(-2.0, 2.0)
                        pitchRatio = 2.0.pow(deltaSemi / 12.0).toFloat()
                    }
                }

                for (k in 0 until winSize) {
                    val w = hann[k]
                    val srcOffset = k * pitchRatio.toDouble()
                    val i0 = srcOffset.toInt().coerceIn(0, winSize - 1)
                    val i1 = (i0 + 1).coerceIn(0, winSize - 1)
                    val frac = (srcOffset - i0).toFloat()
                    val interp = samples[pos + i0] * (1f - frac) + samples[pos + i1] * frac
                    out[pos + k] += interp * w
                    norm[pos + k] += w
                }
                pos += hop
            }

            return FloatArray(samples.size) { i ->
                if (norm[i] > 1e-4f) (out[i] / norm[i]).coerceIn(-1f, 1f) else samples[i]
            }
        }

        private fun findNearestScaleMidi(detectedMidi: Double, rootPc: Int, scale: AutoTuneScale): Double {
            val baseOctave = (detectedMidi.roundToInt() / 12) * 12
            var bestMidi = detectedMidi.roundToInt().toDouble()
            var bestDist = Double.MAX_VALUE
            for (octOffset in -12..12 step 12) {
                for (pc in scale.pitchClasses) {
                    val candidate = (baseOctave + octOffset + ((rootPc + pc) % 12)).toDouble()
                    val d = abs(candidate - detectedMidi)
                    if (d < bestDist) {
                        bestDist = d
                        bestMidi = candidate
                    }
                }
            }
            return bestMidi
        }

        /**
         * Dynamic High-Frequency De-Esser (~5.5kHz+) to soften harsh 'S'/'Sh' bursts cleanly.
         */
        fun applyDeEsser(samples: FloatArray, sampleRate: Int, amount: Float): FloatArray {
            if (samples.isEmpty() || amount <= 0.02f) return samples
            val strength = amount.coerceIn(0f, 1f)
            val rc = 1.0 / (2.0 * PI * 5200.0)
            val dt = 1.0 / sampleRate
            val alpha = (rc / (rc + dt)).toFloat()

            var prevX = 0f
            var prevHp = 0f
            var sibEnv = 0f
            val threshold = 0.08f - strength * 0.045f
            val out = FloatArray(samples.size)

            for (i in samples.indices) {
                val x = samples[i]
                val hp = alpha * (prevHp + x - prevX)
                prevX = x
                prevHp = hp

                val absHp = abs(hp)
                sibEnv = if (absHp > sibEnv) {
                    sibEnv + 0.045f * (absHp - sibEnv)
                } else {
                    sibEnv + 0.002f * (absHp - sibEnv)
                }

                val cutRatio = if (sibEnv > threshold) {
                    val excess = (sibEnv - threshold) / sibEnv
                    (excess * strength * 0.78f).coerceIn(0f, 0.75f)
                } else {
                    0f
                }
                out[i] = x - hp * cutRatio
            }
            return out
        }

        /**
         * Studio Vocal Doubler + Optional Soft Harmony Shimmer Layer.
         */
        fun applyVocalDoublerAndHarmony(
            samples: FloatArray,
            sampleRate: Int,
            enableDoubler: Boolean,
            harmonyAmount: Float
        ): FloatArray {
            if (samples.isEmpty() || (!enableDoubler && harmonyAmount <= 0.03f)) return samples
            val harmMix = harmonyAmount.coerceIn(0f, 1f)
            val out = samples.copyOf()

            if (enableDoubler) {
                val baseDelay = (sampleRate * 0.017).toInt()
                val modDepth = (sampleRate * 0.004).toInt().coerceAtLeast(8)
                for (i in baseDelay + modDepth until samples.size) {
                    val lfo = sin(2.0 * PI * 1.4 * i / sampleRate)
                    val idx = (i - baseDelay - (lfo * modDepth).toInt()).coerceIn(0, samples.lastIndex)
                    out[i] = (out[i] + samples[idx] * 0.34f).coerceIn(-1f, 1f)
                }
            }

            if (harmMix > 0.03f) {
                val harmonyUp = WsolaTimeStretch.timeScaleAndPitchShift(
                    samples = samples,
                    speed = 1.0f,
                    semitones = 7f // Fifth harmony interval (+7 semitones)
                )
                val limit = minOf(out.size, harmonyUp.size)
                val delayOff = (sampleRate * 0.011).toInt()
                val gain = harmMix * 0.28f
                for (i in delayOff until limit) {
                    out[i] = (out[i] + harmonyUp[i - delayOff] * gain).coerceIn(-1f, 1f)
                }
            }
            return out
        }

        fun applyEchoDelay(
            samples: FloatArray,
            sampleRate: Int,
            echoAmount: Float,
            bpm: Int?
        ): FloatArray {
            if (samples.isEmpty() || echoAmount <= 0.02f) return samples
            val amount = echoAmount.coerceIn(0f, 1f)
            val quarterMs = if (bpm != null && bpm in 60..180) (60_000f / bpm) else 460f
            val tap1 = (sampleRate * (quarterMs / 2000.0)).toInt().coerceIn(2200, sampleRate)
            val tap2 = (tap1 * 2).coerceIn(tap1 + 1000, sampleRate * 2)
            val out = samples.copyOf()
            val g1 = amount * 0.36f
            val g2 = amount * 0.19f
            for (i in samples.indices) {
                var e = 0f
                if (i >= tap1) e += out[i - tap1] * g1 * 0.55f + samples[i - tap1] * g1 * 0.45f
                if (i >= tap2) e += samples[i - tap2] * g2
                out[i] = (out[i] + e).coerceIn(-1f, 1f)
            }
            return out
        }

        fun applyStudioEffectPreset(
            samples: FloatArray,
            sampleRate: Int,
            preset: DspEffectPreset
        ): FloatArray {
            if (samples.isEmpty() || preset == DspEffectPreset.NONE) return samples
            return when (preset) {
                DspEffectPreset.NONE -> samples
                DspEffectPreset.HALL_REVERB -> {
                    val out = samples.copyOf()
                    val delays = intArrayOf(2205, 3721, 5683, 8111, 11317)
                    val decays = floatArrayOf(0.42f, 0.34f, 0.26f, 0.19f, 0.12f)
                    for (idx in delays.indices) {
                        val d = delays[idx]
                        val dec = decays[idx]
                        for (i in d until samples.size) {
                            out[i] = (out[i] + samples[i - d] * dec).coerceIn(-1f, 1f)
                        }
                    }
                    out
                }
                DspEffectPreset.ECHO_DELAY -> applyEchoDelay(samples, sampleRate, 0.68f, 100)
                DspEffectPreset.TUBE_WARMTH -> FloatArray(samples.size) { i ->
                    val driven = samples[i] * 1.45f
                    (tanh((driven + 0.06f * driven * driven).toDouble()) / 1.05).toFloat().coerceIn(-0.98f, 0.98f)
                }
                DspEffectPreset.RADIO_VINTAGE -> applyBandPassAndDrive(samples, sampleRate, 380.0, 2900.0, drive = 1.6f)
                DspEffectPreset.TELEPHONE -> applyBandPassAndDrive(samples, sampleRate, 480.0, 2350.0, drive = 1.35f)
                DspEffectPreset.MEGAPHONE -> applyBandPassAndDrive(samples, sampleRate, 560.0, 3400.0, drive = 2.4f)
            }
        }

        private fun applyBandPassAndDrive(
            samples: FloatArray,
            sampleRate: Int,
            lowHz: Double,
            highHz: Double,
            drive: Float
        ): FloatArray {
            val rcHp = 1.0 / (2.0 * PI * lowHz)
            val dt = 1.0 / sampleRate
            val alphaHp = (rcHp / (rcHp + dt)).toFloat()
            val alphaLp = (dt / ((1.0 / (2.0 * PI * highHz)) + dt)).toFloat()

            var prevX = 0f
            var hp = 0f
            var lp = 0f
            return FloatArray(samples.size) { i ->
                val x = samples[i]
                hp = alphaHp * (hp + x - prevX)
                prevX = x
                lp += alphaLp * (hp - lp)
                tanh((lp * drive).toDouble()).toFloat().coerceIn(-0.96f, 0.96f)
            }
        }
    }

    suspend fun analyzeVocalForAutoBeat(vocalFile: File): AutoBeatMatchResult = withContext(Dispatchers.IO) {
        val raw = decodeAudioToMonoFloat(vocalFile, SAMPLE_RATE)
        launchpadEngine.analyzeVocalAndAutoMatchBeat(raw, SAMPLE_RATE)
    }

    suspend fun mixAndMaster(
        vocalFile: File,
        outputFile: File,
        config: MixMasterConfig
    ): File = withContext(Dispatchers.IO) {
        val rawVocal = decodeAudioToMonoFloat(vocalFile, SAMPLE_RATE)
        if (rawVocal.isEmpty()) {
            throw IllegalStateException("فایل صدای خوانندگی قابل رمزگشایی نبود.")
        }

        // 1. Clean low-end mud (< 90Hz) & trim leading silence if Auto-Align is active
        val cleanedVocal = applyHighPass90Hz(
            if (config.autoAlignAndDuck) trimLeadingSilence(rawVocal, SAMPLE_RATE) else rawVocal,
            SAMPLE_RATE
        )

        // 2. De-Esser to tame harsh 'S'/'Sh' before boost/compression
        val deEssed = applyDeEsser(cleanedVocal, SAMPLE_RATE, config.deEsserAmount)

        // 3. Scale-Locked Auto-Tune pitch quantization
        val autoTuned = if (config.enableAutoTune) {
            applyAutoTunePitchCorrection(
                samples = deEssed,
                sampleRate = SAMPLE_RATE,
                scale = config.autoTuneScale,
                rootMidi = config.detectedRootMidi,
                amount = config.autoTuneAmount
            )
        } else {
            deEssed
        }

        // 4. Independent Speed & Pitch Shift via shared WSOLA
        val pitchedVocal = WsolaTimeStretch.timeScaleAndPitchShift(
            samples = autoTuned,
            speed = config.vocalSpeed.coerceIn(0.70f, 1.35f),
            semitones = config.vocalPitchSemitones.coerceIn(-7f, 7f)
        )

        // 5. Dual Vocal Doubler + Harmony Layer
        val doubledVocal = applyVocalDoublerAndHarmony(
            samples = pitchedVocal,
            sampleRate = SAMPLE_RATE,
            enableDoubler = config.enableVocalDoubler,
            harmonyAmount = if (config.enableVocalDoubler) config.harmonyAmount else 0f
        )

        // 6. Studio Vocal Reverb, Rhythmic Echo Delay & Vocal Rack FX Preset
        val withReverb = if (config.reverbAmount > 0.01f) {
            applyStudioReverb(doubledVocal, config.reverbAmount)
        } else {
            doubledVocal
        }
        val withEcho = applyEchoDelay(
            samples = withReverb,
            sampleRate = SAMPLE_RATE,
            echoAmount = config.echoAmount,
            bpm = config.detectedBpmOverride ?: config.launchpadBpm
        )
        val fxVocal = applyStudioEffectPreset(withEcho, SAMPLE_RATE, config.effectPreset)

        val vocalCalibrated = if (config.autoAlignAndDuck) {
            normalizeRmsToTarget(fxVocal, targetRms = 0.24f)
        } else {
            fxVocal
        }

        // 7. Generate or load backing beat with independent Speed & Pitch Shift
        val rawBacking: FloatArray = when (config.beatStyle) {
            BackingBeatStyle.NONE -> FloatArray(vocalCalibrated.size)
            BackingBeatStyle.CUSTOM_FILE -> {
                val beatPath = config.customBeatFilePath
                if (beatPath != null && File(beatPath).exists()) {
                    decodeAudioToMonoFloat(File(beatPath), SAMPLE_RATE)
                } else {
                    FloatArray(vocalCalibrated.size)
                }
            }
            BackingBeatStyle.CUSTOM_LAUNCHPAD -> {
                val grid = config.launchpadGrid.ifEmpty { launchpadEngine.defaultSequencerGrid() }
                val preLen = (vocalCalibrated.size * config.beatSpeed.coerceAtLeast(0.7f)).toInt()
                    .coerceAtLeast(SAMPLE_RATE * 2)
                launchpadEngine.synthesizeSequencerFloatArray(
                    bpm = config.launchpadBpm,
                    grid = grid,
                    rootMidiNote = config.detectedRootMidi,
                    targetSamples = preLen,
                    sampleRate = SAMPLE_RATE
                )
            }
            else -> {
                val preLen = (vocalCalibrated.size * config.beatSpeed.coerceAtLeast(0.7f)).toInt()
                    .coerceAtLeast(SAMPLE_RATE * 2)
                launchpadEngine.synthesizeGenreBeat(
                    style = config.beatStyle,
                    length = preLen,
                    sampleRate = SAMPLE_RATE,
                    bpmOverride = config.detectedBpmOverride,
                    rootMidiNote = config.detectedRootMidi
                )
            }
        }

        val tunedBeat = if (config.beatStyle != BackingBeatStyle.NONE && rawBacking.isNotEmpty()) {
            val shiftedBeat = WsolaTimeStretch.timeScaleAndPitchShift(
                samples = rawBacking,
                speed = config.beatSpeed.coerceIn(0.70f, 1.35f),
                semitones = config.beatPitchSemitones.coerceIn(-7f, 7f)
            )
            fitToLength(shiftedBeat, vocalCalibrated.size)
        } else {
            FloatArray(vocalCalibrated.size)
        }

        val beatCalibrated = if (config.autoAlignAndDuck && config.beatStyle != BackingBeatStyle.NONE) {
            normalizeRmsToTarget(tunedBeat, targetRms = 0.17f)
        } else {
            tunedBeat
        }

        // 8. Engineered Mix: Dynamic Sidechain Ducking + Vocal/Beat Balance
        val mixed = if (config.autoAlignAndDuck && config.beatStyle != BackingBeatStyle.NONE) {
            mixWithSidechainDucking(
                vocal = vocalCalibrated,
                beat = beatCalibrated,
                vocalGain = config.vocalGain,
                beatGain = config.beatGain
            )
        } else {
            FloatArray(vocalCalibrated.size) { i ->
                (vocalCalibrated[i] * config.vocalGain) + (beatCalibrated[i] * config.beatGain)
            }
        }

        // 9. Mastering chain: 3-Band EQ -> Soft-Knee Compressor -> Peak Limiter
        val eqProcessed = applyThreeBandEq(
            samples = mixed,
            bassDb = config.bassBoostDb,
            midDb = config.presenceBoostDb,
            trebleDb = config.airTrebleDb,
            sampleRate = SAMPLE_RATE
        )
        val mastered = applyCompressorAndLimiter(eqProcessed, config.compressionAmount)

        writeFloatSamplesToWav(mastered, outputFile, SAMPLE_RATE)
        outputFile
    }

    private fun trimLeadingSilence(samples: FloatArray, sampleRate: Int): FloatArray {
        val threshold = 0.014f
        var firstIdx = 0
        while (firstIdx < samples.size && abs(samples[firstIdx]) < threshold) {
            firstIdx++
        }
        val leadMargin = (sampleRate * 0.06).toInt()
        val start = (firstIdx - leadMargin).coerceAtLeast(0)
        return if (start > sampleRate / 10 && start < samples.size - sampleRate / 2) {
            samples.copyOfRange(start, samples.size)
        } else {
            samples
        }
    }

    private fun applyHighPass90Hz(samples: FloatArray, sampleRate: Int): FloatArray {
        val rc = 1.0 / (2.0 * PI * 90.0)
        val dt = 1.0 / sampleRate
        val alpha = (rc / (rc + dt)).toFloat()
        val out = FloatArray(samples.size)
        var prevX = 0f
        var prevY = 0f
        for (i in samples.indices) {
            val y = alpha * (prevY + samples[i] - prevX)
            out[i] = y
            prevX = samples[i]
            prevY = y
        }
        return out
    }

    private fun normalizeRmsToTarget(samples: FloatArray, targetRms: Float): FloatArray {
        var sumSq = 0.0
        for (s in samples) sumSq += s * s
        val rms = sqrt(sumSq / samples.size.coerceAtLeast(1)).toFloat()
        if (rms < 1e-5f) return samples
        val gain = (targetRms / rms).coerceIn(0.35f, 3.5f)
        return FloatArray(samples.size) { i -> (samples[i] * gain).coerceIn(-0.98f, 0.98f) }
    }

    private fun mixWithSidechainDucking(
        vocal: FloatArray,
        beat: FloatArray,
        vocalGain: Float,
        beatGain: Float
    ): FloatArray {
        val out = FloatArray(vocal.size)
        var env = 0f
        val attack = 0.012f
        val release = 0.00045f

        for (i in vocal.indices) {
            val v = vocal[i] * vocalGain
            val absV = abs(v)
            env = if (absV > env) env + attack * (absV - env) else env + release * (absV - env)
            val duckFactor = (1.0f - (env * 1.4f).coerceIn(0f, 0.45f))
            val b = beat[i] * beatGain * duckFactor
            out[i] = v + b
        }
        return out
    }

    private fun decodeAudioToMonoFloat(file: File, targetRate: Int): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            return FloatArray(0)
        }

        extractor.selectTrack(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sourceRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
        } else {
            targetRate
        }
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        } else {
            1
        }

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmShorts = ArrayList<Short>(sourceRate * 10)
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        try {
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inputIdx = codec.dequeueInputBuffer(10_000)
                    if (inputIdx >= 0) {
                        val inputBuf = codec.getInputBuffer(inputIdx)!!
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIdx,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inputIdx,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    val chunk = if (bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        ByteArray(bufferInfo.size).also { outBuf.get(it) }
                    } else {
                        ByteArray(0)
                    }
                    outBuf.clear()
                    codec.releaseOutputBuffer(outIdx, false)

                    val shortBuf = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val frameShorts = ShortArray(shortBuf.remaining())
                    shortBuf.get(frameShorts)

                    if (channels <= 1) {
                        for (s in frameShorts) pcmShorts.add(s)
                    } else {
                        var idx = 0
                        while (idx + channels - 1 < frameShorts.size) {
                            var sum = 0
                            for (c in 0 until channels) sum += frameShorts[idx + c]
                            pcmShorts.add((sum / channels).toShort())
                            idx += channels
                        }
                    }

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEos = true
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        if (pcmShorts.isEmpty()) return FloatArray(0)

        val monoFloat = FloatArray(pcmShorts.size) { i -> pcmShorts[i] / 32768f }
        if (sourceRate == targetRate) return monoFloat

        val ratio = sourceRate.toDouble() / targetRate.toDouble()
        val outLength = (monoFloat.size / ratio).toInt().coerceAtLeast(1)
        return FloatArray(outLength) { i ->
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, monoFloat.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, monoFloat.lastIndex)
            val frac = (srcPos - idx0).toFloat()
            monoFloat[idx0] * (1f - frac) + monoFloat[idx1] * frac
        }
    }

    private fun fitToLength(source: FloatArray, targetLength: Int): FloatArray {
        if (source.isEmpty()) return FloatArray(targetLength)
        return FloatArray(targetLength) { i -> source[i % source.size] }
    }

    private fun applyStudioReverb(input: FloatArray, amount: Float): FloatArray {
        val output = input.copyOf()
        val delays = intArrayOf(1323, 2117, 3491, 5113)
        val decays = floatArrayOf(0.45f, 0.36f, 0.27f, 0.18f)
        val wetScale = amount.coerceIn(0f, 1f) * 0.55f

        for (dIdx in delays.indices) {
            val d = delays[dIdx]
            val decay = decays[dIdx] * wetScale
            for (i in d until input.size) {
                output[i] += input[i - d] * decay
            }
        }
        return output
    }

    private fun applyThreeBandEq(
        samples: FloatArray,
        bassDb: Float,
        midDb: Float,
        trebleDb: Float,
        sampleRate: Int
    ): FloatArray {
        val bassGain = dbToLinear(bassDb)
        val midGain = dbToLinear(midDb)
        val trebleGain = dbToLinear(trebleDb)

        val alphaLow = (2.0 * PI * 220.0 / sampleRate).toFloat().coerceIn(0.001f, 0.99f)
        val alphaHigh = (2.0 * PI * 3200.0 / sampleRate).toFloat().coerceIn(0.001f, 0.99f)

        var lowState = 0f
        var highBaseState = 0f

        return FloatArray(samples.size) { i ->
            val x = samples[i]
            lowState += alphaLow * (x - lowState)
            highBaseState += alphaHigh * (x - highBaseState)

            val lowBand = lowState
            val highBand = x - highBaseState
            val midBand = x - lowBand - highBand

            (lowBand * bassGain) + (midBand * midGain) + (highBand * trebleGain)
        }
    }

    private fun applyCompressorAndLimiter(
        samples: FloatArray,
        compressionAmount: Float
    ): FloatArray {
        val ratio = 1f + compressionAmount.coerceIn(0f, 1f) * 3.5f
        val threshold = 0.28f
        var env = 0f
        val attack = 0.015f
        val release = 0.0015f

        val compressed = FloatArray(samples.size)
        var peak = 0.001f
        for (i in samples.indices) {
            val x = samples[i]
            val absX = abs(x)
            env = if (absX > env) env + attack * (absX - env) else env + release * (absX - env)

            val gain = if (env > threshold) {
                val excess = env - threshold
                (threshold + excess / ratio) / env
            } else {
                1f
            }
            val y = tanh((x * gain * 1.35f).toDouble()).toFloat()
            compressed[i] = y
            if (abs(y) > peak) peak = abs(y)
        }

        val normScale = (0.94f / peak).coerceAtMost(2.5f)
        return FloatArray(compressed.size) { i ->
            (compressed[i] * normScale).coerceIn(-0.98f, 0.98f)
        }
    }

    private fun dbToLinear(db: Float): Float =
        10.0.pow(db.toDouble() / 20.0).toFloat()

    private fun writeFloatSamplesToWav(samples: FloatArray, outFile: File, sampleRate: Int) {
        val byteData = ByteArray(samples.size * 2)
        val buf = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            val pcm = (s.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            buf.putShort(pcm)
        }

        val totalDataLen = byteData.size + 36
        val byteRate = sampleRate * 2

        FileOutputStream(outFile).use { out ->
            val header = ByteArrayOutputStream(44)
            header.write("RIFF".toByteArray())
            header.write(intLe(totalDataLen))
            header.write("WAVE".toByteArray())
            header.write("fmt ".toByteArray())
            header.write(intLe(16))
            header.write(shortLe(1))
            header.write(shortLe(1))
            header.write(intLe(sampleRate))
            header.write(intLe(byteRate))
            header.write(shortLe(2))
            header.write(shortLe(16))
            header.write("data".toByteArray())
            header.write(intLe(byteData.size))
            out.write(header.toByteArray())
            out.write(byteData)
        }
    }

    private fun intLe(v: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    private fun shortLe(v: Short): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array()
}
