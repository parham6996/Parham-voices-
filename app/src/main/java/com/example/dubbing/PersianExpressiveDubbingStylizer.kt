package com.example.dubbing

import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.example.data.remote.VoiceSettingsDto
import com.example.domain.CinemaDubbingStyle
import com.example.domain.DubbingToneStyle
import com.example.domain.PersianAccent
import com.example.domain.VoiceCharacterArchetype
import com.example.domain.VoiceEmotion
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.sqrt

data class AcousticProsodyHint(
    val inferredEmotion: VoiceEmotion,
    val recommendedPitch: Float,
    val recommendedSpeed: Float,
    val energyNormalized: Float,
    val inferredArchetype: VoiceCharacterArchetype = VoiceCharacterArchetype.ADULT_MALE
)

object PersianExpressiveDubbingStylizer {

    fun analyzePcmProsody(pcmBytes: ByteArray): AcousticProsodyHint {
        if (pcmBytes.size < 4) {
            return AcousticProsodyHint(VoiceEmotion.NEUTRAL, 1.0f, 1.0f, 0.2f)
        }
        val speechAnalysis = SpeechVsSfxClassifier.analyzePcm16Mono(pcmBytes, 16000)
        val rms = speechAnalysis.rms
        val zcr = speechAnalysis.zeroCrossingRate

        val baseHint = when {
            rms > 0.22f && speechAnalysis.crestFactor > 5.0f -> AcousticProsodyHint(
                inferredEmotion = VoiceEmotion.SHOUTING,
                recommendedPitch = 1.08f,
                recommendedSpeed = 1.09f,
                energyNormalized = rms,
                inferredArchetype = speechAnalysis.inferredArchetype
            )
            rms > 0.15f && zcr > 0.11f -> AcousticProsodyHint(
                inferredEmotion = VoiceEmotion.EXCITED,
                recommendedPitch = 1.07f,
                recommendedSpeed = 1.06f,
                energyNormalized = rms,
                inferredArchetype = speechAnalysis.inferredArchetype
            )
            rms > 0.10f -> AcousticProsodyHint(
                inferredEmotion = VoiceEmotion.HAPPY,
                recommendedPitch = 1.04f,
                recommendedSpeed = 1.03f,
                energyNormalized = rms,
                inferredArchetype = speechAnalysis.inferredArchetype
            )
            rms < 0.032f -> AcousticProsodyHint(
                inferredEmotion = VoiceEmotion.WHISPER,
                recommendedPitch = 0.94f,
                recommendedSpeed = 0.89f,
                energyNormalized = rms,
                inferredArchetype = speechAnalysis.inferredArchetype
            )
            zcr < 0.055f -> AcousticProsodyHint(
                inferredEmotion = VoiceEmotion.CALM,
                recommendedPitch = 0.95f,
                recommendedSpeed = 0.94f,
                energyNormalized = rms,
                inferredArchetype = speechAnalysis.inferredArchetype
            )
            else -> AcousticProsodyHint(
                inferredEmotion = VoiceEmotion.NEUTRAL,
                recommendedPitch = 1.0f,
                recommendedSpeed = 1.0f,
                energyNormalized = rms,
                inferredArchetype = speechAnalysis.inferredArchetype
            )
        }
        return baseHint
    }

