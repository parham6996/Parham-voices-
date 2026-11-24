package com.example.dubbing

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.ai.GeminiVoiceStudioAi
import com.example.ai.PodcastDialogueTurn
import com.example.data.remote.ElevenLabsApi
import com.example.data.remote.ProxyConfigStore
import com.example.data.remote.UserApiKeyStore
import com.example.domain.CinemaDubbingStyle
import com.example.domain.DubbingEngineMethod
import com.example.domain.DubbingToneStyle
import com.example.domain.DubbingTranslationResult
import com.example.domain.IranGatewayPreset
import com.example.domain.PersianAccent
import com.example.domain.SpeechLanguage
import com.example.domain.VoiceCharacterArchetype
import com.example.domain.VoiceEmotion
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

private const val TAG = "DubbingFailoverEngine"

class DubbingFailoverEngine(
    private val context: Context,
    private val geminiAi: GeminiVoiceStudioAi
) {
    val characterMemoryBook = CharacterMemoryBook()

    private fun buildElevenLabsApi(): ElevenLabsApi {
        val client = ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 25L)
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl("https://api.elevenlabs.io/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ElevenLabsApi::class.java)
    }

    private fun resolveElevenLabsKey(): String = UserApiKeyStore.resolveElevenLabsKey(context)
    private fun resolveOpenAiKey(): String = UserApiKeyStore.resolveOpenAiKey(context)
    private fun resolveGeminiDirectKey(): String = UserApiKeyStore.resolveGeminiDirectKey(context)
    private fun resolveIranGatewayKey(): String = UserApiKeyStore.resolveIranGatewayKey(context)

    fun resetDialogueMemory() {
        characterMemoryBook.reset()
    }

    suspend fun translateAudioWithFailover(
        audioBytes: ByteArray,
        mimeType: String,
        pcmBytesForProsody: ByteArray? = null,
        preferredMethod: DubbingEngineMethod,
        targetAccent: PersianAccent,
        toneStyle: DubbingToneStyle,
        cinemaStyle: CinemaDubbingStyle = CinemaDubbingStyle.NATURAL_CINEMA,
        expressiveness: Float,
        onEngineSwitchNotice: (String) -> Unit = {}
    ): DubbingTranslationResult = withContext(Dispatchers.IO) {
        // 1. Acoustic pre-filter: block explosions, gunshots, thunder, silence & pure SFX before calling any AI API!
        val speechAnalysis = pcmBytesForProsody?.let {
            SpeechVsSfxClassifier.analyzePcm16Mono(it, 16000)
        }
        if (speechAnalysis != null && speechAnalysis.isLikelySoundEffectOnly) {
            onEngineSwitchNotice("افکت/انفجار شناسایی شد • عبور برای حفظ صدای اصلی فیلم")
            return@withContext DubbingTranslationResult(
                hasSpeech = false,
                isSoundEffectOnly = true,
                detectedLanguage = "افکت صوتی / محیط",
                originalTranscript = "",
                persianTranslation = "",
                engineUsedName = "فیلتر هوشمند انفجار و افکت"
            )
        }

        val orderedAttempts = buildEngineOrder(preferredMethod)
        val prosodyHint = pcmBytesForProsody?.let {
            PersianExpressiveDubbingStylizer.analyzePcmProsody(it)
        } ?: AcousticProsodyHint(VoiceEmotion.NEUTRAL, 1.0f, 1.0f, 0.25f)
        val dialogueContext = characterMemoryBook.buildContextPromptHint()

        val errors = mutableListOf<String>()

        orderedAttempts.forEachIndexed { idx, step ->
            val isFailoverAttempt = idx > 0
            if (isFailoverAttempt) {
                onEngineSwitchNotice("موتور قبلی قطع شد • جابه‌جایی خودکار به ${step.displayFa}...")
            } else {
                onEngineSwitchNotice("در حال ترجمه با ${step.displayFa}...")
            }

            try {
                val rawRes = when (step) {
                    FailoverStep.IRAN_GATEWAY -> translateViaIranNoVpnGateway(
                        audioBytes = audioBytes,
                        mimeType = mimeType,
                        prosodyHint = prosodyHint,
                        targetAccent = targetAccent,
                        toneStyle = toneStyle,
                        cinemaStyle = cinemaStyle,
                        dialogueContext = dialogueContext,
                        expressiveness = expressiveness,
                        failoverOccurred = isFailoverAttempt
                    )

                    FailoverStep.GEMINI_38 -> geminiAi.translateAudioToPersianDubbing(
                        audioBytes = audioBytes,
                        mimeType = mimeType,
                        modelName = "gemini-3.8-flash",
                        targetAccent = targetAccent,
                        toneStyle = toneStyle,
                        cinemaStyle = cinemaStyle,
                        dialogueContextHint = dialogueContext,
                        expressiveness = expressiveness,
                        timeoutMs = 18_000L
                    ).copy(
                        engineUsedName = "Gemini 3.8 Flash",
                        failoverOccurred = isFailoverAttempt
                    )

                    FailoverStep.GEMINI_35_LITE -> geminiAi.translateAudioToPersianDubbing(
                        audioBytes = audioBytes,
                        mimeType = mimeType,
                        modelName = "gemini-3.5-flash-lite",
                        targetAccent = targetAccent,
                        toneStyle = toneStyle,
                        cinemaStyle = cinemaStyle,
                        dialogueContextHint = dialogueContext,
                        expressiveness = expressiveness,
                        timeoutMs = 16_000L
                    ).copy(
                        engineUsedName = "Gemini 3.5 Flash-Lite",
                        failoverOccurred = isFailoverAttempt
                    )

                    FailoverStep.GEMINI_DIRECT_BYOK -> translateViaDirectGeminiRest(
                        audioBytes = audioBytes,
                        mimeType = mimeType,
                        apiKey = resolveGeminiDirectKey(),
                        targetAccent = targetAccent,
                        toneStyle = toneStyle,
                        cinemaStyle = cinemaStyle,
                        dialogueContext = dialogueContext,
                        expressiveness = expressiveness,
                        failoverOccurred = isFailoverAttempt
                    )

                    FailoverStep.OPENAI_WHISPER_GPT -> translateViaOpenAi(
                        audioBytes = audioBytes,
                        mimeType = mimeType,
                        prosodyHint = prosodyHint,
                        targetAccent = targetAccent,
                        toneStyle = toneStyle,
                        cinemaStyle = cinemaStyle,
                        dialogueContext = dialogueContext,
                        expressiveness = expressiveness,
                        failoverOccurred = isFailoverAttempt
                    )

                    FailoverStep.ELEVENLABS_SCRIBE -> translateViaElevenLabsScribe(
                        audioBytes = audioBytes,
                        mimeType = mimeType,
                        prosodyHint = prosodyHint,
                        targetAccent = targetAccent,
                        toneStyle = toneStyle,
                        cinemaStyle = cinemaStyle,
                        dialogueContext = dialogueContext,
                        expressiveness = expressiveness,
                        failoverOccurred = isFailoverAttempt
                    )

                    FailoverStep.CUSTOM_AI_PROVIDER -> translateViaCustomAiProvider(
                        audioBytes = audioBytes,
                        mimeType = mimeType,
                        prosodyHint = prosodyHint,
                        targetAccent = targetAccent,
                        toneStyle = toneStyle,
                        cinemaStyle = cinemaStyle,
                        dialogueContext = dialogueContext,
                        expressiveness = expressiveness,
                        failoverOccurred = isFailoverAttempt
                    )
                }

                // Merge acoustic gender/age pitch cue + AI character classification + Character Memory Book
                if (rawRes.hasSpeech && !rawRes.isSoundEffectOnly && rawRes.persianTranslation.isNotBlank()) {
                    val acousticPitchHz = speechAnalysis?.estimatedPitchHz ?: 150f
                    val chosenArchetype = rawRes.speakerCharacter
                    val slot = characterMemoryBook.resolveCharacterSlot(chosenArchetype, acousticPitchHz)
                    characterMemoryBook.recordDialogueLine(slot.displayLabel, rawRes.persianTranslation)
                    return@withContext rawRes.copy(
                        speakerCharacter = slot.archetype,
                        speakerSlotLabel = slot.displayLabel
                    )
                }

                return@withContext rawRes
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Engine ${step.displayFa} failed: ${e.message}", e)
                errors += "${step.displayFa}: ${e.localizedMessage ?: "خطای اتصال"}"
            }
        }

        throw IllegalStateException(
            "موتورهای هوش مصنوعی دوبله در دسترس نبودند (${errors.joinToString(" | ").take(240)}). در بخش API کلید درگاه بدون فیلترشکن ایران (AvalAI/GapGPT) یا اینترنت/پروکسی را بررسی کنید."
        )
    }

    /**
     * Synthesizes expressive TTS audio through Iranian No-VPN Gateways (AvalAI/Liara/GapGPT) or OpenAI `/audio/speech`
     * with automatic multi-character voice selection (woman/girl/boy/man/elder).
     */
    suspend fun synthesizeSpeechViaIranOrOpenAiTts(
        text: String,
        archetype: VoiceCharacterArchetype,
        speed: Float,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val cfg = UserApiKeyStore.load(context)
        val iranKey = cfg.iranGatewayApiKey.trim()
        val iranPreset = IranGatewayPreset.fromId(cfg.iranGatewayPresetId)
        val openAiKey = resolveOpenAiKey()

        val targets = mutableListOf<Triple<String, String, OkHttpClient>>()
        if (iranKey.isNotBlank() && cfg.iranGatewayBaseUrl.isNotBlank() && iranPreset.supportsTts) {
            targets += Triple(
                cfg.iranGatewayBaseUrl.trim().trimEnd('/'),
                iranKey,
                ProxyConfigStore.buildDirectOkHttpClient(readTimeoutSec = 25L)
            )
        }
        if (openAiKey.isNotBlank()) {
            targets += Triple(
                cfg.openAiBaseUrl.trim().trimEnd('/'),
                openAiKey,
                ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 25L)
            )
        }

        for ((baseUrl, key, client) in targets) {
            try {
                val ttsModel = if (baseUrl.contains("liara", ignoreCase = true)) "openai/tts-1" else "tts-1"
                val payload = JSONObject().apply {
                    put("model", ttsModel)
                    put("voice", archetype.openAiVoiceName)
                    put("input", text.replace(Regex("\\[[a-zA-Z_ ]+]"), " ").trim())
                    put("speed", speed.coerceIn(0.75f, 1.35f).toDouble())
                    put("response_format", "mp3")
                }
                val req = Request.Builder()
                    .url("$baseUrl/audio/speech")
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful && resp.body != null) {
                        resp.body!!.byteStream().use { input ->
                            FileOutputStream(outputFile).use { out -> input.copyTo(out) }
                        }
                        if (outputFile.exists() && outputFile.length() > 500L) {
                            return@withContext true
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cloud character TTS at $baseUrl failed: ${e.message}")
            }
        }
        false
    }

    suspend fun generatePodcastScriptWithFallback(
        topicOrBookExcerpt: String,
        episodeMode: String,
        accent: PersianAccent,
        turnCount: Int = 6
    ): List<PodcastDialogueTurn> = withContext(Dispatchers.IO) {
        val cfg = UserApiKeyStore.load(context)
        val iranKey = cfg.iranGatewayApiKey.trim()
        val iranUrl = cfg.iranGatewayBaseUrl.trim().trimEnd('/')

        // 1. Try Iranian No-VPN Gateway first if configured (fastest without proxy in Iran)
        if (iranKey.isNotBlank() && iranUrl.isNotBlank()) {
            try {
                val turns = generatePodcastViaOpenAiCompatible(
                    client = ProxyConfigStore.buildDirectOkHttpClient(readTimeoutSec = 35L),
                    baseUrl = iranUrl,
                    apiKey = iranKey,
                    model = cfg.iranGatewayModel.ifBlank { "gpt-4o-mini" },
                    topicOrBookExcerpt = topicOrBookExcerpt,
                    episodeMode = episodeMode,
                    accent = accent,
                    turnCount = turnCount
                )
                if (turns.isNotEmpty()) return@withContext turns
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        // 2. Try Firebase Gemini AI
        try {
            val turns = geminiAi.generateMultiCharacterPodcastScript(
                topicOrBookExcerpt = topicOrBookExcerpt,
                episodeMode = episodeMode,
                accent = accent,
                turnCount = turnCount
            )
            if (turns.isNotEmpty()) return@withContext turns
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }

        // 3. Try OpenAI / Custom provider via Proxy
        val openAiKey = resolveOpenAiKey()
        if (openAiKey.isNotBlank()) {
            return@withContext generatePodcastViaOpenAiCompatible(
                client = ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 35L),
                baseUrl = cfg.openAiBaseUrl.trim().trimEnd('/'),
                apiKey = openAiKey,
                model = cfg.openAiModel.ifBlank { "gpt-4o-mini" },
                topicOrBookExcerpt = topicOrBookExcerpt,
                episodeMode = episodeMode,
                accent = accent,
                turnCount = turnCount
            )
        }

        throw IllegalStateException("برای تولید سناریوی چندشخصیتی پادکست/کتاب، اتصال اینترنت یا یکی از کلیدهای AvalAI/Liara/GapGPT/Gemini/GPT نیاز است.")
    }

    private fun generatePodcastViaOpenAiCompatible(
        client: OkHttpClient,
        baseUrl: String,
        apiKey: String,
        model: String,
        topicOrBookExcerpt: String,
        episodeMode: String,
        accent: PersianAccent,
        turnCount: Int
    ): List<PodcastDialogueTurn> {
        val sys = """
            تو کارگردان پادکست و کتاب صوتی نمایشی هستی.
            فقط JSON خالص با کلید "turns" برگردان:
            {"turns":[{"speakerName":"راوی","character":"narrator","emotion":"warmly/calm/excited/happy/sad/crying/laughing/serious/whisper","text":"متن گفتار فارسی"}]}
            character فقط از: adult_male, adult_female, young_boy, young_girl, elder_male, elder_female, narrator
            emotion فقط از: neutral, happy, excited, calm, sad, crying, laughing, serious, whisper
        """.trimIndent()
        val payload = JSONObject().apply {
            put("model", model)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", sys))
                put(
                    JSONObject().put(
                        "role",
                        "user"
                    ).put(
                        "content",
                        "نوع برنامه: $episodeMode | لهجه: ${accent.titleFa} | تعداد نوبت: $turnCount\nمتن یا موضوع:\n$topicOrBookExcerpt"
                    )
                )
            })
        }
        val req = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("خطا در تولید سناریو (${resp.code})")
            }
            val contentStr = JSONObject(raw)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            val arr = JSONObject(contentStr).optJSONArray("turns") ?: JSONArray()
            val list = mutableListOf<PodcastDialogueTurn>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val txt = obj.optString("text", "").trim()
                if (txt.isBlank()) continue
                list += PodcastDialogueTurn(
                    speakerName = obj.optString("speakerName", "راوی"),
                    archetype = VoiceCharacterArchetype.fromId(obj.optString("character", "narrator")),
                    emotion = VoiceEmotion.fromId(obj.optString("emotion", "neutral")),
                    text = txt
                )
            }
            return list
        }
    }

    private enum class FailoverStep(val displayFa: String) {
        IRAN_GATEWAY("درگاه ایران بدون فیلترشکن"),
        GEMINI_38("۱. Gemini 3.8 Flash"),
        GEMINI_35_LITE("۲. Gemini 3.5 Flash-Lite"),
        GEMINI_DIRECT_BYOK("Gemini API شخصی"),
        OPENAI_WHISPER_GPT("۳. OpenAI Whisper + GPT"),
        ELEVENLABS_SCRIBE("۴. ElevenLabs Scribe"),
        CUSTOM_AI_PROVIDER("۵. هوش مصنوعی دلخواه")
    }

    private fun buildEngineOrder(preferred: DubbingEngineMethod): List<FailoverStep> {
        val userCfg = UserApiKeyStore.load(context)
        val hasIranGateway = userCfg.iranGatewayBaseUrl.isNotBlank() && userCfg.iranGatewayApiKey.isNotBlank()
        val hasDirectGemini = resolveGeminiDirectKey().isNotBlank()
        val hasOpenAi = resolveOpenAiKey().isNotBlank()
        val hasElevenLabs = resolveElevenLabsKey().isNotBlank()
        val hasCustomAi = userCfg.customProviderBaseUrl.isNotBlank() && userCfg.customProviderApiKey.isNotBlank()

        val base = mutableListOf<FailoverStep>()
        when (preferred) {
            DubbingEngineMethod.AUTO_FAILOVER -> {
                if (hasIranGateway) base += FailoverStep.IRAN_GATEWAY
                if (hasDirectGemini) base += FailoverStep.GEMINI_DIRECT_BYOK
                base += FailoverStep.GEMINI_38
                base += FailoverStep.GEMINI_35_LITE
                if (hasOpenAi) base += FailoverStep.OPENAI_WHISPER_GPT
                if (hasElevenLabs) base += FailoverStep.ELEVENLABS_SCRIBE
                if (hasCustomAi) base += FailoverStep.CUSTOM_AI_PROVIDER
            }
            DubbingEngineMethod.IRAN_NO_VPN -> {
                if (hasIranGateway) base += FailoverStep.IRAN_GATEWAY
                base += FailoverStep.GEMINI_38
                base += FailoverStep.GEMINI_35_LITE
                if (hasCustomAi) base += FailoverStep.CUSTOM_AI_PROVIDER
                if (hasOpenAi) base += FailoverStep.OPENAI_WHISPER_GPT
            }
            DubbingEngineMethod.GEMINI_PRIMARY -> {
                if (hasDirectGemini) base += FailoverStep.GEMINI_DIRECT_BYOK
                base += FailoverStep.GEMINI_38
                base += FailoverStep.GEMINI_35_LITE
                if (hasIranGateway) base += FailoverStep.IRAN_GATEWAY
                if (hasOpenAi) base += FailoverStep.OPENAI_WHISPER_GPT
                if (hasElevenLabs) base += FailoverStep.ELEVENLABS_SCRIBE
                if (hasCustomAi) base += FailoverStep.CUSTOM_AI_PROVIDER
            }
            DubbingEngineMethod.GEMINI_BACKUP -> {
                base += FailoverStep.GEMINI_35_LITE
                if (hasDirectGemini) base += FailoverStep.GEMINI_DIRECT_BYOK
                base += FailoverStep.GEMINI_38
                if (hasIranGateway) base += FailoverStep.IRAN_GATEWAY
                if (hasOpenAi) base += FailoverStep.OPENAI_WHISPER_GPT
                if (hasElevenLabs) base += FailoverStep.ELEVENLABS_SCRIBE
                if (hasCustomAi) base += FailoverStep.CUSTOM_AI_PROVIDER
            }
            DubbingEngineMethod.OPENAI_WHISPER -> {
                if (hasOpenAi) base += FailoverStep.OPENAI_WHISPER_GPT
                if (hasIranGateway) base += FailoverStep.IRAN_GATEWAY
                if (hasCustomAi) base += FailoverStep.CUSTOM_AI_PROVIDER
                if (hasElevenLabs) base += FailoverStep.ELEVENLABS_SCRIBE
                if (hasDirectGemini) base += FailoverStep.GEMINI_DIRECT_BYOK
                base += FailoverStep.GEMINI_38
                base += FailoverStep.GEMINI_35_LITE
            }
            DubbingEngineMethod.ELEVENLABS_SCRIBE -> {
                if (hasElevenLabs) base += FailoverStep.ELEVENLABS_SCRIBE
                if (hasIranGateway) base += FailoverStep.IRAN_GATEWAY
                if (hasCustomAi) base += FailoverStep.CUSTOM_AI_PROVIDER
                if (hasOpenAi) base += FailoverStep.OPENAI_WHISPER_GPT
                if (hasDirectGemini) base += FailoverStep.GEMINI_DIRECT_BYOK
                base += FailoverStep.GEMINI_38
                base += FailoverStep.GEMINI_35_LITE
            }
            DubbingEngineMethod.CUSTOM_AI -> {
                if (hasCustomAi) base += FailoverStep.CUSTOM_AI_PROVIDER
                if (hasIranGateway) base += FailoverStep.IRAN_GATEWAY
                if (hasOpenAi) base += FailoverStep.OPENAI_WHISPER_GPT
                if (hasElevenLabs) base += FailoverStep.ELEVENLABS_SCRIBE
                if (hasDirectGemini) base += FailoverStep.GEMINI_DIRECT_BYOK
                base += FailoverStep.GEMINI_38
                base += FailoverStep.GEMINI_35_LITE
            }
        }
        return base.distinct()
    }

    private suspend fun translateViaIranNoVpnGateway(
        audioBytes: ByteArray,
        mimeType: String,
        prosodyHint: AcousticProsodyHint,
        targetAccent: PersianAccent,
        toneStyle: DubbingToneStyle,
        cinemaStyle: CinemaDubbingStyle,
        dialogueContext: String,
        expressiveness: Float,
        failoverOccurred: Boolean
    ): DubbingTranslationResult {
        val cfg = UserApiKeyStore.load(context)
        val baseUrl = cfg.iranGatewayBaseUrl.trim().trimEnd('/')
        val apiKey = cfg.iranGatewayApiKey.trim()
        val gatewayLabel = cfg.iranGatewayName.ifBlank { "درگاه ایران" }
        val preset = IranGatewayPreset.fromId(cfg.iranGatewayPresetId)
        val chatModel = cfg.iranGatewayModel.ifBlank { preset.defaultModel }
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw IllegalStateException("کلید درگاه بدون فیلترشکن ایران (AvalAI/Liara/GapGPT) تنظیم نشده است.")
        }

        val directClient = ProxyConfigStore.buildDirectOkHttpClient(readTimeoutSec = 25L)
        val ext = extensionForMime(mimeType)
        var transcript = ""
        var detectedLangRaw = "auto"

        // 1. Try Whisper STT through Iranian gateway directly without proxy (AvalAI / GapGPT)
        if (preset.supportsWhisperStt || baseUrl.contains("avalai", true) || baseUrl.contains("gapgpt", true)) {
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("file", "clip.$ext", audioBytes.toRequestBody(mimeType.toMediaType()))
                .build()
            val sttReq = Request.Builder()
                .url("$baseUrl/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(multipart)
                .build()
            directClient.newCall(sttReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    transcript = json.optString("text", "").trim()
                    detectedLangRaw = json.optString("language", "auto")
                }
            }
        }

        // 2. Fallback STT if user picked chat-only gateway (Liara/Metis) and has OpenAI or ElevenLabs Scribe
        if (transcript.isBlank()) {
            val elevenKey = resolveElevenLabsKey()
            if (elevenKey.isNotBlank()) {
                val filePart = MultipartBody.Part.createFormData("file", "dub.$ext", audioBytes.toRequestBody(mimeType.toMediaType()))
                val modelBody = "scribe_v1".toRequestBody("text/plain".toMediaType())
                val sttResp = buildElevenLabsApi().speechToText(elevenKey, modelBody, filePart)
                if (sttResp.isSuccessful) {
                    transcript = sttResp.body()?.text?.trim().orEmpty()
                    detectedLangRaw = sttResp.body()?.languageCode ?: "auto"
                }
            }
        }

        if (transcript.isBlank() || isPureNonSpeechTranscript(transcript)) {
            return DubbingTranslationResult(
                hasSpeech = false,
                isSoundEffectOnly = transcript.isNotBlank(),
                detectedLanguage = mapLangCodeToPersian(detectedLangRaw),
                originalTranscript = "",
                persianTranslation = "",
                engineUsedName = "$gatewayLabel (بدون فیلترشکن)",
                failoverOccurred = failoverOccurred
            )
        }

        return rewriteAndDirectWithChatProvider(
            client = directClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = chatModel,
            transcript = transcript,
            detectedLangRaw = detectedLangRaw,
            prosodyHint = prosodyHint,
            targetAccent = targetAccent,
            toneStyle = toneStyle,
            cinemaStyle = cinemaStyle,
            dialogueContext = dialogueContext,
            expressiveness = expressiveness,
            engineName = "$gatewayLabel ($chatModel • بدون VPN)",
            failoverOccurred = failoverOccurred
        )
    }

    private fun translateViaDirectGeminiRest(
        audioBytes: ByteArray,
        mimeType: String,
        apiKey: String,
        targetAccent: PersianAccent,
        toneStyle: DubbingToneStyle,
        cinemaStyle: CinemaDubbingStyle,
        dialogueContext: String,
        expressiveness: Float,
        failoverOccurred: Boolean
    ): DubbingTranslationResult {
        val client = ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 25L)
        val b64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val prompt = """
            تو مدیر دوبلاژ حرفه‌ای سینمای ایران هستی. به این قطعه صوتی گوش بده.
            اگر فقط صدای انفجار، شلیک، ضربه یا موسیقی بدون کلام است hasSpeech=false و isSoundEffectOnly=true برگردان.
            در غیر این صورت با گویش ${targetAccent.titleFa}، سبک ${toneStyle.titleFa} و استایل ${cinemaStyle.titleFa} (شدت احساس ${(expressiveness * 100).toInt()}%) به فارسی زنده و شیک دوبله کن.
            ${if (dialogueContext.isNotBlank()) "حافظه کاراکترها و دیالوگ قبلی:\n$dialogueContext\n" else ""}
            فقط JSON خالص برگردان:
            {"hasSpeech":true,"isSoundEffectOnly":false,"speakerCharacter":"adult_male|adult_female|young_boy|young_girl|elder_male|elder_female|narrator","detectedLanguage":"...","originalTranscript":"...","persianTranslation":"...","expressiveActingText":"...","detectedEmotion":"neutral|happy|excited|calm|sad|crying|laughing|fearful|shouting|serious|whisper","recommendedPitch":1.0,"recommendedSpeed":1.0}
        """.trimIndent()

        val bodyJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", mimeType)
                                put("data", b64)
                            })
                        })
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
            put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        }

        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=$apiKey")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Gemini REST ${resp.code}: ${raw.take(120)}")
            }
            val text = JSONObject(raw)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            val json = JSONObject(text)
            val isSfxOnly = json.optBoolean("isSoundEffectOnly", false)
            val emotion = VoiceEmotion.fromId(json.optString("detectedEmotion", "neutral"))
            val archetype = VoiceCharacterArchetype.fromId(json.optString("speakerCharacter", "adult_male"))
            val stylized = PersianExpressiveDubbingStylizer.stylizeColloquialPersian(
                rawText = json.optString("persianTranslation", ""),
                accent = targetAccent,
                toneStyle = toneStyle,
                emotion = emotion,
                cinemaStyle = cinemaStyle
            )
            val acting = json.optString("expressiveActingText", "").ifBlank {
                PersianExpressiveDubbingStylizer.buildElevenV3ActingText(
                    persianText = stylized,
                    emotion = emotion,
                    expressiveness = expressiveness,
                    toneStyle = toneStyle
                )
            }
            return DubbingTranslationResult(
                hasSpeech = !isSfxOnly && json.optBoolean("hasSpeech", stylized.isNotBlank()) && stylized.isNotBlank(),
                isSoundEffectOnly = isSfxOnly,
                speakerCharacter = archetype,
                detectedLanguage = json.optString("detectedLanguage", "نامشخص"),
                originalTranscript = json.optString("originalTranscript", ""),
                persianTranslation = stylized,
                detectedEmotion = emotion,
                expressiveActingText = acting,
                recommendedPitch = json.optDouble("recommendedPitch", 1.0).toFloat().coerceIn(0.80f, 1.25f),
                recommendedSpeed = json.optDouble("recommendedSpeed", 1.0).toFloat().coerceIn(0.82f, 1.20f),
                engineUsedName = "Gemini API شخصی",
                failoverOccurred = failoverOccurred
            )
        }
    }

    private fun translateViaOpenAi(
        audioBytes: ByteArray,
        mimeType: String,
        prosodyHint: AcousticProsodyHint,
        targetAccent: PersianAccent,
        toneStyle: DubbingToneStyle,
        cinemaStyle: CinemaDubbingStyle,
        dialogueContext: String,
        expressiveness: Float,
        failoverOccurred: Boolean
    ): DubbingTranslationResult {
        val userCfg = UserApiKeyStore.load(context)
        val apiKey = resolveOpenAiKey()
        val baseUrl = userCfg.openAiBaseUrl.trimEnd('/')
        val chatModel = userCfg.openAiModel.ifBlank { "gpt-4o-mini" }
        val client = ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 25L)

        val ext = extensionForMime(mimeType)
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-1")
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("file", "clip.$ext", audioBytes.toRequestBody(mimeType.toMediaType()))
            .build()

        val sttRequest = Request.Builder()
            .url("$baseUrl/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(multipart)
            .build()

        val (transcript, detectedLangRaw) = client.newCall(sttRequest).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("OpenAI STT ${resp.code}: ${bodyStr.take(120)}")
            }
            val json = JSONObject(bodyStr)
            json.optString("text", "").trim() to json.optString("language", "نامشخص")
        }

        if (transcript.isBlank() || isPureNonSpeechTranscript(transcript)) {
            return DubbingTranslationResult(
                hasSpeech = false,
                isSoundEffectOnly = transcript.isNotBlank(),
                detectedLanguage = detectedLangRaw,
                originalTranscript = "",
                persianTranslation = "",
                engineUsedName = "OpenAI Whisper",
                failoverOccurred = failoverOccurred
            )
        }

        return rewriteAndDirectWithChatProvider(
            client = client,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = chatModel,
            transcript = transcript,
            detectedLangRaw = detectedLangRaw,
            prosodyHint = prosodyHint,
            targetAccent = targetAccent,
            toneStyle = toneStyle,
            cinemaStyle = cinemaStyle,
            dialogueContext = dialogueContext,
            expressiveness = expressiveness,
            engineName = "OpenAI Whisper + $chatModel",
            failoverOccurred = failoverOccurred
        )
    }

    private fun rewriteAndDirectWithChatProvider(
        client: OkHttpClient,
        baseUrl: String,
        apiKey: String,
        modelName: String,
        transcript: String,
        detectedLangRaw: String,
        prosodyHint: AcousticProsodyHint,
        targetAccent: PersianAccent,
        toneStyle: DubbingToneStyle,
        cinemaStyle: CinemaDubbingStyle,
        dialogueContext: String,
        expressiveness: Float,
        engineName: String,
        failoverOccurred: Boolean
    ): DubbingTranslationResult {
        val sysPrompt = """
            تو مدیر دوبلاژ حرفه‌ای سینمای ایران هستی.
            اگر متن صرفاً توضیح صدای انفجار، شلیک، خنده بی‌کلام یا تگ صوتی است (مثل [explosion]، [boom]، [music])، isSoundEffectOnly=true و hasSpeech=false برگردان.
            متن اصلی را با گویش «${targetAccent.titleFa}» (${targetAccent.stylePromptHint})، سبک «${toneStyle.titleFa}» و استایل «${cinemaStyle.titleFa}» (${cinemaStyle.promptDirective}) به فارسی شیک، زنده، سینمایی و غیررباتیک ترجمه کن.
            شدت احساس: ${(expressiveness * 100).toInt()}%.
            ${if (dialogueContext.isNotBlank()) "حافظه دیالوگ قبلی:\n$dialogueContext\n" else ""}
            فقط JSON خالص با کلیدهای زیر برگردان:
            {"hasSpeech":true,"isSoundEffectOnly":false,"speakerCharacter":"${prosodyHint.inferredArchetype.id}","detectedLanguageFa":"...","persianTranslation":"...","detectedEmotion":"neutral|happy|excited|calm|sad|crying|laughing|fearful|shouting|serious|whisper","recommendedPitch":1.0,"recommendedSpeed":1.0}
        """.trimIndent()

        val payload = JSONObject().apply {
            put("model", modelName)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", sysPrompt))
                put(
                    JSONObject().put("role", "user").put(
                        "content",
                        "زبان: $detectedLangRaw | جنسیت/سن تخمینی از صوت: ${prosodyHint.inferredArchetype.id}\nمتن: $transcript"
                    )
                )
            })
        }

        val chatReq = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(chatReq).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("$engineName ${resp.code}: ${bodyStr.take(120)}")
            }
            val contentJsonStr = JSONObject(bodyStr)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            val parsed = JSONObject(contentJsonStr)
            val isSfxOnly = parsed.optBoolean("isSoundEffectOnly", false)
            val hasSpeech = !isSfxOnly && parsed.optBoolean("hasSpeech", true)
            val emotion = VoiceEmotion.fromId(parsed.optString("detectedEmotion", prosodyHint.inferredEmotion.id))
            val archetype = VoiceCharacterArchetype.fromId(
                parsed.optString("speakerCharacter", prosodyHint.inferredArchetype.id)
            )
            val stylized = PersianExpressiveDubbingStylizer.stylizeColloquialPersian(
                rawText = parsed.optString("persianTranslation", transcript),
                accent = targetAccent,
                toneStyle = toneStyle,
                emotion = emotion,
                cinemaStyle = cinemaStyle
            )
            val acting = PersianExpressiveDubbingStylizer.buildElevenV3ActingText(
                persianText = stylized,
                emotion = emotion,
                expressiveness = expressiveness,
                toneStyle = toneStyle
            )
            return DubbingTranslationResult(
                hasSpeech = hasSpeech && stylized.isNotBlank(),
                isSoundEffectOnly = isSfxOnly,
                speakerCharacter = archetype,
                detectedLanguage = parsed.optString("detectedLanguageFa", mapLangCodeToPersian(detectedLangRaw)),
                originalTranscript = transcript,
                persianTranslation = stylized,
                detectedEmotion = emotion,
                expressiveActingText = acting,
                recommendedPitch = parsed.optDouble("recommendedPitch", prosodyHint.recommendedPitch.toDouble()).toFloat().coerceIn(0.80f, 1.25f),
                recommendedSpeed = parsed.optDouble("recommendedSpeed", prosodyHint.recommendedSpeed.toDouble()).toFloat().coerceIn(0.82f, 1.20f),
                engineUsedName = engineName,
                failoverOccurred = failoverOccurred
            )
        }
    }

    private suspend fun translateViaCustomAiProvider(
        audioBytes: ByteArray,
        mimeType: String,
        prosodyHint: AcousticProsodyHint,
        targetAccent: PersianAccent,
        toneStyle: DubbingToneStyle,
        cinemaStyle: CinemaDubbingStyle,
        dialogueContext: String,
        expressiveness: Float,
        failoverOccurred: Boolean
    ): DubbingTranslationResult {
        val userCfg = UserApiKeyStore.load(context)
        val baseUrl = userCfg.customProviderBaseUrl.trim().trimEnd('/')
        val apiKey = userCfg.customProviderApiKey.trim()
        val providerTitle = userCfg.customProviderName.ifBlank { "AI دلخواه" }
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw IllegalStateException("کلید یا آدرس سرویس هوش مصنوعی دلخواه تنظیم نشده است.")
        }

        val chatModel = userCfg.customProviderModel.ifBlank {
            if (baseUrl.contains("groq", ignoreCase = true)) "llama-3.3-70b-versatile" else "gpt-4o-mini"
        }
        val client = ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 25L)
        val ext = extensionForMime(mimeType)
        val sttModel = if (baseUrl.contains("groq", ignoreCase = true)) "whisper-large-v3-turbo" else "whisper-1"
        var transcript = ""
        var detectedLangRaw = "auto"

        try {
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", sttModel)
                .addFormDataPart("response_format", "verbose_json")
                .addFormDataPart("file", "clip.$ext", audioBytes.toRequestBody(mimeType.toMediaType()))
                .build()

            val sttReq = Request.Builder()
                .url("$baseUrl/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(multipart)
                .build()

            client.newCall(sttReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    transcript = json.optString("text", "").trim()
                    detectedLangRaw = json.optString("language", "auto")
                }
            }
        } catch (_: Exception) {
        }

        if (transcript.isBlank()) {
            val elevenKey = resolveElevenLabsKey()
            if (elevenKey.isNotBlank()) {
                val filePart = MultipartBody.Part.createFormData("file", "dub_chunk.$ext", audioBytes.toRequestBody(mimeType.toMediaType()))
                val modelBody = "scribe_v1".toRequestBody("text/plain".toMediaType())
                val sttResp = buildElevenLabsApi().speechToText(elevenKey, modelBody, filePart)
                if (sttResp.isSuccessful) {
                    transcript = sttResp.body()?.text?.trim().orEmpty()
                    detectedLangRaw = sttResp.body()?.languageCode ?: "auto"
                }
            }
        }

        if (transcript.isBlank()) {
            throw IllegalStateException("$providerTitle برای تبدیل صوت به متن نیاز به سرویس سازگار Whisper یا کلید ElevenLabs Scribe دارد.")
        }
        if (isPureNonSpeechTranscript(transcript)) {
            return DubbingTranslationResult(
                hasSpeech = false,
                isSoundEffectOnly = true,
                detectedLanguage = mapLangCodeToPersian(detectedLangRaw),
                originalTranscript = "",
                persianTranslation = "",
                engineUsedName = providerTitle,
                failoverOccurred = failoverOccurred
            )
        }

        return rewriteAndDirectWithChatProvider(
            client = client,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelName = chatModel,
            transcript = transcript,
            detectedLangRaw = detectedLangRaw,
            prosodyHint = prosodyHint,
            targetAccent = targetAccent,
            toneStyle = toneStyle,
            cinemaStyle = cinemaStyle,
            dialogueContext = dialogueContext,
            expressiveness = expressiveness,
            engineName = "$providerTitle ($chatModel)",
            failoverOccurred = failoverOccurred
        )
    }

    private suspend fun translateViaElevenLabsScribe(
        audioBytes: ByteArray,
        mimeType: String,
        prosodyHint: AcousticProsodyHint,
        targetAccent: PersianAccent,
        toneStyle: DubbingToneStyle,
        cinemaStyle: CinemaDubbingStyle,
        dialogueContext: String,
        expressiveness: Float,
        failoverOccurred: Boolean
    ): DubbingTranslationResult {
        val apiKey = resolveElevenLabsKey()
        val ext = extensionForMime(mimeType)
        val filePart = MultipartBody.Part.createFormData("file", "dub_chunk.$ext", audioBytes.toRequestBody(mimeType.toMediaType()))
        val modelBody = "scribe_v1".toRequestBody("text/plain".toMediaType())
        val sttResp = buildElevenLabsApi().speechToText(apiKey, modelBody, filePart)
        if (!sttResp.isSuccessful) {
            val err = sttResp.errorBody()?.string().orEmpty()
            throw IllegalStateException("Scribe ${sttResp.code()}: ${err.take(120)}")
        }
        val body = sttResp.body()
        val transcript = body?.text?.trim().orEmpty()
        val langCode = body?.languageCode ?: "auto"

        if (transcript.isBlank() || isPureNonSpeechTranscript(transcript)) {
            return DubbingTranslationResult(
                hasSpeech = false,
                isSoundEffectOnly = transcript.isNotBlank(),
                detectedLanguage = mapLangCodeToPersian(langCode),
                originalTranscript = "",
                persianTranslation = "",
                engineUsedName = "ElevenLabs Scribe",
                failoverOccurred = failoverOccurred
            )
        }

        val userCfg = UserApiKeyStore.load(context)
        if (userCfg.iranGatewayBaseUrl.isNotBlank() && userCfg.iranGatewayApiKey.isNotBlank()) {
            try {
                return rewriteAndDirectWithChatProvider(
                    client = ProxyConfigStore.buildDirectOkHttpClient(readTimeoutSec = 15L),
                    baseUrl = userCfg.iranGatewayBaseUrl,
                    apiKey = userCfg.iranGatewayApiKey,
                    modelName = userCfg.iranGatewayModel.ifBlank { "gpt-4o-mini" },
                    transcript = transcript,
                    detectedLangRaw = langCode,
                    prosodyHint = prosodyHint,
                    targetAccent = targetAccent,
                    toneStyle = toneStyle,
                    cinemaStyle = cinemaStyle,
                    dialogueContext = dialogueContext,
                    expressiveness = expressiveness,
                    engineName = "Scribe + ${userCfg.iranGatewayName.ifBlank { "درگاه ایران" }}",
                    failoverOccurred = failoverOccurred
                )
            } catch (_: Exception) {
            }
        }

        val proxyClient = ProxyConfigStore.buildOkHttpClient(context, readTimeoutSec = 15L)
        if (userCfg.customProviderBaseUrl.isNotBlank() && userCfg.customProviderApiKey.isNotBlank()) {
            try {
                return rewriteAndDirectWithChatProvider(
                    client = proxyClient,
                    baseUrl = userCfg.customProviderBaseUrl,
                    apiKey = userCfg.customProviderApiKey,
                    modelName = userCfg.customProviderModel.ifBlank { "gpt-4o-mini" },
                    transcript = transcript,
                    detectedLangRaw = langCode,
                    prosodyHint = prosodyHint,
                    targetAccent = targetAccent,
                    toneStyle = toneStyle,
                    cinemaStyle = cinemaStyle,
                    dialogueContext = dialogueContext,
                    expressiveness = expressiveness,
                    engineName = "Scribe + ${userCfg.customProviderName.ifBlank { "سرویس دلخواه" }}",
                    failoverOccurred = failoverOccurred
                )
            } catch (_: Exception) {
            }
        }

        val openAiKey = resolveOpenAiKey()
        if (openAiKey.isNotBlank()) {
            return rewriteAndDirectWithChatProvider(
                client = proxyClient,
                baseUrl = userCfg.openAiBaseUrl,
                apiKey = openAiKey,
                modelName = userCfg.openAiModel.ifBlank { "gpt-4o-mini" },
                transcript = transcript,
                detectedLangRaw = langCode,
                prosodyHint = prosodyHint,
                targetAccent = targetAccent,
                toneStyle = toneStyle,
                cinemaStyle = cinemaStyle,
                dialogueContext = dialogueContext,
                expressiveness = expressiveness,
                engineName = "ElevenLabs Scribe + GPT",
                failoverOccurred = failoverOccurred
            )
        }

        val (rawPersian, detectedSrc) = translateTextViaProxyEndpoint(proxyClient, transcript)
        val emotion = inferEmotionFromPunctuationAndProsody(rawPersian, prosodyHint)
        val stylized = PersianExpressiveDubbingStylizer.stylizeColloquialPersian(
            rawText = rawPersian,
            accent = targetAccent,
            toneStyle = toneStyle,
            emotion = emotion,
            cinemaStyle = cinemaStyle
        )
        val acting = PersianExpressiveDubbingStylizer.buildElevenV3ActingText(
            persianText = stylized,
            emotion = emotion,
            expressiveness = expressiveness,
            toneStyle = toneStyle
        )

        return DubbingTranslationResult(
            hasSpeech = stylized.isNotBlank(),
            speakerCharacter = prosodyHint.inferredArchetype,
            detectedLanguage = mapLangCodeToPersian(detectedSrc.ifBlank { langCode }),
            originalTranscript = transcript,
            persianTranslation = stylized,
            detectedEmotion = emotion,
            expressiveActingText = acting,
            recommendedPitch = prosodyHint.recommendedPitch,
            recommendedSpeed = prosodyHint.recommendedSpeed,
            engineUsedName = "ElevenLabs Scribe + محاوره‌ساز",
            failoverOccurred = failoverOccurred
        )
    }

    private fun isPureNonSpeechTranscript(transcript: String): Boolean {
        val cleaned = transcript.trim().lowercase()
        if (cleaned.isEmpty()) return true
        // Common Whisper audio event tags that mean explosions, SFX, music only (no dialogue!)
        val pureEventRegex = Regex("^[\\[(]*(music|explosion|boom|gunshot|applause|cheering|silence|noise|screaming|cough|sigh|footsteps|siren|thunder|laughter|sfx|موسیقی|انفجار|سکوت)[\\])!.\\s]*$")
        return pureEventRegex.matches(cleaned)
    }

    private fun translateTextViaProxyEndpoint(
        client: OkHttpClient,
        sourceText: String
    ): Pair<String, String> {
        val encoded = URLEncoder.encode(sourceText, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=fa&dt=t&q=$encoded"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || raw.isBlank()) {
                throw IllegalStateException("خطا در ترجمه شبکه (${resp.code})")
            }
            val outer = JSONArray(raw)
            val segments = outer.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until segments.length()) {
                val seg = segments.getJSONArray(i)
                sb.append(seg.optString(0, ""))
            }
            val detectedLang = outer.optString(2, "auto")
            return sb.toString().trim() to detectedLang
        }
    }

    private fun inferEmotionFromPunctuationAndProsody(
        persianText: String,
        prosody: AcousticProsodyHint
    ): VoiceEmotion {
        return when {
            persianText.contains("هاها") || persianText.contains("هه") -> VoiceEmotion.LAUGHING
            persianText.contains("!") && prosody.energyNormalized > 0.14f -> VoiceEmotion.EXCITED
            persianText.contains("!") -> VoiceEmotion.HAPPY
            prosody.inferredEmotion != VoiceEmotion.NEUTRAL -> prosody.inferredEmotion
            else -> VoiceEmotion.NEUTRAL
        }
    }

    private fun extensionForMime(mimeType: String): String = when {
        mimeType.contains("wav") -> "wav"
        mimeType.contains("mp4") || mimeType.contains("m4a") -> "m4a"
        mimeType.contains("ogg") -> "ogg"
        mimeType.contains("webm") -> "webm"
        else -> "mp3"
    }

    private fun mapLangCodeToPersian(codeOrName: String): String {
        val normalized = codeOrName.lowercase().trim()
        SpeechLanguage.entries.firstOrNull { normalized.startsWith(it.code) }?.let {
            return it.titleFa
        }
        return when (normalized) {
            "english", "en", "eng" -> "انگلیسی"
            "arabic", "ar", "ara" -> "عربی"
            "turkish", "tr", "tur" -> "ترکی"
            "french", "fr", "fra" -> "فرانسوی"
            "german", "de", "deu" -> "آلمانی"
            "spanish", "es", "spa" -> "اسپانیایی"
            "japanese", "ja", "jpn" -> "ژاپنی"
            "korean", "ko", "kor" -> "کره‌ای"
            "russian", "ru", "rus" -> "روسی"
            "persian", "fa", "fas" -> "فارسی"
            else -> codeOrName.ifBlank { "نامشخص" }
        }
    }
}
