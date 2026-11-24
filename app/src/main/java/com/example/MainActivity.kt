package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.GeneratedSpeechEntity
import com.example.dubbing.PersianDubbingService
import com.example.firebase.configureAppCheck
import com.example.ui.screens.ApiKeysSettingsScreen
import com.example.ui.screens.DubbingScreen
import com.example.ui.screens.MixMasterScreen
import com.example.ui.screens.PodcastStudioScreen
import com.example.ui.screens.ProxySettingsScreen
import com.example.ui.screens.Studio3DScreen
import com.example.ui.screens.StudioSpeakScreen
import com.example.ui.screens.SupportChatScreen
import com.example.ui.screens.VoiceCloningScreen
import com.example.ui.screens.VoiceLibraryScreen
import com.example.ui.theme.AvaCloneTheme
import com.example.viewmodel.StudioTab
import com.example.viewmodel.VoiceStudioViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or ViewModel operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            AvaCloneTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AvaCloneRootScreen()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvaCloneRootScreen(
    viewModel: VoiceStudioViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val studioControls by viewModel.studioControls.collectAsStateWithLifecycle()
    val cloningState by viewModel.cloningState.collectAsStateWithLifecycle()
    val mixState by viewModel.singingMixState.collectAsStateWithLifecycle()
    val voiceProfiles by viewModel.voiceProfiles.collectAsStateWithLifecycle()
    val audioSamples by viewModel.audioSamples.collectAsStateWithLifecycle()
    val generatedSpeeches by viewModel.generatedSpeeches.collectAsStateWithLifecycle()
    val latestSpeech by viewModel.latestGeneratedSpeech.collectAsStateWithLifecycle()
    val operationState by viewModel.operationState.collectAsStateWithLifecycle()

    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.recordingElapsedSeconds.collectAsStateWithLifecycle()
    val waveBars by viewModel.recordingWaveBars.collectAsStateWithLifecycle()
    val recordingLevel by viewModel.recordingLevel.collectAsStateWithLifecycle()
    val recordingPeakDb by viewModel.recordingPeakDb.collectAsStateWithLifecycle()

    val playingItemId by viewModel.playingItemId.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val currentPosMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val playbackWaveformEnvelope by viewModel.playbackWaveformEnvelope.collectAsStateWithLifecycle()
    val playbackLiveBars by viewModel.playbackLiveBars.collectAsStateWithLifecycle()
    val playbackLevel by viewModel.playbackLevel.collectAsStateWithLifecycle()
    val playbackDb by viewModel.playbackDb.collectAsStateWithLifecycle()

    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val chatInput by viewModel.chatInput.collectAsStateWithLifecycle()
    val streamingBotText by viewModel.streamingBotResponse.collectAsStateWithLifecycle()
    val isBotResponding by viewModel.isBotResponding.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()

    val proxySettings by viewModel.proxySettings.collectAsStateWithLifecycle()
    val userApiKeys by viewModel.userApiKeys.collectAsStateWithLifecycle()
    val isTestingProxy by viewModel.isTestingProxy.collectAsStateWithLifecycle()
    val isAutoDiscoveringProxy by viewModel.isAutoDiscoveringProxy.collectAsStateWithLifecycle()
    val proxyTestResult by viewModel.proxyTestResult.collectAsStateWithLifecycle()
    val autoProxyDiscoveryReport by viewModel.autoProxyDiscoveryReport.collectAsStateWithLifecycle()
    val apiTestResults by viewModel.apiTestResults.collectAsStateWithLifecycle()
    val testingApiProvider by viewModel.testingApiProvider.collectAsStateWithLifecycle()
    val isTestingAllApis by viewModel.isTestingAllApis.collectAsStateWithLifecycle()
    val podcastState by viewModel.podcastState.collectAsStateWithLifecycle()

    val dubServiceRunning by viewModel.dubbingServiceRunning.collectAsStateWithLifecycle()
    val dubOverlayVisible by viewModel.dubbingOverlayVisible.collectAsStateWithLifecycle()
    val dubActive by viewModel.dubbingActive.collectAsStateWithLifecycle()
    val dubProcessing by viewModel.dubbingProcessing.collectAsStateWithLifecycle()
    val dubOriginalVolume by viewModel.dubOriginalVolume.collectAsStateWithLifecycle()
    val dubVolume by viewModel.dubVolume.collectAsStateWithLifecycle()
    val dubUseClonedVoice by viewModel.dubUseClonedVoice.collectAsStateWithLifecycle()
    val dubOutputMode by viewModel.dubOutputMode.collectAsStateWithLifecycle()
    val dubCurrentSubtitle by viewModel.dubCurrentSubtitle.collectAsStateWithLifecycle()
    val dubLatestSrtFilePath by viewModel.dubLatestSrtFilePath.collectAsStateWithLifecycle()
    val dubLatestDubbedAudioPath by viewModel.dubLatestDubbedAudioPath.collectAsStateWithLifecycle()
    val dubLatestDubbedVideoPath by viewModel.dubLatestDubbedVideoPath.collectAsStateWithLifecycle()
    val dubLatestDubOnlyStemPath by viewModel.dubLatestDubOnlyStemPath.collectAsStateWithLifecycle()
    val dubLatestSfxOnlyStemPath by viewModel.dubLatestSfxOnlyStemPath.collectAsStateWithLifecycle()
    val dubLongFileProgressText by viewModel.dubLongFileProgressText.collectAsStateWithLifecycle()
    val mediaWorkloadSummary by viewModel.mediaWorkloadSummary.collectAsStateWithLifecycle()
    val dubEngineMethod by viewModel.dubEngineMethod.collectAsStateWithLifecycle()
    val dubAccent by viewModel.dubAccent.collectAsStateWithLifecycle()
    val dubToneStyle by viewModel.dubToneStyle.collectAsStateWithLifecycle()
    val dubCinemaStyle by viewModel.dubCinemaStyle.collectAsStateWithLifecycle()
    val dubDualLanguageSubtitles by viewModel.dubDualLanguageSubtitles.collectAsStateWithLifecycle()
    val exportMp4Video by viewModel.exportMp4Video.collectAsStateWithLifecycle()
    val clipStartSecText by viewModel.clipStartSecText.collectAsStateWithLifecycle()
    val clipEndSecText by viewModel.clipEndSecText.collectAsStateWithLifecycle()
    val dubExpressiveness by viewModel.dubExpressiveness.collectAsStateWithLifecycle()
    val dubPitch by viewModel.dubPitch.collectAsStateWithLifecycle()
    val dubSpeed by viewModel.dubSpeed.collectAsStateWithLifecycle()
    val dubActiveEngineLabel by viewModel.dubActiveEngineLabel.collectAsStateWithLifecycle()
    val dubStatusText by viewModel.dubStatusText.collectAsStateWithLifecycle()
    val dubSegments by viewModel.dubSegments.collectAsStateWithLifecycle()

    var pendingExportSpeech by remember { mutableStateOf<GeneratedSpeechEntity?>(null) }
    var pendingExportFilePath by remember { mutableStateOf<String?>(null) }
    var pendingExportSuccessMsg by remember { mutableStateOf("فایل ذخیره شد.") }

    val saveAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val targetSpeech = pendingExportSpeech
        pendingExportSpeech = null
        if (destUri != null && targetSpeech != null) {
            viewModel.exportSpeechToUri(targetSpeech, destUri)
        }
    }

    val saveGenericFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val srcPath = pendingExportFilePath
        pendingExportFilePath = null
        if (destUri != null && srcPath != null) {
            viewModel.exportLocalFileToUri(srcPath, destUri, pendingExportSuccessMsg)
        }
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, PersianDubbingService::class.java).apply {
                action = PersianDubbingService.ACTION_START_CAPTURE
                putExtra(PersianDubbingService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(PersianDubbingService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

    val handleSaveSpeech: (GeneratedSpeechEntity) -> Unit = { speech ->
        pendingExportSpeech = speech
        val ext = File(speech.audioFilePath).extension.ifBlank { "mp3" }
        saveAudioLauncher.launch("parham_voice_${speech.id}.$ext")
    }

    val handleShareSpeech: (GeneratedSpeechEntity) -> Unit = { speech ->
        viewModel.createShareIntent(speech)?.let { shareIntent ->
            context.startActivity(
                Intent.createChooser(shareIntent, "اشتراک‌گذاری فایل صدای کلون‌شده")
            )
        }
    }

    val handleRequestMediaProjection: () -> Unit = {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    val handleRequestOverlayPermission: () -> Unit = {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }

    val handleShowFloatingOverlay: () -> Unit = {
        context.startService(
            Intent(context, PersianDubbingService::class.java).apply {
                action = PersianDubbingService.ACTION_SHOW_OVERLAY_ONLY
            }
        )
    }

    val handleToggleDubbingActive: () -> Unit = {
        context.startService(
            Intent(context, PersianDubbingService::class.java).apply {
                action = PersianDubbingService.ACTION_TOGGLE_DUBBING
            }
        )
    }

    val handleStopDubbingService: () -> Unit = {
        context.startService(
            Intent(context, PersianDubbingService::class.java).apply {
                action = PersianDubbingService.ACTION_STOP_SERVICE
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ParHam VoiCe",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.selectTab(StudioTab.PODCAST) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (currentTab == StudioTab.PODCAST) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            modifier = Modifier.testTag("top_podcast_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Podcasts,
                                contentDescription = "پادکست و کتاب صوتی",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "پادکست",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        FilledTonalButton(
                            onClick = { viewModel.selectTab(StudioTab.API_KEYS) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (currentTab == StudioTab.API_KEYS || userApiKeys.hasAnyKey()) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            modifier = Modifier.testTag("top_api_keys_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "کلیدهای API",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "API",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        FilledTonalButton(
                            onClick = { viewModel.selectTab(StudioTab.STUDIO_3D) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (currentTab == StudioTab.STUDIO_3D) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            modifier = Modifier.testTag("top_3d_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewInAr,
                                contentDescription = "استودیو سه‌بعدی",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "3D",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        FilledTonalButton(
                            onClick = { viewModel.selectTab(StudioTab.PROXY) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (proxySettings.enabled || currentTab == StudioTab.PROXY) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            modifier = Modifier.testTag("top_proxy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnLock,
                                contentDescription = "تنظیمات پروکسی",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (proxySettings.enabled) "پروکسی✓" else "پروکسی",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        FilledTonalButton(
                            onClick = { viewModel.selectTab(StudioTab.SUPPORT) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (currentTab == StudioTab.SUPPORT) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            modifier = Modifier.testTag("top_support_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = "پشتیبانی هوشمند",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "پشتیبان",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == StudioTab.STUDIO,
                    onClick = { viewModel.selectTab(StudioTab.STUDIO) },
                    icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) },
                    label = { Text("گویندگی", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("nav_studio")
                )
                NavigationBarItem(
                    selected = currentTab == StudioTab.CLONING,
                    onClick = { viewModel.selectTab(StudioTab.CLONING) },
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    label = { Text("کلون صدا", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("nav_cloning")
                )
                NavigationBarItem(
                    selected = currentTab == StudioTab.DUBBING,
                    onClick = { viewModel.selectTab(StudioTab.DUBBING) },
                    icon = { Icon(Icons.Default.Translate, contentDescription = null) },
                    label = { Text("دوبله فارسی", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("nav_dubbing")
                )
                NavigationBarItem(
                    selected = currentTab == StudioTab.MIX_MASTER,
                    onClick = { viewModel.selectTab(StudioTab.MIX_MASTER) },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                    label = { Text("میکس و مستر", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("nav_mix_master")
                )
                NavigationBarItem(
                    selected = currentTab == StudioTab.LIBRARY,
                    onClick = { viewModel.selectTab(StudioTab.LIBRARY) },
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                    label = { Text("آرشیو", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("nav_library")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                StudioTab.STUDIO -> StudioSpeakScreen(
                    controls = studioControls,
                    voiceProfiles = voiceProfiles,
                    latestSpeech = latestSpeech,
                    operationState = operationState,
                    isElevenLabsConfigured = viewModel.isElevenLabsConfigured,
                    playingItemId = playingItemId,
                    isPlaying = isPlaying,
                    playbackProgress = playbackProgress,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    waveformEnvelope = playbackWaveformEnvelope,
                    liveBars = playbackLiveBars,
                    playbackLevel = playbackLevel,
                    playbackDb = playbackDb,
                    onSelectVoice = viewModel::selectVoiceProfile,
                    onUpdateInputText = viewModel::updateInputText,
                    onUpdateAdaptedText = viewModel::updateAdaptedTextPreview,
                    onToggleAutoAdapt = viewModel::setAutoAdaptAccentWithAi,
                    onSelectLanguage = viewModel::selectStudioLanguage,
                    onSelectAccent = viewModel::selectStudioAccent,
                    onSelectEmotion = viewModel::selectEmotion,
                    onPitchChange = viewModel::updatePitch,
                    onSpeedChange = viewModel::updateSpeed,
                    onStabilityChange = viewModel::updateStability,
                    onSimilarityChange = viewModel::updateSimilarityBoost,
                    onPreviewAdaptation = viewModel::previewAccentAdaptationOnly,
                    onSynthesizeSpeech = viewModel::synthesizeClonedPersianSpeech,
                    onTogglePlayLatest = { viewModel.playGeneratedSpeech(it, useLiveStudioParams = true) },
                    onSeekPlayback = viewModel::seekPlayback,
                    onShareSpeech = handleShareSpeech,
                    onSaveSpeech = handleSaveSpeech,
                    onSyncVoices = viewModel::syncCloudVoices,
                    onGoToCloning = { viewModel.selectTab(StudioTab.CLONING) },
                    onGoToSupport = { viewModel.selectTab(StudioTab.SUPPORT) },
                    onDismissBanner = viewModel::clearOperationState
                )

                StudioTab.CLONING -> VoiceCloningScreen(
                    cloningState = cloningState,
                    voiceProfiles = voiceProfiles,
                    audioSamples = audioSamples,
                    isRecording = isRecording,
                    elapsedSeconds = elapsedSeconds,
                    waveBars = waveBars,
                    recordingLevel = recordingLevel,
                    recordingPeakDb = recordingPeakDb,
                    playingItemId = playingItemId,
                    isPlaying = isPlaying,
                    playbackProgress = playbackProgress,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    waveformEnvelope = playbackWaveformEnvelope,
                    liveBars = playbackLiveBars,
                    playbackLevel = playbackLevel,
                    playbackDb = playbackDb,
                    operationState = operationState,
                    isElevenLabsConfigured = viewModel.isElevenLabsConfigured,
                    onStartRecording = viewModel::startVoiceRecording,
                    onStopRecording = viewModel::stopVoiceRecordingAndSave,
                    onImportAudioFiles = viewModel::importAdditionalDatasets,
                    onNextCalibrationPrompt = viewModel::nextCalibrationPrompt,
                    onToggleSampleSelection = viewModel::toggleSampleSelection,
                    onPlaySample = viewModel::playAudioSample,
                    onSeekPlayback = viewModel::seekPlayback,
                    onAnalyzeSample = viewModel::analyzeSampleQuality,
                    onDeleteSample = viewModel::deleteSample,
                    onUpdateVoiceName = viewModel::updateNewVoiceName,
                    onSelectAccent = viewModel::selectCloningAccent,
                    onToggleNoiseRemoval = viewModel::setRemoveBackgroundNoise,
                    onSelectRetrainTarget = viewModel::setRetrainTargetVoice,
                    onSubmitCloneOrRetrain = viewModel::submitCloneOrRetrain,
                    onGoToSupport = { viewModel.selectTab(StudioTab.SUPPORT) },
                    onDismissBanner = viewModel::clearOperationState,
                    onBackToStudio = { viewModel.selectTab(StudioTab.STUDIO) }
                )

                StudioTab.DUBBING -> DubbingScreen(
                    isServiceRunning = dubServiceRunning,
                    isOverlayVisible = dubOverlayVisible,
                    isDubbingActive = dubActive,
                    isProcessingSegment = dubProcessing,
                    originalVolume = dubOriginalVolume,
                    dubVolume = dubVolume,
                    useClonedVoice = dubUseClonedVoice,
                    dubOutputMode = dubOutputMode,
                    currentSubtitleText = dubCurrentSubtitle,
                    latestSrtFilePath = dubLatestSrtFilePath,
                    latestDubbedAudioPath = dubLatestDubbedAudioPath,
                    latestDubbedVideoPath = dubLatestDubbedVideoPath,
                    latestDubOnlyStemPath = dubLatestDubOnlyStemPath,
                    latestSfxOnlyStemPath = dubLatestSfxOnlyStemPath,
                    longFileProgressText = dubLongFileProgressText,
                    mediaWorkloadSummary = mediaWorkloadSummary,
                    isPlayingDubbedFile = isPlaying && playingItemId == "dub_file_output",
                    playingItemId = playingItemId,
                    isPlaying = isPlaying,
                    playbackProgress = playbackProgress,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    waveformEnvelope = playbackWaveformEnvelope,
                    liveBars = playbackLiveBars,
                    playbackLevel = playbackLevel,
                    playbackDb = playbackDb,
                    selectedEngineMethod = dubEngineMethod,
                    selectedAccent = dubAccent,
                    selectedToneStyle = dubToneStyle,
                    selectedCinemaStyle = dubCinemaStyle,
                    dualLanguageSubtitles = dubDualLanguageSubtitles,
                    exportMp4Video = exportMp4Video,
                    clipStartSecText = clipStartSecText,
                    clipEndSecText = clipEndSecText,
                    dubExpressiveness = dubExpressiveness,
                    dubPitch = dubPitch,
                    dubSpeed = dubSpeed,
                    activeEngineLabel = dubActiveEngineLabel,
                    voiceProfiles = voiceProfiles,
                    selectedClonedProfileId = studioControls.selectedVoiceProfileId,
                    statusText = dubStatusText,
                    segments = dubSegments,
                    operationState = operationState,
                    onRequestMediaProjectionCapture = handleRequestMediaProjection,
                    onShowFloatingOverlay = handleShowFloatingOverlay,
                    onRequestOverlayPermission = handleRequestOverlayPermission,
                    onToggleDubbingActive = handleToggleDubbingActive,
                    onStopDubbingService = handleStopDubbingService,
                    onOriginalVolumeChange = viewModel::updateOriginalVolume,
                    onDubVolumeChange = viewModel::updateDubVolume,
                    onToggleUseClonedVoice = viewModel::toggleUseClonedVoiceForDubbing,
                    onSelectDubOutputMode = viewModel::selectDubOutputMode,
                    onSelectEngineMethod = viewModel::selectDubEngineMethod,
                    onSelectAccent = viewModel::selectDubAccent,
                    onSelectToneStyle = viewModel::selectDubToneStyle,
                    onSelectCinemaStyle = viewModel::selectCinemaDubbingStyle,
                    onToggleDualLanguageSubtitles = viewModel::toggleDualLanguageSubtitles,
                    onToggleExportMp4Video = viewModel::toggleExportMp4Video,
                    onClipRangeChange = viewModel::updateClipRange,
                    onExpressivenessChange = viewModel::updateDubExpressiveness,
                    onDubPitchChange = viewModel::updateDubPitch,
                    onDubSpeedChange = viewModel::updateDubSpeed,
                    onEstimateWorkloadUri = viewModel::estimateMediaWorkloadUri,
                    onDubMediaFileUri = viewModel::dubMediaFileUri,
                    onSaveSrtFile = { srtPath ->
                        pendingExportFilePath = srtPath
                        pendingExportSuccessMsg = "فایل زیرنویس فارسی (.srt) با موفقیت ذخیره شد!"
                        saveGenericFileLauncher.launch("parham_subtitles_${System.currentTimeMillis()}.srt")
                    },
                    onShareSrtFile = { srtPath ->
                        viewModel.createShareFileIntent(srtPath, "application/x-subrip")?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فایل زیرنویس فارسی"))
                        }
                    },
                    onTogglePlayDubbedFile = viewModel::togglePlayDubbedAudioFile,
                    onSeekPlayback = viewModel::seekPlayback,
                    onSaveDubbedAudioFile = { wavPath ->
                        pendingExportFilePath = wavPath
                        pendingExportSuccessMsg = "فایل صوتی دوبله فارسی با موفقیت ذخیره شد!"
                        saveGenericFileLauncher.launch("parham_dubbed_${System.currentTimeMillis()}.wav")
                    },
                    onSaveDubbedVideoFile = { mp4Path ->
                        pendingExportFilePath = mp4Path
                        pendingExportSuccessMsg = "ویدئوی دوبله‌شده MP4 با موفقیت ذخیره شد!"
                        saveGenericFileLauncher.launch("parham_dubbed_movie_${System.currentTimeMillis()}.mp4")
                    },
                    onShareDubbedVideoFile = { mp4Path ->
                        viewModel.createShareFileIntent(mp4Path, "video/mp4")?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری ویدئوی دوبله فارسی"))
                        }
                    },
                    onSaveStemFile = { stemPath, stemKind ->
                        pendingExportFilePath = stemPath
                        pendingExportSuccessMsg = "فایل استم صوتی ($stemKind) با موفقیت ذخیره شد!"
                        saveGenericFileLauncher.launch("parham_${stemKind}_${System.currentTimeMillis()}.wav")
                    },
                    onClearSegments = viewModel::clearDubbingSegments,
                    onDismissBanner = viewModel::clearOperationState,
                    onBackToStudio = { viewModel.selectTab(StudioTab.STUDIO) }
                )

                StudioTab.MIX_MASTER -> {
                    val effectiveMixState = mixState.copy(
                        isLaunchpadLoopPlaying = isPlaying && playingItemId == "launchpad_preview",
                        previewingGenreStyle = if (isPlaying && playingItemId?.startsWith("genre_preview_") == true) {
                            com.example.audio.BackingBeatStyle.entries.firstOrNull {
                                playingItemId == "genre_preview_${it.id}"
                            }
                        } else {
                            null
                        }
                    )
                    MixMasterScreen(
                        mixState = effectiveMixState,
                        selectedLanguage = studioControls.selectedLanguage,
                        audioSamples = audioSamples,
                        operationState = operationState,
                        playingItemId = playingItemId,
                        isPlaying = isPlaying,
                        playbackProgress = playbackProgress,
                        currentPosMs = currentPosMs,
                        durationMs = durationMs,
                        waveformEnvelope = playbackWaveformEnvelope,
                        liveBars = playbackLiveBars,
                        playbackLevel = playbackLevel,
                        playbackDb = playbackDb,
                        onSelectLanguage = viewModel::selectStudioLanguage,
                        onUpdateLyricsTopic = viewModel::updateLyricsTopic,
                        onUpdateLyrics = viewModel::updateSingingLyrics,
                        onGenerateAiLyrics = viewModel::generateAiSingingLyrics,
                        onToggleVocalSource = viewModel::setUseRecordedSampleForMix,
                        onSelectVocalSample = viewModel::selectVocalSampleForMix,
                        onUpdateMixConfig = viewModel::updateMixConfig,
                        onImportCustomBeat = viewModel::importCustomBeat,
                        onTapLaunchpadPad = viewModel::tapLaunchpadPad,
                        onToggleSequencerStep = viewModel::toggleSequencerStep,
                        onLaunchpadBpmChange = viewModel::updateLaunchpadBpm,
                        onToggleSequencerLoop = viewModel::toggleSequencerLoop,
                        onUseLaunchpadBeatInMix = viewModel::useLaunchpadBeatInMix,
                        onTogglePreviewGenreBeat = viewModel::togglePreviewGenreBeat,
                        onAutoMatchBeatToVocal = viewModel::autoMatchBeatToVocal,
                        onSynthesizeAndMaster = viewModel::synthesizeSingingAndMaster,
                        onRemasterExisting = viewModel::remasterExistingVocalTake,
                        onTogglePlayMastered = { viewModel.playGeneratedSpeech(it, useLiveStudioParams = false) },
                        onSeekPlayback = viewModel::seekPlayback,
                        onShareSpeech = handleShareSpeech,
                        onSaveSpeech = handleSaveSpeech,
                        onDismissBanner = viewModel::clearOperationState,
                        onBackToStudio = { viewModel.selectTab(StudioTab.STUDIO) }
                    )
                }

                StudioTab.LIBRARY -> VoiceLibraryScreen(
                    voiceProfiles = voiceProfiles,
                    generatedSpeeches = generatedSpeeches,
                    operationState = operationState,
                    playingItemId = playingItemId,
                    isPlaying = isPlaying,
                    playbackProgress = playbackProgress,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    waveformEnvelope = playbackWaveformEnvelope,
                    liveBars = playbackLiveBars,
                    playbackLevel = playbackLevel,
                    playbackDb = playbackDb,
                    onSyncVoices = viewModel::syncCloudVoices,
                    onUseVoiceInStudio = { profile ->
                        viewModel.selectVoiceProfile(profile)
                        viewModel.selectTab(StudioTab.STUDIO)
                    },
                    onRetrainVoice = { profile ->
                        viewModel.setRetrainTargetVoice(profile.id)
                        viewModel.selectTab(StudioTab.CLONING)
                    },
                    onDeleteVoiceProfile = viewModel::deleteVoiceProfile,
                    onTogglePlaySpeech = { viewModel.playGeneratedSpeech(it, useLiveStudioParams = false) },
                    onSeekPlayback = viewModel::seekPlayback,
                    onShareSpeech = handleShareSpeech,
                    onSaveSpeech = handleSaveSpeech,
                    onDeleteSpeech = viewModel::deleteGeneratedSpeech,
                    onDismissBanner = viewModel::clearOperationState,
                    onBackToStudio = { viewModel.selectTab(StudioTab.STUDIO) }
                )

                StudioTab.SUPPORT -> SupportChatScreen(
                    messages = chatMessages,
                    streamingBotText = streamingBotText,
                    isBotResponding = isBotResponding,
                    chatInput = chatInput,
                    chatError = chatError,
                    voiceCount = voiceProfiles.size,
                    sampleCount = audioSamples.size,
                    activeAccentLabel = studioControls.selectedAccent.titleFa,
                    isElevenLabsConfigured = viewModel.isElevenLabsConfigured,
                    onUpdateChatInput = viewModel::updateChatInput,
                    onSendMessage = viewModel::sendSupportMessage,
                    onResetChat = viewModel::resetSupportChat,
                    onBackToStudio = { viewModel.selectTab(StudioTab.STUDIO) }
                )

                StudioTab.PODCAST -> PodcastStudioScreen(
                    state = podcastState,
                    operationState = operationState,
                    playingItemId = playingItemId,
                    isPlaying = isPlaying,
                    playbackProgress = playbackProgress,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    waveformEnvelope = playbackWaveformEnvelope,
                    liveBars = playbackLiveBars,
                    playbackLevel = playbackLevel,
                    playbackDb = playbackDb,
                    onSelectMode = viewModel::selectPodcastMode,
                    onSelectAccent = viewModel::selectPodcastAccent,
                    onUpdateTopicText = viewModel::updatePodcastTopicText,
                    onToggleAmbientMusic = viewModel::togglePodcastAmbientMusic,
                    onToggleClonedHost = viewModel::togglePodcastClonedHost,
                    onUpdateTurnText = viewModel::updatePodcastTurnText,
                    onCycleTurnCharacter = viewModel::cyclePodcastTurnCharacter,
                    onGenerateScript = viewModel::generatePodcastScript,
                    onRenderEpisodeAudio = viewModel::renderPodcastEpisodeAudio,
                    onTogglePlayEpisode = viewModel::togglePlayPodcastEpisode,
                    onSeekPlayback = viewModel::seekPlayback,
                    onSaveEpisodeWav = { wavPath ->
                        pendingExportFilePath = wavPath
                        pendingExportSuccessMsg = "اپیزود صوتی با موفقیت ذخیره شد!"
                        saveGenericFileLauncher.launch("parham_podcast_${System.currentTimeMillis()}.wav")
                    },
                    onShareEpisodeWav = { wavPath ->
                        viewModel.createShareFileIntent(wavPath, "audio/wav")?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری اپیزود پادکست"))
                        }
                    },
                    onDismissBanner = viewModel::clearOperationState,
                    onBackToStudio = { viewModel.selectTab(StudioTab.STUDIO) }
                )

                StudioTab.PROXY -> ProxySettingsScreen(
                    currentSettings = proxySettings,
                    isTestingConnection = isTestingProxy,
                    isAutoDiscovering = isAutoDiscoveringProxy,
                    testResult = proxyTestResult,
                    autoDiscoveryReport = autoProxyDiscoveryReport,
                    onSaveSettings = viewModel::saveProxySettings,
                    onTestConnection = viewModel::testProxyConnection,
                    onAutoDiscoverBestProxy = viewModel::autoDiscoverBestProxy,
                    onApplyDiscoveredCandidate = viewModel::applyDiscoveredProxyCandidate,
                    onBackToStudio = { viewModel.selectTab(StudioTab.STUDIO) }
                )

                StudioTab.API_KEYS -> ApiKeysSettingsScreen(
                    currentConfig = userApiKeys,
                    selectedDubbingEngine = dubEngineMethod,
                    keystoreSecurityStatus = viewModel.keystoreSecurityStatus,
                    apiTestResults = apiTestResults,
                    testingApiProvider = testingApiProvider,
                    isTestingAllApis = isTestingAllApis,
                    operationState = operationState,
                    onSelectDubbingEngine = viewModel::selectDubEngineMethod,
                    onTestSingleApi = viewModel::testSingleApi,
                    onTestAllApis = viewModel::testAllConfiguredApis,
                    onSaveKeys = viewModel::saveUserApiKeys,
                    onClearKeys = viewModel::clearUserApiKeys,
                    onDismissBanner = viewModel::clearOperationState,
                    onBackToStudio = { viewModel.selectTab(StudioTab.STUDIO) }
                )

                StudioTab.STUDIO_3D -> Studio3DScreen(
                    pitch = studioControls.pitch,
                    speed = studioControls.speed,
                    isAudioActive = isPlaying || isRecording || dubActive,
                    audioEnergy = if (isRecording && waveBars.isNotEmpty()) {
                        waveBars.last()
                    } else if (isPlaying || dubActive) {
                        0.72f
                    } else {
                        0.15f
                    },
                    onBackToStudio = { viewModel.selectTab(StudioTab.STUDIO) }
                )
            }
        }
    }
}