    fun stylizeColloquialPersian(
        rawText: String,
        accent: PersianAccent,
        toneStyle: DubbingToneStyle,
        emotion: VoiceEmotion,
        cinemaStyle: CinemaDubbingStyle = CinemaDubbingStyle.NATURAL_CINEMA
    ): String {
        var text = rawText.trim()
        if (text.isBlank()) return ""

        val shouldColloquialize = cinemaStyle != CinemaDubbingStyle.DOCUMENTARY_DEEP &&
            (accent != PersianAccent.STANDARD ||
                toneStyle == DubbingToneStyle.CASUAL_CONVERSATIONAL ||
                toneStyle == DubbingToneStyle.CINEMA_NATURAL ||
                toneStyle == DubbingToneStyle.DRAMATIC_ACTING)

        if (shouldColloquialize) {
            val replacements = listOf(
                "می‌خواهم" to "می‌خوام",
                "می خواهم" to "می‌خوام",
                "می‌خواهی" to "می‌خوای",
                "می‌خواهد" to "می‌خواد",
                "می‌خواهیم" to "می‌خوایم",
                "می‌خواهند" to "می‌خوان",
                "نمی‌خواهم" to "نمی‌خوام",
                "می‌توانم" to "می‌تونم",
                "می توانم" to "می‌تونم",
                "می‌توانی" to "می‌تونی",
                "می‌تواند" to "می‌تونه",
                "می‌توانیم" to "می‌تونیم",
                "می‌توانند" to "می‌تونن",
                "نمی‌توانم" to "نمی‌تونم",
                "نمی‌تواند" to "نمی‌تونه",
                "می‌دانم" to "می‌دونم",
                "می دانم" to "می‌دونم",
                "نمی‌دانم" to "نمی‌دونم",
                "می‌گویم" to "میگم",
                "می‌گوید" to "میگه",
                "می‌گویند" to "میگن",
                "می‌روم" to "میرم",
                "می‌رود" to "میره",
                "می‌رویم" to "میریم",
                "می‌آیم" to "میام",
                "می‌آید" to "میاد",
                "بسیار " to "خیلی ",
                "چگونه " to "چطور ",
                "چگونه؟" to "چطور؟",
                "بنابراین " to "واسه همین ",
                "زیرا " to "آخه ",
                "برای چه " to "واسه چی ",
                "اینجا است" to "اینجاست",
                "آنجا است" to "اونجاست",
                "خوب است" to "خوبه",
                "بد است" to "بده",
                "درست است" to "درسته",
                "واقعی است" to "واقعیه",
                "مهم است" to "مهمه",
                "ممکن است" to "ممکنه",
                "لازم است" to "لازمه",
                "کافی است" to "کافیه",
                "چیزی است" to "چیزیه",
                "این است" to "اینه",
                "آن است" to "اونه",
                "آنها " to "اونا ",
                "آنان " to "اونا ",
                "شما را " to "شما رو ",
                "تو را " to "تو رو ",
                "من را " to "منو ",
                "آن را " to "اونو ",
                "این را " to "اینو "
            )
            for ((from, to) in replacements) {
                text = text.replace(from, to)
            }
            if (text.startsWith("آیا ")) {
                text = text.removePrefix("آیا ")
            }
        }

        text = when (accent) {
            PersianAccent.SHIRAZI -> text
                .replace("میگم", "میگوم")
                .replace("چطور", "چطو")
            PersianAccent.MASHHADI -> text
                .replace("میگم", "موگوم")
            else -> text
        }

        // Authentic acting punctuation for emotional breaths & tears/laughter
        when (emotion) {
            VoiceEmotion.CRYING -> {
                text = text.replace("، ", "... ").replace(". ", "... ")
            }
            VoiceEmotion.SAD, VoiceEmotion.CALM, VoiceEmotion.WHISPER, VoiceEmotion.FEARFUL -> {
                text = text.replace("، ", "... ")
            }
            VoiceEmotion.LAUGHING -> {
                if (!text.contains("!") && !text.contains("؟")) {
                    text = "$text!"
                }
            }
            else -> Unit
        }
        return text.trim()
    }

