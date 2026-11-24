package com.example.dubbing

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "Mp4DubbingMuxer"

/**
 * Combines lossless original H.264/HEVC video track from source URI with our new Persian Dubbed + SFX WAV track
 * by encoding the WAV into AAC and muxing both tracks via Android MediaMuxer.
 */
object Mp4DubbingMuxer {

    suspend fun muxVideoWithDubbedWav(
        context: Context,
        sourceUri: Uri,
        dubbedWavFile: File,
        outputMp4File: File,
        clipStartMs: Long = 0L,
        clipEndMs: Long = Long.MAX_VALUE
    ): File? = withContext(Dispatchers.IO) {
        if (!dubbedWavFile.exists() || dubbedWavFile.length() <= 44L) return@withContext null

        val wavSampleRate = readWavSampleRate(dubbedWavFile) ?: 16000
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var aacEncoder: MediaCodec? = null

        try {
            extractor.setDataSource(context, sourceUri, null)
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    videoFormat = fmt
                    break
                }
            }
            if (videoTrackIndex < 0 || videoFormat == null) {
                return@withContext null
            }

            // Prepare AAC audio encoder
            val aacFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, wavSampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            aacEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            muxer = MediaMuxer(outputMp4File.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxVideoTrack = muxer.addTrack(videoFormat)
            var muxAudioTrack = -1
            var muxerStarted = false

            // Encode first few frames until AAC MediaCodec emits INFO_OUTPUT_FORMAT_CHANGED so we can add audio track & start muxer
            val pcmRaf = RandomAccessFile(dubbedWavFile, "r")
            val totalPcmBytes = (dubbedWavFile.length() - 44L).coerceAtLeast(0L)
            pcmRaf.seek(44L)
            var bytesReadSoFar = 0L
            var totalSamplesFed = 0L
            var inputEos = false
            var outputEos = false
            val encoderInfo = MediaCodec.BufferInfo()
            val encodedAudioSamples = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>()

            val readChunk = ByteArray(2048)
            while (!outputEos) {
                if (!inputEos) {
                    val inIdx = aacEncoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = aacEncoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val maxToRead = minOf(readChunk.size, inBuf.remaining())
                        val n = if (bytesReadSoFar < totalPcmBytes) {
                            pcmRaf.read(readChunk, 0, minOf(maxToRead.toLong(), totalPcmBytes - bytesReadSoFar).toInt())
                        } else {
                            -1
                        }
                        if (n <= 0) {
                            aacEncoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        } else {
                            inBuf.put(readChunk, 0, n)
                            val ptsUs = (totalSamplesFed * 1_000_000L) / wavSampleRate
                            aacEncoder.queueInputBuffer(inIdx, 0, n, ptsUs, 0)
                            bytesReadSoFar += n
                            totalSamplesFed += n / 2L
                        }
                    }
                }

                val outIdx = aacEncoder.dequeueOutputBuffer(encoderInfo, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            muxAudioTrack = muxer.addTrack(aacEncoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = aacEncoder.getOutputBuffer(outIdx)!!
                        if ((encoderInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && encoderInfo.size > 0) {
                            outBuf.position(encoderInfo.offset)
                            outBuf.limit(encoderInfo.offset + encoderInfo.size)
                            val copyBytes = ByteArray(encoderInfo.size)
                            outBuf.get(copyBytes)
                            val copyInfo = MediaCodec.BufferInfo().apply {
                                set(0, encoderInfo.size, encoderInfo.presentationTimeUs, encoderInfo.flags)
                            }
                            if (muxerStarted) {
                                muxer.writeSampleData(muxAudioTrack, ByteBuffer.wrap(copyBytes), copyInfo)
                            } else {
                                encodedAudioSamples += copyBytes to copyInfo
                            }
                        }
                        aacEncoder.releaseOutputBuffer(outIdx, false)
                        if ((encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEos = true
                        }
                    }
                }
            }
            pcmRaf.close()

            if (!muxerStarted) {
                return@withContext null
            }

            for ((bytes, info) in encodedAudioSamples) {
                muxer.writeSampleData(muxAudioTrack, ByteBuffer.wrap(bytes), info)
            }

            // Copy lossless video track samples within optional clipStartMs..clipEndMs
            extractor.selectTrack(videoTrackIndex)
            val clipStartUs = clipStartMs.coerceAtLeast(0L) * 1000L
            val clipEndUs = if (clipEndMs < Long.MAX_VALUE / 1000L) clipEndMs * 1000L else Long.MAX_VALUE
            if (clipStartUs > 0L) {
                extractor.seekTo(clipStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }
            val maxSampleSize = if (videoFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
            } else {
                1024 * 1024
            }
            val videoBuf = ByteBuffer.allocateDirect(maxSampleSize)
            val videoInfo = MediaCodec.BufferInfo()
            var firstVideoPtsUs = -1L

            while (true) {
                videoBuf.clear()
                val sampleSize = extractor.readSampleData(videoBuf, 0)
                if (sampleSize < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > clipEndUs) break
                if (sampleTimeUs >= clipStartUs) {
                    if (firstVideoPtsUs < 0L) firstVideoPtsUs = sampleTimeUs
                    videoInfo.offset = 0
                    videoInfo.size = sampleSize
                    videoInfo.presentationTimeUs = (sampleTimeUs - firstVideoPtsUs).coerceAtLeast(0L)
                    @Suppress("DEPRECATION")
                    videoInfo.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    muxer.writeSampleData(muxVideoTrack, videoBuf, videoInfo)
                }
                extractor.advance()
            }

            muxer.stop()
            outputMp4File
        } catch (e: Exception) {
            Log.e(TAG, "MP4 dubbing mux failed", e)
            outputMp4File.delete()
            null
        } finally {
            try {
                aacEncoder?.stop()
                aacEncoder?.release()
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun readWavSampleRate(wavFile: File): Int? {
        return try {
            RandomAccessFile(wavFile, "r").use { raf ->
                if (raf.length() < 28) return null
                raf.seek(24)
                val b = ByteArray(4)
                raf.readFully(b)
                ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(8000, 48000)
            }
        } catch (_: Exception) {
            null
        }
    }
}
