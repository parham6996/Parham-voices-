package com.example.dubbing

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.GeminiVoiceStudioAi
import com.example.data.remote.ElevenLabsApi
import com.example.data.remote.ProxyConfigStore
import com.example.data.remote.TtsRequestBody
import com.example.data.remote.UserApiKeyStore
import com.example.domain.DubbingOutputMode
import com.example.domain.DubbingTranslationResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "PersianDubbingService"
private const val CHANNEL_ID = "avaclone_dubbing_channel"
private const val NOTIFICATION_ID = 4401

class PersianDubbingService : Service() {

    companion object {
        const val ACTION_START_CAPTURE = "com.example.dubbing.ACTION_START_CAPTURE"
        const val ACTION_SHOW_OVERLAY_ONLY = "com.example.dubbing.ACTION_SHOW_OVERLAY_ONLY"
        const val ACTION_TOGGLE_DUBBING = "com.example.dubbing.ACTION_TOGGLE_DUBBING"
        const val ACTION_STOP_SERVICE = "com.example.dubbing.ACTION_STOP_SERVICE"
        const val ACTION_UPDATE_ORIGINAL_VOLUME = "com.example.dubbing.ACTION_UPDATE_ORIGINAL_VOLUME"
        const val ACTION_UPDATE_DUB_VOLUME = "com.example.dubbing.ACTION_UPDATE_DUB_VOLUME"
        const val ACTION_SYNC_OUTPUT_MODE = "com.example.dubbing.ACTION_SYNC_OUTPUT_MODE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_VOLUME = "extra_volume"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val geminiAi by lazy { GeminiVoiceStudioAi() }
    private val failoverEngine by lazy { DubbingFailoverEngine(this, geminiAi) }

    private fun getElevenLabsApi(): ElevenLabsApi {
        val client = ProxyConfigStore.buildOkHttpClient(this, readTimeoutSec = 60L)
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl("https://api.elevenlabs.io/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ElevenLabsApi::class.java)
    }

    private var mediaProjection: MediaProjection? = null
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var dubMediaPlayer: MediaPlayer? = null

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var floatingIconText: TextView? = null
    private var floatingBg: GradientDrawable? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var floatingSubtitleView: View? = null
    private var floatingSubtitleText: TextView? = null

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        DubbingStateBus.setServiceRunning(true)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                PersianExpressiveDubbingStylizer.selectBestPersianVoice(tts)
                val faLocale = Locale("fa", "IR")
                val res = tts?.setLanguage(faLocale)
                ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ttsReady) {
                    tts?.setLanguage(Locale("fa"))
                    ttsReady = true
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        duckOriginalMediaVolume(true)
                    }

                    override fun onDone(utteranceId: String?) {
                        duckOriginalMediaVolume(false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        duckOriginalMediaVolume(false)
                    }
                })
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopAllAndSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE_ORIGINAL_VOLUME -> {
                val vol = intent.getFloatExtra(EXTRA_VOLUME, DubbingStateBus.originalVolume.value)
                DubbingStateBus.setOriginalVolume(vol)
                applyStreamMusicVolume(vol)
                return START_STICKY
            }

            ACTION_UPDATE_DUB_VOLUME -> {
                val vol = intent.getFloatExtra(EXTRA_VOLUME, DubbingStateBus.dubVolume.value)
                DubbingStateBus.setDubVolume(vol)
                dubMediaPlayer?.setVolume(vol, vol)
                return START_STICKY
            }

            ACTION_SYNC_OUTPUT_MODE -> {
                updateFloatingSubtitleBanner(DubbingStateBus.currentSubtitleText.value)
                updateFloatingOverlayAppearance()
                return START_STICKY
            }

            ACTION_SHOW_OVERLAY_ONLY -> {
                if (mediaProjection != null) {
                    promoteToForeground(includeMediaProjectionType = true)
                }
                showFloatingOverlayIfPermitted()
                return START_STICKY
            }