    fun buildElevenV3ActingText(
        persianText: String,
        emotion: VoiceEmotion,
        expressiveness: Float,
        toneStyle: DubbingToneStyle
    ): String {
        val clean = persianText.trim()
        if (clean.startsWith("[")) return clean
        val exp = expressiveness.coerceIn(0f, 1f)
        val cue = when (emotion) {
            VoiceEmotion.EXCITED -> if (exp > 0.65f) "[excited]" else "[cheerfully]"
            VoiceEmotion.HAPPY -> "[cheerfully]"
            VoiceEmotion.CALM -> "[calmly]"
            VoiceEmotion.SAD -> if (exp > 0.6f) "[sighs] [sorrowfully]" else "[sorrowfully]"
            VoiceEmotion.CRYING -> "[crying] [sobbing]"
            VoiceEmotion.LAUGHING -> "[laughs] [cheerfully]"
            VoiceEmotion.FEARFUL -> "[gasps] [fearfully]"
            VoiceEmotion.SHOUTING -> "[shouts]"
            VoiceEmotion.SERIOUS -> "[firmly]"
            VoiceEmotion.WHISPER -> "[whispers]"
            VoiceEmotion.NEUTRAL -> when (toneStyle) {
                DubbingToneStyle.DRAMATIC_ACTING -> "[warmly]"
                DubbingToneStyle.CASUAL_CONVERSATIONAL -> "[cheerfully]"
                DubbingToneStyle.DOCUMENTARY_NARRATOR -> "[calmly]"
                DubbingToneStyle.CINEMA_NATURAL -> ""
            }
        }
        return if (cue.isNotBlank()) "$cue $clean" else clean
    }

    fun buildDynamicElevenV3Settings(
        emotion: VoiceEmotion,
        expressiveness: Float
    ): VoiceSettingsDto {
        val exp = expressiveness.coerceIn(0f, 1f)
        val baseStability = emotion.stability
        val dynamicStability = (baseStability - (exp - 0.5f) * 0.26f).coerceIn(0.20f, 0.80f)
        val dynamicStyle = (emotion.style + exp * 0.44f).coerceIn(0.18f, 0.90f)
        return VoiceSettingsDto(
            stability = dynamicStability,
            similarityBoost = emotion.similarityBoost,
            style = dynamicStyle,
            useSpeakerBoost = true
        )
    }

