package com.example.data

import android.content.Context
import com.example.BuildConfig
import com.example.ai.GeminiVoiceStudioAi
import com.example.data.local.AppDatabase
import com.example.data.local.AudioSampleEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.GeneratedSpeechEntity
import com.example.data.local.VoiceProfileEntity
import com.example.data.remote.ElevenLabsApi
import com.example.data.remote.TtsRequestBody
import com.example.data.remote.VoiceSettingsDto
import com.example.domain.AudioQualityAnalysis
import com.example.domain.PersianAccent
import com.example.domain.SpeechLanguage
import com.example.domain.VoiceEmotion
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class VoiceStudioRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val geminiAi: GeminiVoiceStudioAi
) {
    private val voiceProfileDao = database.voiceProfileDao()
    private val audioSampleDao = database.audioSampleDao()
    private val generatedSpeechDao = database.generatedSpeechDao()
    private val chatMessageDao = database.chatMessageDao()

    private fun getApi(): ElevenLabsApi {
        val client = com.example.data.remote.ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 90L)
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.elevenlabs.io/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ElevenLabsApi::class.java)
    }

    fun observeVoiceProfiles(): Flow<List<VoiceProfileEntity>> = voiceProfileDao.observeAll()
    fun observeAudioSamples(): Flow<List<AudioSampleEntity>> = audioSampleDao.observeAll()
    fun observeGeneratedSpeeches(): Flow<List<GeneratedSpeechEntity>> = generatedSpeechDao.observeAll()
    fun observeChatMessages(): Flow<List<ChatMessageEntity>> = chatMessageDao.observeAll()

    fun isElevenLabsKeyConfigured(): Boolean =
        com.example.data.remote.UserApiKeyStore.resolveElevenLabsKey(context).isNotBlank()

    private fun requireApiKey(): String {
        val key = com.example.data.remote.UserApiKeyStore.resolveElevenLabsKey(context)
        if (key.isBlank()) {
            throw IllegalStateException(
                "کلید ElevenLabs API تنظیم نشده است. از بخش «کلید API» در نوار بالا کلید خود را وارد کنید (یا در پنل Secrets ثبت نمایید)."
            )
        }
        return key
    }

    suspend fun saveAudioSample(
        title: String,
        file: File,
        mimeType: String,
        durationSeconds: Int,
        sourceType: String
    ): AudioSampleEntity = withContext(Dispatchers.IO) {
        val entity = AudioSampleEntity(
            title = title,
            filePath = file.absolutePath,
            mimeType = mimeType,
            durationSeconds = durationSeconds,
            sourceType = sourceType
        )
        val id = audioSampleDao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun analyzeAndSaveSampleQuality(sample: AudioSampleEntity): AudioQualityAnalysis =
        withContext(Dispatchers.IO) {
            val file = File(sample.filePath)
            val analysis = geminiAi.analyzeVoiceSample(file, sample.mimeType)
            audioSampleDao.update(
                sample.copy(
                    qualityScore = analysis.qualityScore,
                    clarityNote = analysis.clarityNote,
                    detectedAccent = analysis.detectedAccent,
                    naturalPitchLabel = analysis.naturalPitchLabel,
                    improvementTip = analysis.improvementTip
                )
            )
            analysis
        }

    suspend fun deleteAudioSample(sample: AudioSampleEntity) = withContext(Dispatchers.IO) {
        File(sample.filePath).delete()
        audioSampleDao.deleteById(sample.id)
    }

    suspend fun createClonedVoice(
        name: String,
        accent: PersianAccent,
        sampleIds: List<Long>,
        removeBackgroundNoise: Boolean
    ): VoiceProfileEntity = withContext(Dispatchers.IO) {
        val apiKey = requireApiKey()
        val samples = audioSampleDao.getByIds(sampleIds)
        if (samples.isEmpty()) {
            throw IllegalArgumentException("حداقل یک فایل یا نمونه صدای فارسی برای کلونینگ انتخاب کنید.")
        }

        val fileParts = samples.mapNotNull { sample ->
            val file = File(sample.filePath)
            if (!file.exists()) return@mapNotNull null
            val mediaType = sample.mimeType.toMediaType()
            MultipartBody.Part.createFormData("files", file.name, file.asRequestBody(mediaType))
        }
        if (fileParts.isEmpty()) {
            throw IllegalArgumentException("فایل‌های صوتی انتخاب‌شده روی دستگاه یافت نشدند.")
        }

        val labelsJson = JSONObject().apply {
            put("language", "Persian")
            put("accent", accent.id)
            put("accent_label", accent.titleFa)
        }.toString()

        val descriptionText = "صدای کلون‌شده فارسی با لهجه ${accent.titleFa} (${samples.size} نمونه صوتی)"

        val response = getApi().addVoice(
            apiKey = apiKey,
            name = name.toRequestBody("text/plain".toMediaType()),
            description = descriptionText.toRequestBody("text/plain".toMediaType()),
            labels = labelsJson.toRequestBody("text/plain".toMediaType()),
            removeBackgroundNoise = removeBackgroundNoise.toString().toRequestBody("text/plain".toMediaType()),
            files = fileParts
        )

        if (!response.isSuccessful) {
            val errBody = response.errorBody()?.string().orEmpty()
            throw IllegalStateException(parseElevenLabsError(response.code(), errBody))
        }

        val remoteVoiceId = response.body()?.voiceId
            ?: throw IllegalStateException("شناسه صدای کلون‌شده از سرور دریافت نشد.")

        val profile = VoiceProfileEntity(
            elevenLabsVoiceId = remoteVoiceId,
            name = name,
            description = descriptionText,
            accentId = accent.id,
            sampleCount = fileParts.size
        )
        val profileId = voiceProfileDao.insert(profile)
        audioSampleDao.assignToProfile(samples.map { it.id }, profileId)
        profile.copy(id = profileId)
    }

    suspend fun retrainClonedVoiceWithDataset(
        profile: VoiceProfileEntity,
        additionalSampleIds: List<Long>,
        accent: PersianAccent,
        removeBackgroundNoise: Boolean
    ): VoiceProfileEntity = withContext(Dispatchers.IO) {
        val apiKey = requireApiKey()
        val samples = audioSampleDao.getByIds(additionalSampleIds)
        if (samples.isEmpty()) {
            throw IllegalArgumentException("حداقل یک دیتاست صوتی فارسی جدید برای بازآموزی مدل انتخاب کنید.")
        }

        val fileParts = samples.mapNotNull { sample ->
            val file = File(sample.filePath)
            if (!file.exists()) return@mapNotNull null
            MultipartBody.Part.createFormData("files", file.name, file.asRequestBody(sample.mimeType.toMediaType()))
        }
        if (fileParts.isEmpty()) {
            throw IllegalArgumentException("فایل‌های دیتاست انتخاب‌شده یافت نشدند.")
        }

        val labelsJson = JSONObject().apply {
            put("language", "Persian")
            put("accent", accent.id)
            put("accent_label", accent.titleFa)
        }.toString()

        val updatedTotalSamples = profile.sampleCount + fileParts.size
        val updatedDescription = "صدای بازآموزی‌شده فارسی با لهجه ${accent.titleFa} ($updatedTotalSamples نمونه صوتی)"

        val response = getApi().editVoice(
            apiKey = apiKey,
            voiceId = profile.elevenLabsVoiceId,
            name = profile.name.toRequestBody("text/plain".toMediaType()),
            description = updatedDescription.toRequestBody("text/plain".toMediaType()),
            labels = labelsJson.toRequestBody("text/plain".toMediaType()),
            removeBackgroundNoise = removeBackgroundNoise.toString().toRequestBody("text/plain".toMediaType()),
            files = fileParts
        )

        if (!response.isSuccessful) {
            val errBody = response.errorBody()?.string().orEmpty()
            throw IllegalStateException(parseElevenLabsError(response.code(), errBody))
        }

        val updatedProfile = profile.copy(
            accentId = accent.id,
            description = updatedDescription,
            sampleCount = updatedTotalSamples,
            updatedAt = System.currentTimeMillis()
        )
        voiceProfileDao.update(updatedProfile)
        audioSampleDao.assignToProfile(samples.map { it.id }, profile.id)
        updatedProfile
    }

    suspend fun syncVoicesFromCloud(): Int = withContext(Dispatchers.IO) {
        val apiKey = requireApiKey()
        val response = getApi().getVoices(apiKey)
        if (!response.isSuccessful) {
            val errBody = response.errorBody()?.string().orEmpty()
            throw IllegalStateException(parseElevenLabsError(response.code(), errBody))
        }
        val remoteVoices = response.body()?.voices.orEmpty()
        var syncedCount = 0
        for (remote in remoteVoices) {
            // Prioritize cloned or user-created voices, or sync any voice so user can speak Persian
            val existing = voiceProfileDao.getByRemoteId(remote.voiceId)
            val accentId = remote.labels?.get("accent") ?: PersianAccent.STANDARD.id
            if (existing == null) {
                voiceProfileDao.insert(
                    VoiceProfileEntity(
                        elevenLabsVoiceId = remote.voiceId,
                        name = remote.name,
                        description = remote.description ?: (remote.category ?: "صدای همگام‌سازی‌شده"),
                        accentId = accentId,
                        sampleCount = 1
                    )
                )
                syncedCount++
            }
        }
        syncedCount
    }

    suspend fun deleteVoiceProfile(profile: VoiceProfileEntity) = withContext(Dispatchers.IO) {
        if (isElevenLabsKeyConfigured()) {
            try {
                getApi().deleteVoice(requireApiKey(), profile.elevenLabsVoiceId)
            } catch (_: Exception) {
            }
        }
        voiceProfileDao.deleteById(profile.id)
    }

    suspend fun adaptScriptWithGemini(
        rawText: String,
        targetLanguage: SpeechLanguage,
        accent: PersianAccent,
        emotion: VoiceEmotion,
        pitch: Float,
        speed: Float
    ): String {
        return geminiAi.adaptPersianScriptForVoice(rawText, targetLanguage, accent, emotion, pitch, speed)
    }

    suspend fun generateSingingLyrics(
        topic: String,
        mood: String,
        targetLanguage: SpeechLanguage,
        accent: PersianAccent
    ): String {
        return geminiAi.generatePersianSingingLyrics(topic, mood, targetLanguage, accent)
    }

    suspend fun saveMasteredAudioRecord(
        voiceProfileId: Long,
        voiceName: String,
        lyrics: String,
        accent: PersianAccent,
        beatLabel: String,
        masteredFile: File
    ): GeneratedSpeechEntity = withContext(Dispatchers.IO) {
        val entity = GeneratedSpeechEntity(
            voiceProfileId = voiceProfileId,
            voiceName = "$voiceName (میکس و مستر)",
            originalText = lyrics,
            adaptedText = "خوانندگی و مسترینگ • $beatLabel",
            accentId = accent.id,
            emotionId = VoiceEmotion.HAPPY.id,
            pitch = 1.0f,
            speed = 1.0f,
            stability = 0.45f,
            similarityBoost = 0.80f,
            audioFilePath = masteredFile.absolutePath
        )
        val id = generatedSpeechDao.insert(entity)
        entity.copy(id = id)
    }

    suspend fun exportAudioFileToUri(sourcePath: String, destUri: android.net.Uri) =
        withContext(Dispatchers.IO) {
            val source = File(sourcePath)
            if (!source.exists()) {
                throw IllegalStateException("فایل صوتی مبدأ برای ذخیره یافت نشد.")
            }
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                source.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: throw IllegalStateException("امکان نوشتن در مسیر انتخاب‌شده وجود ندارد.")
        }

    suspend fun synthesizeSpeech(
        profile: VoiceProfileEntity,
        originalText: String,
        adaptedText: String,
        accent: PersianAccent,
        emotion: VoiceEmotion,
        pitch: Float,
        speed: Float,
        stability: Float,
        similarityBoost: Float
    ): GeneratedSpeechEntity = withContext(Dispatchers.IO) {
        val apiKey = requireApiKey()
        val requestBody = TtsRequestBody(
            text = adaptedText,
            modelId = "eleven_v3",
            voiceSettings = VoiceSettingsDto(
                stability = stability.coerceIn(0f, 1f),
                similarityBoost = similarityBoost.coerceIn(0f, 1f),
                style = emotion.style.coerceIn(0f, 1f),
                useSpeakerBoost = true
            )
        )

        val response = getApi().textToSpeech(
            apiKey = apiKey,
            voiceId = profile.elevenLabsVoiceId,
            body = requestBody
        )
        if (!response.isSuccessful) {
            val errBody = response.errorBody()?.string().orEmpty()
            throw IllegalStateException(parseElevenLabsError(response.code(), errBody))
        }

        val body = response.body() ?: throw IllegalStateException("فایل صوتی از سرور دریافت نشد.")
        val outputDir = File(context.filesDir, "generated_audio").apply { mkdirs() }
        val outputFile = File(outputDir, "speech_${System.currentTimeMillis()}.mp3")
        body.byteStream().use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }

        val speech = GeneratedSpeechEntity(
            voiceProfileId = profile.id,
            voiceName = profile.name,
            originalText = originalText,
            adaptedText = adaptedText,
            accentId = accent.id,
            emotionId = emotion.id,
            pitch = pitch,
            speed = speed,
            stability = stability,
            similarityBoost = similarityBoost,
            audioFilePath = outputFile.absolutePath
        )
        val id = generatedSpeechDao.insert(speech)
        speech.copy(id = id)
    }

    suspend fun deleteGeneratedSpeech(speech: GeneratedSpeechEntity) = withContext(Dispatchers.IO) {
        File(speech.audioFilePath).delete()
        generatedSpeechDao.deleteById(speech.id)
    }

    suspend fun saveChatMessage(role: String, text: String): ChatMessageEntity =
        withContext(Dispatchers.IO) {
            val entity = ChatMessageEntity(role = role, text = text)
            val id = chatMessageDao.insert(entity)
            entity.copy(id = id)
        }

    suspend fun getChatHistorySync(): List<ChatMessageEntity> =
        withContext(Dispatchers.IO) { chatMessageDao.getAllSync() }

    suspend fun clearChatHistory() = withContext(Dispatchers.IO) {
        chatMessageDao.clearAll()
    }

    fun streamSupportChat(
        userMessage: String,
        priorHistory: List<ChatMessageEntity>,
        studioContextSummary: String
    ): Flow<String> {
        return geminiAi.streamSupportChat(userMessage, priorHistory, studioContextSummary)
    }

    private fun parseElevenLabsError(statusCode: Int, rawBody: String): String {
        return try {
            val detail = JSONObject(rawBody).optJSONObject("detail")?.optString("message")
                ?: JSONObject(rawBody).optString("detail")
                ?: rawBody
            "خطای سرویس صدا ($statusCode): ${detail.ifBlank { "لطفاً کلید API و اشتراک خود را بررسی کنید." }}"
        } catch (_: Exception) {
            "خطای سرویس صدا (کد $statusCode): ${rawBody.take(180)}"
        }
    }
}
