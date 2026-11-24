package com.example.dubbing

import com.example.domain.VoiceCharacterArchetype
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class AcousticSpeechAnalysis(
    val isLikelySoundEffectOnly: Boolean,
    val sfxReason: String,
    val rms: Float,
    val crestFactor: Float,
    val zeroCrossingRate: Float,
    val lowBassRatio: Float,
    val speechPeriodicity: Float,
    val estimatedPitchHz: Float,
    val inferredArchetype: VoiceCharacterArchetype
)

/**
 * Acoustic DSP Classifier:
 * 1) Separates human spoken dialogue from explosions, gunshots, thunder, pure music, or microphone wind bursts
 *    BEFORE calling cloud APIs or speaking gibberish dubbing.
 * 2) Estimates vocal fundamental pitch F0 (Hz) via normalized autocorrelation to automatically identify
 *    adult male, adult female, young boy, or young girl voices.
 */
object SpeechVsSfxClassifier {

    fun analyzePcm16Mono(pcmBytes: ByteArray, sampleRate: Int = 16000): AcousticSpeechAnalysis {
        if (pcmBytes.size < sampleRate / 4) {
            return AcousticSpeechAnalysis(
                isLikelySoundEffectOnly = true,
                sfxReason = "قطعه بسیار کوتاه است",
                rms = 0f,
                crestFactor = 0f,
                zeroCrossingRate = 0f,
                lowBassRatio = 0f,
                speechPeriodicity = 0f,
                estimatedPitchHz = 0f,
                inferredArchetype = VoiceCharacterArchetype.ADULT_MALE
            )
        }
        val shortCount = pcmBytes.size / 2
        val samples = ShortArray(shortCount)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        return analyzeShortsMono(samples, sampleRate)
    }

    fun analyzeShortsMono(samples: ShortArray, sampleRate: Int = 16000): AcousticSpeechAnalysis {
        if (samples.size < 400) {
            return AcousticSpeechAnalysis(
                isLikelySoundEffectOnly = true,
                sfxReason = "قطعه صوتی خالی",
                rms = 0f,
                crestFactor = 0f,
                zeroCrossingRate = 0f,
                lowBassRatio = 0f,
                speechPeriodicity = 0f,
                estimatedPitchHz = 0f,
                inferredArchetype = VoiceCharacterArchetype.ADULT_MALE
            )
        }

        var sumSq = 0.0
        var peak = 0.0
        var zeroCrossings = 0
        var lowState = 0.0
        var lowEnergy = 0.0
        var speechBandPrev = 0.0
        var highDiffEnergy = 0.0
        val alphaBass = (2.0 * Math.PI * 165.0 / sampleRate).coerceIn(0.01, 0.5)

        // Compute syllabic frame envelope modulation (human speech fluctuates ~3-8 Hz vs flat siren/drone/noise)
        val frameSize = (sampleRate / 50).coerceAtLeast(160) // 20ms frames
        val frameRmsList = ArrayList<Double>(samples.size / frameSize + 1)
        var frameSumSq = 0.0
        var framePos = 0

        for (i in samples.indices) {
            val s = samples[i] / 32768.0
            val absS = abs(s)
            if (absS > peak) peak = absS
            sumSq += s * s

            if (i > 0 && ((samples[i - 1] < 0 && samples[i] >= 0) || (samples[i - 1] >= 0 && samples[i] < 0))) {
                zeroCrossings++
            }

            // Separate sub-bass rumble (<165Hz, heavy in explosions/thunder) vs upper energy
            lowState += alphaBass * (s - lowState)
            lowEnergy += lowState * lowState
            val diff = s - speechBandPrev
            highDiffEnergy += diff * diff
            speechBandPrev = s

            frameSumSq += s * s
            framePos++
            if (framePos >= frameSize) {
                frameRmsList.add(sqrt(frameSumSq / framePos))
                frameSumSq = 0.0
                framePos = 0
            }
        }

        val rms = sqrt(sumSq / samples.size).toFloat()
        val zcr = zeroCrossings.toFloat() / samples.size.coerceAtLeast(1)
        val crestFactor = if (rms > 1e-5f) (peak / rms).toFloat() else 0f
        val lowBassRatio = if (sumSq > 1e-8) (lowEnergy / sumSq).toFloat().coerceIn(0f, 1f) else 0f

        // Compute active voiced windows pitch & harmonic periodicity
        val (estimatedF0, periodicity) = estimatePitchAndPeriodicity(samples, sampleRate, frameSize)

        // Compute active-syllable variance vs sustained impulse
        val activeFrames = frameRmsList.count { it > rms * 0.45 }
        val activeRatio = if (frameRmsList.isNotEmpty()) activeFrames.toFloat() / frameRmsList.size else 0f

        val isTooQuiet = rms < 0.008f
        // Explosions / gunshots: massive crest spike on tiny active window OR overwhelming sub-bass boom with low harmonicity
        val isExplosionOrBang = (lowBassRatio > 0.74f && periodicity < 0.40f) ||
            (crestFactor > 9.5f && activeRatio < 0.22f && periodicity < 0.36f)
        // Pure noise / static / rushing water / applause / crowd roar
        val isPureNoise = zcr > 0.36f && periodicity < 0.28f
        // Pure continuous non-vocal drone or flat background hum
        val isFlatHum = periodicity < 0.21f && lowBassRatio > 0.62f

        val sfxReason = when {
            isTooQuiet -> "سکوت یا صدای بسیار ضعیف"
            isExplosionOrBang -> "صدای انفجار، شلیک یا کوبش محیطی (بدون گفتار)"
            isPureNoise -> "نویز محیطی یا افکت غیرگفتاری"
            isFlatHum -> "همهمه پس‌زمینه بدون دیالوگ"
            else -> ""
        }

        val archetype = inferArchetypeFromPitch(estimatedF0, lowBassRatio)

        return AcousticSpeechAnalysis(
            isLikelySoundEffectOnly = sfxReason.isNotEmpty(),
            sfxReason = sfxReason,
            rms = rms,
            crestFactor = crestFactor,
            zeroCrossingRate = zcr,
            lowBassRatio = lowBassRatio,
            speechPeriodicity = periodicity,
            estimatedPitchHz = estimatedF0,
            inferredArchetype = archetype
        )
    }