    fun selectBestPersianVoice(
        tts: TextToSpeech?,
        archetype: VoiceCharacterArchetype? = null
    ) {
        val engine = tts ?: return
        try {
            val voices = engine.voices.orEmpty().filter { v ->
                v.locale.language.equals("fa", ignoreCase = true) &&
                    !v.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
            val genderKeyword = when {
                archetype?.isFemale == true -> "female"
                archetype != null -> "male"
                else -> null
            }
            val matchedGender = if (genderKeyword != null) {
                voices.firstOrNull { v ->
                    v.name.lowercase(Locale.US).contains(genderKeyword) ||
                        v.features.any { f -> f.lowercase(Locale.US).contains(genderKeyword) }
                }
            } else {
                null
            }
            val bestFa = matchedGender ?: voices.sortedWith(
                compareByDescending<android.speech.tts.Voice> { !it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
            ).firstOrNull()

            if (bestFa != null) {
                engine.voice = bestFa
            } else {
                engine.setLanguage(Locale("fa", "IR"))
            }
        } catch (_: Exception) {
        }
    }

    fun computeCharacterFinalPitchAndRate(
        archetype: VoiceCharacterArchetype,
        emotion: VoiceEmotion,
        recommendedPitch: Float,
        recommendedSpeed: Float,
        userPitch: Float,
        userSpeed: Float,
        expressiveness: Float,
        applyArchetypeShift: Boolean = true
    ): Pair<Float, Float> {
        val exp = expressiveness.coerceIn(0f, 1f)
        val (emoPitchMul, emoRateMul) = when (emotion) {
            VoiceEmotion.EXCITED -> (1.0f + 0.12f * exp) to (1.0f + 0.08f * exp)
            VoiceEmotion.HAPPY -> (1.0f + 0.07f * exp) to (1.0f + 0.04f * exp)
            VoiceEmotion.CALM -> (1.0f - 0.05f * exp) to (1.0f - 0.08f * exp)
            VoiceEmotion.SAD -> (1.0f - 0.10f * exp) to (1.0f - 0.13f * exp)
            VoiceEmotion.CRYING -> (1.0f + 0.06f * exp) to (1.0f - 0.18f * exp) // trembling broken sobbing cadence
            VoiceEmotion.LAUGHING -> (1.0f + 0.14f * exp) to (1.0f + 0.11f * exp)
            VoiceEmotion.FEARFUL -> (1.0f + 0.13f * exp) to (1.0f + 0.12f * exp)
            VoiceEmotion.SHOUTING -> (1.0f + 0.10f * exp) to (1.0f + 0.09f * exp)
            VoiceEmotion.SERIOUS -> (1.0f - 0.09f * exp) to (1.0f - 0.02f * exp)
            VoiceEmotion.WHISPER -> (1.0f - 0.06f * exp) to (1.0f - 0.15f * exp)
            VoiceEmotion.NEUTRAL -> 1.0f to 1.0f
        }

        val charPitch = if (applyArchetypeShift) archetype.defaultTtsPitch else 1.0f
        val charSpeed = if (applyArchetypeShift) archetype.defaultTtsSpeed else 1.0f

        val finalPitch = (charPitch * userPitch * recommendedPitch * emoPitchMul).coerceIn(0.65f, 1.55f)
        val finalRate = (charSpeed * userSpeed * recommendedSpeed * emoRateMul).coerceIn(0.72f, 1.38f)
        return finalPitch to finalRate
    }

    fun speakExpressiveAndroidTts(
        tts: TextToSpeech?,
        text: String,
        emotion: VoiceEmotion,
        archetype: VoiceCharacterArchetype = VoiceCharacterArchetype.ADULT_MALE,
        recommendedPitch: Float,
        recommendedSpeed: Float,
        userPitch: Float,
        userSpeed: Float,
        expressiveness: Float,
        volume: Float,
        utterancePrefix: String,
        flushQueue: Boolean = false
    ) {
        val engine = tts ?: return
        val clean = text
            .replace(Regex("\\[[a-zA-Z_ ]+]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isBlank()) return

        selectBestPersianVoice(engine, archetype)

        val exp = expressiveness.coerceIn(0f, 1f)
        val (finalPitch, finalRate) = computeCharacterFinalPitchAndRate(
            archetype = archetype,
            emotion = emotion,
            recommendedPitch = recommendedPitch,
            recommendedSpeed = recommendedSpeed,
            userPitch = userPitch,
            userSpeed = userSpeed,
            expressiveness = exp,
            applyArchetypeShift = true
        )

        val clauses = clean.split(Regex("(?<=[.!؟…،])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(clean) }

        val volParams = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
        }

        clauses.forEachIndexed { idx, clause ->
            val isQuestion = clause.endsWith("؟") || clause.endsWith("?")
            val isExclaim = clause.endsWith("!")
            val clausePitch = when {
                emotion == VoiceEmotion.CRYING -> {
                    // Slight tremolo wavering across phrases for crying realism
                    val wave = if (idx % 2 == 0) 1.05f else 0.94f
                    (finalPitch * wave).coerceIn(0.65f, 1.55f)
                }
                isQuestion -> (finalPitch * (1.0f + 0.06f * exp)).coerceIn(0.65f, 1.55f)
                isExclaim -> (finalPitch * (1.0f + 0.05f * exp)).coerceIn(0.65f, 1.55f)
                idx == clauses.lastIndex && clauses.size > 1 -> (finalPitch * 0.97f).coerceIn(0.65f, 1.55f)
                else -> finalPitch
            }
            engine.setPitch(clausePitch)
            engine.setSpeechRate(finalRate)
            val mode = if (flushQueue && idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(clause, mode, volParams, "${utterancePrefix}_$idx")
            if (idx < clauses.lastIndex) {
                val pauseMs = when (emotion) {
                    VoiceEmotion.CRYING -> 210L
                    VoiceEmotion.SAD, VoiceEmotion.WHISPER -> 160L
                    else -> 105L
                }
                engine.playSilentUtterance(pauseMs, TextToSpeech.QUEUE_ADD, "${utterancePrefix}_p$idx")
            }
        }
    }
}
