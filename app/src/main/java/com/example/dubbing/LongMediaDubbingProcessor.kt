package com.example.dubbing

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.audio.WsolaTimeStretch
import com.example.data.remote.ElevenLabsApi
import com.example.data.remote.ProxyConfigStore
import com.example.data.remote.TtsRequestBody
import com.example.data.remote.UserApiKeyStore
import com.example.domain.CinemaDubbingStyle
import com.example.domain.DubbingEngineMethod
import com.example.domain.DubbingOutputMode
import com.example.domain.DubbingToneStyle
import com.example.domain.DubbingTranslationResult
import com.example.domain.PersianAccent
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

private const val TAG = "LongMediaDubProcessor"

data class MediaWorkloadEstimate(
    val durationMs: Long,
    val hasVideoTrack: Boolean,
    val estimatedMinutes: Int,
    val estimatedDialogueChunks: Int,
    val summaryFa: String
)

data class LongMediaProcessingResult(
    val segmentCount: Int,
    val skippedSfxCount: Int,
    val resumedFromCheckpoint: Boolean,
    val srtFile: File?,
    val dubbedAudioFile: File?,
    val dubOnlyStemFile: File?,
    val sfxBgmStemFile: File?,
    val dubbedVideoFile: File?,
    val durationMs: Long,
    val lastResult: DubbingTranslationResult?
)

