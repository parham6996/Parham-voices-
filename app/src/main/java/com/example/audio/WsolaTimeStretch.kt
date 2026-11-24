package com.example.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow

/**
 * Shared WSOLA (Waveform Similarity Overlap-Add) Time-Scale Modification and Pitch-Shift DSP.
 * Enables independent vocal/beat speed & pitch control in MixMaster AND natural lip-sync fitting
 * in movie dubbing without chipmunk distortion.
 */
object WsolaTimeStretch {

    fun timeScaleAndPitchShift(
        samples: FloatArray,
        speed: Float,
        semitones: Float
    ): FloatArray {
        if (samples.isEmpty()) return samples
        val pitchRatio = 2.0.pow(semitones.toDouble() / 12.0).toFloat()
        val nearUnitySpeed = abs(speed - 1.0f) < 0.015f
        val nearZeroPitch = abs(semitones) < 0.1f
        if (nearUnitySpeed && nearZeroPitch) return samples

        val wsolaSpeedFactor = (speed / pitchRatio).coerceIn(0.45f, 2.2f)
        val stretched = if (abs(wsolaSpeedFactor - 1.0f) > 0.015f) {
            wsolaTimeStretch(samples, rate = wsolaSpeedFactor)
        } else {
            samples
        }

        if (nearZeroPitch) return stretched

        val outLen = (stretched.size / pitchRatio).toInt().coerceAtLeast(1)
        return FloatArray(outLen) { i ->
            val srcPos = i * pitchRatio.toDouble()
            val idx0 = srcPos.toInt().coerceIn(0, stretched.lastIndex)
            val idx1 = (idx0 + 1).coerceIn(0, stretched.lastIndex)
            val frac = (srcPos - idx0).toFloat()
            stretched[idx0] * (1f - frac) + stretched[idx1] * frac
        }
    }

    /**
     * Fits synthesized dubbed speech PCM into target window sample length while preserving natural timbre.
     * Gently time-compresses up to 1.28x if dubbed speech slightly overflows window so dialogue won't step on explosions or next actor.
     */
    fun fitDubbedSpeechToWindowPcm16(
        pcm: ShortArray,
        targetWindowSamples: Int,
        minSpeedRate: Float = 0.88f,
        maxSpeedRate: Float = 1.28f
    ): ShortArray {
        if (pcm.isEmpty() || targetWindowSamples <= 0) return pcm
        val ratio = pcm.size.toFloat() / targetWindowSamples.toFloat()
        // Only compress or subtly expand if within natural intelligible margin
        if (ratio <= 1.03f && ratio >= 0.78f) return pcm

        val desiredRate = ratio.coerceIn(minSpeedRate, maxSpeedRate)
        if (abs(desiredRate - 1.0f) < 0.03f) return pcm

        val floats = FloatArray(pcm.size) { i -> pcm[i] / 32768f }
        val scaled = wsolaTimeStretch(floats, desiredRate)
        return ShortArray(scaled.size) { i ->
            (scaled[i].coerceIn(-0.99f, 0.99f) * 32767f).toInt().toShort()
        }
    }

    fun wsolaTimeStretch(input: FloatArray, rate: Float): FloatArray {
        val winSize = 1024
        val synHop = winSize / 2 // 50% overlap
        val anaHop = (synHop * rate).toInt().coerceAtLeast(64)
        val searchRadius = 192

        if (input.size < winSize * 2) {
            val outLen = (input.size / rate).toInt().coerceAtLeast(1)
            return FloatArray(outLen) { i ->
                val p = (i * rate).toInt().coerceIn(0, input.lastIndex)
                input[p]
            }
        }

        val hann = FloatArray(winSize) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (winSize - 1)))).toFloat()
        }

        val expectedOutLen = ((input.size - winSize) / rate).toInt().coerceAtLeast(winSize) + winSize
        val output = FloatArray(expectedOutLen)
        val norm = FloatArray(expectedOutLen)

        var inNominal = 0
        var outPos = 0

        while (inNominal + winSize < input.size && outPos + winSize < output.size) {
            val actualInPos = if (outPos == 0) {
                0
            } else {
                val minOff = (inNominal - searchRadius).coerceAtLeast(0)
                val maxOff = (inNominal + searchRadius).coerceAtMost(input.size - winSize)
                var bestOffset = inNominal.coerceIn(minOff, maxOff)
                var bestCorr = Float.NEGATIVE_INFINITY
                val overlapCheck = 256.coerceAtMost(synHop)

                var candidate = minOff
                while (candidate <= maxOff) {
                    var corr = 0f
                    var k = 0
                    while (k < overlapCheck) {
                        corr += input[candidate + k] * output[outPos + k]
                        k += 4
                    }
                    if (corr > bestCorr) {
                        bestCorr = corr
                        bestOffset = candidate
                    }
                    candidate += 8
                }
                bestOffset
            }

            for (k in 0 until winSize) {
                val w = hann[k]
                output[outPos + k] += input[actualInPos + k] * w
                norm[outPos + k] += w
            }

            inNominal += anaHop
            outPos += synHop
        }

        return FloatArray(outPos.coerceAtLeast(1)) { i ->
            if (norm[i] > 1e-4f) output[i] / norm[i] else output[i]
        }
    }
}