    private fun estimatePitchAndPeriodicity(
        samples: ShortArray,
        sampleRate: Int,
        frameSize: Int
    ): Pair<Float, Float> {
        val minLag = (sampleRate / 420).coerceAtLeast(16) // 420 Hz (young girl ceiling)
        val maxLag = (sampleRate / 72).coerceAtMost(samples.size / 3) // 72 Hz (deep male floor)
        if (maxLag <= minLag) return 145f to 0.3f

        val analyzeWindow = (sampleRate / 16).coerceAtLeast(512).coerceAtMost(samples.size / 2)
        val step = (analyzeWindow * 2).coerceAtLeast(frameSize)
        val f0Samples = ArrayList<Float>()
        var maxPeriodicity = 0f

        val dipStartLag = (minLag / 2).coerceAtLeast(8)
        var pos = 0
        while (pos + analyzeWindow + maxLag < samples.size && f0Samples.size < 16) {
            var winEnergy = 0.0
            for (i in 0 until analyzeWindow step 2) {
                val v = samples[pos + i].toDouble()
                winEnergy += v * v
            }
            if (winEnergy > 1e6) {
                val corrs = DoubleArray(maxLag + 1)
                var passedMainLobeDip = false
                var firstDipLag = maxLag + 1
                var bestCorr = 0.0
                for (lag in dipStartLag..maxLag) {
                    var num = 0.0
                    var denA = 0.0
                    var denB = 0.0
                    var i = 0
                    while (i < analyzeWindow) {
                        val a = samples[pos + i].toDouble()
                        val b = samples[pos + i + lag].toDouble()
                        num += a * b
                        denA += a * a
                        denB += b * b
                        i += 2
                    }
                    val den = sqrt(denA * denB)
                    val normCorr = if (den > 1.0) num / den else 0.0
                    corrs[lag] = normCorr
                    if (!passedMainLobeDip && normCorr < 0.25) {
                        passedMainLobeDip = true
                        firstDipLag = lag
                    }
                    if (passedMainLobeDip && lag >= minLag && normCorr > bestCorr) {
                        bestCorr = normCorr
                    }
                }
                if (bestCorr > maxPeriodicity) maxPeriodicity = bestCorr.toFloat()
                if (bestCorr > 0.42) {
                    // Pick first local peak >= 90% of global best correlation to avoid 2T/3T octave halving
                    val threshold = (bestCorr * 0.90).coerceAtLeast(0.42)
                    val searchStart = firstDipLag.coerceAtLeast(minLag)
                    var fundamentalLag = -1
                    for (lag in searchStart until maxLag) {
                        val c = corrs[lag]
                        if (c >= threshold && c >= corrs[lag - 1] && c >= corrs[lag + 1]) {
                            fundamentalLag = lag
                            break
                        }
                    }
                    if (fundamentalLag > 0) {
                        f0Samples.add(sampleRate.toFloat() / fundamentalLag.toFloat())
                    }
                }
            }
            pos += step
        }

        val medianF0 = if (f0Samples.isNotEmpty()) {
            val sorted = f0Samples.sorted()
            sorted[sorted.size / 2]
        } else {
            145f
        }
        return medianF0 to maxPeriodicity.coerceIn(0f, 1f)
    }