            ACTION_TOGGLE_DUBBING -> {
                val nextActive = !DubbingStateBus.isDubbingActive.value
                DubbingStateBus.setDubbingActive(nextActive)
                updateFloatingOverlayAppearance()
                if (nextActive && mediaProjection == null) {
                    DubbingStateBus.setStatus("برای دوبله صدای پخش‌کننده، لطفاً در اپلیکیشن دسترسی ضبط صدای رسانه را فعال کنید.")
                } else if (nextActive) {
                    DubbingStateBus.setStatus("در حال گوش دادن به گفتار واقعی و فیلتر کردن انفجار/افکت...")
                } else {
                    DubbingStateBus.setStatus("دوبله متوقف شد (دکمه کوچک شناور در حالت محو).")
                }
                return START_STICKY
            }

            ACTION_START_CAPTURE -> {
                promoteToForeground(includeMediaProjectionType = true)
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val resultData: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA)
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startMediaProjectionCapture(resultCode, resultData)
                    showFloatingOverlayIfPermitted()
                } else {
                    DubbingStateBus.setStatus("اجازه ضبط صدای پخش‌کننده صادر نشد.")
                }
                return START_STICKY
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground(includeMediaProjectionType: Boolean) {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && includeMediaProjectionType) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMediaProjectionCapture(resultCode: Int, resultData: Intent) {
        captureJob?.cancel()
        stopAudioRecordOnly()
        mediaProjection?.stop()
        failoverEngine.resetDialogueMemory()

        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = pm.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            DubbingStateBus.setStatus("امکان دسترسی به خروجی صدای پخش‌کننده صادر نشد.")
            return
        }
        mediaProjection = projection

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            DubbingStateBus.setStatus("ضبط داخلی صدای پخش‌کننده نیازمند اندروید ۱۰ به بالا است.")
            return
        }

        DubbingStateBus.setDubbingActive(true)
        DubbingStateBus.setStatus("متصل به صدای پخش‌کننده • تشخیص خودکار کاراکتر و عبور از افکت/انفجار...")
        updateFloatingOverlayAppearance()

        captureJob = serviceScope.launch(Dispatchers.IO) {
            runPlaybackCaptureLoop(projection)
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private suspend fun runPlaybackCaptureLoop(projection: MediaProjection) {
        val sampleRate = 16000
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val recorder = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBuf * 2)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            DubbingStateBus.setStatus("امکان اتصال به خروجی صدای پخش‌کننده وجود نداشت.")
            return
        }

        audioRecord = recorder
        recorder.startRecording()

        val shortBuf = ShortArray(minBuf / 2)
        val pcmAccumulator = ByteArrayOutputStream()
        var speechDetectedInChunk = false
        var chunkStartMs = System.currentTimeMillis()
        var lastSoundMs = System.currentTimeMillis()

        try {
            while (serviceScope.isActive && mediaProjection != null) {
                val read = recorder.read(shortBuf, 0, shortBuf.size)
                if (read <= 0) continue

                if (!DubbingStateBus.isDubbingActive.value) {
                    pcmAccumulator.reset()
                    speechDetectedInChunk = false
                    chunkStartMs = System.currentTimeMillis()
                    continue
                }

                if (DubbingStateBus.isProcessingSegment.value) {
                    continue
                }

                var sumSq = 0.0
                val byteBuf = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until read) {
                    val s = shortBuf[i]
                    byteBuf.putShort(s)
                    sumSq += s.toDouble() * s.toDouble()
                }
                val rms = sqrt(sumSq / read) / 32768.0
                val now = System.currentTimeMillis()

                if (rms > 0.012) {
                    speechDetectedInChunk = true
                    lastSoundMs = now
                }

                if (speechDetectedInChunk) {
                    pcmAccumulator.write(byteBuf.array())
                }

                val elapsed = now - chunkStartMs
                val silenceGap = now - lastSoundMs

                if (speechDetectedInChunk && ((elapsed >= 4800L) || (elapsed >= 1500L && silenceGap >= 850L))) {
                    val pcmData = pcmAccumulator.toByteArray()
                    pcmAccumulator.reset()
                    speechDetectedInChunk = false
                    chunkStartMs = System.currentTimeMillis()

                    if (pcmData.size >= sampleRate) {
                        processCapturedAudioChunk(pcmData, sampleRate)
                    }
                } else if (!speechDetectedInChunk && elapsed > 3000L) {
                    pcmAccumulator.reset()
                    chunkStartMs = System.currentTimeMillis()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Playback capture loop error", e)
            DubbingStateBus.setStatus("خطا در ضبط صدای پخش‌کننده: ${e.localizedMessage ?: "نامشخص"}")
        } finally {
            stopAudioRecordOnly()
        }
    }

    private suspend fun processCapturedAudioChunk(pcmBytes: ByteArray, sampleRate: Int) {
        // Acoustic pre-check BEFORE UI processing indicator: skip explosions/gunshots/ambience immediately!
        val acousticCheck = SpeechVsSfxClassifier.analyzePcm16Mono(pcmBytes, sampleRate)
        if (acousticCheck.isLikelySoundEffectOnly) {
            DubbingStateBus.setStatus("پخش صدای اصلی فیلم (${acousticCheck.sfxReason})...")
            return
        }

        DubbingStateBus.setProcessingSegment(true)
        DubbingStateBus.setStatus("تشخیص کاراکتر (${acousticCheck.inferredArchetype.titleFa}) و ترجمه حسی...")
        updateFloatingOverlayAppearance()

        try {
            val wavBytes = wrapPcmAsWavBytes(pcmBytes, sampleRate)
            val accent = DubbingStateBus.dubAccent.value
            val toneStyle = DubbingStateBus.dubToneStyle.value
            val cinemaStyle = DubbingStateBus.cinemaDubbingStyle.value
            val expressiveness = DubbingStateBus.dubExpressiveness.value
            val preferredMethod = DubbingStateBus.dubEngineMethod.value

            val result = failoverEngine.translateAudioWithFailover(
                audioBytes = wavBytes,
                mimeType = "audio/wav",
                pcmBytesForProsody = pcmBytes,
                preferredMethod = preferredMethod,
                targetAccent = accent,
                toneStyle = toneStyle,
                cinemaStyle = cinemaStyle,
                expressiveness = expressiveness,
                onEngineSwitchNotice = { notice ->
                    DubbingStateBus.setActiveEngineLabel(notice)
                }
            )

            DubbingStateBus.setActiveEngineLabel(
                if (result.failoverOccurred) {
                    "متصل به جایگزین خودکار: ${result.engineUsedName}"
                } else {
                    "موتور فعال: ${result.engineUsedName}"
                }
            )

            if (result.isSoundEffectOnly || !result.hasSpeech || result.persianTranslation.isBlank()) {
                DubbingStateBus.setStatus("در حال گوش دادن (افکت/موسیقی اصلی حفظ شد)...")
                return
            }

            val resolvedElevenKey = UserApiKeyStore.resolveElevenLabsKey(this)
            val outputMode = DubbingStateBus.dubOutputMode.value
            val useClone = DubbingStateBus.useClonedVoiceForDubbing.value
            val remoteVoiceId = DubbingStateBus.selectedClonedVoiceRemoteId.value
            val canUseClone = useClone && !remoteVoiceId.isNullOrBlank() && resolvedElevenKey.isNotBlank()

            val subLine = if (DubbingStateBus.dualLanguageSubtitles.value && result.originalTranscript.isNotBlank()) {
                "${result.persianTranslation}\n${result.originalTranscript}"
            } else {
                result.persianTranslation
            }
            DubbingStateBus.setCurrentSubtitleText(subLine)
            updateFloatingSubtitleBanner(subLine)

            DubbingStateBus.addSegment(
                DubbingSegment(
                    detectedLanguage = result.detectedLanguage,
                    originalTranscript = result.originalTranscript,
                    persianTranslation = result.persianTranslation,
                    usedClonedVoice = canUseClone && outputMode != DubbingOutputMode.SUBTITLE_ONLY,
                    detectedEmotion = result.detectedEmotion,
                    speakerCharacter = result.speakerCharacter,
                    speakerSlotLabel = result.speakerSlotLabel,
                    engineUsedName = result.engineUsedName,
                    failoverOccurred = result.failoverOccurred
                )
            )

            val charLabel = result.speakerSlotLabel.ifBlank { result.speakerCharacter.titleFa }
            DubbingStateBus.setStatus("$charLabel • ${result.detectedEmotion.titleFa}: ${result.persianTranslation}")

            if (outputMode != DubbingOutputMode.SUBTITLE_ONLY) {
                when {
                    canUseClone && remoteVoiceId != null -> {
                        speakWithElevenLabsVoice(remoteVoiceId, resolvedElevenKey, result, applyArchetypeShift = false)
                    }
                    // Multi-speaker automatic casting!
                    else -> {
                        val cloudCharFile = File(cacheDir, "char_tts_${System.nanoTime()}.mp3")
                        val speedMul = (DubbingStateBus.dubSpeed.value * result.recommendedSpeed * result.speakerCharacter.defaultTtsSpeed).coerceIn(0.78f, 1.32f)
                        val generatedViaIranOrOpenAi = failoverEngine.synthesizeSpeechViaIranOrOpenAiTts(
                            text = result.persianTranslation,
                            archetype = result.speakerCharacter,
                            speed = speedMul,
                            outputFile = cloudCharFile
                        )
                        if (generatedViaIranOrOpenAi) {
                            playDubbedAudioFileWithDucking(cloudCharFile, result, applyArchetypeShift = false)
                        } else if (resolvedElevenKey.isNotBlank()) {
                            speakWithElevenLabsVoice(
                                voiceId = result.speakerCharacter.elevenLabsPresetVoiceId,
                                elevenLabsApiKey = resolvedElevenKey,
                                result = result,
                                applyArchetypeShift = false
                            )
                        } else {
                            cloudCharFile.delete()
                            speakWithAndroidPersianTts(result)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Dubbing chunk translation error", e)
            DubbingStateBus.setStatus("خطا در ترجمه قطعه صوتی: ${e.localizedMessage ?: "نامشخص"}")
        } finally {
            DubbingStateBus.setProcessingSegment(false)
            updateFloatingOverlayAppearance()
        }
    }

    private suspend fun speakWithElevenLabsVoice(
        voiceId: String,
        elevenLabsApiKey: String,
        result: DubbingTranslationResult,
        applyArchetypeShift: Boolean
    ) {
        try {
            val expressiveness = DubbingStateBus.dubExpressiveness.value
            val actingScript = result.expressiveActingText.ifBlank {
                PersianExpressiveDubbingStylizer.buildElevenV3ActingText(
                    persianText = result.persianTranslation,
                    emotion = result.detectedEmotion,
                    expressiveness = expressiveness,
                    toneStyle = DubbingStateBus.dubToneStyle.value
                )
            }
            val voiceSettings = PersianExpressiveDubbingStylizer.buildDynamicElevenV3Settings(
                emotion = result.detectedEmotion,
                expressiveness = expressiveness
            )

            val response = getElevenLabsApi().textToSpeech(
                apiKey = elevenLabsApiKey.trim(),
                voiceId = voiceId,
                body = TtsRequestBody(
                    text = actingScript,
                    modelId = "eleven_v3",
                    voiceSettings = voiceSettings
                )
            )
            if (!response.isSuccessful || response.body() == null) {
                speakWithAndroidPersianTts(result)
                return
            }

            val tmpMp3 = File(cacheDir, "dub_${System.currentTimeMillis()}.mp3")
            response.body()!!.byteStream().use { input ->
                FileOutputStream(tmpMp3).use { output -> input.copyTo(output) }
            }
            playDubbedAudioFileWithDucking(tmpMp3, result, applyArchetypeShift)
        } catch (e: Exception) {
            Log.w(TAG, "Cloud dubbing fallback to multi-character expressive TTS", e)
            speakWithAndroidPersianTts(result)
        }
    }

    private suspend fun playDubbedAudioFileWithDucking(
        audioFile: File,
        result: DubbingTranslationResult,
        applyArchetypeShift: Boolean
    ) {
        val (targetPitch, targetSpeed) = PersianExpressiveDubbingStylizer.computeCharacterFinalPitchAndRate(
            archetype = result.speakerCharacter,
            emotion = result.detectedEmotion,
            recommendedPitch = result.recommendedPitch,
            recommendedSpeed = result.recommendedSpeed,
            userPitch = DubbingStateBus.dubPitch.value,
            userSpeed = DubbingStateBus.dubSpeed.value,
            expressiveness = DubbingStateBus.dubExpressiveness.value,
            applyArchetypeShift = applyArchetypeShift
        )

        withContext(Dispatchers.Main) {
            duckOriginalMediaVolume(true)
            dubMediaPlayer?.release()
            val vol = DubbingStateBus.dubVolume.value
            dubMediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setVolume(vol, vol)
                prepare()
                try {
                    playbackParams = android.media.PlaybackParams()
                        .setPitch(targetPitch.coerceIn(0.70f, 1.38f))
                        .setSpeed(targetSpeed.coerceIn(0.75f, 1.35f))
                } catch (_: Exception) {
                }
                setOnCompletionListener {
                    duckOriginalMediaVolume(false)
                    audioFile.delete()
                }
                setOnErrorListener { _, _, _ ->
                    duckOriginalMediaVolume(false)
                    audioFile.delete()
                    true
                }
                start()
            }
        }
    }

    private fun speakWithAndroidPersianTts(result: DubbingTranslationResult) {
        if (!ttsReady) return
        PersianExpressiveDubbingStylizer.speakExpressiveAndroidTts(
            tts = tts,
            text = result.persianTranslation,
            emotion = result.detectedEmotion,
            archetype = result.speakerCharacter,
            recommendedPitch = result.recommendedPitch,
            recommendedSpeed = result.recommendedSpeed,
            userPitch = DubbingStateBus.dubPitch.value,
            userSpeed = DubbingStateBus.dubSpeed.value,
            expressiveness = DubbingStateBus.dubExpressiveness.value,
            volume = DubbingStateBus.dubVolume.value,
            utterancePrefix = "dub_${System.currentTimeMillis()}"
        )
    }

    private fun applyStreamMusicVolume(normalizedVolume: Float) {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (max * normalizedVolume.coerceIn(0f, 1f)).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (_: Exception) {
        }
    }

    private fun duckOriginalMediaVolume(duck: Boolean) {
        val base = DubbingStateBus.originalVolume.value
        val target = if (duck) (base * 0.35f) else base
        applyStreamMusicVolume(target)
    }

    /**
     * Compact, icon-only floating Play/Pause orb (~46dp):
     * - NO cluttering text on top of videos.
     * - Automatically dims & turns translucent (alpha = 0.34f) when paused/idle.
     * - Brightens cleanly when active (alpha = 0.94f).
     * - Auto-snaps smoothly to the nearest left or right screen edge on release!
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingOverlayIfPermitted() {
        if (floatingView != null) {
            updateFloatingOverlayAppearance()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            DubbingStateBus.setOverlayVisible(false)
            return
        }

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val density = resources.displayMetrics.density
        val buttonSizePx = (46f * density).roundToInt()
        val edgeMarginPx = (8f * density).roundToInt()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenWidthPx = resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            buttonSizePx,
            buttonSizePx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidthPx - buttonSizePx - edgeMarginPx
            y = (resources.displayMetrics.heightPixels * 0.36f).roundToInt()
        }
        floatingParams = params

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#0F172A"))
            setStroke((1.5f * density).roundToInt(), Color.parseColor("#6638BDF8"))
        }
        floatingBg = bg

        val iconView = TextView(this).apply {
            text = "▶"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        floatingIconText = iconView

        val container = FrameLayout(this).apply {
            background = bg
            elevation = 8f * density
            alpha = 0.34f
            addView(
                iconView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    container.animate().alpha(0.95f).setDuration(120).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 9 || abs(dy) > 9) moved = true
                    params.x = (initialX + dx).coerceIn(0, (screenWidthPx - buttonSizePx).coerceAtLeast(0))
                    params.y = (initialY + dy).coerceIn(40, (resources.displayMetrics.heightPixels - buttonSizePx - 40).coerceAtLeast(40))
                    wm.updateViewLayout(container, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        val nextActive = !DubbingStateBus.isDubbingActive.value
                        DubbingStateBus.setDubbingActive(nextActive)
                        updateFloatingOverlayAppearance()
                    }
                    // Smooth auto-snap to nearest left or right screen edge
                    val targetX = if (params.x + buttonSizePx / 2 < screenWidthPx / 2) {
                        edgeMarginPx
                    } else {
                        (screenWidthPx - buttonSizePx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
                    }
                    ValueAnimator.ofInt(params.x, targetX).apply {
                        duration = 210L
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { anim ->
                            params.x = anim.animatedValue as Int
                            try {
                                if (floatingView != null) {
                                    wm.updateViewLayout(container, params)
                                }
                            } catch (_: Exception) {
                            }
                        }
                        start()
                    }
                    updateFloatingOverlayAppearance()
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(container, params)
            floatingView = container
            DubbingStateBus.setOverlayVisible(true)
            updateFloatingOverlayAppearance()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add compact floating orb", e)
            DubbingStateBus.setOverlayVisible(false)
        }
    }

    private fun updateFloatingOverlayAppearance() {
        serviceScope.launch(Dispatchers.Main) {
            val active = DubbingStateBus.isDubbingActive.value
            val busy = DubbingStateBus.isProcessingSegment.value
            floatingIconText?.text = if (active) "⏸" else "▶"
            floatingBg?.setColor(
                when {
                    busy -> Color.parseColor("#D97706")
                    active -> Color.parseColor("#0D9488")
                    else -> Color.parseColor("#0F172A")
                }
            )
            // Dim and fade automatically when not playing; stay crisp & clear while dubbing
            val targetAlpha = when {
                busy -> 0.96f
                active -> 0.90f
                else -> 0.34f
            }
            floatingView?.animate()?.alpha(targetAlpha)?.setDuration(220L)?.start()
        }
    }

    private fun updateFloatingSubtitleBanner(text: String) {
        serviceScope.launch(Dispatchers.Main) {
            val mode = DubbingStateBus.dubOutputMode.value
            val showSub = Settings.canDrawOverlays(this@PersianDubbingService) &&
                mode != DubbingOutputMode.DUB_ONLY &&
                text.isNotBlank() &&
                DubbingStateBus.isOverlayVisible.value

            if (!showSub) {
                floatingSubtitleView?.visibility = View.GONE
                return@launch
            }

            if (floatingSubtitleView == null) {
                val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager).also {
                    windowManager = it
                }
                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                val subParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = 120
                }
                val subBg = GradientDrawable().apply {
                    cornerRadius = 24f
                    setColor(Color.parseColor("#CC0F172A"))
                    setStroke(2, Color.parseColor("#5538BDF8"))
                }
                val subTv = TextView(this@PersianDubbingService).apply {
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(28, 16, 28, 16)
                }
                val box = LinearLayout(this@PersianDubbingService).apply {
                    orientation = LinearLayout.VERTICAL
                    background = subBg
                    setPadding(20, 6, 20, 6)
                    addView(subTv)
                }
                val wrapper = LinearLayout(this@PersianDubbingService).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(32, 0, 32, 0)
                    addView(box)
                }
                try {
                    wm.addView(wrapper, subParams)
                    floatingSubtitleView = wrapper
                    floatingSubtitleText = subTv
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to attach floating subtitle banner", e)
                }
            }
            floatingSubtitleText?.text = text
            floatingSubtitleView?.visibility = View.VISIBLE
        }
    }

    private fun removeFloatingOverlay() {
        floatingView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        floatingSubtitleView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        floatingView = null
        floatingIconText = null
        floatingParams = null
        floatingSubtitleView = null
        floatingSubtitleText = null
        DubbingStateBus.setOverlayVisible(false)
    }

    private fun stopAudioRecordOnly() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }

    private fun stopAllAndSelf() {
        captureJob?.cancel()
        stopAudioRecordOnly()
        mediaProjection?.stop()
        mediaProjection = null
        dubMediaPlayer?.release()
        dubMediaPlayer = null
        removeFloatingOverlay()
        DubbingStateBus.setDubbingActive(false)
        DubbingStateBus.setServiceRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PersianDubbingService::class.java).apply { action = ACTION_STOP_SERVICE },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("دوبله زنده فارسی ParHam VoiCe")
            .setContentText("تشخیص خودکار زن/مرد/کودک • حفظ صدای انفجار")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "توقف", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "دوبله زنده فارسی",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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

    override fun onDestroy() {
        super.onDestroy()
        captureJob?.cancel()
        stopAudioRecordOnly()
        mediaProjection?.stop()
        dubMediaPlayer?.release()
        tts?.shutdown()
        removeFloatingOverlay()
        DubbingStateBus.setDubbingActive(false)
        DubbingStateBus.setServiceRunning(false)
        serviceScope.cancel()
    }
}
