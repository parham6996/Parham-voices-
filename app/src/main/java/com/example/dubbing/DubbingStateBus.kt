package com.example.dubbing

import com.example.domain.CinemaDubbingStyle
import com.example.domain.DubbingEngineMethod
import com.example.domain.DubbingOutputMode
import com.example.domain.DubbingToneStyle
import com.example.domain.PersianAccent
import com.example.domain.VoiceCharacterArchetype
import com.example.domain.VoiceEmotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DubbingSegment(
    val id: Long = System.currentTimeMillis(),
    val detectedLanguage: String,
    val originalTranscript: String,
    val persianTranslation: String,
    val usedClonedVoice: Boolean,
    val detectedEmotion: VoiceEmotion = VoiceEmotion.NEUTRAL,
    val speakerCharacter: VoiceCharacterArchetype = VoiceCharacterArchetype.ADULT_MALE,
    val speakerSlotLabel: String = "",
    val engineUsedName: String = "Gemini 3.8",
    val failoverOccurred: Boolean = false,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

object DubbingStateBus {
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _isDubbingActive = MutableStateFlow(false)
    val isDubbingActive: StateFlow<Boolean> = _isDubbingActive.asStateFlow()

    private val _isProcessingSegment = MutableStateFlow(false)
    val isProcessingSegment: StateFlow<Boolean> = _isProcessingSegment.asStateFlow()

    private val _originalVolume = MutableStateFlow(0.65f)
    val originalVolume: StateFlow<Float> = _originalVolume.asStateFlow()

    private val _dubVolume = MutableStateFlow(0.95f)
    val dubVolume: StateFlow<Float> = _dubVolume.asStateFlow()

    private val _useClonedVoiceForDubbing = MutableStateFlow(false)
    val useClonedVoiceForDubbing: StateFlow<Boolean> = _useClonedVoiceForDubbing.asStateFlow()

    private val _dubOutputMode = MutableStateFlow(DubbingOutputMode.DUB_AND_SUBTITLE)
    val dubOutputMode: StateFlow<DubbingOutputMode> = _dubOutputMode.asStateFlow()

    private val _currentSubtitleText = MutableStateFlow("")
    val currentSubtitleText: StateFlow<String> = _currentSubtitleText.asStateFlow()

    private val _dubEngineMethod = MutableStateFlow(DubbingEngineMethod.AUTO_FAILOVER)
    val dubEngineMethod: StateFlow<DubbingEngineMethod> = _dubEngineMethod.asStateFlow()

    private val _dubAccent = MutableStateFlow(PersianAccent.TEHRANI)
    val dubAccent: StateFlow<PersianAccent> = _dubAccent.asStateFlow()

    private val _dubToneStyle = MutableStateFlow(DubbingToneStyle.CINEMA_NATURAL)
    val dubToneStyle: StateFlow<DubbingToneStyle> = _dubToneStyle.asStateFlow()

    private val _cinemaDubbingStyle = MutableStateFlow(CinemaDubbingStyle.NATURAL_CINEMA)
    val cinemaDubbingStyle: StateFlow<CinemaDubbingStyle> = _cinemaDubbingStyle.asStateFlow()

    private val _dualLanguageSubtitles = MutableStateFlow(false)
    val dualLanguageSubtitles: StateFlow<Boolean> = _dualLanguageSubtitles.asStateFlow()

    private val _dubExpressiveness = MutableStateFlow(0.82f)
    val dubExpressiveness: StateFlow<Float> = _dubExpressiveness.asStateFlow()

    private val _dubPitch = MutableStateFlow(1.0f)
    val dubPitch: StateFlow<Float> = _dubPitch.asStateFlow()

    private val _dubSpeed = MutableStateFlow(1.0f)
    val dubSpeed: StateFlow<Float> = _dubSpeed.asStateFlow()

    private val _selectedClonedVoiceRemoteId = MutableStateFlow<String?>(null)
    val selectedClonedVoiceRemoteId: StateFlow<String?> = _selectedClonedVoiceRemoteId.asStateFlow()

    private val _activeEngineLabel = MutableStateFlow("آماده • سوییچ ضدقطعی + درگاه ایران بدون فیلترشکن")
    val activeEngineLabel: StateFlow<String> = _activeEngineLabel.asStateFlow()

    private val _statusText = MutableStateFlow("آماده دوبله چندشخصیتی و زیرنویس زنده فارسی (بدون دوبله انفجار و افکت)")
    val statusText: StateFlow<String?> = _statusText.asStateFlow()

    private val _latestSrtFilePath = MutableStateFlow<String?>(null)
    val latestSrtFilePath: StateFlow<String?> = _latestSrtFilePath.asStateFlow()

    private val _latestDubbedAudioPath = MutableStateFlow<String?>(null)
    val latestDubbedAudioPath: StateFlow<String?> = _latestDubbedAudioPath.asStateFlow()

    private val _latestDubbedVideoPath = MutableStateFlow<String?>(null)
    val latestDubbedVideoPath: StateFlow<String?> = _latestDubbedVideoPath.asStateFlow()

    private val _latestDubOnlyStemPath = MutableStateFlow<String?>(null)
    val latestDubOnlyStemPath: StateFlow<String?> = _latestDubOnlyStemPath.asStateFlow()

    private val _latestSfxOnlyStemPath = MutableStateFlow<String?>(null)
    val latestSfxOnlyStemPath: StateFlow<String?> = _latestSfxOnlyStemPath.asStateFlow()

    private val _longFileProgressText = MutableStateFlow<String?>(null)
    val longFileProgressText: StateFlow<String?> = _longFileProgressText.asStateFlow()

    private val _segments = MutableStateFlow<List<DubbingSegment>>(emptyList())
    val segments: StateFlow<List<DubbingSegment>> = _segments.asStateFlow()

    fun setServiceRunning(running: Boolean) { _isServiceRunning.value = running }
    fun setOverlayVisible(visible: Boolean) { _isOverlayVisible.value = visible }
    fun setDubbingActive(active: Boolean) { _isDubbingActive.value = active }
    fun setProcessingSegment(processing: Boolean) { _isProcessingSegment.value = processing }
    fun setOriginalVolume(volume: Float) { _originalVolume.value = volume.coerceIn(0f, 1f) }
    fun setDubVolume(volume: Float) { _dubVolume.value = volume.coerceIn(0f, 1f) }
    fun setUseClonedVoiceForDubbing(useClone: Boolean) { _useClonedVoiceForDubbing.value = useClone }
    fun setDubOutputMode(mode: DubbingOutputMode) { _dubOutputMode.value = mode }
    fun setCurrentSubtitleText(text: String) { _currentSubtitleText.value = text }
    fun setSelectedClonedVoiceRemoteId(remoteId: String?) { _selectedClonedVoiceRemoteId.value = remoteId }

    fun setDubEngineMethod(method: DubbingEngineMethod) {
        _dubEngineMethod.value = method
        _activeEngineLabel.value = "موتور انتخابی: ${method.titleFa}"
    }

    fun setDubAccent(accent: PersianAccent) { _dubAccent.value = accent }
    fun setDubToneStyle(style: DubbingToneStyle) { _dubToneStyle.value = style }
    fun setCinemaDubbingStyle(style: CinemaDubbingStyle) { _cinemaDubbingStyle.value = style }
    fun setDualLanguageSubtitles(enabled: Boolean) { _dualLanguageSubtitles.value = enabled }
    fun setDubExpressiveness(value: Float) { _dubExpressiveness.value = value.coerceIn(0f, 1f) }
    fun setDubPitch(pitch: Float) { _dubPitch.value = pitch.coerceIn(0.75f, 1.30f) }
    fun setDubSpeed(speed: Float) { _dubSpeed.value = speed.coerceIn(0.80f, 1.25f) }
    fun setActiveEngineLabel(label: String) { _activeEngineLabel.value = label }
    fun setStatus(message: String) { _statusText.value = message }
    fun setLatestSrtFilePath(path: String?) { _latestSrtFilePath.value = path }
    fun setLatestDubbedAudioPath(path: String?) { _latestDubbedAudioPath.value = path }
    fun setLatestDubbedVideoPath(path: String?) { _latestDubbedVideoPath.value = path }
    fun setLatestDubOnlyStemPath(path: String?) { _latestDubOnlyStemPath.value = path }
    fun setLatestSfxOnlyStemPath(path: String?) { _latestSfxOnlyStemPath.value = path }
    fun setLongFileProgressText(text: String?) { _longFileProgressText.value = text }

    fun addSegment(segment: DubbingSegment) {
        _segments.value = (listOf(segment) + _segments.value).take(120)
    }

    fun clearSegments() {
        _segments.value = emptyList()
        _currentSubtitleText.value = ""
    }
}
