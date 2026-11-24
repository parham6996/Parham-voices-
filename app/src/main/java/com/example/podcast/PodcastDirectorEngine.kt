package com.example.podcast

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.ai.PodcastDialogueTurn
import com.example.audio.BackingBeatStyle
import com.example.audio.BeatLaunchpadEngine
import com.example.data.remote.ElevenLabsApi
import com.example.data.remote.ProxyConfigStore
import com.example.data.remote.TtsRequestBody
import com.example.data.remote.UserApiKeyStore
import com.example.domain.CinemaDubbingStyle
import com.example.domain.DubbingToneStyle
import com.example.domain.PersianAccent
import com.example.domain.VoiceCharacterArchetype
import com.example.dubbing.DubbingFailoverEngine
import com.example.dubbing.PersianExpressiveDubbingStylizer
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.tanh

enum class PodcastEpisodeMode(val id: String, val titleFa: String) {
    PODCAST_TALK("podcast", "پادکست گفتگومحور"),
    DRAMATIC_AUDIOBOOK("audiobook", "کتاب صوتی نمایشی"),
    DOCUMENTARY_STORY("story", "مستند و قصه‌گویی")
}

class PodcastDirectorEngine(
    private val context: Context,
    private val failoverEngine: DubbingFailoverEngine,
    private val launchpadEngine: BeatLaunchpadEngine
) {
    companion object {
        private const val SAMPLE_RATE = 44100
    }

    private fun buildElevenLabsApi(): ElevenLabsApi {
        val client = ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 60L)
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl("https://api.elevenlabs.io/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ElevenLabsApi::class.java)
    }

    suspend fun renderMultiCharacterEpisode(
        turns: List<PodcastDialogueTurn>,
        episodeMode: PodcastEpisodeMode,
        accent: PersianAccent,
        includeAmbientMusic: Boolean,
        useClonedVoiceForHost: Boolean,
        clonedVoiceRemoteId: String?,
        localTts: TextToSpeech?,
        localTtsReady: Boolean,
        onProgress: (String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (turns.isEmpty()) {
            throw IllegalArgumentException("ابتدا سناریو را تولید یا وارد کنید.")
        }

        val elevenKey = UserApiKeyStore.resolveElevenLabsKey(context)
        val tempDir = File(context.cacheDir, "podcast_tmp").apply { mkdirs() }
        val pauseSamples = FloatArray((SAMPLE_RATE * 0.36).toInt())
        val leadInSamples = if (includeAmbientMusic) FloatArray((SAMPLE_RATE * 1.4).toInt()) else FloatArray(0)
        val tailOutSamples = if (includeAmbientMusic) FloatArray((SAMPLE_RATE * 1.8).toInt()) else FloatArray(0)

        val vocalSegments = mutableListOf<FloatArray>()
        if (leadInSamples.isNotEmpty()) vocalSegments += leadInSamples

        turns.forEachIndexed { idx, turn ->
            onProgress("در حال صداگذاری دیالوگ ${idx + 1} از ${turns.size} (${turn.speakerName} - ${turn.archetype.titleFa})...")
            val cleanColloquial = PersianExpressiveDubbingStylizer.stylizeColloquialPersian(
                rawText = turn.text,
                accent = accent,
                toneStyle = DubbingToneStyle.CINEMA_NATURAL,
                emotion = turn.emotion,
                cinemaStyle = CinemaDubbingStyle.NATURAL_CINEMA
            )
            val actingScript = PersianExpressiveDubbingStylizer.buildElevenV3ActingText(
                persianText = cleanColloquial,
                emotion = turn.emotion,
                expressiveness = 0.85f,
                toneStyle = DubbingToneStyle.DRAMATIC_ACTING
            )

            val turnAudioFile = File(tempDir, "turn_${System.currentTimeMillis()}_$idx.bin")
            var rendered = false

            // 1. ElevenLabs Cloned Host Voice or Archetype Preset
            val targetElevenVoice = when {
                useClonedVoiceForHost &&
                    !clonedVoiceRemoteId.isNullOrBlank() &&
                    (idx == 0 || turn.archetype == VoiceCharacterArchetype.NARRATOR || turn.archetype == VoiceCharacterArchetype.ADULT_MALE) ->
                    clonedVoiceRemoteId
                else -> turn.archetype.elevenLabsPresetVoiceId
            }

            if (elevenKey.isNotBlank() && !targetElevenVoice.isNullOrBlank()) {
                try {
                    val body = TtsRequestBody(
                        text = actingScript,
                        modelId = "eleven_v3",
                        voiceSettings = PersianExpressiveDubbingStylizer.buildDynamicElevenV3Settings(
                            emotion = turn.emotion,
                            expressiveness = 0.85f
                        )
                    )
                    val resp = buildElevenLabsApi().textToSpeech(elevenKey, targetElevenVoice, body)
                    if (resp.isSuccessful && resp.body() != null) {
                        resp.body()!!.byteStream().use { input ->
                            FileOutputStream(turnAudioFile).use { out -> input.copyTo(out) }
                        }
                        rendered = turnAudioFile.exists() && turnAudioFile.length() > 500L
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }

            // 2. Iranian No-VPN / OpenAI Character TTS fallback
            if (!rendered) {
                rendered = failoverEngine.synthesizeSpeechViaIranOrOpenAiTts(
                    text = cleanColloquial,
                    archetype = turn.archetype,
                    speed = turn.archetype.defaultTtsSpeed,
                    outputFile = turnAudioFile
                )
            }

            // 3. On-Device Multi-Character Expressive Persian TTS fallback
            if (!rendered && localTts != null && localTtsReady) {
                rendered = synthesizeLocalExpressiveTts(
                    tts = localTts,
                    text = cleanColloquial,
                    archetype = turn.archetype,
                    emotion = turn.emotion,
                    outFile = turnAudioFile
                )
            }

            if (rendered && turnAudioFile.exists()) {
                val pcmFloat = decodeAudioFileToMonoFloat(turnAudioFile, SAMPLE_RATE)
                if (pcmFloat.isNotEmpty()) {
                    vocalSegments += normalizePeak(pcmFloat, 0.78f)
                    vocalSegments += pauseSamples
                }
            }
            turnAudioFile.delete()
        }

        if (tailOutSamples.isNotEmpty()) vocalSegments += tailOutSamples

        val totalSamples = vocalSegments.sumOf { it.size }.coerceAtLeast(SAMPLE_RATE)
        val combinedDialogue = FloatArray(totalSamples)
        var cursor = 0
        for (seg in vocalSegments) {
            System.arraycopy(seg, 0, combinedDialogue, cursor, seg.size)
            cursor += seg.size
        }

        val finalMix = if (includeAmbientMusic) {
            onProgress("در حال میکس موسیقی پس‌زمینه هوشمند با داکینگ روی دیالوگ‌ها...")
            val beatStyle = when (episodeMode) {
                PodcastEpisodeMode.PODCAST_TALK -> BackingBeatStyle.LOFI_CHILL
                PodcastEpisodeMode.DRAMATIC_AUDIOBOOK -> BackingBeatStyle.ACOUSTIC_BALLAD
                PodcastEpisodeMode.DOCUMENTARY_STORY -> BackingBeatStyle.ACOUSTIC_BALLAD
            }
            val musicBed = launchpadEngine.synthesizeGenreBeat(
                style = beatStyle,
                length = combinedDialogue.size,
                sampleRate = SAMPLE_RATE,
                bpmOverride = 76,
                rootMidiNote = 57
            )
            mixDialogueOverDuckedBed(combinedDialogue, musicBed, SAMPLE_RATE)
        } else {
            combinedDialogue
        }

        val outDir = File(context.filesDir, "podcasts").apply { mkdirs() }
        val outFile = File(outDir, "parham_podcast_${System.currentTimeMillis()}.wav")
        writeFloatSamplesToWav(finalMix, outFile, SAMPLE_RATE)
        outFile
    }

    private fun mixDialogueOverDuckedBed(
        dialogue: FloatArray,
        musicBed: FloatArray,
        sampleRate: Int
    ): FloatArray {
        val out = FloatArray(dialogue.size)
        var env = 0f
        val fadeSamples = (sampleRate * 1.2f).coerceAtLeast(1f)
        for (i in dialogue.indices) {
            val v = dialogue[i]
            val absV = abs(v)
            env = if (absV > env) env + 0.015f * (absV - env) else env + 0.0005f * (absV - env)
            val fadeIn = (i / fadeSamples).coerceIn(0f, 1f)
            val fadeOut = ((dialogue.size - 1 - i) / fadeSamples).coerceIn(0f, 1f)
            val duck = (1.0f - (env * 2.2f).coerceIn(0f, 0.72f))
            val b = musicBed[i] * 0.28f * duck * fadeIn * fadeOut
            out[i] = tanh(((v * 1.05f + b) * 1.1f).toDouble()).toFloat().coerceIn(-0.98f, 0.98f)
        }
        return out
    }

    private suspend fun synthesizeLocalExpressiveTts(
        tts: TextToSpeech,
        text: String,
        archetype: VoiceCharacterArchetype,
        emotion: com.example.domain.VoiceEmotion,
        outFile: File
    ): Boolean {
        val done = CompletableDeferred<Boolean>()
        val uttId = "podcast_utt_${System.nanoTime()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == uttId) done.complete(true)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == uttId) done.complete(false)
            }
        })
        PersianExpressiveDubbingStylizer.selectBestPersianVoice(tts, archetype)
        val (finalPitch, finalRate) = PersianExpressiveDubbingStylizer.computeCharacterFinalPitchAndRate(
            archetype = archetype,
            emotion = emotion,
            recommendedPitch = 1.0f,
            recommendedSpeed = 1.0f,
            userPitch = 1.0f,
            userSpeed = 1.0f,
            expressiveness = 0.85f,
            applyArchetypeShift = true
        )
        tts.setPitch(finalPitch)
        tts.setSpeechRate(finalRate)
        val bundle = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val status = tts.synthesizeToFile(text, bundle, outFile, uttId)
        if (status != TextToSpeech.SUCCESS) return false
        return withTimeoutOrNull(18_000L) { done.await() } == true && outFile.exists() && outFile.length() > 100L
    }

    private fun decodeAudioFileToMonoFloat(file: File, targetRate: Int): FloatArray {
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
        val srcRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else targetRate
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmShorts = ArrayList<Short>(srcRate * 4)
        val info = MediaCodec.BufferInfo()
        var eosIn = false
        var eosOut = false
        try {
            while (!eosOut) {
                if (!eosIn) {
                    val inIdx = codec.dequeueInputBuffer(8000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val sz = extractor.readSampleData(buf, 0)
                        if (sz < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosIn = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sz, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 8000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    if (info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size).also { outBuf.get(it) }
                        val sb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val arr = ShortArray(sb.remaining()).also { sb.get(it) }
                        if (channels <= 1) {
                            arr.forEach { pcmShorts.add(it) }
                        } else {
                            var i = 0
                            while (i + channels - 1 < arr.size) {
                                var sum = 0
                                for (c in 0 until channels) sum += arr[i + c]
                                pcmShorts.add((sum / channels).toShort())
                                i += channels
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) eosOut = true
                }
            }
        } finally {
            codec.stop()
            codec.release()
            extractor.release()
        }

        if (pcmShorts.isEmpty()) return FloatArray(0)
        val floats = FloatArray(pcmShorts.size) { pcmShorts[it] / 32768f }
        if (srcRate == targetRate) return floats
        val ratio = srcRate.toDouble() / targetRate.toDouble()
        val outLen = (floats.size / ratio).toInt().coerceAtLeast(1)
        return FloatArray(outLen) { i ->
            val pos = i * ratio
            val i0 = pos.toInt().coerceIn(0, floats.lastIndex)
            val i1 = (i0 + 1).coerceIn(0, floats.lastIndex)
            val f = (pos - i0).toFloat()
            floats[i0] * (1f - f) + floats[i1] * f
        }
    }

    private fun normalizePeak(samples: FloatArray, targetPeak: Float): FloatArray {
        var maxV = 0.001f
        for (s in samples) {
            val a = abs(s)
            if (a > maxV) maxV = a
        }
        val gain = (targetPeak / maxV).coerceIn(0.3f, 3.0f)
        return FloatArray(samples.size) { i -> (samples[i] * gain).coerceIn(-0.98f, 0.98f) }
    }

    private fun writeFloatSamplesToWav(samples: FloatArray, outFile: File, sampleRate: Int) {
        val bytes = ByteArray(samples.size * 2)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) {
            bb.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        }
        val totalLen = bytes.size + 36
        FileOutputStream(outFile).use { out ->
            val hdr = ByteArrayOutputStream(44)
            hdr.write("RIFF".toByteArray())
            hdr.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalLen).array())
            hdr.write("WAVEfmt ".toByteArray())
            hdr.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(16).array())
            hdr.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array())
            hdr.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(1).array())
            hdr.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate).array())
            hdr.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate * 2).array())
            hdr.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(2).array())
            hdr.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(16).array())
            hdr.write("data".toByteArray())
            hdr.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.size).array())
            out.write(hdr.toByteArray())
            out.write(bytes)
        }
    }
}
