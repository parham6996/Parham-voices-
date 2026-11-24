package com.example.ai

import android.util.Log
import com.example.data.local.ChatMessageEntity
import com.example.domain.AudioQualityAnalysis
import com.example.domain.CinemaDubbingStyle
import com.example.domain.DubbingToneStyle
import com.example.domain.DubbingTranslationResult
import com.example.domain.PersianAccent
import com.example.domain.SpeechLanguage
import com.example.domain.VoiceCharacterArchetype
import com.example.domain.VoiceEmotion
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "GeminiVoiceStudioAi"

data class PodcastDialogueTurn(
    val speakerName: String,
    val archetype: VoiceCharacterArchetype,
    val emotion: VoiceEmotion,
    val text: String
)

class GeminiVoiceStudioAi {

    private val audioQualitySchema = Schema.obj(
        properties = mapOf(
            "qualityScore" to Schema.integer(description = "Voice sample cloning suitability score from 0 to 100"),
            "clarityNote" to Schema.string(description = "Concise Persian assessment of microphone clarity, noise floor, and clipping"),
            "detectedAccent" to Schema.string(description = "Detected Persian accent or dialect in Persian"),
            "naturalPitchLabel" to Schema.string(description = "Natural voice pitch label in Persian: بم، متعادل، یا زیر"),
            "improvementTip" to Schema.string(description = "Actionable Persian recommendation to improve voice cloning accuracy")
        )
    )

