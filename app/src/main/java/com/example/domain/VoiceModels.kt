package com.example.domain

enum class SpeechLanguage(
    val code: String,
    val titleFa: String,
    val nativeName: String
) {
    PERSIAN("fa", "فارسی", "فارسی"),
    ENGLISH("en", "انگلیسی", "English"),
    ARABIC("ar", "عربی", "العربية"),
    TURKISH("tr", "ترکی استانبولی", "Türkçe"),
    FRENCH("fr", "فرانسوی", "Français"),
    GERMAN("de", "آلمانی", "Deutsch"),
    SPANISH("es", "اسپانیایی", "Español"),
    ITALIAN("it", "ایتالیایی", "Italiano"),
    RUSSIAN("ru", "روسی", "Русский"),
    JAPANESE("ja", "ژاپنی", "日本語"),
    KOREAN("ko", "کره‌ای", "한국어"),
    HINDI("hi", "هندی", "हिन्दी");

    companion object {
        fun fromCode(code: String): SpeechLanguage =
            entries.firstOrNull { it.code == code } ?: PERSIAN
    }
}

enum class PersianAccent(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val stylePromptHint: String
) {
    STANDARD(
        id = "standard",
        titleFa = "رسمی و کتابی",
        subtitleFa = "تلفظ معیار و دقیق فارسی برای پادکست، خبر و کتاب صوتی",
        stylePromptHint = "فارسی رسمی، معیار و ادبی با علائم نگارشی دقیق و مکث‌های طبیعی"
    ),
    TEHRANI(
        id = "tehrani",
        titleFa = "تهرانی محاوره‌ای",
        subtitleFa = "گویش طبیعی روزمره و خودمانی پایتخت",
        stylePromptHint = "فارسی محاوره‌ای تهرانی (مثلاً میرم، میگم، می‌خوام، چطوره) با آهنگ طبیعی گفتگو"
    ),
    SHIRAZI(
        id = "shirazi",
        titleFa = "شیرازی",
        subtitleFa = "آهنگ شیرین و کشیدگی ملایم واکه‌ها",
        stylePromptHint = "لهجه شیرین شیرازی با اصطلاحات و افعال محاوره‌ای شیرازی (مثل کاکو، میگوم، چطو) و آهنگ نرم"
    ),
    ISFAHANI(
        id = "isfahani",
        titleFa = "اصفهانی",
        subtitleFa = "گویش اصیل و ریتم خوش‌آهنگ اصفهانی",
        stylePromptHint = "لهجه اصفهانی با لحن صمیمی، کشیدگی و تلفظ اصیل واژگان اصفهانی (مثل دادا، چطوریس، می‌خوام)"
    ),
    MASHHADI(
        id = "mashhadi",
        titleFa = "مشهدی / خراسانی",
        subtitleFa = "گرمای بیان خراسانی با ضرب‌آهنگ خاص",
        stylePromptHint = "لهجه مشهدی و خراسانی طبیعی با واژه‌گزینی و آهنگ گرم مشهدی (مثل یره، موگوم، چطوره)"
    ),
    TABRIZI(
        id = "tabrizi",
        titleFa = "آذری / تبریزی",
        subtitleFa = "فارسی با تکیه‌ها و گرمای آهنگ آذری",
        stylePromptHint = "فارسی با ریتم، تکیه هجایی و صمیمیت گویش تبریزی"
    ),
    GILAKI(
        id = "gilaki",
        titleFa = "شمالی / گیلکی",
        subtitleFa = "گویش نرم و خوش‌ریتم خطه شمال",
        stylePromptHint = "فارسی با آهنگ ملودیک شمالی و حس و حال محاوره گیلانی"
    ),
    BANDARI(
        id = "bandari",
        titleFa = "جنوبی / بندری",
        subtitleFa = "گویش پرانرژی و گرم جنوب ایران",
        stylePromptHint = "فارسی با ریتم گرم، پرانرژی و اصطلاحات ملایم جنوبی"
    );

    companion object {
        fun fromId(id: String): PersianAccent =
            entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

enum class VoiceEmotion(
    val id: String,
    val titleFa: String,
    val stability: Float,
    val similarityBoost: Float,
    val style: Float,
    val elevenV3Cue: String
) {
    NEUTRAL("neutral", "عادی و طبیعی", 0.55f, 0.82f, 0.15f, ""),
    HAPPY("happy", "شاد و پرانرژی", 0.42f, 0.78f, 0.50f, "[cheerfully]"),
    EXCITED("excited", "هیجان‌زده", 0.34f, 0.75f, 0.70f, "[excited]"),
    CALM("calm", "آرام و ملایم", 0.74f, 0.86f, 0.10f, "[calmly]"),
    SAD("sad", "غمگین و احساسی", 0.64f, 0.82f, 0.35f, "[sorrowfully]"),
    CRYING("crying", "گریه و هق‌هق", 0.36f, 0.80f, 0.78f, "[crying] [sobbing]"),
    LAUGHING("laughing", "خنده و شوخی", 0.32f, 0.76f, 0.76f, "[laughs] [cheerfully]"),
    FEARFUL("fearful", "ترس و دلهره", 0.38f, 0.79f, 0.72f, "[gasps] [fearfully]"),
    SHOUTING("shouting", "فریاد و خشم", 0.30f, 0.75f, 0.85f, "[shouts] [angrily]"),
    SERIOUS("serious", "جدی و قاطع", 0.80f, 0.86f, 0.12f, "[firmly]"),
    WHISPER("whisper", "زمزمه و صمیمی", 0.48f, 0.76f, 0.42f, "[whispers]");

    companion object {
        fun fromId(id: String): VoiceEmotion =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: NEUTRAL
    }
}

enum class VoiceCharacterArchetype(
    val id: String,
    val titleFa: String,
    val defaultTtsPitch: Float,
    val defaultTtsSpeed: Float,
    val openAiVoiceName: String,
    val elevenLabsPresetVoiceId: String,
    val isFemale: Boolean
) {
    ADULT_MALE(
        id = "adult_male",
        titleFa = "مرد بالغ",
        defaultTtsPitch = 0.86f,
        defaultTtsSpeed = 0.99f,
        openAiVoiceName = "onyx",
        elevenLabsPresetVoiceId = "pNInz6obpgDQGcFmaJgB",
        isFemale = false
    ),
    ADULT_FEMALE(
        id = "adult_female",
        titleFa = "زن بالغ",
        defaultTtsPitch = 1.18f,
        defaultTtsSpeed = 1.02f,
        openAiVoiceName = "nova",
        elevenLabsPresetVoiceId = "EXAVITQu4vr4xnSDxMaL",
        isFemale = true
    ),
    YOUNG_BOY(
        id = "young_boy",
        titleFa = "پسر / نوجوان",
        defaultTtsPitch = 1.12f,
        defaultTtsSpeed = 1.04f,
        openAiVoiceName = "echo",
        elevenLabsPresetVoiceId = "TX3LPaxmHKxFdv7VOQHJ",
        isFemale = false
    ),
    YOUNG_GIRL(
        id = "young_girl",
        titleFa = "دختربچه / کودک",
        defaultTtsPitch = 1.34f,
        defaultTtsSpeed = 1.06f,
        openAiVoiceName = "shimmer",
        elevenLabsPresetVoiceId = "jBpfuIE2acCO8z3wKNLl",
        isFemale = true
    ),
    ELDER_MALE(
        id = "elder_male",
        titleFa = "مرد مسن",
        defaultTtsPitch = 0.78f,
        defaultTtsSpeed = 0.90f,
        openAiVoiceName = "onyx",
        elevenLabsPresetVoiceId = "VR6AewLTigWG4xSOukaG",
        isFemale = false
    ),
    ELDER_FEMALE(
        id = "elder_female",
        titleFa = "زن مسن",
        defaultTtsPitch = 1.05f,
        defaultTtsSpeed = 0.91f,
        openAiVoiceName = "shimmer",
        elevenLabsPresetVoiceId = "MF3mGyEYCl7XYWbV9V6O",
        isFemale = true
    ),
    NARRATOR(
        id = "narrator",
        titleFa = "راوی سینمایی",
        defaultTtsPitch = 0.95f,
        defaultTtsSpeed = 0.98f,
        openAiVoiceName = "fable",
        elevenLabsPresetVoiceId = "onwK4e9ZLuTAKqWW03F9",
        isFemale = false
    );

    companion object {
        fun fromId(id: String?): VoiceCharacterArchetype =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: ADULT_MALE
    }
}

enum class CinemaDubbingStyle(
    val id: String,
    val titleFa: String,
    val promptDirective: String
) {
    NATURAL_CINEMA(
        id = "natural_cinema",
        titleFa = "درام سینمایی طبیعی",
        promptDirective = "سبک دوبله فیلم‌های مدرن: فارسی بسیار شیک، روان، گرم و واقعی بدون مصنوعی بودن."
    ),
    CLASSIC_PERSIAN(
        id = "classic_persian",
        titleFa = "دوبله کلاسیک گرم ایران",
        promptDirective = "سبک دوبلاژ طلایی و کلاسیک سینمای ایران (مثل گویندگان پیشکسوت): واژه‌گزینی فاخر اما صمیمی، با ابهت، مکث‌های دراماتیک و آهنگ صدای شیک."
    ),
    ANIME_DYNAMIC(
        id = "anime_dynamic",
        titleFa = "انیمه و انیمیشن پرانرژی",
        promptDirective = "سبک دوبله انیمیشن و انیمه: انرژی بالا، واکنش‌های احساسی سریع، غلیظ و پرکشش."
    ),
    DOCUMENTARY_DEEP(
        id = "documentary_deep",
        titleFa = "مستند و روایت پخته",
        promptDirective = "سبک مستند سینمایی: بیان محکم، شمرده، گرم و جذاب بدون شتاب‌زدگی."
    );

    companion object {
        fun fromId(id: String): CinemaDubbingStyle =
            entries.firstOrNull { it.id == id } ?: NATURAL_CINEMA
    }
}

enum class IranGatewayPreset(
    val id: String,
    val titleFa: String,
    val baseUrl: String,
    val defaultModel: String,
    val supportsWhisperStt: Boolean,
    val supportsTts: Boolean
) {
    AVALAI(
        id = "avalai",
        titleFa = "آوال‌ای‌آی (AvalAI — همه‌کاره)",
        baseUrl = "https://api.avalai.ir/v1",
        defaultModel = "gpt-4o-mini",
        supportsWhisperStt = true,
        supportsTts = true
    ),
    LIARA(
        id = "liara",
        titleFa = "لیارا AI (Liara — چت + TTS)",
        baseUrl = "https://ai.liara.ir/api/v1",
        defaultModel = "openai/gpt-4o-mini",
        supportsWhisperStt = false,
        supportsTts = true
    ),
    GAPGPT(
        id = "gapgpt",
        titleFa = "گپ‌جی‌پی‌تی (GapGPT)",
        baseUrl = "https://api.gapgpt.app/v1",
        defaultModel = "gpt-4o-mini",
        supportsWhisperStt = true,
        supportsTts = true
    ),
    METIS(
        id = "metis",
        titleFa = "متیس AI (Metis AI)",
        baseUrl = "https://api.metisai.ir/openai/v1",
        defaultModel = "gpt-4o-mini",
        supportsWhisperStt = false,
        supportsTts = false
    );

    companion object {
        fun fromId(id: String): IranGatewayPreset =
            entries.firstOrNull { it.id == id } ?: AVALAI
    }
}

data class PersianTrainingPrompt(
    val title: String,
    val text: String,
    val focusArea: String
)

object PersianCalibrationCorpus {
    val prompts = listOf(
        PersianTrainingPrompt(
            title = "جمله متعادل آوایی ۱",
            text = "سلام! امروز هوای شهر بسیار دلپذیر و آرام است و من با صدای طبیعی خودم در حال آموزش مدل هوشمند هستم تا تمام واج‌های فارسی را دقیق و روان بشناسد.",
            focusArea = "واکه‌های بلند و همخوان‌های پایه"
        ),
        PersianTrainingPrompt(
            title = "جمله پرچالش واجی (ژ، گ، چ، پ، ق)",
            text = "پژوهشگران جوان در باغ چنار، قصه‌های شیرین و گزارش‌های دقیق درباره پرواز پرندگان مهاجر را با هیجان فراوان برای جمع تعریف کردند.",
            focusArea = "واکاوی دقیق صامت‌های خاص فارسی"
        ),
        PersianTrainingPrompt(
            title = "جمله محاوره‌ای و احساسی",
            text = "راستش رو بخوای، وقتی با یه لحن گرم و صمیمی با هم صحبت می‌کنیم، کلمات خیلی واقعی‌تر و زنده‌تر به گوش می‌رسن؛ مگه نه؟",
            focusArea = "آهنگ پرسشی و مکث محاوره‌ای"
        ),
        PersianTrainingPrompt(
            title = "متن خبری و جدی",
            text = "بر اساس آخرین گزارش‌های فناوری، سیستم‌های نوین پردازش گفتار می‌توانند لحن، سرعت و ظرافت‌های لهجه‌های گوناگون زبان فارسی را با دقت بالا بازسازی کنند.",
            focusArea = "ریتم رسمی و قاطعیت بیان"
        )
    )
}

data class AudioQualityAnalysis(
    val qualityScore: Int,
    val clarityNote: String,
    val detectedAccent: String,
    val naturalPitchLabel: String,
    val improvementTip: String
)

enum class DubbingEngineMethod(
    val id: String,
    val titleFa: String,
    val shortBadge: String
) {
    AUTO_FAILOVER("auto", "خودکار ۶ موتوره (ضدقطعی)", "Failover هوشمند"),
    IRAN_NO_VPN("iran_no_vpn", "درگاه بدون فیلترشکن ایران (AvalAI/Liara/GapGPT)", "ایران بدون VPN"),
    GEMINI_PRIMARY("gemini_primary", "۱. جمینای 3.8 صوتی (اصلی)", "Gemini 3.8"),
    GEMINI_BACKUP("gemini_backup", "۲. جمینای 3.5 لایت (پشتیبان)", "Gemini 3.5 Lite"),
    OPENAI_WHISPER("openai", "۳. OpenAI Whisper + GPT", "OpenAI + پروکسی"),
    ELEVENLABS_SCRIBE("elevenlabs_scribe", "۴. ElevenLabs Scribe + ترجمه", "Scribe + پروکسی"),
    CUSTOM_AI("custom_ai", "۵. هوش مصنوعی دلخواه (Groq/DeepSeek)", "AI دلخواه");

    companion object {
        fun fromId(id: String): DubbingEngineMethod =
            entries.firstOrNull { it.id == id } ?: AUTO_FAILOVER
    }
}

enum class DubbingToneStyle(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val directorHint: String
) {
    CINEMA_NATURAL(
        id = "cinema",
        titleFa = "دوبله سینمایی طبیعی",
        subtitleFa = "گرم، زنده و مشابه دوبلاژ حرفه‌ای فیلم و سریال",
        directorHint = "مثل مدیر دوبلاژ حرفه‌ای ایران: فارسی کاملاً زنده، خوش‌آهنگ، شیک و سینمایی؛ هرگز ترجمه کتابی و تحت‌اللفظی ننویس."
    ),
    CASUAL_CONVERSATIONAL(
        id = "casual",
        titleFa = "محاوره‌ای و خودمانی",
        subtitleFa = "شکسته‌نویسی کامل روزمره، واکنش‌های واقعی و مکث طبیعی",
        directorHint = "کاملاً محاوره‌ای، روزمره و صمیمی با شکسته‌گویی طبیعی افعال (می‌خوام، می‌شه، بریم، چطوره) و حس واقعی گفتگو."
    ),
    DRAMATIC_ACTING(
        id = "dramatic",
        titleFa = "دراماتیک و پراحساس",
        subtitleFa = "بازیگری عمیق حسی با افت‌وخیز شدید لحن، گریه، خنده و مکث‌های نمایشی",
        directorHint = "پر از احساس، نمایشی و عمیق؛ حس واقعی گوینده (گریه، خنده، فریاد، ترس، نجوا) را با علامت‌گذاری و کلمات کاملاً منتقل کن."
    ),
    DOCUMENTARY_NARRATOR(
        id = "documentary",
        titleFa = "راوی و مستند گرم",
        subtitleFa = "پخته، جذاب، باوقار و پرکشش بدون لحن خشک ماشینی",
        directorHint = "لحن راوی گرم، جذاب و خوش‌بیان مستندهای مدرن؛ رسا، انسانی و گوش‌نواز بدون خشکی ماشینی."
    );

    companion object {
        fun fromId(id: String): DubbingToneStyle =
            entries.firstOrNull { it.id == id } ?: CINEMA_NATURAL
    }
}

enum class DubbingOutputMode(val id: String, val titleFa: String) {
    DUB_ONLY("dub_only", "فقط دوبله"),
    SUBTITLE_ONLY("subtitle_only", "فقط زیرنویس"),
    DUB_AND_SUBTITLE("both", "دوبله + زیرنویس");

    companion object {
        fun fromId(id: String): DubbingOutputMode =
            entries.firstOrNull { it.id == id } ?: DUB_AND_SUBTITLE
    }
}

data class DubbingTranslationResult(
    val hasSpeech: Boolean,
    val detectedLanguage: String,
    val originalTranscript: String,
    val persianTranslation: String,
    val detectedEmotion: VoiceEmotion = VoiceEmotion.NEUTRAL,
    val speakerCharacter: VoiceCharacterArchetype = VoiceCharacterArchetype.ADULT_MALE,
    val speakerSlotLabel: String = "",
    val isSoundEffectOnly: Boolean = false,
    val expressiveActingText: String = "",
    val recommendedPitch: Float = 1.0f,
    val recommendedSpeed: Float = 1.0f,
    val engineUsedName: String = "Gemini 3.8",
    val failoverOccurred: Boolean = false
)
