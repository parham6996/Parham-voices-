package com.example.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

data class AudioWaveformData(
    val envelopeBars: List<Float>,
    val detailedEnvelope: List<Float>,
    val durationMs: Int
)

object AudioWaveformExtractor {

    private const val ENVELOPE_BAR_COUNT = 52
    private const val DETAILED_POINT_COUNT = 240

    private val cache = LruCache<String, AudioWaveformData>(18)

    suspend fun extractWaveform(filePath: String): AudioWaveformData = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            return@withContext defaultEmptyWaveform()
        }

        val cacheKey = "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        cache.get(cacheKey)?.let { return@withContext it }

        val result = decodeEnvelope(file)
        cache.put(cacheKey, result)
        result
    }

    private fun decodeEnvelope(file: File): AudioWaveformData {
        val extractor = MediaExtractor()
        return try {
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
                return defaultEmptyWaveform()
            }

            extractor.selectTrack(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return defaultEmptyWaveform()
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else {
                1
            }
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(0L)
            } else {
                0L
            }

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            // Collect coarse RMS buckets (~30ms-80ms frames) cleanly in O(N_buckets) memory
            val rawBuckets = ArrayList<Float>(512)
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            try {
                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inIdx = codec.dequeueInputBuffer(8_000)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIdx,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 8_000)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        val bytes = if (bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            ByteArray(bufferInfo.size).also { outBuf.get(it) }
                        } else {
                            ByteArray(0)
                        }
                        outBuf.clear()
                        codec.releaseOutputBuffer(outIdx, false)

                        if (bytes.isNotEmpty()) {
                            val sb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val shorts = ShortArray(sb.remaining())
                            sb.get(shorts)

                            // Compute RMS per decoded frame chunk
                            var sumSq = 0.0
                            var count = 0
                            var i = 0
                            while (i < shorts.size) {
                                val normalized = shorts[i] / 32768.0
                                sumSq += normalized * normalized
                                count++
                                i += channels
                            }
                            if (count > 0) {
                                val rms = sqrt(sumSq / count).toFloat()
                                rawBuckets.add(rms)
                            }
                        }

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            } finally {
                try {
                    codec.stop()
                    codec.release()
                } catch (_: Exception) {
                }
            }

            if (rawBuckets.isEmpty()) {
                return defaultEmptyWaveform()
            }

            val detailed = resampleBuckets(rawBuckets, DETAILED_POINT_COUNT)
            val envelope = resampleBuckets(rawBuckets, ENVELOPE_BAR_COUNT)
            AudioWaveformData(
                envelopeBars = envelope,
                detailedEnvelope = detailed,
                durationMs = (durationUs / 1000L).toInt().coerceAtLeast(1)
            )
        } catch (_: Exception) {
            defaultEmptyWaveform()
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun resampleBuckets(raw: List<Float>, targetCount: Int): List<Float> {
        if (raw.isEmpty()) return List(targetCount) { 0.1f }
        val maxVal = (raw.maxOrNull() ?: 0.1f).coerceAtLeast(0.02f)

        return List(targetCount) { idx ->
            val start = (idx * raw.size) / targetCount
            val end = (((idx + 1) * raw.size) / targetCount).coerceAtLeast(start + 1).coerceAtMost(raw.size)
            var peak = 0f
            var sum = 0f
            for (k in start until end) {
                val v = raw[k]
                if (v > peak) peak = v
                sum += v
            }
            val avg = sum / (end - start).coerceAtLeast(1)
            val blended = (peak * 0.65f + avg * 0.35f) / maxVal
            // Perceptual power curve so softer vocal syllables remain clearly visible
            val perceptual = sqrt(blended.coerceIn(0f, 1f))
            perceptual.coerceIn(0.08f, 1.0f)
        }
    }

    fun defaultEmptyWaveform(): AudioWaveformData = AudioWaveformData(
        envelopeBars = List(ENVELOPE_BAR_COUNT) { 0.12f },
        detailedEnvelope = List(DETAILED_POINT_COUNT) { 0.12f },
        durationMs = 0
    )
}
