package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiVoiceStudioAi
import com.example.ai.PodcastDialogueTurn
import com.example.audio.AudioMixMasterEngine
import com.example.audio.AudioPlayerManager
import com.example.audio.AudioRecorderManager
import com.example.audio.BackingBeatStyle
import com.example.audio.BeatLaunchpadEngine
import com.example.audio.LaunchpadPadType
import com.example.audio.MixMasterConfig
import com.example.data.VoiceStudioRepository
import com.example.data.local.AppDatabase
import com.example.data.local.AudioSampleEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.GeneratedSpeechEntity
import com.example.data.local.VoiceProfileEntity
import com.example.data.remote.ApiConnectionTester
import com.example.data.remote.ApiProviderTarget
import com.example.data.remote.ApiTestResult
import com.example.data.remote.AutoProxyDiscoveryReport
import com.example.data.remote.ProxyBenchmarkCandidate
import com.example.data.remote.ProxyConfigStore
import com.example.data.remote.ProxySettings
import com.example.data.remote.UserApiKeyStore
import com.example.data.remote.UserApiKeysConfig
import com.example.domain.CinemaDubbingStyle
import com.example.domain.DubbingEngineMethod
import com.example.domain.DubbingOutputMode
import com.example.domain.DubbingToneStyle
import com.example.domain.PersianAccent
import com.example.domain.PersianCalibrationCorpus
import com.example.domain.SpeechLanguage
import com.example.domain.VoiceCharacterArchetype
import com.example.domain.VoiceEmotion
import com.example.dubbing.DubbingFailoverEngine
import com.example.dubbing.DubbingSegment
import com.example.dubbing.DubbingStateBus
import com.example.dubbing.LongMediaDubbingProcessor
import com.example.dubbing.PersianDubbingService
import com.example.dubbing.PersianExpressiveDubbingStylizer
import com.example.podcast.PodcastDirectorEngine
import com.example.podcast.PodcastEpisodeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private const val TAG = "VoiceStudioViewModel"

enum class StudioTab {
    STUDIO,
    CLONING,
    DUBBING,
    MIX_MASTER,
    LIBRARY,
    PODCAST,
    STUDIO_3D,
    SUPPORT,
    PROXY,
    API_KEYS
}

data class PodcastWorkspaceState(
    val episodeMode: PodcastEpisodeMode = PodcastEpisodeMode.PODCAST_TALK,
    val accent: PersianAccent = PersianAccent.TEHRANI,
    val topicOrBookText: String = "گفتگوی جذاب درباره آینده هوش مصنوعی در دوبله فیلم و موسیقی فارسی",
    val includeAmbientMusic: Boolean = true,
    val useClonedVoiceForHost: Boolean = true,
    val turns: List<PodcastDialogueTurn> = emptyList(),
    val latestRenderedWavPath: String? = null
)

data class SingingMixState(
    val lyricsTopic: String = "باران و خاطره",
    val lyrics: String = "[melodic] توو کوچه باز بارون می‌باره آروم\nبا صدای گرم تو دلِ شب می‌شه آروم\n[softly] بیا بخونیم از فردای روشن\nکه هیچ غمی نمونه توو دنیای من",
    val useRecordedSampleInsteadOfClone: Boolean = false,
    val selectedVocalSampleId: Long? = null,
    val mixConfig: MixMasterConfig = MixMasterConfig(),
    val customBeatName: String? = null,
    val rawVocalFilePath: String? = null,
    val latestMasteredSpeech: GeneratedSpeechEntity? = null,
    val isLaunchpadLoopPlaying: Boolean = false,
    val previewingGenreStyle: BackingBeatStyle? = null,
    val autoMatchSummary: String? = null
)

sealed interface AsyncOperationState {
    data object Idle : AsyncOperationState
    data class Loading(val label: String) : AsyncOperationState
    data class Success(val message: String) : AsyncOperationState
    data class Error(val message: String) : AsyncOperationState
}

data class StudioControlsState(
    val selectedVoiceProfileId: Long? = null,
    val inputText: String = "سلام! این صدای کلون‌شده من است که به هر زبانی و با لحن طبیعی صحبت می‌کند.",
    val adaptedTextPreview: String = "",
    val autoAdaptAccentWithAi: Boolean = true,
    val selectedLanguage: SpeechLanguage = SpeechLanguage.PERSIAN,
    val selectedAccent: PersianAccent = PersianAccent.TEHRANI,
    val selectedEmotion: VoiceEmotion = VoiceEmotion.NEUTRAL,
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val stability: Float = VoiceEmotion.NEUTRAL.stability,
    val similarityBoost: Float = VoiceEmotion.NEUTRAL.similarityBoost
)

data class CloningWorkspaceState(
    val newVoiceName: String = "",
    val cloningAccent: PersianAccent = PersianAccent.TEHRANI,
    val removeBackgroundNoise: Boolean = true,
    val selectedSampleIds: Set<Long> = emptySet(),
    val retrainTargetVoiceId: Long? = null,
    val calibrationPromptIndex: Int = 0,
    val analyzingSampleId: Long? = null
)

class VoiceStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val geminiAi = GeminiVoiceStudioAi()
    private val failoverEngine = DubbingFailoverEngine(application, geminiAi)
    private val longMediaProcessor = LongMediaDubbingProcessor(application, failoverEngine)
    private val repository = VoiceStudioRepository(application, database, geminiAi)
    private val recorderManager = AudioRecorderManager(application)
    private val playerManager = AudioPlayerManager()
    private val launchpadEngine = BeatLaunchpadEngine()
    private val mixMasterEngine = AudioMixMasterEngine(launchpadEngine)
    private val podcastEngine = PodcastDirectorEngine(application, failoverEngine, launchpadEngine)

    private val _podcastState = MutableStateFlow(PodcastWorkspaceState())
    val podcastState: StateFlow<PodcastWorkspaceState> = _podcastState.asStateFlow()

    private val _singingMixState = MutableStateFlow(
        SingingMixState(
            mixConfig = MixMasterConfig(
                launchpadGrid = launchpadEngine.defaultSequencerGrid()
            )
        )
    )
    val singingMixState: StateFlow<SingingMixState> = _singingMixState.asStateFlow()

    val voiceProfiles: StateFlow<List<VoiceProfileEntity>> =
        repository.observeVoiceProfiles().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val audioSamples: StateFlow<List<AudioSampleEntity>> =
        repository.observeAudioSamples().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val generatedSpeeches: StateFlow<List<GeneratedSpeechEntity>> =
        repository.observeGeneratedSpeeches().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    val chatMessages: StateFlow<List<ChatMessageEntity>> =
        repository.observeChatMessages().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private val _currentTab = MutableStateFlow(StudioTab.STUDIO)
    val currentTab: StateFlow<StudioTab> = _currentTab.asStateFlow()

    private val _studioControls = MutableStateFlow(StudioControlsState())
    val studioControls: StateFlow<StudioControlsState> = _studioControls.asStateFlow()

    private val _cloningState = MutableStateFlow(CloningWorkspaceState())
    val cloningState: StateFlow<CloningWorkspaceState> = _cloningState.asStateFlow()

    private val _operationState = MutableStateFlow<AsyncOperationState>(AsyncOperationState.Idle)
    val operationState: StateFlow<AsyncOperationState> = _operationState.asStateFlow()

    private val _latestGeneratedSpeech = MutableStateFlow<GeneratedSpeechEntity?>(null)
    val latestGeneratedSpeech: StateFlow<GeneratedSpeechEntity?> = _latestGeneratedSpeech.asStateFlow()

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    private val _streamingBotResponse = MutableStateFlow<String?>(null)
    val streamingBotResponse: StateFlow<String?> = _streamingBotResponse.asStateFlow()

    private val _isBotResponding = MutableStateFlow(false)
    val isBotResponding: StateFlow<Boolean> = _isBotResponding.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private var lastEncounteredError: String? = null

    val isRecording = recorderManager.isRecording
    val recordingElapsedSeconds = recorderManager.elapsedSeconds
    val recordingWaveBars = recorderManager.amplitudeHistory
    val recordingLevel = recorderManager.currentLevel
    val recordingPeakDb = recorderManager.peakDb

    val playingItemId = playerManager.playingItemId
    val isPlaying = playerManager.isPlaying
    val playbackProgress = playerManager.progress
    val currentPositionMs = playerManager.currentPositionMs
    val durationMs = playerManager.durationMs
    val playbackWaveformEnvelope = playerManager.waveformEnvelope
    val playbackLiveBars = playerManager.liveLevelBars
    val playbackLevel = playerManager.currentPlaybackLevel
    val playbackDb = playerManager.currentPlaybackDb

    private val _proxySettings = MutableStateFlow(ProxyConfigStore.load(application))
    val proxySettings: StateFlow<ProxySettings> = _proxySettings.asStateFlow()

    private val _userApiKeys = MutableStateFlow(UserApiKeyStore.load(application))
    val userApiKeys: StateFlow<UserApiKeysConfig> = _userApiKeys.asStateFlow()

    private val _isTestingProxy = MutableStateFlow(false)
    val isTestingProxy: StateFlow<Boolean> = _isTestingProxy.asStateFlow()

    private val _isAutoDiscoveringProxy = MutableStateFlow(false)
    val isAutoDiscoveringProxy: StateFlow<Boolean> = _isAutoDiscoveringProxy.asStateFlow()

    private val _proxyTestResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val proxyTestResult: StateFlow<Pair<Boolean, String>?> = _proxyTestResult.asStateFlow()

    private val _autoProxyDiscoveryReport = MutableStateFlow<AutoProxyDiscoveryReport?>(null)
    val autoProxyDiscoveryReport: StateFlow<AutoProxyDiscoveryReport?> = _autoProxyDiscoveryReport.asStateFlow()

    private val _apiTestResults = MutableStateFlow<Map<ApiProviderTarget, ApiTestResult>>(emptyMap())
    val apiTestResults: StateFlow<Map<ApiProviderTarget, ApiTestResult>> = _apiTestResults.asStateFlow()

    private val _testingApiProvider = MutableStateFlow<ApiProviderTarget?>(null)
    val testingApiProvider: StateFlow<ApiProviderTarget?> = _testingApiProvider.asStateFlow()

    private val _isTestingAllApis = MutableStateFlow(false)
    val isTestingAllApis: StateFlow<Boolean> = _isTestingAllApis.asStateFlow()

    val keystoreSecurityStatus: String = UserApiKeyStore.storageSecuritySummaryFa()

    val dubbingServiceRunning = DubbingStateBus.isServiceRunning
    val dubbingOverlayVisible = DubbingStateBus.isOverlayVisible
    val dubbingActive = DubbingStateBus.isDubbingActive
    val dubbingProcessing = DubbingStateBus.isProcessingSegment
    val dubOriginalVolume = DubbingStateBus.originalVolume
    val dubVolume = DubbingStateBus.dubVolume
    val dubUseClonedVoice = DubbingStateBus.useClonedVoiceForDubbing
    val dubOutputMode = DubbingStateBus.dubOutputMode
    val dubCurrentSubtitle = DubbingStateBus.currentSubtitleText
    val dubLatestSrtFilePath = DubbingStateBus.latestSrtFilePath
    val dubLatestDubbedAudioPath = DubbingStateBus.latestDubbedAudioPath
    val dubLatestDubbedVideoPath = DubbingStateBus.latestDubbedVideoPath
    val dubLatestDubOnlyStemPath = DubbingStateBus.latestDubOnlyStemPath
    val dubLatestSfxOnlyStemPath = DubbingStateBus.latestSfxOnlyStemPath
    val dubLongFileProgressText = DubbingStateBus.longFileProgressText
    val dubEngineMethod = DubbingStateBus.dubEngineMethod
    val dubAccent = DubbingStateBus.dubAccent
    val dubToneStyle = DubbingStateBus.dubToneStyle
    val dubCinemaStyle = DubbingStateBus.cinemaDubbingStyle
    val dubDualLanguageSubtitles = DubbingStateBus.dualLanguageSubtitles
    val dubExpressiveness = DubbingStateBus.dubExpressiveness
    val dubPitch = DubbingStateBus.dubPitch
    val dubSpeed = DubbingStateBus.dubSpeed
    val dubActiveEngineLabel = DubbingStateBus.activeEngineLabel
    val dubStatusText = DubbingStateBus.statusText
    val dubSegments = DubbingStateBus.segments

    private val _exportMp4Video = MutableStateFlow(true)
    val exportMp4Video: StateFlow<Boolean> = _exportMp4Video.asStateFlow()

    private val _clipStartSecText = MutableStateFlow("")
    val clipStartSecText: StateFlow<String> = _clipStartSecText.asStateFlow()

    private val _clipEndSecText = MutableStateFlow("")
    val clipEndSecText: StateFlow<String> = _clipEndSecText.asStateFlow()

    private val _mediaWorkloadSummary = MutableStateFlow<String?>(null)
    val mediaWorkloadSummary: StateFlow<String?> = _mediaWorkloadSummary.asStateFlow()

    private var localDubTts: TextToSpeech? = null
    private var localDubTtsReady = false

    val isElevenLabsConfigured: Boolean
        get() = repository.isElevenLabsKeyConfigured()

    init {
        localDubTts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                PersianExpressiveDubbingStylizer.selectBestPersianVoice(localDubTts)
                val res = localDubTts?.setLanguage(Locale("fa", "IR"))
                localDubTtsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                if (!localDubTtsReady) {
                    localDubTts?.setLanguage(Locale("fa"))
                    localDubTtsReady = true
                }
            }
        }

        viewModelScope.launch {
            val existing = repository.getChatHistorySync()
            if (existing.isEmpty()) {
                repository.saveChatMessage(
                    role = "model",
                    text = "سلام! من «همیار ParHam VoiCe» هستم. تمام مراحل گفتگو را به خاطر می‌سپارم و از وضعیت فعلی استودیو و صداهای شما خبر دارم. برای کلون صدای شخصی، تنظیم لهجه‌ها و احساسات، دوبله فارسی یا تنظیم پروکسی چه کمکی می‌تونم بکنم؟"
                )
            }
        }

        // Auto-select first available voice profile if none selected yet and sync remote voice ID to dubbing bus
        viewModelScope.launch {
            voiceProfiles.collect { profiles ->
                val currentId = _studioControls.value.selectedVoiceProfileId
                val activeProfile = if (profiles.isNotEmpty() && (currentId == null || profiles.none { it.id == currentId })) {
                    val first = profiles.first()
                    _studioControls.update {
                        it.copy(
                            selectedVoiceProfileId = first.id,
                            selectedAccent = PersianAccent.fromId(first.accentId)
                        )
                    }
                    first
                } else {
                    profiles.firstOrNull { it.id == currentId }
                }
                DubbingStateBus.setSelectedClonedVoiceRemoteId(activeProfile?.elevenLabsVoiceId)
            }
        }
    }

    fun selectTab(tab: StudioTab) {
        _currentTab.value = tab
    }

    fun clearOperationState() {
        _operationState.value = AsyncOperationState.Idle
    }

    // --- Studio Controls ---

    fun selectVoiceProfile(profile: VoiceProfileEntity) {
        _studioControls.update {
            it.copy(
                selectedVoiceProfileId = profile.id,
                selectedAccent = PersianAccent.fromId(profile.accentId)
            )
        }
        DubbingStateBus.setSelectedClonedVoiceRemoteId(profile.elevenLabsVoiceId)
    }

    fun updateInputText(text: String) {
        _studioControls.update { it.copy(inputText = text) }
    }

    fun updateAdaptedTextPreview(text: String) {
        _studioControls.update { it.copy(adaptedTextPreview = text) }
    }

    fun setAutoAdaptAccentWithAi(enabled: Boolean) {
        _studioControls.update { it.copy(autoAdaptAccentWithAi = enabled) }
    }

    fun selectStudioLanguage(language: SpeechLanguage) {
        _studioControls.update { it.copy(selectedLanguage = language) }
    }

    fun selectStudioAccent(accent: PersianAccent) {
        _studioControls.update { it.copy(selectedAccent = accent) }
    }

    fun selectEmotion(emotion: VoiceEmotion) {
        _studioControls.update {
            it.copy(
                selectedEmotion = emotion,
                stability = emotion.stability,
                similarityBoost = emotion.similarityBoost
            )
        }
    }

    fun updatePitch(pitch: Float) {
        _studioControls.update { it.copy(pitch = pitch) }
        playerManager.applyPlaybackParams(pitch, _studioControls.value.speed)
    }

    fun updateSpeed(speed: Float) {
        _studioControls.update { it.copy(speed = speed) }
        playerManager.applyPlaybackParams(_studioControls.value.pitch, speed)
    }

    fun updateStability(stability: Float) {
        _studioControls.update { it.copy(stability = stability) }
    }

    fun updateSimilarityBoost(similarity: Float) {
        _studioControls.update { it.copy(similarityBoost = similarity) }
    }

    fun previewAccentAdaptationOnly() {
        val controls = _studioControls.value
        if (controls.inputText.isBlank()) {
            reportError("لطفاً ابتدا متن فارسی خود را وارد کنید.")
            return
        }
        viewModelScope.launch {
            _operationState.value = AsyncOperationState.Loading("در حال تطبیق لهجه و لحن فارسی با هوش مصنوعی...")
            try {
                val adapted = repository.adaptScriptWithGemini(
                    rawText = controls.inputText,
                    targetLanguage = controls.selectedLanguage,
                    accent = controls.selectedAccent,
                    emotion = controls.selectedEmotion,
                    pitch = controls.pitch,
                    speed = controls.speed
                )
                _studioControls.update { it.copy(adaptedTextPreview = adapted) }
                _operationState.value = AsyncOperationState.Success(
                    if (controls.selectedLanguage == SpeechLanguage.PERSIAN) {
                        "متن با لهجه ${controls.selectedAccent.titleFa} و احساس ${controls.selectedEmotion.titleFa} آماده شد."
                    } else {
                        "متن به زبان ${controls.selectedLanguage.titleFa} (${controls.selectedLanguage.nativeName}) آماده شد."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: TimeoutCancellationException) {
                reportError("زمان تطبیق متن به پایان رسید. دوباره تلاش کنید.")
            } catch (e: Exception) {
                Log.e(TAG, "previewAccentAdaptationOnly failed", e)
                reportError(e.localizedMessage ?: "خطا در تطبیق لهجه با جمینای.")
            }
        }
    }

    fun synthesizeClonedPersianSpeech() {
        val controls = _studioControls.value
        val profiles = voiceProfiles.value
        val selectedProfile = profiles.firstOrNull { it.id == controls.selectedVoiceProfileId }

        if (selectedProfile == null) {
            reportError("ابتدا در بخش «کلون و آموزش» صدای خود را بسازید یا از آرشیو همگام‌سازی کنید.")
            return
        }
        if (controls.inputText.isBlank()) {
            reportError("متن فارسی برای تبدیل به صدا نمی‌تواند خالی باشد.")
            return
        }

        viewModelScope.launch {
            _operationState.value = AsyncOperationState.Loading("در حال آماده‌سازی متن و تولید صدای فارسی...")
            try {
                val finalScript = if (controls.autoAdaptAccentWithAi) {
                    val adapted = repository.adaptScriptWithGemini(
                        rawText = controls.inputText,
                        targetLanguage = controls.selectedLanguage,
                        accent = controls.selectedAccent,
                        emotion = controls.selectedEmotion,
                        pitch = controls.pitch,
                        speed = controls.speed
                    )
                    _studioControls.update { it.copy(adaptedTextPreview = adapted) }
                    adapted
                } else {
                    controls.adaptedTextPreview.ifBlank {
                        val cue = controls.selectedEmotion.elevenV3Cue
                        if (cue.isNotBlank()) "$cue ${controls.inputText}" else controls.inputText
                    }
                }

                _operationState.value = AsyncOperationState.Loading("در حال سنتز صدای کلون‌شده با مدل eleven_v3...")
                val generated = repository.synthesizeSpeech(
                    profile = selectedProfile,
                    originalText = controls.inputText,
                    adaptedText = finalScript,
                    accent = controls.selectedAccent,
                    emotion = controls.selectedEmotion,
                    pitch = controls.pitch,
                    speed = controls.speed,
                    stability = controls.stability,
                    similarityBoost = controls.similarityBoost
                )
                _latestGeneratedSpeech.value = generated
                _operationState.value = AsyncOperationState.Success("صوت فارسی با موفقیت تولید شد!")
                playGeneratedSpeech(generated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TimeoutCancellationException) {
                reportError("تولید صوت بیشتر از حد مجاز طول کشید. لطفاً دوباره امتحان کنید.")
            } catch (e: Exception) {
                Log.e(TAG, "synthesizeClonedPersianSpeech failed", e)
                reportError(e.localizedMessage ?: "خطا در تولید صدای کلون‌شده.")
            }
        }
    }

    // --- Recording & Dataset Management ---

    fun nextCalibrationPrompt() {
        _cloningState.update {
            val next = (it.calibrationPromptIndex + 1) % PersianCalibrationCorpus.prompts.size
            it.copy(calibrationPromptIndex = next)
        }
    }

    fun updateNewVoiceName(name: String) {
        _cloningState.update { it.copy(newVoiceName = name) }
    }

    fun selectCloningAccent(accent: PersianAccent) {
        _cloningState.update { it.copy(cloningAccent = accent) }
    }

    fun setRemoveBackgroundNoise(enabled: Boolean) {
        _cloningState.update { it.copy(removeBackgroundNoise = enabled) }
    }

    fun setRetrainTargetVoice(profileId: Long?) {
        _cloningState.update { it.copy(retrainTargetVoiceId = profileId) }
    }

    fun toggleSampleSelection(sampleId: Long) {
        _cloningState.update { state ->
            val updated = if (sampleId in state.selectedSampleIds) {
                state.selectedSampleIds - sampleId
            } else {
                state.selectedSampleIds + sampleId
            }
            state.copy(selectedSampleIds = updated)
        }
    }

    fun startVoiceRecording() {
        playerManager.stop()
        recorderManager.startRecording(viewModelScope) { err ->
            reportError(err)
        }
    }

    fun stopVoiceRecordingAndSave() {
        viewModelScope.launch {
            val result = recorderManager.stopRecording()
            if (result == null) {
                reportError("ضبط صدا کوتاه بود یا داده‌ای ذخیره نشد.")
                return@launch
            }
            val promptTitle = PersianCalibrationCorpus.prompts[_cloningState.value.calibrationPromptIndex].title
            val count = audioSamples.value.size + 1
            val saved = repository.saveAudioSample(
                title = "ضبط #$count ($promptTitle)",
                file = result.file,
                mimeType = "audio/wav",
                durationSeconds = result.durationSeconds,
                sourceType = "RECORDED"
            )
            _cloningState.update {
                it.copy(selectedSampleIds = it.selectedSampleIds + saved.id)
            }
            _operationState.value = AsyncOperationState.Success("نمونه صوتی (${result.durationSeconds} ثانیه) ذخیره شد و به لیست آموزش اضافه گردید.")
            analyzeSampleQuality(saved)
        }
    }

    fun importAdditionalDatasets(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _operationState.value = AsyncOperationState.Loading("در حال وارد کردن دیتاست‌های صوتی فارسی...")
            var importedCount = 0
            val newIds = mutableSetOf<Long>()
            try {
                for (uri in uris) {
                    val result = recorderManager.importAudioDataset(uri) ?: continue
                    val (file, durationSec) = result
                    val mimeType = when (file.extension.lowercase()) {
                        "wav" -> "audio/wav"
                        "m4a" -> "audio/mp4"
                        "ogg" -> "audio/ogg"
                        else -> "audio/mpeg"
                    }
                    val saved = repository.saveAudioSample(
                        title = "دیتاست: ${file.nameWithoutExtension.take(28)}",
                        file = file,
                        mimeType = mimeType,
                        durationSeconds = durationSec,
                        sourceType = "IMPORTED"
                    )
                    newIds += saved.id
                    importedCount++
                }
                if (importedCount > 0) {
                    _cloningState.update {
                        it.copy(selectedSampleIds = it.selectedSampleIds + newIds)
                    }
                    _operationState.value = AsyncOperationState.Success("$importedCount فایل صوتی فارسی به مجموعه آموزش اضافه شد.")
                } else {
                    reportError("فایل صوتی معتبری وارد نشد.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "importAdditionalDatasets failed", e)
                reportError(e.localizedMessage ?: "خطا در وارد کردن فایل‌های صوتی.")
            }
        }
    }

    fun analyzeSampleQuality(sample: AudioSampleEntity) {
        viewModelScope.launch {
            _cloningState.update { it.copy(analyzingSampleId = sample.id) }
            try {
                repository.analyzeAndSaveSampleQuality(sample)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "analyzeSampleQuality failed", e)
                reportError("تحلیل هوشمند نمونه صوتی ناموفق بود: ${e.localizedMessage ?: "خطا"}")
            } finally {
                _cloningState.update {
                    if (it.analyzingSampleId == sample.id) it.copy(analyzingSampleId = null) else it
                }
            }
        }
    }

    fun deleteSample(sample: AudioSampleEntity) {
        viewModelScope.launch {
            if (playingItemId.value == "sample_${sample.id}") {
                playerManager.stop()
            }
            _cloningState.update { it.copy(selectedSampleIds = it.selectedSampleIds - sample.id) }
            repository.deleteAudioSample(sample)
        }
    }

    fun submitCloneOrRetrain() {
        val state = _cloningState.value
        val sampleIds = state.selectedSampleIds.toList()
        if (sampleIds.isEmpty()) {
            reportError("حداقل یک نمونه صوتی ضبط شده یا دیتاست وارد شده را انتخاب کنید.")
            return
        }

        val targetId = state.retrainTargetVoiceId
        if (targetId == null) {
            val voiceName = state.newVoiceName.trim()
            if (voiceName.isBlank()) {
                reportError("لطفاً یک نام برای صدای کلون‌شده خود وارد کنید (مثلاً: صدای من).")
                return
            }
            viewModelScope.launch {
                _operationState.value = AsyncOperationState.Loading("در حال آپلود نمونه‌ها و کلون صدای فارسی...")
                try {
                    val created = repository.createClonedVoice(
                        name = voiceName,
                        accent = state.cloningAccent,
                        sampleIds = sampleIds,
                        removeBackgroundNoise = state.removeBackgroundNoise
                    )
                    _studioControls.update {
                        it.copy(
                            selectedVoiceProfileId = created.id,
                            selectedAccent = state.cloningAccent
                        )
                    }
                    _cloningState.update {
                        it.copy(newVoiceName = "", selectedSampleIds = emptySet())
                    }
                    _operationState.value = AsyncOperationState.Success("صدای «${created.name}» با لهجه ${state.cloningAccent.titleFa} با موفقیت کلون شد!")
                    _currentTab.value = StudioTab.STUDIO
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "createClonedVoice failed", e)
                    reportError(e.localizedMessage ?: "خطا در کلون کردن صدا.")
                }
            }
        } else {
            val targetProfile = voiceProfiles.value.firstOrNull { it.id == targetId }
            if (targetProfile == null) {
                reportError("پروفایل صدای انتخاب‌شده برای بازآموزی یافت نشد.")
                return
            }
            viewModelScope.launch {
                _operationState.value = AsyncOperationState.Loading("در حال آموزش مجدد صدای «${targetProfile.name}» با دیتاست فارسی جدید...")
                try {
                    val updated = repository.retrainClonedVoiceWithDataset(
                        profile = targetProfile,
                        additionalSampleIds = sampleIds,
                        accent = state.cloningAccent,
                        removeBackgroundNoise = state.removeBackgroundNoise
                    )
                    _studioControls.update {
                        it.copy(
                            selectedVoiceProfileId = updated.id,
                            selectedAccent = state.cloningAccent
                        )
                    }
                    _cloningState.update { it.copy(selectedSampleIds = emptySet()) }
                    _operationState.value = AsyncOperationState.Success("صدای «${updated.name}» با ${sampleIds.size} دیتاست جدید بازآموزی شد و دقت آن بهبود یافت!")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "retrainClonedVoiceWithDataset failed", e)
                    reportError(e.localizedMessage ?: "خطا در بازآموزی مدل صدا.")
                }
            }
        }
    }

    fun syncCloudVoices() {
        viewModelScope.launch {
            _operationState.value = AsyncOperationState.Loading("در حال همگام‌سازی صداها از سرور...")
            try {
                val count = repository.syncVoicesFromCloud()
                _operationState.value = AsyncOperationState.Success(
                    if (count > 0) "$count صدای جدید همگام‌سازی شد." else "همه صداهای حساب شما به‌روز هستند."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "syncCloudVoices failed", e)
                reportError(e.localizedMessage ?: "خطا در دریافت لیست صداها.")
            }
        }
    }

    fun deleteVoiceProfile(profile: VoiceProfileEntity) {
        viewModelScope.launch {
            if (_cloningState.value.retrainTargetVoiceId == profile.id) {
                _cloningState.update { it.copy(retrainTargetVoiceId = null) }
            }
            repository.deleteVoiceProfile(profile)
            _operationState.value = AsyncOperationState.Success("پروفایل «${profile.name}» حذف شد.")
        }
    }

    // --- Playback & Sharing ---

    fun playAudioSample(sample: AudioSampleEntity) {
        playerManager.togglePlay(
            itemId = "sample_${sample.id}",
            filePath = sample.filePath,
            pitch = 1.0f,
            speed = 1.0f,
            scope = viewModelScope,
            onError = { reportError(it) }
        )
    }

    fun playGeneratedSpeech(speech: GeneratedSpeechEntity, useLiveStudioParams: Boolean = false) {
        val controls = _studioControls.value
        val pitch = if (useLiveStudioParams) controls.pitch else speech.pitch
        val speed = if (useLiveStudioParams) controls.speed else speech.speed
        playerManager.togglePlay(
            itemId = "speech_${speech.id}",
            filePath = speech.audioFilePath,
            pitch = pitch,
            speed = speed,
            scope = viewModelScope,
            onError = { reportError(it) }
        )
    }

    fun seekPlayback(fraction: Float) {
        playerManager.seekTo(fraction)
    }

    fun deleteGeneratedSpeech(speech: GeneratedSpeechEntity) {
        viewModelScope.launch {
            if (playingItemId.value == "speech_${speech.id}") {
                playerManager.stop()
            }
            if (_latestGeneratedSpeech.value?.id == speech.id) {
                _latestGeneratedSpeech.value = null
            }
            repository.deleteGeneratedSpeech(speech)
        }
    }

    fun createShareIntent(speech: GeneratedSpeechEntity): Intent? {
        val file = File(speech.audioFilePath)
        if (!file.exists()) {
            reportError("فایل صوتی برای اشتراک‌گذاری یافت نشد.")
            return null
        }
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = if (file.extension.equals("wav", ignoreCase = true)) "audio/wav" else "audio/mpeg"
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, speech.originalText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun exportSpeechToUri(speech: GeneratedSpeechEntity, destUri: Uri) {
        viewModelScope.launch {
            _operationState.value = AsyncOperationState.Loading("در حال ذخیره فایل صوتی در حافظه...")
            try {
                repository.exportAudioFileToUri(speech.audioFilePath, destUri)
                _operationState.value = AsyncOperationState.Success("فایل خروجی صدای کلون‌شده با موفقیت ذخیره شد!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "exportSpeechToUri failed", e)
                reportError(e.localizedMessage ?: "خطا در ذخیره فایل صوتی.")
            }
        }
    }

    // --- Singing, Mixing & Mastering ---

    fun updateLyricsTopic(topic: String) {
        _singingMixState.update { it.copy(lyricsTopic = topic) }
    }

    fun updateSingingLyrics(lyrics: String) {
        _singingMixState.update { it.copy(lyrics = lyrics) }
    }

    fun setUseRecordedSampleForMix(useSample: Boolean) {
        _singingMixState.update { it.copy(useRecordedSampleInsteadOfClone = useSample) }
    }

    fun selectVocalSampleForMix(sampleId: Long) {
        _singingMixState.update { it.copy(selectedVocalSampleId = sampleId) }
    }

    fun updateMixConfig(transform: (MixMasterConfig) -> MixMasterConfig) {
        _singingMixState.update { it.copy(mixConfig = transform(it.mixConfig)) }
    }

    fun tapLaunchpadPad(pad: LaunchpadPadType) {
        val rootMidi = _singingMixState.value.mixConfig.detectedRootMidi
        launchpadEngine.playPad(pad, rootMidi)
    }

    fun toggleSequencerStep(pad: LaunchpadPadType, step: Int) {
        _singingMixState.update { state ->
            val currentGrid = state.mixConfig.launchpadGrid.toMutableMap()
            val row = (currentGrid[pad] ?: List(BeatLaunchpadEngine.STEPS_COUNT) { false }).toMutableList()
            if (step in row.indices) {
                row[step] = !row[step]
                currentGrid[pad] = row
            }
            state.copy(
                mixConfig = state.mixConfig.copy(
                    launchpadGrid = currentGrid,
                    beatStyle = BackingBeatStyle.CUSTOM_LAUNCHPAD
                )
            )
        }
    }

    fun updateLaunchpadBpm(bpm: Int) {
        _singingMixState.update {
            it.copy(mixConfig = it.mixConfig.copy(launchpadBpm = bpm.coerceIn(65, 160)))
        }
    }

    fun useLaunchpadBeatInMix() {
        _singingMixState.update {
            it.copy(
                mixConfig = it.mixConfig.copy(
                    beatStyle = BackingBeatStyle.CUSTOM_LAUNCHPAD,
                    detectedBpmOverride = null
                )
            )
        }
        _operationState.value = AsyncOperationState.Success("بیت ساخته‌شده در لانچ‌پد برای خوانندگی و میکس انتخاب شد!")
    }

    fun toggleSequencerLoop() {
        val itemId = "launchpad_preview"
        if (playingItemId.value == itemId && isPlaying.value) {
            playerManager.stop()
            _singingMixState.update { it.copy(isLaunchpadLoopPlaying = false, previewingGenreStyle = null) }
            return
        }
        val cfg = _singingMixState.value.mixConfig
        val grid = cfg.launchpadGrid.ifEmpty { launchpadEngine.defaultSequencerGrid() }
        viewModelScope.launch {
            try {
                val outFile = File(getApplication<Application>().cacheDir, "launchpad_loop.wav")
                launchpadEngine.renderSequencerToWav(
                    bpm = cfg.launchpadBpm,
                    grid = grid,
                    rootMidiNote = cfg.detectedRootMidi,
                    barsCount = 4,
                    outputFile = outFile
                )
                _singingMixState.update { it.copy(isLaunchpadLoopPlaying = true, previewingGenreStyle = null) }
                playerManager.togglePlay(
                    itemId = itemId,
                    filePath = outFile.absolutePath,
                    pitch = 1f,
                    speed = cfg.beatSpeed,
                    scope = viewModelScope,
                    onError = { reportError(it) }
                )
            } catch (e: Exception) {
                reportError(e.localizedMessage ?: "خطا در پخش لوپ لانچ‌پد.")
            }
        }
    }

    fun togglePreviewGenreBeat(style: BackingBeatStyle) {
        val itemId = "genre_preview_${style.id}"
        if (playingItemId.value == itemId && isPlaying.value) {
            playerManager.stop()
            _singingMixState.update { it.copy(previewingGenreStyle = null, isLaunchpadLoopPlaying = false) }
            return
        }
        val cfg = _singingMixState.value.mixConfig
        viewModelScope.launch {
            try {
                val outFile = File(getApplication<Application>().cacheDir, "genre_preview_${style.id}.wav")
                launchpadEngine.renderGenreBeatPreviewToWav(
                    style = style,
                    bpmOverride = null,
                    rootMidiNote = cfg.detectedRootMidi,
                    durationSeconds = 12,
                    outputFile = outFile
                )
                _singingMixState.update {
                    it.copy(
                        previewingGenreStyle = style,
                        isLaunchpadLoopPlaying = false,
                        mixConfig = it.mixConfig.copy(beatStyle = style)
                    )
                }
                playerManager.togglePlay(
                    itemId = itemId,
                    filePath = outFile.absolutePath,
                    pitch = 1f,
                    speed = cfg.beatSpeed,
                    scope = viewModelScope,
                    onError = { reportError(it) }
                )
            } catch (e: Exception) {
                reportError(e.localizedMessage ?: "خطا در پخش پیش‌نمایش بیت.")
            }
        }
    }

    fun autoMatchBeatToVocal() {
        val state = _singingMixState.value
        val controls = _studioControls.value
        val profiles = voiceProfiles.value
        val selectedProfile = profiles.firstOrNull { it.id == controls.selectedVoiceProfileId }

        viewModelScope.launch {
            _operationState.value = AsyncOperationState.Loading("در حال آنالیز گام، تمپو و انرژی صدای شما و ساخت بیت هماهنگ...")
            try {
                val vocalFile: File
                val displayVoiceName: String
                val profileId: Long

                if (state.useRecordedSampleInsteadOfClone) {
                    val sample = audioSamples.value.firstOrNull { it.id == state.selectedVocalSampleId }
                        ?: audioSamples.value.firstOrNull()
                    if (sample == null || !File(sample.filePath).exists()) {
                        reportError("ابتدا یک نمونه صدای خوانندگی ضبط یا انتخاب کنید.")
                        return@launch
                    }
                    vocalFile = File(sample.filePath)
                    displayVoiceName = sample.title
                    profileId = selectedProfile?.id ?: 0L
                } else {
                    val existingRaw = state.rawVocalFilePath?.takeIf { File(it).exists() }
                    if (existingRaw != null) {
                        vocalFile = File(existingRaw)
                        displayVoiceName = selectedProfile?.name ?: "صدای کلون‌شده"
                        profileId = selectedProfile?.id ?: 0L
                    } else if (selectedProfile != null && state.lyrics.isNotBlank()) {
                        val finalLyrics = if (controls.autoAdaptAccentWithAi) {
                            repository.adaptScriptWithGemini(
                                rawText = state.lyrics,
                                targetLanguage = controls.selectedLanguage,
                                accent = controls.selectedAccent,
                                emotion = VoiceEmotion.HAPPY,
                                pitch = controls.pitch,
                                speed = controls.speed
                            )
                        } else {
                            state.lyrics
                        }
                        val sungSpeech = repository.synthesizeSpeech(
                            profile = selectedProfile,
                            originalText = state.lyrics,
                            adaptedText = finalLyrics,
                            accent = controls.selectedAccent,
                            emotion = VoiceEmotion.HAPPY,
                            pitch = controls.pitch,
                            speed = controls.speed,
                            stability = 0.42f,
                            similarityBoost = 0.80f
                        )
                        vocalFile = File(sungSpeech.audioFilePath)
                        displayVoiceName = selectedProfile.name
                        profileId = selectedProfile.id
                    } else {
                        val sample = audioSamples.value.firstOrNull()
                        if (sample == null || !File(sample.filePath).exists()) {
                            reportError("ابتدا صدای کلون‌شده یا یک ضبط خوانندگی داشته باشید تا بیت هماهنگ با آن ساخته شود.")
                            return@launch
                        }
                        vocalFile = File(sample.filePath)
                        displayVoiceName = sample.title
                        profileId = 0L
                    }
                }

                val match = mixMasterEngine.analyzeVocalForAutoBeat(vocalFile)
                val summary = "آنالیز خودکار صدا: گام ${match.detectedKeyNameFa} • سرعت ${match.detectedBpm} BPM • سبک پیشنهادی: ${match.recommendedStyle.titleFa}"

                _singingMixState.update {
                    it.copy(
                        rawVocalFilePath = vocalFile.absolutePath,
                        autoMatchSummary = summary,
                        mixConfig = it.mixConfig.copy(
                            beatStyle = match.recommendedStyle,
                            detectedBpmOverride = match.detectedBpm,
                            detectedRootMidi = match.detectedRootMidi,
                            autoAlignAndDuck = true
                        )
                    )
                }

                runMasteringOnVocalFile(
                    vocalFile = vocalFile,
                    profileId = profileId,
                    voiceName = displayVoiceName,
                    lyrics = state.lyrics
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "autoMatchBeatToVocal failed", e)
                reportError(e.localizedMessage ?: "خطا در پیدا کردن بیت خودکار.")
            }
        }
    }

    fun importCustomBeat(uri: Uri) {
        viewModelScope.launch {
            try {
                val imported = recorderManager.importAudioDataset(uri)
                if (imported != null) {
                    val (file, _) = imported
                    _singingMixState.update {
                        it.copy(
                            customBeatName = file.name,
                            mixConfig = it.mixConfig.copy(
                                beatStyle = BackingBeatStyle.CUSTOM_FILE,
                                customBeatFilePath = file.absolutePath
                            )
                        )
                    }
                    _operationState.value = AsyncOperationState.Success("فایل موزیک پس‌زمینه اضافه شد.")
                }
            } catch (e: Exception) {
                reportError(e.localizedMessage ?: "خطا در وارد کردن فایل موزیک.")
            }
        }
    }

    fun generateAiSingingLyrics() {
        val state = _singingMixState.value
        val topic = state.lyricsTopic.ifBlank { "عشق و باران" }
        val controls = _studioControls.value
        viewModelScope.launch {
            _operationState.value = AsyncOperationState.Loading("در حال سرایش ترانه به زبان ${controls.selectedLanguage.titleFa}...")
            try {
                val generated = repository.generateSingingLyrics(
                    topic = topic,
                    mood = controls.selectedEmotion.titleFa,
                    targetLanguage = controls.selectedLanguage,
                    accent = controls.selectedAccent
                )
                _singingMixState.update { it.copy(lyrics = generated) }
                _operationState.value = AsyncOperationState.Success("ترانه جدید به زبان ${controls.selectedLanguage.titleFa} آماده اجرا شد!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "generateAiSingingLyrics failed", e)
                reportError(e.localizedMessage ?: "خطا در سرایش ترانه.")
            }
        }
    }

    fun synthesizeSingingAndMaster() {
        val mixState = _singingMixState.value
        val controls = _studioControls.value
        val profiles = voiceProfiles.value
        val selectedProfile = profiles.firstOrNull { it.id == controls.selectedVoiceProfileId }

        viewModelScope.launch {
            try {
                val vocalFile: File
                val displayVoiceName: String
                val profileId: Long

                if (mixState.useRecordedSampleInsteadOfClone) {
                    val sample = audioSamples.value.firstOrNull { it.id == mixState.selectedVocalSampleId }
                        ?: audioSamples.value.firstOrNull()
                    if (sample == null || !File(sample.filePath).exists()) {
                        reportError("ابتدا یک نمونه صدای خوانندگی در بخش کلونینگ ضبط یا وارد کنید.")
                        return@launch
                    }
                    vocalFile = File(sample.filePath)
                    displayVoiceName = sample.title
                    profileId = selectedProfile?.id ?: 0L
                } else {
                    if (selectedProfile == null) {
                        reportError("ابتدا صدای کلون‌شده خود را بسازید یا گزینه «استفاده از صدای ضبط‌شده» را فعال کنید.")
                        return@launch
                    }
                    if (mixState.lyrics.isBlank()) {
                        reportError("متن ترانه برای خوانندگی نمی‌تواند خالی باشد.")
                        return@launch
                    }
                    _operationState.value = AsyncOperationState.Loading("در حال آماده‌سازی ترانه به زبان ${controls.selectedLanguage.titleFa}...")
                    val finalLyrics = if (controls.autoAdaptAccentWithAi) {
                        val adapted = repository.adaptScriptWithGemini(
                            rawText = mixState.lyrics,
                            targetLanguage = controls.selectedLanguage,
                            accent = controls.selectedAccent,
                            emotion = VoiceEmotion.HAPPY,
                            pitch = controls.pitch,
                            speed = controls.speed
                        )
                        _singingMixState.update { it.copy(lyrics = adapted) }
                        adapted
                    } else {
                        mixState.lyrics
                    }
                    _operationState.value = AsyncOperationState.Loading("در حال اجرای ترانه با صدای کلون‌شده...")
                    val sungSpeech = repository.synthesizeSpeech(
                        profile = selectedProfile,
                        originalText = mixState.lyrics,
                        adaptedText = finalLyrics,
                        accent = controls.selectedAccent,
                        emotion = VoiceEmotion.HAPPY,
                        pitch = controls.pitch,
                        speed = controls.speed,
                        stability = 0.42f,
                        similarityBoost = 0.80f
                    )
                    vocalFile = File(sungSpeech.audioFilePath)
                    displayVoiceName = selectedProfile.name
                    profileId = selectedProfile.id
                }

                _singingMixState.update { it.copy(rawVocalFilePath = vocalFile.absolutePath) }
                runMasteringOnVocalFile(
                    vocalFile = vocalFile,
                    profileId = profileId,
                    voiceName = displayVoiceName,
                    lyrics = mixState.lyrics
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "synthesizeSingingAndMaster failed", e)
                reportError(e.localizedMessage ?: "خطا در اجرای خوانندگی و میکس.")
            }
        }
    }

    fun remasterExistingVocalTake() {
        val mixState = _singingMixState.value
        val rawPath = mixState.rawVocalFilePath
        if (rawPath.isNullOrBlank() || !File(rawPath).exists()) {
            synthesizeSingingAndMaster()
            return
        }
        val selectedProfile = voiceProfiles.value.firstOrNull {
            it.id == _studioControls.value.selectedVoiceProfileId
        }
        viewModelScope.launch {
            try {
                runMasteringOnVocalFile(
                    vocalFile = File(rawPath),
                    profileId = selectedProfile?.id ?: 0L,
                    voiceName = selectedProfile?.name ?: "وکال من",
                    lyrics = mixState.lyrics
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "remasterExistingVocalTake failed", e)
                reportError(e.localizedMessage ?: "خطا در مسترینگ مجدد.")
            }
        }
    }

    private suspend fun runMasteringOnVocalFile(
        vocalFile: File,
        profileId: Long,
        voiceName: String,
        lyrics: String
    ) {
        val mixState = _singingMixState.value
        _operationState.value = AsyncOperationState.Loading("در حال میکس با بیت، ریورب، اکولایزر و مسترینگ نهایی...")
        val outDir = File(getApplication<Application>().filesDir, "generated_audio").apply { mkdirs() }
        val masteredFile = File(outDir, "mastered_${System.currentTimeMillis()}.wav")

        mixMasterEngine.mixAndMaster(
            vocalFile = vocalFile,
            outputFile = masteredFile,
            config = mixState.mixConfig
        )

        val savedRecord = repository.saveMasteredAudioRecord(
            voiceProfileId = profileId,
            voiceName = voiceName,
            lyrics = lyrics,
            accent = _studioControls.value.selectedAccent,
            beatLabel = mixState.mixConfig.beatStyle.titleFa,
            masteredFile = masteredFile
        )
        _singingMixState.update { it.copy(latestMasteredSpeech = savedRecord) }
        _operationState.value = AsyncOperationState.Success("میکس و مستر با موفقیت انجام شد و فایل خروجی آماده پخش و ذخیره است!")
        playGeneratedSpeech(savedRecord)
    }

    // --- Context-Aware Multi-Turn Support Chatbot ---

    fun updateChatInput(text: String) {
        _chatInput.value = text
    }

    fun sendSupportMessage(overridePrompt: String? = null) {
        val messageText = (overridePrompt ?: _chatInput.value).trim()
        if (messageText.isBlank() || _isBotResponding.value) return

        if (overridePrompt == null) {
            _chatInput.value = ""
        }
        _chatError.value = null

        viewModelScope.launch {
            _isBotResponding.value = true
            _streamingBotResponse.value = ""
            try {
                val priorHistory = repository.getChatHistorySync()
                repository.saveChatMessage("user", messageText)

                val contextSummary = buildStudioContextSummary()
                var finalAnswer = ""
                repository.streamSupportChat(
                    userMessage = messageText,
                    priorHistory = priorHistory,
                    studioContextSummary = contextSummary
                ).collect { accumulated ->
                    finalAnswer = accumulated
                    _streamingBotResponse.value = accumulated
                }

                if (finalAnswer.isNotBlank()) {
                    repository.saveChatMessage("model", finalAnswer)
                }
                _streamingBotResponse.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: TimeoutCancellationException) {
                _streamingBotResponse.value = null
                _chatError.value = "پاسخگویی پشتیبان بیش از حد طول کشید. لطفاً دوباره ارسال کنید."
            } catch (e: Exception) {
                Log.e(TAG, "sendSupportMessage failed", e)
                _streamingBotResponse.value = null
                _chatError.value = e.localizedMessage ?: "خطا در ارتباط با دستیار هوشمند پشتیبانی."
            } finally {
                _isBotResponding.value = false
            }
        }
    }

    fun resetSupportChat() {
        viewModelScope.launch {
            _chatError.value = null
            _streamingBotResponse.value = null
            repository.clearChatHistory()
            repository.saveChatMessage(
                role = "model",
                text = "گفتگوی جدید شروع شد! من وضعیت فعلی صداها، دیتاست‌ها و تنظیمات شما را می‌بینم. چطور می‌توانم کمکتان کنم؟"
            )
        }
    }

    // --- Persian Dubbing Controls ---

    fun updateOriginalVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        DubbingStateBus.setOriginalVolume(clamped)
        val app = getApplication<Application>()
        try {
            val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (max * clamped).toInt(), 0)
        } catch (_: Exception) {
        }
        if (DubbingStateBus.isServiceRunning.value) {
            app.startService(
                Intent(app, PersianDubbingService::class.java).apply {
                    action = PersianDubbingService.ACTION_UPDATE_ORIGINAL_VOLUME
                    putExtra(PersianDubbingService.EXTRA_VOLUME, clamped)
                }
            )
        }
    }

    fun updateDubVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        DubbingStateBus.setDubVolume(clamped)
        playerManager.setVolume(clamped)
        if (DubbingStateBus.isServiceRunning.value) {
            val app = getApplication<Application>()
            app.startService(
                Intent(app, PersianDubbingService::class.java).apply {
                    action = PersianDubbingService.ACTION_UPDATE_DUB_VOLUME
                    putExtra(PersianDubbingService.EXTRA_VOLUME, clamped)
                }
            )
        }
    }

    fun toggleUseClonedVoiceForDubbing(enabled: Boolean) {
        DubbingStateBus.setUseClonedVoiceForDubbing(enabled)
    }

    fun selectDubOutputMode(mode: DubbingOutputMode) {
        DubbingStateBus.setDubOutputMode(mode)
        if (DubbingStateBus.isServiceRunning.value) {
            val app = getApplication<Application>()
            app.startService(
                Intent(app, PersianDubbingService::class.java).apply {
                    action = PersianDubbingService.ACTION_SYNC_OUTPUT_MODE
                }
            )
        }
    }

    fun selectDubEngineMethod(method: DubbingEngineMethod) {
        DubbingStateBus.setDubEngineMethod(method)
    }

    fun selectDubAccent(accent: PersianAccent) {
        DubbingStateBus.setDubAccent(accent)
    }

    fun selectDubToneStyle(style: DubbingToneStyle) {
        DubbingStateBus.setDubToneStyle(style)
    }

    fun selectCinemaDubbingStyle(style: CinemaDubbingStyle) {
        DubbingStateBus.setCinemaDubbingStyle(style)
    }

    fun toggleDualLanguageSubtitles(enabled: Boolean) {
        DubbingStateBus.setDualLanguageSubtitles(enabled)
    }

    fun toggleExportMp4Video(enabled: Boolean) {
        _exportMp4Video.value = enabled
    }

    fun updateClipRange(startSecText: String, endSecText: String) {
        _clipStartSecText.value = startSecText
        _clipEndSecText.value = endSecText
    }

    fun estimateMediaWorkloadUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val est = longMediaProcessor.estimateMediaWorkload(uri)
                _mediaWorkloadSummary.value = est.summaryFa
                _operationState.value = AsyncOperationState.Success(est.summaryFa)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportError(e.localizedMessage ?: "خطا در بررسی مشخصات فایل.")
            }
        }
    }

    fun updateDubExpressiveness(value: Float) {
        DubbingStateBus.setDubExpressiveness(value)
    }

    fun updateDubPitch(pitch: Float) {
        DubbingStateBus.setDubPitch(pitch)
    }

    fun updateDubSpeed(speed: Float) {
        DubbingStateBus.setDubSpeed(speed)
    }

    fun clearDubbingSegments() {
        DubbingStateBus.clearSegments()
    }

    fun dubMediaFileUri(uri: Uri) {
        viewModelScope.launch {
            val mode = DubbingStateBus.dubOutputMode.value
            _operationState.value = AsyncOperationState.Loading("در حال پردازش هوشمند فایل (${mode.titleFa})...")
            DubbingStateBus.setLongFileProgressText("در حال آماده‌سازی جریان صوتی فایل...")
            try {
                val est = runCatching { longMediaProcessor.estimateMediaWorkload(uri) }.getOrNull()
                if (est != null) _mediaWorkloadSummary.value = est.summaryFa

                val accent = DubbingStateBus.dubAccent.value
                val toneStyle = DubbingStateBus.dubToneStyle.value
                val cinemaStyle = DubbingStateBus.cinemaDubbingStyle.value
                val expressiveness = DubbingStateBus.dubExpressiveness.value
                val preferredMethod = DubbingStateBus.dubEngineMethod.value
                val selectedProfile = voiceProfiles.value.firstOrNull {
                    it.id == _studioControls.value.selectedVoiceProfileId
                }
                val startSec = _clipStartSecText.value.toLongOrNull()
                val endSec = _clipEndSecText.value.toLongOrNull()

                val summary = longMediaProcessor.processLongMediaUri(
                    uri = uri,
                    outputMode = mode,
                    preferredMethod = preferredMethod,
                    targetAccent = accent,
                    toneStyle = toneStyle,
                    cinemaStyle = cinemaStyle,
                    expressiveness = expressiveness,
                    userPitch = DubbingStateBus.dubPitch.value,
                    userSpeed = DubbingStateBus.dubSpeed.value,
                    originalBedVolume = DubbingStateBus.originalVolume.value,
                    dualLanguageSubtitles = DubbingStateBus.dualLanguageSubtitles.value,
                    exportMp4Video = _exportMp4Video.value,
                    clipStartSec = startSec,
                    clipEndSec = endSec,
                    clonedVoiceRemoteId = selectedProfile?.elevenLabsVoiceId,
                    useClonedVoice = DubbingStateBus.useClonedVoiceForDubbing.value,
                    localTts = localDubTts,
                    localTtsReady = localDubTtsReady,
                    onProgress = { prog ->
                        DubbingStateBus.setLongFileProgressText(prog)
                        _operationState.value = AsyncOperationState.Loading(prog)
                    },
                    onSegmentProduced = { seg ->
                        DubbingStateBus.addSegment(seg)
                        val speakerBadge = seg.speakerSlotLabel.ifBlank { seg.speakerCharacter.titleFa }
                        DubbingStateBus.setStatus("$speakerBadge (${seg.detectedEmotion.titleFa}): ${seg.persianTranslation}")
                    }
                )

                DubbingStateBus.setLatestSrtFilePath(summary.srtFile?.absolutePath)
                DubbingStateBus.setLatestDubbedAudioPath(summary.dubbedAudioFile?.absolutePath)
                DubbingStateBus.setLatestDubbedVideoPath(summary.dubbedVideoFile?.absolutePath)
                DubbingStateBus.setLatestDubOnlyStemPath(summary.dubOnlyStemFile?.absolutePath)
                DubbingStateBus.setLatestSfxOnlyStemPath(summary.sfxBgmStemFile?.absolutePath)

                if (summary.segmentCount == 0) {
                    DubbingStateBus.setLongFileProgressText(null)
                    reportError("گفتار واضحی برای ترجمه به فارسی در این فایل پیدا نشد.")
                    return@launch
                }

                val doneMsg = buildString {
                    append("${summary.segmentCount} دیالوگ ترجمه شد")
                    if (summary.skippedSfxCount > 0) append(" (${summary.skippedSfxCount} بخش افکت/انفجار حفظ و رایگان رد شد)")
                    if (summary.dubbedVideoFile != null) append(" • ویدئوی MP4 آماده ذخیره است!")
                    else if (summary.dubbedAudioFile != null) append(" • فایل صوتی دوبله آماده پخش است!")
                }
                DubbingStateBus.setLongFileProgressText(doneMsg)
                _operationState.value = AsyncOperationState.Success(doneMsg)

                val dubbedPath = summary.dubbedAudioFile?.absolutePath
                if (dubbedPath != null && mode != DubbingOutputMode.SUBTITLE_ONLY) {
                    playerManager.togglePlay(
                        itemId = "dub_file_output",
                        filePath = dubbedPath,
                        pitch = 1f,
                        speed = 1f,
                        volume = DubbingStateBus.dubVolume.value,
                        scope = viewModelScope,
                        onError = { reportError(it) }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "dubMediaFileUri failed", e)
                DubbingStateBus.setLongFileProgressText(null)
                reportError(e.localizedMessage ?: "خطا در پردازش زیرنویس/دوبله فایل.")
            }
        }
    }

    fun togglePlayDubbedAudioFile(path: String) {
        val file = File(path)
        if (!file.exists()) {
            reportError("فایل صوتی دوبله یافت نشد.")
            return
        }
        playerManager.togglePlay(
            itemId = "dub_file_output",
            filePath = path,
            pitch = 1f,
            speed = 1f,
            volume = DubbingStateBus.dubVolume.value,
            scope = viewModelScope,
            onError = { reportError(it) }
        )
    }

    fun exportLocalFileToUri(sourcePath: String, destUri: Uri, successMessage: String) {
        viewModelScope.launch {
            _operationState.value = AsyncOperationState.Loading("در حال ذخیره فایل در حافظه...")
            try {
                repository.exportAudioFileToUri(sourcePath, destUri)
                _operationState.value = AsyncOperationState.Success(successMessage)
            } catch (e: Exception) {
                reportError(e.localizedMessage ?: "خطا در ذخیره فایل.")
            }
        }
    }

    fun createShareFileIntent(sourcePath: String, mimeType: String): Intent? {
        val file = File(sourcePath)
        if (!file.exists()) {
            reportError("فایل مورد نظر یافت نشد.")
            return null
        }
        val app = getApplication<Application>()
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // --- User API Keys Settings (BYOK + Encrypted Android Keystore) ---

    fun saveUserApiKeys(config: UserApiKeysConfig) {
        val app = getApplication<Application>()
        UserApiKeyStore.save(app, config)
        _userApiKeys.value = UserApiKeyStore.load(app)
        _operationState.value = AsyncOperationState.Success(
            "کلیدهای API شما به‌صورت رمزنگاری‌شده در Keystore محلی دستگاه ذخیره شدند."
        )
    }

    fun clearUserApiKeys() {
        val app = getApplication<Application>()
        UserApiKeyStore.clearAll(app)
        _userApiKeys.value = UserApiKeyStore.load(app)
        _apiTestResults.value = emptyMap()
        _operationState.value = AsyncOperationState.Success(
            "کلیدهای رمزنگاری‌شده از Keystore پاک شدند."
        )
    }

    fun testSingleApi(target: ApiProviderTarget, draftConfig: UserApiKeysConfig) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _testingApiProvider.value = target
            try {
                val res = ApiConnectionTester.testProvider(app, target, draftConfig)
                _apiTestResults.update { it + (target to res) }
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (_testingApiProvider.value == target) {
                    _testingApiProvider.value = null
                }
            }
        }
    }

    fun testAllConfiguredApis(draftConfig: UserApiKeysConfig) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _isTestingAllApis.value = true
            try {
                val targets = listOf(
                    ApiProviderTarget.IRAN_GATEWAY,
                    ApiProviderTarget.ELEVENLABS,
                    ApiProviderTarget.GEMINI,
                    ApiProviderTarget.OPENAI,
                    ApiProviderTarget.CUSTOM
                )
                for (target in targets) {
                    _testingApiProvider.value = target
                    val res = ApiConnectionTester.testProvider(app, target, draftConfig)
                    _apiTestResults.update { it + (target to res) }
                }
                val passed = _apiTestResults.value.values.count { it.success }
                _operationState.value = AsyncOperationState.Success(
                    "تست API ها انجام شد: $passed از ${targets.size} سرویس متصل است."
                )
            } catch (e: CancellationException) {
                throw e
            } finally {
                _testingApiProvider.value = null
                _isTestingAllApis.value = false
            }
        }
    }

    // --- Podcast & Multi-Character Audiobook Studio ---

    fun selectPodcastMode(mode: PodcastEpisodeMode) {
        _podcastState.update { it.copy(episodeMode = mode) }
    }

    fun selectPodcastAccent(accent: PersianAccent) {
        _podcastState.update { it.copy(accent = accent) }
    }

    fun updatePodcastTopicText(text: String) {
        _podcastState.update { it.copy(topicOrBookText = text) }
    }

    fun togglePodcastAmbientMusic(enabled: Boolean) {
        _podcastState.update { it.copy(includeAmbientMusic = enabled) }
    }

    fun togglePodcastClonedHost(enabled: Boolean) {
        _podcastState.update { it.copy(useClonedVoiceForHost = enabled) }
    }

    fun updatePodcastTurnText(index: Int, newText: String) {
        _podcastState.update { st ->
            val updated = st.turns.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(text = newText)
            }
            st.copy(turns = updated)
        }
    }

    fun cyclePodcastTurnCharacter(index: Int) {
        _podcastState.update { st ->
            val updated = st.turns.toMutableList()
            if (index in updated.indices) {
                val all = VoiceCharacterArchetype.entries
                val next = all[(updated[index].archetype.ordinal + 1) % all.size]
                updated[index] = updated[index].copy(archetype = next)
            }
            st.copy(turns = updated)
        }
    }

    fun generatePodcastScript() {
        val st = _podcastState.value
        if (st.topicOrBookText.isBlank()) {
            reportError("لطفاً موضوع پادکست یا متن کتاب را وارد کنید.")
            return
        }
        viewModelScope.launch {
            _operationState.value = AsyncOperationState.Loading("در حال نگارش سناریوی چندشخصیتی با کارگردان هوش مصنوعی...")
            try {
                val turns = failoverEngine.generatePodcastScriptWithFallback(
                    topicOrBookExcerpt = st.topicOrBookText,
                    episodeMode = st.episodeMode.titleFa,
                    accent = st.accent,
                    turnCount = 6
                )
                _podcastState.update { it.copy(turns = turns) }
                _operationState.value = AsyncOperationState.Success(
                    "سناریو با ${turns.size} شخصیت آماده شد! می‌توانید متن یا جنسیت کاراکترها را تغییر دهید و اپیزود را بسازید."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "generatePodcastScript failed", e)
                reportError(e.localizedMessage ?: "خطا در تولید سناریو.")
            }
        }
    }

    fun renderPodcastEpisodeAudio() {
        val st = _podcastState.value
        if (st.turns.isEmpty()) {
            reportError("ابتدا سناریو را تولید کنید.")
            return
        }
        val selectedProfile = voiceProfiles.value.firstOrNull {
            it.id == _studioControls.value.selectedVoiceProfileId
        }
        viewModelScope.launch {
            _operationState.value = AsyncOperationState.Loading("در حال صداگذاری و مستر اپیزود پادکست...")
            try {
                val outFile = podcastEngine.renderMultiCharacterEpisode(
                    turns = st.turns,
                    episodeMode = st.episodeMode,
                    accent = st.accent,
                    includeAmbientMusic = st.includeAmbientMusic,
                    useClonedVoiceForHost = st.useClonedVoiceForHost,
                    clonedVoiceRemoteId = selectedProfile?.elevenLabsVoiceId,
                    localTts = localDubTts,
                    localTtsReady = localDubTtsReady,
                    onProgress = { msg ->
                        _operationState.value = AsyncOperationState.Loading(msg)
                    }
                )
                _podcastState.update { it.copy(latestRenderedWavPath = outFile.absolutePath) }
                _operationState.value = AsyncOperationState.Success("اپیزود صوتی چندشخصیتی با موفقیت ساخته شد!")
                togglePlayPodcastEpisode(outFile.absolutePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "renderPodcastEpisodeAudio failed", e)
                reportError(e.localizedMessage ?: "خطا در رندر صوتی پادکست.")
            }
        }
    }

    fun togglePlayPodcastEpisode(path: String) {
        if (!File(path).exists()) {
            reportError("فایل اپیزود یافت نشد.")
            return
        }
        playerManager.togglePlay(
            itemId = "podcast_episode_output",
            filePath = path,
            pitch = 1f,
            speed = 1f,
            volume = 1f,
            scope = viewModelScope,
            onError = { reportError(it) }
        )
    }

    // --- Proxy Settings (App-Only Split Tunnel) ---

    fun saveProxySettings(settings: ProxySettings) {
        val app = getApplication<Application>()
        ProxyConfigStore.save(app, settings)
        _proxySettings.value = settings
        _proxyTestResult.value = null
        _operationState.value = AsyncOperationState.Success(
            if (settings.enabled) {
                "تونل اختصاصی برنامه (${settings.protocol.titleFa}: ${settings.host}:${settings.port}) ذخیره و فعال شد."
            } else {
                "پروکسی غیرفعال شد و اتصال مستقیم برقرار است."
            }
        )
    }

    fun testProxyConnection(settings: ProxySettings) {
        val app = getApplication<Application>()
        ProxyConfigStore.save(app, settings)
        _proxySettings.value = settings
        viewModelScope.launch {
            _isTestingProxy.value = true
            _proxyTestResult.value = null
            try {
                _proxyTestResult.value = ProxyConfigStore.testConnection(app)
            } finally {
                _isTestingProxy.value = false
            }
        }
    }

    fun autoDiscoverBestProxy(customListText: String, includePublicPool: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _isAutoDiscoveringProxy.value = true
            _proxyTestResult.value = null
            try {
                val report = ProxyConfigStore.autoDiscoverAndSelectBestProxy(
                    context = app,
                    customListText = customListText,
                    includePublicPool = includePublicPool
                )
                _autoProxyDiscoveryReport.value = report
                _proxySettings.value = ProxyConfigStore.load(app)
                if (report.bestCandidate != null) {
                    _proxyTestResult.value = true to report.summaryMessage
                    _operationState.value = AsyncOperationState.Success(report.summaryMessage)
                } else {
                    _proxyTestResult.value = false to report.summaryMessage
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isAutoDiscoveringProxy.value = false
            }
        }
    }

    fun applyDiscoveredProxyCandidate(candidate: ProxyBenchmarkCandidate, customListText: String) {
        val updated = _proxySettings.value.copy(
            enabled = true,
            protocol = candidate.protocol,
            host = candidate.host,
            port = candidate.port,
            username = candidate.username,
            password = candidate.password,
            customProxyListText = customListText
        )
        saveProxySettings(updated)
        _proxyTestResult.value = true to "پروکسی ${candidate.displayEndpoint()} (${candidate.latencyMs}ms) فقط برای همین برنامه فعال شد."
    }

    private fun buildStudioContextSummary(): String {
        val profiles = voiceProfiles.value
        val samples = audioSamples.value
        val speeches = generatedSpeeches.value
        val controls = _studioControls.value
        val proxy = _proxySettings.value
        val activeVoice = profiles.firstOrNull { it.id == controls.selectedVoiceProfileId }
        val avgQuality = samples.mapNotNull { it.qualityScore }.takeIf { it.isNotEmpty() }?.average()?.toInt()

        return buildString {
            appendLine("- وضعیت کلید ElevenLabs API: ${if (isElevenLabsConfigured) "تنظیم شده و آماده" else "تنظیم نشده (در بخش کلید API یا پنل Secrets قابل ثبت است)"}")
            appendLine("- وضعیت پروکسی: ${if (proxy.enabled) "فعال (${proxy.protocol.titleFa} -> ${proxy.host}:${proxy.port})" else "غیرفعال"}")
            appendLine("- وضعیت دوبله زنده فارسی: ${if (dubbingActive.value) "فعال (${dubOutputMode.value.titleFa})" else "آماده‌باش"} • ولوم اصلی=${(dubOriginalVolume.value * 100).toInt()}% • ولوم دوبله=${(dubVolume.value * 100).toInt()}%")
            appendLine("- تعداد صداهای کلون‌شده: ${profiles.size} (${profiles.joinToString("، ") { "${it.name} [لهجه: ${PersianAccent.fromId(it.accentId).titleFa}، ${it.sampleCount} نمونه]" }.ifBlank { "هیچ" }})")
            appendLine("- تعداد نمونه‌های صوتی و دیتاست‌های فارسی روی دستگاه: ${samples.size} (میانگین امتیاز کیفیت: ${avgQuality?.toString() ?: "هنوز تحلیل نشده"})")
            appendLine("- تعداد خروجی‌های گفتاری ساخته‌شده: ${speeches.size}")
            appendLine("- صدای فعال در استودیو: ${activeVoice?.name ?: "انتخاب نشده"}")
            appendLine("- تنظیمات فعلی استودیو: زبان=${controls.selectedLanguage.titleFa}، لهجه=${controls.selectedAccent.titleFa}، احساس=${controls.selectedEmotion.titleFa}، لحن(Pitch)=${"%.2f".format(controls.pitch)}x، سرعت(Speed)=${"%.2f".format(controls.speed)}x، ثبات=${"%.2f".format(controls.stability)}")
            if (!lastEncounteredError.isNullOrBlank()) {
                appendLine("- آخرین خطای ثبت‌شده در برنامه: $lastEncounteredError")
            }
        }
    }

    private fun reportError(message: String) {
        lastEncounteredError = message
        _operationState.value = AsyncOperationState.Error(message)
    }

    override fun onCleared() {
        super.onCleared()
        recorderManager.release()
        playerManager.stop()
        launchpadEngine.releaseAllTracks()
        localDubTts?.shutdown()
        localDubTts = null
    }
}