    private fun inferArchetypeFromPitch(f0Hz: Float, lowBassRatio: Float): VoiceCharacterArchetype = when {
        f0Hz >= 268f -> VoiceCharacterArchetype.YOUNG_GIRL
        f0Hz >= 198f -> VoiceCharacterArchetype.ADULT_FEMALE
        f0Hz >= 166f -> VoiceCharacterArchetype.YOUNG_BOY
        f0Hz >= 110f -> VoiceCharacterArchetype.ADULT_MALE
        f0Hz > 40f && lowBassRatio > 0.35f -> VoiceCharacterArchetype.ELDER_MALE
        else -> VoiceCharacterArchetype.ADULT_MALE
    }
}

/**
 * Persistent Character Voicebook & Dialogue Context Memory across a movie or live session.
 * Ensures pronouns, tone, and speaker identity stay consistent over long multi-hour dubs.
 */
class CharacterMemoryBook {

    data class CharacterSlot(
        val slotId: String,
        val displayLabel: String,
        val archetype: VoiceCharacterArchetype,
        var avgPitchHz: Float,
        var linesSpoken: Int
    )

    private val slots = mutableListOf<CharacterSlot>()
    private val recentLines = ArrayDeque<String>(6)

    @Synchronized
    fun reset() {
        slots.clear()
        recentLines.clear()
    }

    @Synchronized
    fun resolveCharacterSlot(
        aiArchetype: VoiceCharacterArchetype,
        acousticPitchHz: Float
    ): CharacterSlot {
        val safePitch = acousticPitchHz.coerceIn(70f, 400f)
        // Find matching character of same archetype whose pitch sits within ~34Hz
        val existing = slots.firstOrNull {
            it.archetype == aiArchetype && abs(it.avgPitchHz - safePitch) < 34f
        } ?: slots.firstOrNull {
            it.archetype == aiArchetype && slots.count { s -> s.archetype == aiArchetype } >= 2
        }

        if (existing != null) {
            existing.avgPitchHz = (existing.avgPitchHz * existing.linesSpoken + safePitch) / (existing.linesSpoken + 1)
            existing.linesSpoken += 1
            return existing
        }

        val num = slots.size + 1
        val created = CharacterSlot(
            slotId = "char_$num",
            displayLabel = "کاراکتر $num (${aiArchetype.titleFa})",
            archetype = aiArchetype,
            avgPitchHz = safePitch,
            linesSpoken = 1
        )
        slots += created
        return created
    }

    @Synchronized
    fun recordDialogueLine(speakerLabel: String, persianLine: String) {
        if (persianLine.isBlank()) return
        if (recentLines.size >= 5) recentLines.removeFirst()
        recentLines.addLast("$speakerLabel: $persianLine")
    }

    @Synchronized
    fun buildContextPromptHint(): String {
        if (recentLines.isEmpty()) return ""
        val castSummary = slots.take(6).joinToString("، ") { "${it.displayLabel} (~${it.avgPitchHz.roundToInt()}Hz)" }
        return buildString {
            if (castSummary.isNotBlank()) {
                appendLine("کاراکترهای شناخته‌شده فیلم تا این لحظه: $castSummary")
            }
            appendLine("چند دیالوگ قبلی برای حفظ پیوستگی لحن و ضمیرها:")
            recentLines.forEach { appendLine("- $it") }
        }.trim()
    }
}
