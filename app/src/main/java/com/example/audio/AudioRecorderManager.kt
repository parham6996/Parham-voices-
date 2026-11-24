package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

data class RecordedAudioResult(
    val file: File,
    val durationSeconds: Int
)

class AudioRecorderManager(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_WAVE_BARS = 48
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _amplitudeHistory = MutableStateFlow<List<Float>>(List(MAX_WAVE_BARS) { 0.08f })
    val amplitudeHistory: StateFlow<List<Float>> = _amplitudeHistory.asStateFlow()

    private val _currentLevel = MutableStateFlow(0f)
    val currentLevel: StateFlow<Float> = _currentLevel.asStateFlow()

    private val _peakDb = MutableStateFlow(-60f)
    val peakDb: StateFlow<Float> = _peakDb.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private var currentPcmFile: File? = null
    private var startTimeMs: Long = 0L

    private fun samplesDir(): File {
        return File(context.filesDir, "voice_samples").apply { mkdirs() }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(scope: CoroutineScope, onError: (String) -> Unit) {
        if (_isRecording.value) return

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = (minBufferSize.coerceAtLeast(4096)) * 2

        try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                onError("میکروفون دستگاه در دسترس نیست یا مقداردهی نشد.")
                return
            }

            val pcmFile = File(samplesDir(), "temp_${System.currentTimeMillis()}.pcm")
            currentPcmFile = pcmFile
            audioRecord = recorder
            _elapsedSeconds.value = 0
            _amplitudeHistory.value = List(MAX_WAVE_BARS) { 0.08f }
            _currentLevel.value = 0f
            _peakDb.value = -60f
            startTimeMs = System.currentTimeMillis()
            _isRecording.value = true

            recorder.startRecording()

            timerJob = scope.launch(Dispatchers.Main) {
                while (isActive && _isRecording.value) {
                    _elapsedSeconds.value = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt()
                    delay(250L)
                }
            }

            recordingJob = scope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(bufferSize / 2)
                FileOutputStream(pcmFile).use { fos ->
                    while (isActive && _isRecording.value) {
                        val readCount = recorder.read(shortBuffer, 0, shortBuffer.size)
                        if (readCount > 0) {
                            val byteBuffer = ByteBuffer.allocate(readCount * 2).order(ByteOrder.LITTLE_ENDIAN)
                            var sumSq = 0.0
                            for (i in 0 until readCount) {
                                val s = shortBuffer[i]
                                byteBuffer.putShort(s)
                                sumSq += s.toDouble() * s.toDouble()
                            }
                            fos.write(byteBuffer.array())

                            val rms = sqrt(sumSq / readCount) / 32768.0
                            val db = if (rms > 1e-5) (20.0 * ln(rms) / ln(10.0)).toFloat().coerceIn(-60f, 0f) else -60f
                            val linearBoosted = (rms * 4.2).coerceIn(0.0, 1.0)
                            val perceptual = sqrt(linearBoosted).toFloat().coerceIn(0.06f, 1.0f)

                            val prevLevel = _currentLevel.value
                            val smoothedLevel = if (perceptual > prevLevel) {
                                perceptual
                            } else {
                                max(perceptual, prevLevel * 0.78f)
                            }
                            _currentLevel.value = smoothedLevel
                            _peakDb.value = db
                            val updated = (_amplitudeHistory.value.drop(1) + perceptual)
                            _amplitudeHistory.value = updated
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            _isRecording.value = false
            onError("دسترسی به میکروفون داده نشده است.")
        } catch (e: Exception) {
            _isRecording.value = false
            onError(e.localizedMessage ?: "خطا در شروع ضبط صدا.")
        }
    }

    suspend fun stopRecording(): RecordedAudioResult? = withContext(Dispatchers.IO) {
        if (!_isRecording.value) return@withContext null
        _isRecording.value = false
        _currentLevel.value = 0f
        _peakDb.value = -60f
        timerJob?.cancel()
        recordingJob?.join()

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        audioRecord?.release()
        audioRecord = null

        val pcmFile = currentPcmFile ?: return@withContext null
        currentPcmFile = null

        val durationSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).toInt().coerceAtLeast(1)
        if (!pcmFile.exists() || pcmFile.length() == 0L) {
            pcmFile.delete()
            return@withContext null
        }

        val wavFile = File(samplesDir(), "voice_sample_${System.currentTimeMillis()}.wav")
        convertPcmToWav(pcmFile, wavFile)
        pcmFile.delete()
        RecordedAudioResult(file = wavFile, durationSeconds = durationSec)
    }

    suspend fun importAudioDataset(uri: Uri): Pair<File, Int>? = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "imported_${System.currentTimeMillis()}.mp3"
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(samplesDir(), "${System.currentTimeMillis()}_$safeName")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: return@withContext null

        val durationSec = extractDurationSeconds(destFile)
        destFile to durationSec
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun extractDurationSeconds(file: File): Int {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            (ms / 1000L).toInt().coerceAtLeast(1)
        } catch (_: Exception) {
            1
        }
    }

    private fun convertPcmToWav(pcmFile: File, wavFile: File) {
        val pcmBytes = pcmFile.length()
        val channels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * channels * bitsPerSample / 8
        val totalDataLen = pcmBytes + 36

        FileOutputStream(wavFile).use { out ->
            val header = ByteArrayOutputStream(44)
            header.write("RIFF".toByteArray())
            header.write(intToLittleEndian(totalDataLen.toInt()))
            header.write("WAVE".toByteArray())
            header.write("fmt ".toByteArray())
            header.write(intToLittleEndian(16)) // Subchunk1Size for PCM
            header.write(shortToLittleEndian(1)) // AudioFormat = 1 (PCM)
            header.write(shortToLittleEndian(channels.toShort()))
            header.write(intToLittleEndian(SAMPLE_RATE))
            header.write(intToLittleEndian(byteRate))
            header.write(shortToLittleEndian((channels * bitsPerSample / 8).toShort()))
            header.write(shortToLittleEndian(bitsPerSample.toShort()))
            header.write("data".toByteArray())
            header.write(intToLittleEndian(pcmBytes.toInt()))
            out.write(header.toByteArray())

            pcmFile.inputStream().use { input ->
                input.copyTo(out)
            }
        }
    }

    private fun intToLittleEndian(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun shortToLittleEndian(value: Short): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()

    fun release() {
        _isRecording.value = false
        _currentLevel.value = 0f
        _peakDb.value = -60f
        timerJob?.cancel()
        recordingJob?.cancel()
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }
}
