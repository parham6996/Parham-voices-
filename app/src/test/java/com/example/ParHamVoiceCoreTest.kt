package com.example

import com.example.audio.AudioMixMasterEngine
import com.example.audio.AudioWaveformExtractor
import com.example.audio.AutoTuneScale
import com.example.audio.BackingBeatStyle
import com.example.audio.BeatLaunchpadEngine
import com.example.audio.DspEffectPreset
import com.example.audio.LaunchpadPadType
import com.example.audio.WsolaTimeStretch
import com.example.data.remote.ApiProviderTarget
import com.example.data.remote.ApiTestResult
import com.example.data.remote.ProxyConfigStore
import com.example.data.remote.ProxyProtocol
import com.example.data.remote.ProxySettings
import com.example.data.remote.UserApiKeysConfig
import com.example.domain.CinemaDubbingStyle
import com.example.domain.DubbingEngineMethod
import com.example.domain.DubbingToneStyle
import com.example.domain.IranGatewayPreset
import com.example.domain.PersianAccent
import com.example.domain.VoiceCharacterArchetype
import com.example.domain.VoiceEmotion
import com.example.dubbing.CharacterMemoryBook
import com.example.dubbing.PersianExpressiveDubbingStylizer
import com.example.dubbing.SpeechVsSfxClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class ParHamVoiceCoreTest {

    @Test
    fun apiProviderTargetsIncludeIranGatewayAndGlobalEngines() {
        assertEquals(5, ApiProviderTarget.entries.size)
        assertTrue(ApiProviderTarget.entries.contains(ApiProviderTarget.IRAN_GATEWAY))
        val result = ApiTestResult(
            provider = ApiProviderTarget.IRAN_GATEWAY,
            success = true,
            message = "OK",
            latencyMs = 95L
        )
        assertTrue(result.success)
        assertEquals(95L, result.latencyMs)

        assertTrue(IranGatewayPreset.AVALAI.baseUrl.contains("avalai.ir"))
        assertTrue(IranGatewayPreset.LIARA.baseUrl.contains("liara.ir"))
        assertTrue(IranGatewayPreset.GAPGPT.baseUrl.contains("gapgpt.app"))
        assertTrue(IranGatewayPreset.METIS.baseUrl.contains("metisai.ir"))
    }

    @Test
    fun dubbingEngineMethodsExposeAutoFailoverIranGatewayAndFiveEngines() {
        val entries = DubbingEngineMethod.entries
        assertEquals(7, entries.size)
        assertEquals(DubbingEngineMethod.IRAN_NO_VPN, DubbingEngineMethod.fromId("iran_no_vpn"))
        assertEquals(DubbingEngineMethod.CUSTOM_AI, DubbingEngineMethod.fromId("custom_ai"))
        assertEquals(DubbingEngineMethod.AUTO_FAILOVER, DubbingEngineMethod.fromId("unknown_id"))
    }

    @Test
    fun userApiKeysConfigHasAnyKeyReflectsProviderState() {
        val emptyCfg = UserApiKeysConfig()
        assertFalse(emptyCfg.hasAnyKey())

        assertTrue(emptyCfg.copy(elevenLabsApiKey = "sk_test_11labs").hasAnyKey())
        assertTrue(emptyCfg.copy(geminiDirectApiKey = "AIza_test").hasAnyKey())
        assertTrue(emptyCfg.copy(openAiApiKey = "sk_openai").hasAnyKey())
        assertTrue(emptyCfg.copy(iranGatewayApiKey = "aa_iran_gateway").hasAnyKey())
        assertTrue(
            emptyCfg.copy(
                customProviderName = "Groq",
                customProviderBaseUrl = "https://api.groq.com/openai/v1",
                customProviderApiKey = "gsk_custom",
                customProviderModel = "llama-3.3-70b-versatile"
            ).hasAnyKey()
        )
    }

    @Test
    fun proxySettingsAndCustomProxyListParsing() {
        val defaults = ProxySettings()
        assertFalse(defaults.enabled)
        assertEquals(ProxyProtocol.HTTP, defaults.protocol)
        assertEquals(10808, defaults.port)

        val parsed = ProxyConfigStore.parseProxyList(
            """
            # comment line
            127.0.0.1:10809
            socks5://127.0.0.1:10808
            http://45.10.20.30:8080:user1:pass1
            invalid_entry
            """.trimIndent()
        )
        assertEquals(3, parsed.size)
        assertEquals(ProxyProtocol.HTTP, parsed[0].protocol)
        assertEquals(10809, parsed[0].port)
        assertEquals(ProxyProtocol.SOCKS, parsed[1].protocol)
        assertEquals(10808, parsed[1].port)
        assertEquals("user1", parsed[2].username)
        assertEquals("pass1", parsed[2].password)
    }

    @Test
    fun speechVsSfxClassifierRejectsExplosionAndDetectsGenderAgePitch() {
        val sr = 16000
        // 1. Sub-bass transient explosion boom (55Hz damped pulse)
        val boomShorts = ShortArray(sr / 2) { i ->
            val env = exp(-i / 1400.0)
            (sin(2.0 * PI * 52.0 * i / sr) * env * 22000.0).toInt().toShort()
        }
        val boomBytes = shortsToPcm16(boomShorts)
        val boomRes = SpeechVsSfxClassifier.analyzePcm16Mono(boomBytes, sr)
        assertTrue(boomRes.isLikelySoundEffectOnly)

        // 2. Female voice pitch (~220Hz modulated harmonic)
        val femaleShorts = ShortArray(sr / 2) { i ->
            val carrier = sin(2.0 * PI * 220.0 * i / sr) + 0.35 * sin(2.0 * PI * 440.0 * i / sr)
            val env = 0.6 + 0.4 * sin(2.0 * PI * 4.0 * i / sr)
            (carrier * env * 9000.0).toInt().toShort()
        }
        val femaleRes = SpeechVsSfxClassifier.analyzePcm16Mono(shortsToPcm16(femaleShorts), sr)
        assertFalse(femaleRes.isLikelySoundEffectOnly)
        assertEquals(VoiceCharacterArchetype.ADULT_FEMALE, femaleRes.inferredArchetype)

        // 3. Young girl voice (~315Hz harmonic)
        val girlShorts = ShortArray(sr / 2) { i ->
            val carrier = sin(2.0 * PI * 315.0 * i / sr) + 0.3 * sin(2.0 * PI * 630.0 * i / sr)
            (carrier * 9000.0).toInt().toShort()
        }
        val girlRes = SpeechVsSfxClassifier.analyzePcm16Mono(shortsToPcm16(girlShorts), sr)
        assertFalse(girlRes.isLikelySoundEffectOnly)
        assertEquals(VoiceCharacterArchetype.YOUNG_GIRL, girlRes.inferredArchetype)

        // 4. Adult male voice (~115Hz harmonic)
        val maleShorts = ShortArray(sr / 2) { i ->
            val carrier = sin(2.0 * PI * 115.0 * i / sr) + 0.4 * sin(2.0 * PI * 230.0 * i / sr)
            (carrier * 10000.0).toInt().toShort()
        }
        val maleRes = SpeechVsSfxClassifier.analyzePcm16Mono(shortsToPcm16(maleShorts), sr)
        assertFalse(maleRes.isLikelySoundEffectOnly)
        assertEquals(VoiceCharacterArchetype.ADULT_MALE, maleRes.inferredArchetype)
    }

    @Test
    fun characterMemoryBookMaintainsSpeakerSlotsAndDialogueContext() {
        val book = CharacterMemoryBook()
        val womanSlot = book.resolveCharacterSlot(VoiceCharacterArchetype.ADULT_FEMALE, 215f)
        val womanRepeat = book.resolveCharacterSlot(VoiceCharacterArchetype.ADULT_FEMALE, 222f)
        val girlSlot = book.resolveCharacterSlot(VoiceCharacterArchetype.YOUNG_GIRL, 315f)

        assertEquals(womanSlot.slotId, womanRepeat.slotId)
        assertNotEquals(womanSlot.slotId, girlSlot.slotId)

        book.recordDialogueLine(womanSlot.displayLabel, "گریه نکن عزیزم")
        val hint = book.buildContextPromptHint()
        assertTrue(hint.contains(womanSlot.displayLabel))
        assertTrue(hint.contains("گریه نکن"))
    }

    @Test
    fun wsolaLipSyncFitMatchesTargetWindowDuration() {
        val sr = 16000
        val source = ShortArray(sr) { i ->
            (sin(2.0 * PI * 200.0 * i / sr) * 16000.0).toInt().toShort()
        }
        val targetCount = 12800 // fit 1.0s into ~0.80s (within 1.28x maxSpeedRate)
        val fitted = WsolaTimeStretch.fitDubbedSpeechToWindowPcm16(
            pcm = source,
            targetWindowSamples = targetCount
        )
        assertTrue(fitted.size < source.size)
        val rms = sqrt(fitted.map { val f = it / 32768.0; f * f }.average())
        assertTrue(rms > 0.1)
    }

    @Test
    fun audioMixMasterVocalBoothDspChainProducesValidAudio() {
        val sr = 44100
        val input = FloatArray(sr / 4) { i ->
            (sin(2.0 * PI * 224.0 * i / sr) * 0.45 + 0.08 * sin(2.0 * PI * 6500.0 * i / sr)).toFloat()
        }
        val deEssed = AudioMixMasterEngine.applyDeEsser(input, sr, 0.7f)
        val tuned = AudioMixMasterEngine.applyAutoTunePitchCorrection(deEssed, sr, AutoTuneScale.MINOR, 57, 0.8f)
        val doubled = AudioMixMasterEngine.applyVocalDoublerAndHarmony(tuned, sr, enableDoubler = true, harmonyAmount = 0.4f)
        val preset = AudioMixMasterEngine.applyStudioEffectPreset(doubled, sr, DspEffectPreset.TUBE_WARMTH)

        assertEquals(input.size, preset.size)
        assertTrue(preset.all { !it.isNaN() && it in -1f..1f })
        assertTrue(preset.any { it != 0f })
    }

    @Test
    fun persianExpressiveStylizerAndMultiCharacterTtsParams() {
        val colloquial = PersianExpressiveDubbingStylizer.stylizeColloquialPersian(
            rawText = "آیا می‌توانم اینجا صحبت کنم؟ این بسیار خوب است.",
            accent = PersianAccent.TEHRANI,
            toneStyle = DubbingToneStyle.CINEMA_NATURAL,
            emotion = VoiceEmotion.HAPPY,
            cinemaStyle = CinemaDubbingStyle.NATURAL_CINEMA
        )
        assertFalse(colloquial.startsWith("آیا "))
        assertTrue(colloquial.contains("می‌تونم"))

        val cryingActing = PersianExpressiveDubbingStylizer.buildElevenV3ActingText(
            persianText = colloquial,
            emotion = VoiceEmotion.CRYING,
            expressiveness = 0.9f,
            toneStyle = DubbingToneStyle.DRAMATIC_ACTING
        )
        assertTrue(cryingActing.contains("[crying]"))

        val (girlPitch, _) = PersianExpressiveDubbingStylizer.computeCharacterFinalPitchAndRate(
            archetype = VoiceCharacterArchetype.YOUNG_GIRL,
            emotion = VoiceEmotion.LAUGHING,
            recommendedPitch = 1.0f,
            recommendedSpeed = 1.0f,
            userPitch = 1.0f,
            userSpeed = 1.0f,
            expressiveness = 0.8f
        )
        val (malePitch, _) = PersianExpressiveDubbingStylizer.computeCharacterFinalPitchAndRate(
            archetype = VoiceCharacterArchetype.ADULT_MALE,
            emotion = VoiceEmotion.SERIOUS,
            recommendedPitch = 1.0f,
            recommendedSpeed = 1.0f,
            userPitch = 1.0f,
            userSpeed = 1.0f,
            expressiveness = 0.8f
        )
        assertTrue(girlPitch > malePitch)
    }

    @Test
    fun defaultEmptyWaveformHasExpectedVisualizerBarCounts() {
        val empty = AudioWaveformExtractor.defaultEmptyWaveform()
        assertEquals(52, empty.envelopeBars.size)
        assertEquals(240, empty.detailedEnvelope.size)
        assertTrue(empty.envelopeBars.all { it in 0.05f..1.0f })
    }

    @Test
    fun beatLaunchpadSynthesizesGridAndMatchesVocalPitchAndBpm() {
        val engine = BeatLaunchpadEngine()
        val grid = engine.defaultSequencerGrid()
        assertTrue(grid.containsKey(LaunchpadPadType.KICK))
        assertEquals(BeatLaunchpadEngine.STEPS_COUNT, grid[LaunchpadPadType.KICK]?.size)

        val seqAudio = engine.synthesizeSequencerFloatArray(
            bpm = 110,
            grid = grid,
            rootMidiNote = 57,
            targetSamples = 8820,
            sampleRate = BeatLaunchpadEngine.SAMPLE_RATE
        )
        assertEquals(8820, seqAudio.size)
        assertTrue(seqAudio.any { it != 0f })

        val genreBeat = engine.synthesizeGenreBeat(
            style = BackingBeatStyle.PERSIAN_6_8,
            length = 8820,
            sampleRate = BeatLaunchpadEngine.SAMPLE_RATE
        )
        assertEquals(8820, genreBeat.size)
        assertTrue(genreBeat.any { it != 0f })

        val sr = BeatLaunchpadEngine.SAMPLE_RATE
        val vocal = FloatArray(sr * 2) { i ->
            val t = i.toDouble() / sr
            val env = 0.5 + 0.5 * sin(2.0 * PI * 2.0 * t)
            (sin(2.0 * PI * 220.0 * t) * 0.45 * env).toFloat()
        }
        val match = engine.analyzeVocalAndAutoMatchBeat(vocal, sr)
        assertTrue(match.detectedBpm in 72..140)
        assertNotEquals(BackingBeatStyle.NONE, match.recommendedStyle)
        assertEquals(57, match.detectedRootMidi)
    }

    private fun shortsToPcm16(samples: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { bb.putShort(it) }
        return bb.array()
    }
}