    private val qualityAnalyzerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = audioQualitySchema
            }
        )
    }

    private val scriptAdapterModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    تو متخصص زبان‌شناسی فارسی، گویش‌های ایرانی و کارگردانی صدا (Voice Directing) هستی.
                    وظیفه تو بازنویسی ظریف و طبیعی متن فارسی کاربر بر اساس لهجه انتخاب‌شده و احساس مورد نظر است، بدون اینکه معنی اصلی جمله تغییر کند.
                    قوانین:
                    1. اگر لهجه غیررسمی مثل تهرانی، شیرازی، اصفهانی، مشهدی، تبریزی، گیلکی یا بندری انتخاب شده، واژگان، افعال و علائم نگارشی (مثل مکث، علامت تعجب یا سه‌نقطه) را طوری تنظیم کن که هنگام خوانده شدن توسط مدل گفتاری، لهجه و آهنگ کلام کاملاً طبیعی و ایرانی به نظر برسد.
                    2. بر اساس احساس انتخاب‌شده، فقط در ابتدای جمله یا نقاط طبیعی مکث، تگ صوتی مناسب مدل eleven_v3 را در صورت وجود اضافه کن (مثل [cheerfully]، [excited]، [calmly]، [sorrowfully]، [crying]، [laughs]، [firmly]، [whispers] یا [pause]) و متن فارسی را کامل برگردان.
                    3. خروجی باید فقط متن نهایی آماده برای خواندن باشد؛ هیچ توضیح اضافه‌ای ننویس.
                    """.trimIndent()
                )
            }
        )
    }

    suspend fun analyzeVoiceSample(file: File, mimeType: String): AudioQualityAnalysis =
        withContext(Dispatchers.IO) {
            if (!file.exists()) {
                throw IllegalArgumentException("فایل صوتی برای تحلیل یافت نشد.")
            }
            if (file.length() > 18 * 1024 * 1024) {
                throw IllegalArgumentException("حجم فایل صوتی برای تحلیل آنلاین باید کمتر از ۱۸ مگابایت باشد.")
            }
            val bytes = file.readBytes()
            val resolvedMime = when {
                mimeType.startsWith("audio/") -> mimeType
                file.extension.equals("wav", ignoreCase = true) -> "audio/wav"
                file.extension.equals("m4a", ignoreCase = true) -> "audio/mp4"
                else -> "audio/mpeg"
            }

            try {
                val response = withTimeout(60_000L) {
                    qualityAnalyzerModel.generateContent(
                        content {
                            inlineData(bytes, resolvedMime)
                            text(
                                """
                                این نمونه صدای فارسی را برای آموزش و کلونینگ صدا (Voice Cloning) بررسی کن.
                                میزان وضوح، نویز پس‌زمینه، لهجه فارسی گوینده، زیر و بمی طبیعی صدا و یک پیشنهاد کاربردی برای بهبود کیفیت کلون صدا را به زبان فارسی ارزیابی کن.
                                """.trimIndent()
                            )
                        }
                    )
                }
                val jsonText = response.text
                    ?: throw IllegalStateException("پاسخی از مدل تحلیل صدا دریافت نشد.")
                val json = JSONObject(jsonText)
                AudioQualityAnalysis(
                    qualityScore = json.optInt("qualityScore", 75).coerceIn(0, 100),
                    clarityNote = json.optString("clarityNote", "کیفیت قابل قبول برای آموزش صدا."),
                    detectedAccent = json.optString("detectedAccent", "فارسی معیار"),
                    naturalPitchLabel = json.optString("naturalPitchLabel", "متعادل"),
                    improvementTip = json.optString("improvementTip", "ضبط در محیط ساکت با فاصله حدود ۱۵ سانتی‌متری از میکروفون دقت کلون را افزایش می‌دهد.")
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "analyzeVoiceSample failed", e)
                throw e
            }
        }

    suspend fun adaptPersianScriptForVoice(
        rawText: String,
        targetLanguage: SpeechLanguage,
        accent: PersianAccent,
        emotion: VoiceEmotion,
        pitch: Float,
        speed: Float
    ): String = withContext(Dispatchers.IO) {
        val languageDirective = if (targetLanguage == SpeechLanguage.PERSIAN) {
            "زبان خروجی: فارسی با لهجه ${accent.titleFa} (${accent.stylePromptHint})"
        } else {
            "زبان خروجی: ${targetLanguage.titleFa} (${targetLanguage.nativeName}). اگر متن ورودی به زبان دیگری است، آن را کاملاً طبیعی، بومی و روان به زبان ${targetLanguage.nativeName} ترجمه و بازآرایی کن."
        }
        val prompt = """
            متن اصلی کاربر:
            "$rawText"

            $languageDirective
            احساس گفتار: ${emotion.titleFa} (تگ پیشنهادی eleven_v3: ${emotion.elevenV3Cue.ifBlank { "بدون تگ خاص" }})
            تنظیم لحن (Pitch): ${"%.2f".format(pitch)}
            تنظیم سرعت (Speed): ${"%.2f".format(speed)}

            فقط متن نهایی آماده گویندگی به زبان هدف را بدون هیچ توضیح اضافه‌ای برگردان.
        """.trimIndent()

        val response = withTimeout(45_000L) {
            scriptAdapterModel.generateContent(prompt)
        }
        response.text?.trim()?.takeIf { it.isNotBlank() } ?: rawText
    }

    suspend fun generatePersianSingingLyrics(
        topic: String,
        mood: String,
        targetLanguage: SpeechLanguage,
        accent: PersianAccent
    ): String = withContext(Dispatchers.IO) {
        val langNote = if (targetLanguage == SpeechLanguage.PERSIAN) {
            "به زبان فارسی و نزدیک به لحن «${accent.titleFa}»"
        } else {
            "به زبان ${targetLanguage.titleFa} (${targetLanguage.nativeName}) با قافیه و ریتم طبیعی همان زبان"
        }
        val prompt = """
            یک بند شعر یا ترانه آهنگین و موزون برای خوانندگی درباره موضوع «$topic» با حس‌وحال «$mood» $langNote بنویس.
            طول متن حدود ۴ تا ۶ خط کوتاه و خوش‌ریتم باشد تا برای اجرا با صدای کلون‌شده عالی شود.
            در نقاط مناسب تگ‌های آوایی مثل [melodic]، [softly] یا [pause] بگذار. فقط متن ترانه را برگردان.
        """.trimIndent()

        val response = withTimeout(45_000L) {
            scriptAdapterModel.generateContent(prompt)
        }
        response.text?.trim()?.takeIf { it.isNotBlank() }
            ?: "[melodic] شبِ آروم و ستاره، یادِ تو توو دلِ ما\nبا صدایِ گرمِ بارون، می‌خونم از قصه‌ها..."
    }

    private val dubbingTranslationSchema = Schema.obj(
        properties = mapOf(
            "hasSpeech" to Schema.boolean(description = "True ONLY if clear spoken human dialogue is audible; false for pure explosions, gunshots, thunder, music, or ambient SFX"),
            "isSoundEffectOnly" to Schema.boolean(description = "True when audio is mainly explosion, gunshot, screaming wind, crash, music, or non-dialogue SFX that must NOT be dubbed"),
            "detectedLanguage" to Schema.string(description = "Detected source language name in Persian"),
            "originalTranscript" to Schema.string(description = "Brief transcript of what was spoken in the original language"),
            "persianTranslation" to Schema.string(description = "Natural expressive Persian dubbing dialogue ready for voice acting (NOT stiff literal translation)"),
            "expressiveActingText" to Schema.string(description = "Same Persian line enhanced with eleven_v3 acting cues like [crying], [laughs], [gasps], [shouts], [excited], [sorrowfully], [whispers]"),
            "speakerCharacter" to Schema.string(description = "Speaker gender & age: adult_male, adult_female, young_boy, young_girl, elder_male, elder_female, or narrator"),
            "detectedEmotion" to Schema.string(description = "One of: neutral, happy, excited, calm, sad, crying, laughing, fearful, shouting, serious, whisper"),
            "recommendedPitch" to Schema.double(description = "Recommended voice acting pitch multiplier from 0.80 to 1.25"),
            "recommendedSpeed" to Schema.double(description = "Recommended voice acting speed multiplier from 0.82 to 1.20")
        )
    )

    suspend fun translateAudioToPersianDubbing(
        audioBytes: ByteArray,
        mimeType: String = "audio/wav",
        modelName: String = "gemini-3.8-flash",
        targetAccent: PersianAccent = PersianAccent.TEHRANI,
        toneStyle: DubbingToneStyle = DubbingToneStyle.CINEMA_NATURAL,
        cinemaStyle: CinemaDubbingStyle = CinemaDubbingStyle.NATURAL_CINEMA,
        dialogueContextHint: String = "",
        expressiveness: Float = 0.78f,
        timeoutMs: Long = 22_000L
    ): DubbingTranslationResult = withContext(Dispatchers.IO) {
        if (audioBytes.size > 18 * 1024 * 1024) {
            throw IllegalArgumentException("حجم فایل یا قطعه صوتی برای ترجمه فوری باید کمتر از ۱۸ مگابایت باشد.")
        }
        val model = Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = dubbingTranslationSchema
            }
        )
        val contextSection = if (dialogueContextHint.isNotBlank()) {
            "\nحافظه دیالوگ و کاراکترهای قبلی:\n$dialogueContextHint\n"
        } else {
            ""
        }
        val response = withTimeout(timeoutMs) {
            model.generateContent(
                content {
                    inlineData(audioBytes, mimeType)
                    text(
                        """
                        تو یک «مدیر دوبلاژ و صداپیشه حرفه‌ای سینمای ایران» هستی.
                        به این قطعه صوتی دقیقاً از نظر لحن، احساس، جنسیت/سن گوینده، گریه/خنده و افکت‌های محیطی گوش بده.

                        گویش هدف: ${targetAccent.titleFa} (${targetAccent.stylePromptHint})
                        سبک بیان: ${toneStyle.titleFa} (${toneStyle.directorHint})
                        استایل سینمایی: ${cinemaStyle.titleFa} (${cinemaStyle.promptDirective})
                        شدت احساس و بازیگری: ${(expressiveness * 100).toInt()}%
                        $contextSection
                        قوانین طلایی و بسیار مهم:
                        1. افکت‌های صوتی مثل صدای انفجار، شلیک، آژیر، برخورد، موسیقی بدون کلام یا همهمه را اصلاً دوبله نکن! در آن صورت hasSpeech را false و isSoundEffectOnly را true بگذار تا صدای اصلی فیلم دست‌نخورده پخش شود. فقط زمانی دوبله کن که گفتار واقعی انسان وجود دارد.
                        2. جنسیت و سن گوینده را دقیقاً از صدای اصلی تشخیص بده و در speakerCharacter یکی از این موارد را بگذار:
                           adult_male (مرد بالغ), adult_female (زن بالغ), young_boy (پسر نوجوان), young_girl (دختربچه/کودک), elder_male (مرد مسن), elder_female (زن مسن), narrator (راوی).
                        3. احساس واقعی گوینده را تشخیص بده (اگر گریه/هق‌هق می‌کند crying، اگر می‌خندد laughing، اگر فریاد می‌زند shouting، اگر ترسیده fearful):
                           neutral, happy, excited, calm, sad, crying, laughing, fearful, shouting, serious, whisper.
                        4. در persianTranslation جمله را شیک، زنده، طبیعی و سینمایی به فارسی دوبله کن (متناسب با هم‌زمانی دهان بازیگر، نه کتابی و رباتیک).
                        5. در expressiveActingText همان جمله را با تگ بازیگری مناسب مدل eleven_v3 مثل [crying] [sobbing]، [laughs]، [gasps]، [shouts]، [excited]، [sorrowfully]، [whispers] و مکث‌های انسانی (... و ،) آماده اجرا کن.
                        """.trimIndent()
                    )
                }
            )
        }
        val jsonText = response.text ?: return@withContext DubbingTranslationResult(
            hasSpeech = false,
            detectedLanguage = "نامشخص",
            originalTranscript = "",
            persianTranslation = "",
            engineUsedName = modelName
        )
        val json = JSONObject(jsonText)
        val isSfxOnly = json.optBoolean("isSoundEffectOnly", false)
        val hasSpeech = !isSfxOnly && json.optBoolean("hasSpeech", false)
        val rawTranslation = json.optString("persianTranslation", "")
        val emotion = VoiceEmotion.fromId(json.optString("detectedEmotion", "neutral"))
        val archetype = VoiceCharacterArchetype.fromId(json.optString("speakerCharacter", "adult_male"))
        val stylized = com.example.dubbing.PersianExpressiveDubbingStylizer.stylizeColloquialPersian(
            rawText = rawTranslation,
            accent = targetAccent,
            toneStyle = toneStyle,
            emotion = emotion,
            cinemaStyle = cinemaStyle
        )
        val actingRaw = json.optString("expressiveActingText", "").ifBlank {
            com.example.dubbing.PersianExpressiveDubbingStylizer.buildElevenV3ActingText(
                persianText = stylized,
                emotion = emotion,
                expressiveness = expressiveness,
                toneStyle = toneStyle
            )
        }
        DubbingTranslationResult(
            hasSpeech = hasSpeech && stylized.isNotBlank(),
            detectedLanguage = json.optString("detectedLanguage", "نامشخص"),
            originalTranscript = json.optString("originalTranscript", ""),
            persianTranslation = stylized,
            detectedEmotion = emotion,
            speakerCharacter = archetype,
            isSoundEffectOnly = isSfxOnly,
            expressiveActingText = actingRaw,
            recommendedPitch = json.optDouble("recommendedPitch", 1.0).toFloat().coerceIn(0.80f, 1.25f),
            recommendedSpeed = json.optDouble("recommendedSpeed", 1.0).toFloat().coerceIn(0.82f, 1.20f),
            engineUsedName = modelName
        )
    }

    private val podcastSchema = Schema.obj(
        properties = mapOf(
            "turns" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "speakerName" to Schema.string(),
                        "character" to Schema.string(description = "adult_male, adult_female, young_boy, young_girl, elder_male, elder_female, narrator"),
                        "emotion" to Schema.string(description = "neutral, happy, excited, calm, sad, crying, laughing, serious, whisper"),
                        "text" to Schema.string()
                    )
                )
            )
        )
    )

    suspend fun generateMultiCharacterPodcastScript(
        topicOrBookExcerpt: String,
        episodeMode: String,
        accent: PersianAccent,
        turnCount: Int = 6
    ): List<PodcastDialogueTurn> = withContext(Dispatchers.IO) {
        val model = Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = podcastSchema
            }
        )
        val prompt = """
            تو کارگردان حرفه‌ای پادکست و کتاب صوتی نمایشی هستی.
            نوع تولید: $episodeMode
            لهجه گفتار: ${accent.titleFa} (${accent.stylePromptHint})
            تعداد دیالوگ‌ها: حدود $turnCount نوبت گفتگو/روایت جذاب.

            موضوع یا متن ورودی کاربر:
            "$topicOrBookExcerpt"

            قوانین:
            - یک اجرای زنده، طبیعی، شیک و چندشخصیتی (مثلاً راوی + مهمان خانم + مهمان آقا یا کاراکترهای داستان) بساز.
            - احساسات طبیعی (خنده، هیجان، آرامش، غم، جدیت) را دقیق مشخص کن.
            - برای هر نوبت character را از این لیست انتخاب کن: adult_male, adult_female, young_boy, young_girl, elder_male, elder_female, narrator
        """.trimIndent()

        val resp = withTimeout(45_000L) {
            model.generateContent(prompt)
        }
        val jsonStr = resp.text ?: return@withContext emptyList()
        val arr: JSONArray = JSONObject(jsonStr).optJSONArray("turns") ?: JSONArray()
        val turns = mutableListOf<PodcastDialogueTurn>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val txt = obj.optString("text", "").trim()
            if (txt.isBlank()) continue
            turns += PodcastDialogueTurn(
                speakerName = obj.optString("speakerName", "راوی").trim(),
                archetype = VoiceCharacterArchetype.fromId(obj.optString("character", "narrator")),
                emotion = VoiceEmotion.fromId(obj.optString("emotion", "neutral")),
                text = txt
            )
        }
        turns
    }

    fun streamSupportChat(
        userMessage: String,
        priorHistory: List<ChatMessageEntity>,
        studioContextSummary: String
    ): Flow<String> = flow {
        val supportModel = Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    تو «همیار ParHam VoiCe» هستی؛ دستیار هوشمند و پشتیبان تخصصی اپلیکیشن کلونینگ صدای شخصی و دوبله هوشمند «ParHam VoiCe».
                    تو تمام مکالمه‌های قبلی را به خاطر می‌سپاری و از وضعیت فعلی استودیوی کاربر آگاهی داری تا در راهنمایی‌های چندمرحله‌ای، تنظیم لهجه‌های فارسی، درگاه‌های بدون فیلترشکن ایران (AvalAI، Liara، GapGPT، Metis)، بهبود دیتاست صوتی و عیب‌یابی فنی دقیقاً متناسب با شرایط واقعی کاربر کمک کنی.

                    لحن پاسخ‌ها: صمیمی، دقیق، کوتاه‌گو و ساختارمند به زبان فارسی.
                    """.trimIndent()
                )
            }
        )

        val mappedHistory = priorHistory.takeLast(20).map { msg ->
            content(role = if (msg.role == "user") "user" else "model") {
                text(msg.text)
            }
        }

        val chat = supportModel.startChat(history = mappedHistory)
        val contextualPrompt = """
            [وضعیت لحظه‌ای استودیوی کاربر]
            $studioContextSummary

            [پیام کاربر]
            $userMessage
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(contextualPrompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("پاسخ پشتیبانی خالی بود.")
        }
    }.flowOn(Dispatchers.IO)
}