class LongMediaDubbingProcessor(
    private val context: Context,
    private val failoverEngine: DubbingFailoverEngine
) {

    private fun buildElevenLabsApi(): ElevenLabsApi {
        val client = ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 90L)
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl("https://api.elevenlabs.io/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ElevenLabsApi::class.java)
    }

    suspend fun estimateMediaWorkload(uri: Uri): MediaWorkloadEstimate = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var durationMs = 0L
            var hasVideo = false
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) hasVideo = true
                if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                    durationMs = maxOf(durationMs, fmt.getLong(MediaFormat.KEY_DURATION) / 1000L)
                }
            }
            val mins = (durationMs / 60_000L).toInt().coerceAtLeast(1)
            // After local explosion/silence pre-filtering, typically ~5-7 speech chunks per active movie minute
            val estChunks = ((durationMs / 1000.0) / 10.5).toInt().coerceAtLeast(1)
            val label = buildString {
                append("مدت فایل: ${formatHumanTime(durationMs)}")
                append(" • تخمین حدود $estChunks بخش گفتاری (سکوت و انفجارها روی گوشی حذف و رایگان رد می‌شوند)")
                if (hasVideo) append(" • خروجی ویدئو MP4 هم پشتیبانی می‌شود")
            }
            MediaWorkloadEstimate(
                durationMs = durationMs,
                hasVideoTrack = hasVideo,
                estimatedMinutes = mins,
                estimatedDialogueChunks = estChunks,
                summaryFa = label
            )
        } finally {
            extractor.release()
        }
    }

    suspend fun processLongMediaUri(
        uri: Uri,
        outputMode: DubbingOutputMode,
        preferredMethod: DubbingEngineMethod,
        targetAccent: PersianAccent,
        toneStyle: DubbingToneStyle,
        cinemaStyle: CinemaDubbingStyle,
        expressiveness: Float,
        userPitch: Float,
        userSpeed: Float,
        originalBedVolume: Float,
        dualLanguageSubtitles: Boolean,
        exportMp4Video: Boolean,
        clipStartSec: Long? = null,
        clipEndSec: Long? = null,
        clonedVoiceRemoteId: String?,
        useClonedVoice: Boolean,
        localTts: TextToSpeech?,
        localTtsReady: Boolean,
        onProgress: (String) -> Unit,
        onSegmentProduced: (DubbingSegment) -> Unit
    ): LongMediaProcessingResult = withContext(Dispatchers.IO) {
        val exportsDir = File(context.filesDir, "dub_exports").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val srtFile = File(exportsDir, "parham_subtitles_$timestamp.srt")

        val generateAudioTrack = outputMode != DubbingOutputMode.SUBTITLE_ONLY
        val dubbedWavFile = if (generateAudioTrack) File(exportsDir, "parham_dubbed_$timestamp.wav") else null
        val dubOnlyStemFile = if (generateAudioTrack) File(exportsDir, "parham_stem_dub_$timestamp.wav") else null
        val sfxStemFile = if (generateAudioTrack) File(exportsDir, "parham_stem_bgm_sfx_$timestamp.wav") else null

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            throw IllegalStateException("امکان باز کردن فایل ویدیو یا صدا وجود نداشت: ${e.localizedMessage}")
        }

        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        var hasVideoTrack = false
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) hasVideoTrack = true
            if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                audioTrackIndex = i
                audioFormat = fmt
            }
        }
        if (audioTrackIndex < 0 || audioFormat == null) {
            extractor.release()
            throw IllegalStateException("تراک صوتی در این فایل ویدیویی یا صوتی یافت نشد.")
        }

        val totalDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
            audioFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            0L
        }
        val totalDurationMs = (totalDurationUs / 1000L).coerceAtLeast(0L)

        val clipStartMs = (clipStartSec ?: 0L).coerceAtLeast(0L) * 1000L
        val clipEndMs = clipEndSec?.takeIf { it > (clipStartSec ?: 0L) }?.times(1000L) ?: Long.MAX_VALUE
        val isClippedRun = clipStartMs > 0L || clipEndMs < Long.MAX_VALUE

        val mediaKey = DubbingCheckpointStore.computeMediaKey(
            uri.toString() + if (isClippedRun) "|$clipStartMs-$clipEndMs" else "",
            totalDurationMs
        )
        val existingCheckpoint = if (!isClippedRun) {
            DubbingCheckpointStore.loadCheckpoint(context, mediaKey)
        } else {
            null
        }
        val resumeAfterMs = existingCheckpoint?.lastCompletedMs ?: 0L
        val resumed = resumeAfterMs > 2000L && (existingCheckpoint?.chunks?.isNotEmpty() == true)
        if (!resumed) {
            failoverEngine.resetDialogueMemory()
        }

        extractor.selectTrack(audioTrackIndex)
        if (clipStartMs > 0L) {
            extractor.seekTo(clipStartMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }

        val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
        val sourceRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
        } else {
            16000
        }
        val channels = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        } else {
            1
        }

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(audioFormat, null, null, 0)
        codec.start()

        val targetRate = 16000
        val sampleRatio = sourceRate.toDouble() / targetRate.toDouble()
        var resampleCursor = 0.0

        val pcmBuffer = ByteArrayOutputStream(targetRate * 2 * 14)
        var chunkStartMs = 0L
        var lastSpeechMs = 0L
        var speechDetected = false
        var currentMediaTimeMs = 0L

        val srtEntries = mutableListOf<String>()
        var srtCounter = 1
        var skippedSfxCount = 0
        var lastTranslationResult: DubbingTranslationResult? = null
        val checkpointChunks = existingCheckpoint?.chunks?.toMutableList() ?: mutableListOf()
        val chunkPcmDir = DubbingCheckpointStore.chunkPcmDir(context, mediaKey)

        // Restore already-completed chunks from checkpoint if resuming mid-movie!
        if (resumed && existingCheckpoint != null) {
            onProgress("ادامه خودکار از دقیقه ${formatHumanTime(resumeAfterMs)} (${existingCheckpoint.chunks.size} دیالوگ بازیابی شد)...")
            for (saved in existingCheckpoint.chunks) {
                val res = saved.result
                lastTranslationResult = res
                failoverEngine.characterMemoryBook.recordDialogueLine(
                    saved.result.speakerSlotLabel.ifBlank { res.speakerCharacter.titleFa },
                    res.persianTranslation
                )
                srtEntries += buildSrtBlock(srtCounter++, saved.startMs, saved.endMs, res, dualLanguageSubtitles)
                onSegmentProduced(
                    DubbingSegment(
                        detectedLanguage = res.detectedLanguage,
                        originalTranscript = res.originalTranscript,
                        persianTranslation = res.persianTranslation,
                        usedClonedVoice = useClonedVoice && generateAudioTrack,
                        detectedEmotion = res.detectedEmotion,
                        speakerCharacter = res.speakerCharacter,
                        speakerSlotLabel = res.speakerSlotLabel,
                        engineUsedName = res.engineUsedName,
                        failoverOccurred = res.failoverOccurred,
                        startMs = saved.startMs,
                        endMs = saved.endMs
                    )
                )
            }
        }

        // Disk-backed raw 16kHz PCM stream of the original movie audio (preserves explosions, gunshots, music!)
        val originalBedPcmFile = File(context.cacheDir, "orig_bed_${timestamp}.pcm")
        val originalBedOut = if (generateAudioTrack) FileOutputStream(originalBedPcmFile) else null
        var originalBedSamplesWritten = 0L

        // Active dialogue ranges & their PCM chunk files so we can mix Dub + Ducked BGM/SFX cleanly
        val timelinePlacements = mutableListOf<TimelineDubPlacement>()
        checkpointChunks.forEach { saved ->
            val f = saved.dubbedPcmFilePath?.let { File(it) }
            if (f != null && f.exists()) {
                val sampleStart = ((saved.startMs - clipStartMs).coerceAtLeast(0L) * targetRate) / 1000L
                val sampleEnd = ((saved.endMs - clipStartMs).coerceAtLeast(0L) * targetRate) / 1000L
                timelinePlacements += TimelineDubPlacement(sampleStart, sampleEnd, f)
            }
        }

        val resolvedElevenKey = UserApiKeyStore.resolveElevenLabsKey(context)
        val canUseClone = useClonedVoice && !clonedVoiceRemoteId.isNullOrBlank() && resolvedElevenKey.isNotBlank()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        suspend fun flushChunk(endMs: Long) {
            val rawPcm = pcmBuffer.toByteArray()
            pcmBuffer.reset()
            speechDetected = false
            if (rawPcm.size < targetRate) return

            val safeStartMs = (chunkStartMs - clipStartMs).coerceAtLeast(0L)
            val safeEndMs = (endMs - clipStartMs).coerceAtLeast(safeStartMs + 650L)

            // Skip already checkpointed timeline portion if resuming mid-movie!
            if (resumed && endMs <= resumeAfterMs) {
                return
            }

            // 1. Acoustic Explosion / Gunshot / SFX Filter before touching AI quota
            val speechCheck = SpeechVsSfxClassifier.analyzePcm16Mono(rawPcm, targetRate)
            if (speechCheck.isLikelySoundEffectOnly) {
                skippedSfxCount++
                onProgress("رد شدن هوشمند افکت/انفجار در ${formatHumanTime(safeStartMs)} (حفظ صدای اصلی)...")
                return
            }

            val pctText = if (totalDurationMs > 0L) {
                val pct = (((endMs - clipStartMs).coerceAtLeast(0L) * 100) / (if (isClippedRun && clipEndMs < Long.MAX_VALUE) (clipEndMs - clipStartMs) else totalDurationMs)).coerceIn(0L, 100L)
                " ($pct%)"
            } else {
                ""
            }
            onProgress("دوبله ${speechCheck.inferredArchetype.titleFa} در ${formatHumanTime(safeStartMs)} تا ${formatHumanTime(safeEndMs)}$pctText...")

            val wavBytes = wrapPcmAsWavBytes(rawPcm, targetRate)
            val res = try {
                failoverEngine.translateAudioWithFailover(
                    audioBytes = wavBytes,
                    mimeType = "audio/wav",
                    pcmBytesForProsody = rawPcm,
                    preferredMethod = preferredMethod,
                    targetAccent = targetAccent,
                    toneStyle = toneStyle,
                    cinemaStyle = cinemaStyle,
                    expressiveness = expressiveness,
                    onEngineSwitchNotice = { notice -> DubbingStateBus.setActiveEngineLabel(notice) }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Chunk translation warning at $safeStartMs ms", e)
                null
            } ?: return

            if (res.isSoundEffectOnly || !res.hasSpeech || res.persianTranslation.isBlank()) {
                skippedSfxCount++
                return
            }
            lastTranslationResult = res

            srtEntries += buildSrtBlock(srtCounter++, safeStartMs, safeEndMs, res, dualLanguageSubtitles)
            DubbingStateBus.setCurrentSubtitleText(
                if (dualLanguageSubtitles && res.originalTranscript.isNotBlank()) {
                    "${res.persianTranslation}\n${res.originalTranscript}"
                } else {
                    res.persianTranslation
                }
            )
            onSegmentProduced(
                DubbingSegment(
                    detectedLanguage = res.detectedLanguage,
                    originalTranscript = res.originalTranscript,
                    persianTranslation = res.persianTranslation,
                    usedClonedVoice = canUseClone && generateAudioTrack,
                    detectedEmotion = res.detectedEmotion,
                    speakerCharacter = res.speakerCharacter,
                    speakerSlotLabel = res.speakerSlotLabel,
                    engineUsedName = res.engineUsedName,
                    failoverOccurred = res.failoverOccurred,
                    startMs = safeStartMs,
                    endMs = safeEndMs
                )
            )

            var savedChunkPcmFile: File? = null
            if (generateAudioTrack) {
                val rawSegPcm = synthesizeDubbedSegmentPcm(
                    result = res,
                    canUseClone = canUseClone,
                    clonedVoiceRemoteId = clonedVoiceRemoteId,
                    elevenLabsKey = resolvedElevenKey,
                    expressiveness = expressiveness,
                    toneStyle = toneStyle,
                    userPitch = userPitch,
                    userSpeed = userSpeed,
                    localTts = localTts,
                    localTtsReady = localTtsReady,
                    targetSampleRate = targetRate
                )
                if (rawSegPcm.isNotEmpty()) {
                    // Lip-sync WSOLA time-fit so Persian line matches original dialogue window without stepping on explosions or next actor!
                    val windowSamples = (((safeEndMs - safeStartMs).coerceAtLeast(700L) * targetRate) / 1000L).toInt()
                    val fittedPcm = WsolaTimeStretch.fitDubbedSpeechToWindowPcm16(
                        pcm = rawSegPcm,
                        targetWindowSamples = windowSamples,
                        minSpeedRate = 0.88f,
                        maxSpeedRate = 1.28f
                    )
                    val pcmFile = File(chunkPcmDir, "seg_${safeStartMs}.pcm")
                    val bytes = ByteArray(fittedPcm.size * 2)
                    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    for (s in fittedPcm) bb.putShort(s)
                    pcmFile.writeBytes(bytes)
                    savedChunkPcmFile = pcmFile
                    val startSample = (safeStartMs * targetRate) / 1000L
                    val actualEndSample = startSample + fittedPcm.size
                    timelinePlacements += TimelineDubPlacement(startSample, maxOf(actualEndSample, startSample + windowSamples), pcmFile)
                }
            }

            if (!isClippedRun) {
                checkpointChunks += SavedDubbingCheckpointChunk(
                    startMs = safeStartMs,
                    endMs = safeEndMs,
                    result = res,
                    dubbedPcmFilePath = savedChunkPcmFile?.absolutePath
                )
                DubbingCheckpointStore.saveCheckpoint(
                    context,
                    MediaDubbingCheckpoint(mediaKey, endMs, checkpointChunks)
                )
            }
        }

        try {
            while (!sawOutputEos && coroutineContext.isActive) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        val ptsUs = extractor.sampleTime
                        if (sampleSize < 0 || (ptsUs > clipEndMs * 1000L && clipEndMs < Long.MAX_VALUE)) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, ptsUs, 0)
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
                    val frameTimeMs = (bufferInfo.presentationTimeUs / 1000L).coerceAtLeast(currentMediaTimeMs)
                    currentMediaTimeMs = frameTimeMs
                    codec.releaseOutputBuffer(outIdx, false)

                    if (chunk.isNotEmpty() && frameTimeMs >= clipStartMs) {
                        val shortBuf = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frameShorts = ShortArray(shortBuf.remaining())
                        shortBuf.get(frameShorts)

                        val monoFrames = frameShorts.size / channels
                        var sumSq = 0.0
                        val downsampled = ByteArrayOutputStream(monoFrames * 2)
                        val le = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)

                        var idx = 0
                        while (idx < monoFrames) {
                            var sSum = 0
                            val base = idx * channels
                            for (c in 0 until channels) sSum += frameShorts[base + c]
                            val monoSample = (sSum / channels).toShort()
                            sumSq += monoSample.toDouble() * monoSample.toDouble()

                            if (resampleCursor <= idx.toDouble()) {
                                le.clear()
                                le.putShort(monoSample)
                                downsampled.write(le.array())
                                resampleCursor += sampleRatio
                            }
                            idx++
                        }
                        resampleCursor -= monoFrames.toDouble()
                        if (resampleCursor < 0.0) resampleCursor = 0.0

                        val downsampledBytes = downsampled.toByteArray()
                        // Record continuous original audio bed (explosions, gunshots, BGM!)
                        if (originalBedOut != null && downsampledBytes.isNotEmpty()) {
                            originalBedOut.write(downsampledBytes)
                            originalBedSamplesWritten += downsampledBytes.size / 2L
                        }

                        val rms = if (monoFrames > 0) sqrt(sumSq / monoFrames) / 32768.0 else 0.0
                        if (rms > 0.011) {
                            if (!speechDetected) {
                                speechDetected = true
                                chunkStartMs = frameTimeMs
                            }
                            lastSpeechMs = frameTimeMs
                        }

                        if (speechDetected) {
                            pcmBuffer.write(downsampledBytes)
                        }

                        val bufferedDurationMs = (pcmBuffer.size().toLong() * 1000L) / (targetRate * 2L)
                        val silenceGapMs = frameTimeMs - lastSpeechMs

                        if (speechDetected && (bufferedDurationMs >= 13_500L || (bufferedDurationMs >= 3000L && silenceGapMs >= 620L))) {
                            flushChunk(frameTimeMs)
                            chunkStartMs = frameTimeMs
                        } else if (!speechDetected && bufferedDurationMs > 3000L) {
                            pcmBuffer.reset()
                        }
                    }

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEos = true
                    }
                }
            }

            if (speechDetected && pcmBuffer.size() >= targetRate) {
                flushChunk(currentMediaTimeMs.coerceAtLeast(chunkStartMs + 1000L))
            }
        } finally {
            try {
                codec.stop()
                codec.release()
            } catch (_: Exception) {
            }
            extractor.release()
            try {
                originalBedOut?.flush()
                originalBedOut?.close()
            } catch (_: Exception) {
            }
        }

        // Write SRT subtitle file
        val finalSrtFile: File? = if (srtEntries.isNotEmpty()) {
            srtFile.writeText(srtEntries.joinToString(""), Charsets.UTF_8)
            srtFile
        } else {
            null
        }

        // Build final audio tracks:
        // 1) Mixed Dubbed Voices + Preserved BGM/Explosions WAV (original audio at 1.0f everywhere, smoothly ducked during active Persian lines)
        // 2) Stems: Dub-only WAV + SFX/BGM-only WAV
        var finalMixedAudioFile: File? = null
        var finalDubStemFile: File? = null
        var finalSfxStemFile: File? = null
        var finalMp4File: File? = null

        if (generateAudioTrack && dubbedWavFile != null && dubOnlyStemFile != null && sfxStemFile != null && originalBedSamplesWritten > 0L) {
            onProgress("در حال میکس صدای کاراکترها با حفظ کامل انفجارها و موسیقی متن فیلم...")
            val bedBuilt = buildMixedTimelineAndStems(
                originalBedPcmFile = originalBedPcmFile,
                originalSamplesCount = originalBedSamplesWritten,
                placements = timelinePlacements,
                originalDuckedLevel = originalBedVolume.coerceIn(0.05f, 0.75f),
                mixedOutWav = dubbedWavFile,
                dubOnlyOutWav = dubOnlyStemFile,
                sfxOnlyOutWav = sfxStemFile,
                sampleRate = targetRate
            )
            if (bedBuilt) {
                finalMixedAudioFile = dubbedWavFile
                finalDubStemFile = dubOnlyStemFile
                finalSfxStemFile = sfxStemFile
            }
        }

        originalBedPcmFile.delete()

        // Clear checkpoint on clean completion
        if (!isClippedRun && srtEntries.isNotEmpty()) {
            DubbingCheckpointStore.clearCheckpoint(context, mediaKey)
        }

        // Optional MP4 lossless video + dubbed AAC muxing
        if (exportMp4Video && hasVideoTrack && finalMixedAudioFile != null && finalMixedAudioFile.exists()) {
            onProgress("در حال ساخت فایل ویدئوی MP4 دوبله‌شده با حفظ کامل افکت‌ها...")
            val mp4Target = File(exportsDir, "parham_dubbed_movie_$timestamp.mp4")
            finalMp4File = Mp4DubbingMuxer.muxVideoWithDubbedWav(
                context = context,
                sourceUri = uri,
                dubbedWavFile = finalMixedAudioFile,
                outputMp4File = mp4Target,
                clipStartMs = clipStartMs,
                clipEndMs = clipEndMs
            )
        }

        LongMediaProcessingResult(
            segmentCount = srtEntries.size,
            skippedSfxCount = skippedSfxCount,
            resumedFromCheckpoint = resumed,
            srtFile = finalSrtFile,
            dubbedAudioFile = finalMixedAudioFile,
            dubOnlyStemFile = finalDubStemFile,
            sfxBgmStemFile = finalSfxStemFile,
            dubbedVideoFile = finalMp4File,
            durationMs = (currentMediaTimeMs - clipStartMs).coerceAtLeast(0L).coerceAtLeast(if (isClippedRun) 0L else totalDurationMs),
            lastResult = lastTranslationResult
        )
    }

    private data class TimelineDubPlacement(
        val startSample: Long,
        val endSample: Long,
        val pcmFile: File
    )

    private fun buildMixedTimelineAndStems(
        originalBedPcmFile: File,
        originalSamplesCount: Long,
        placements: List<TimelineDubPlacement>,
        originalDuckedLevel: Float,
        mixedOutWav: File,
        dubOnlyOutWav: File,
        sfxOnlyOutWav: File,
        sampleRate: Int
    ): Boolean {
        return try {
            val totalSamples = originalSamplesCount.coerceAtLeast(1L)
            FileOutputStream(mixedOutWav).use { mixedOut ->
                FileOutputStream(dubOnlyOutWav).use { dubOut ->
                    FileOutputStream(sfxOnlyOutWav).use { sfxOut ->
                        mixedOut.write(ByteArray(44))
                        dubOut.write(ByteArray(44))
                        sfxOut.write(ByteArray(44))

                        RandomAccessFile(originalBedPcmFile, "r").use { bedRaf ->
                            val placementBuffers = placements.mapNotNull { p ->
                                if (p.pcmFile.exists()) {
                                    val b = p.pcmFile.readBytes()
                                    val sb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                    val arr = ShortArray(sb.remaining())
                                    sb.get(arr)
                                    Triple(p.startSample, p.endSample, arr)
                                } else null
                            }

                            val blockSize = 8192
                            val bedBytes = ByteArray(blockSize * 2)
                            val mixedBytes = ByteArray(blockSize * 2)
                            val dubBytes = ByteArray(blockSize * 2)
                            val sfxBytes = ByteArray(blockSize * 2)
                            var sampleCursor = 0L

                            while (sampleCursor < totalSamples) {
                                val count = minOf(blockSize.toLong(), totalSamples - sampleCursor).toInt()
                                val bytesToRead = count * 2
                                val readN = bedRaf.read(bedBytes, 0, bytesToRead)
                                if (readN <= 0) break
                                val actualSamples = readN / 2

                                val bedBb = ByteBuffer.wrap(bedBytes, 0, readN).order(ByteOrder.LITTLE_ENDIAN)
                                val mixedBb = ByteBuffer.wrap(mixedBytes, 0, readN).order(ByteOrder.LITTLE_ENDIAN)
                                val dubBb = ByteBuffer.wrap(dubBytes, 0, readN).order(ByteOrder.LITTLE_ENDIAN)
                                val sfxBb = ByteBuffer.wrap(sfxBytes, 0, readN).order(ByteOrder.LITTLE_ENDIAN)

                                for (i in 0 until actualSamples) {
                                    val globalIdx = sampleCursor + i
                                    val origSample = bedBb.short / 32768f

                                    var dubSample = 0f
                                    var inDialogueWindow = false
                                    for ((startS, endS, arr) in placementBuffers) {
                                        if (globalIdx in startS..endS) {
                                            inDialogueWindow = true
                                        }
                                        val localIdx = (globalIdx - startS).toInt()
                                        if (localIdx in arr.indices) {
                                            dubSample += arr[localIdx] / 32768f
                                        }
                                    }

                                    // Smooth original volume: 1.0f during explosions/BGM/non-speech, ducked ONLY during active Persian dialogue!
                                    val bedGain = if (inDialogueWindow || kotlin.math.abs(dubSample) > 0.01f) {
                                        originalDuckedLevel
                                    } else {
                                        1.0f
                                    }
                                    val sfxPart = (origSample * bedGain).coerceIn(-0.98f, 0.98f)
                                    val mixedSample = (sfxPart + dubSample * 1.08f).coerceIn(-0.98f, 0.98f)

                                    mixedBb.putShort((mixedSample * 32767f).toInt().toShort())
                                    dubBb.putShort((dubSample.coerceIn(-0.98f, 0.98f) * 32767f).toInt().toShort())
                                    sfxBb.putShort((origSample.coerceIn(-0.98f, 0.98f) * 32767f).toInt().toShort())
                                }

                                mixedOut.write(mixedBytes, 0, readN)
                                dubOut.write(dubBytes, 0, readN)
                                sfxOut.write(sfxBytes, 0, readN)
                                sampleCursor += actualSamples
                            }
                        }
                    }
                }
            }

            finalizeWavHeader(mixedOutWav, totalSamples, sampleRate)
            finalizeWavHeader(dubOnlyOutWav, totalSamples, sampleRate)
            finalizeWavHeader(sfxOnlyOutWav, totalSamples, sampleRate)
            true
        } catch (e: Exception) {
            Log.e(TAG, "buildMixedTimelineAndStems failed", e)
            false
        }
    }

    private fun buildSrtBlock(
        index: Int,
        startMs: Long,
        endMs: Long,
        res: DubbingTranslationResult,
        dualLanguage: Boolean
    ): String = buildString {
        appendLine(index)
        appendLine("${formatSrtTime(startMs)} --> ${formatSrtTime(endMs)}")
        appendLine(res.persianTranslation.trim())
        if (dualLanguage && res.originalTranscript.isNotBlank()) {
            appendLine(res.originalTranscript.trim())
        }
        appendLine()
    }

    private suspend fun synthesizeDubbedSegmentPcm(
        result: DubbingTranslationResult,
        canUseClone: Boolean,
        clonedVoiceRemoteId: String?,
        elevenLabsKey: String,
        expressiveness: Float,
        toneStyle: DubbingToneStyle,
        userPitch: Float,
        userSpeed: Float,
        localTts: TextToSpeech?,
        localTtsReady: Boolean,
        targetSampleRate: Int
    ): ShortArray {
        val targetVoiceId = if (canUseClone && clonedVoiceRemoteId != null) {
            clonedVoiceRemoteId
        } else if (elevenLabsKey.isNotBlank()) {
            result.speakerCharacter.elevenLabsPresetVoiceId
        } else {
            null
        }

        // 1. Try ElevenLabs v3 acting synthesis (either user's clone or character archetype preset voice)
        if (targetVoiceId != null) {
            try {
                val acting = result.expressiveActingText.ifBlank {
                    PersianExpressiveDubbingStylizer.buildElevenV3ActingText(
                        persianText = result.persianTranslation,
                        emotion = result.detectedEmotion,
                        expressiveness = expressiveness,
                        toneStyle = toneStyle
                    )
                }
                val settings = PersianExpressiveDubbingStylizer.buildDynamicElevenV3Settings(
                    emotion = result.detectedEmotion,
                    expressiveness = expressiveness
                )
                val resp = buildElevenLabsApi().textToSpeech(
                    apiKey = elevenLabsKey.trim(),
                    voiceId = targetVoiceId,
                    body = TtsRequestBody(
                        text = acting,
                        modelId = "eleven_v3",
                        voiceSettings = settings
                    )
                )
                if (resp.isSuccessful && resp.body() != null) {
                    val tmpMp3 = File(context.cacheDir, "seg_dub_${System.nanoTime()}.mp3")
                    resp.body()!!.byteStream().use { input ->
                        FileOutputStream(tmpMp3).use { output -> input.copyTo(output) }
                    }
                    val decoded = decodeFileTo16kMonoPcm(tmpMp3, targetSampleRate)
                    tmpMp3.delete()
                    if (decoded.isNotEmpty()) return decoded
                }
            } catch (e: Exception) {
                Log.w(TAG, "ElevenLabs character chunk failed, trying Iran/OpenAI/Local TTS", e)
            }
        }

        // 2. Try Iranian No-VPN Gateway or OpenAI TTS (`/audio/speech`) with character gender/age voice mapping!
        if (!canUseClone) {
            val tmpCloudFile = File(context.cacheDir, "seg_ir_tts_${System.nanoTime()}.mp3")
            val speed = (userSpeed * result.recommendedSpeed * result.speakerCharacter.defaultTtsSpeed).coerceIn(0.75f, 1.35f)
            if (failoverEngine.synthesizeSpeechViaIranOrOpenAiTts(
                    text = result.persianTranslation,
                    archetype = result.speakerCharacter,
                    speed = speed,
                    outputFile = tmpCloudFile
                )
            ) {
                val decoded = decodeFileTo16kMonoPcm(tmpCloudFile, targetSampleRate)
                tmpCloudFile.delete()
                if (decoded.isNotEmpty()) return decoded
            }
            tmpCloudFile.delete()
        }

        // 3. Fallback: Expressive Multi-Character Android Persian TTS
        if (localTts != null && localTtsReady) {
            PersianExpressiveDubbingStylizer.selectBestPersianVoice(localTts, result.speakerCharacter)
            val tmpWav = File(context.cacheDir, "tts_seg_${System.nanoTime()}.wav")
            val uttId = "longdub_${System.nanoTime()}"
            val done = CompletableDeferred<Boolean>()
            localTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == uttId) done.complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == uttId) done.complete(false)
                }
            })
            val (pitch, speed) = PersianExpressiveDubbingStylizer.computeCharacterFinalPitchAndRate(
                archetype = result.speakerCharacter,
                emotion = result.detectedEmotion,
                recommendedPitch = result.recommendedPitch,
                recommendedSpeed = result.recommendedSpeed,
                userPitch = userPitch,
                userSpeed = userSpeed,
                expressiveness = expressiveness,
                applyArchetypeShift = true
            )
            localTts.setPitch(pitch)
            localTts.setSpeechRate(speed)
            val cleanText = result.persianTranslation.replace(Regex("\\[[a-zA-Z_ ]+]"), " ").trim()
            localTts.synthesizeToFile(cleanText, Bundle(), tmpWav, uttId)
            val ok = withTimeoutOrNull(12_000L) { done.await() } ?: false
            if (ok && tmpWav.exists()) {
                val decoded = decodeFileTo16kMonoPcm(tmpWav, targetSampleRate)
                tmpWav.delete()
                return decoded
            }
            tmpWav.delete()
        }
        return ShortArray(0)
    }

    private fun decodeFileTo16kMonoPcm(file: File, targetRate: Int): ShortArray {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var track = -1
            var fmt: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    track = i
                    fmt = f
                    break
                }
            }
            if (track < 0 || fmt == null) return ShortArray(0)
            extractor.selectTrack(track)
            val srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
            val channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(fmt, null, null, 0)
            codec.start()

            val collected = ArrayList<Short>(srcRate * 4)
            val info = MediaCodec.BufferInfo()
            var inEos = false
            var outEos = false
            try {
                while (!outEos) {
                    if (!inEos) {
                        val idx = codec.dequeueInputBuffer(10_000)
                        if (idx >= 0) {
                            val buf = codec.getInputBuffer(idx)!!
                            val sz = extractor.readSampleData(buf, 0)
                            if (sz < 0) {
                                codec.queueInputBuffer(idx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inEos = true
                            } else {
                                codec.queueInputBuffer(idx, 0, sz, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        val bytes = if (info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            ByteArray(info.size).also { outBuf.get(it) }
                        } else {
                            ByteArray(0)
                        }
                        outBuf.clear()
                        codec.releaseOutputBuffer(outIdx, false)
                        val sb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val arr = ShortArray(sb.remaining())
                        sb.get(arr)
                        var p = 0
                        while (p + channels - 1 < arr.size) {
                            var s = 0
                            for (c in 0 until channels) s += arr[p + c]
                            collected.add((s / channels).toShort())
                            p += channels
                        }
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEos = true
                    }
                }
            } finally {
                codec.stop()
                codec.release()
            }

            if (collected.isEmpty()) return ShortArray(0)
            if (srcRate == targetRate) return collected.toShortArray()

            val ratio = srcRate.toDouble() / targetRate.toDouble()
            val outLen = (collected.size / ratio).toInt().coerceAtLeast(1)
            ShortArray(outLen) { i ->
                val srcPos = (i * ratio).toInt().coerceIn(0, collected.lastIndex)
                collected[srcPos]
            }
        } catch (_: Exception) {
            ShortArray(0)
        } finally {
            extractor.release()
        }
    }

    private fun finalizeWavHeader(wavFile: File, sampleCount: Long, sampleRate: Int) {
        val dataBytes = sampleCount * 2L
        val totalLen = (dataBytes + 36L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val dataLenInt = dataBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val byteRate = sampleRate * 2

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalLen)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(sampleRate)
            putInt(byteRate)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(dataLenInt)
        }.array()

        RandomAccessFile(wavFile, "rw").use { raf ->
            raf.seek(0L)
            raf.write(header)
        }
    }

    private fun formatSrtTime(ms: Long): String {
        val totalMs = ms.coerceAtLeast(0L)
        val hours = totalMs / 3_600_000
        val minutes = (totalMs % 3_600_000) / 60_000
        val seconds = (totalMs % 60_000) / 1000
        val millis = totalMs % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun formatHumanTime(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    private fun wrapPcmAsWavBytes(pcmBytes: ByteArray, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream(44 + pcmBytes.size)
        val totalDataLen = pcmBytes.size + 36
        val byteRate = sampleRate * 2
        out.write("RIFF".toByteArray())
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalDataLen).array())
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(16).array())
        out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array())
        out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array())
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate).array())
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate).array())
        out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(2).array())
        out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(16).array())
        out.write("data".toByteArray())
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(pcmBytes.size).array())
        out.write(pcmBytes)
        return out.toByteArray()
    }
}
