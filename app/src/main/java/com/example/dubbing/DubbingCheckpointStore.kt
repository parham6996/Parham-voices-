package com.example.dubbing

import android.content.Context
import com.example.domain.DubbingTranslationResult
import com.example.domain.VoiceCharacterArchetype
import com.example.domain.VoiceEmotion
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class SavedDubbingCheckpointChunk(
    val startMs: Long,
    val endMs: Long,
    val result: DubbingTranslationResult,
    val dubbedPcmFilePath: String?
)

data class MediaDubbingCheckpoint(
    val mediaKey: String,
    val lastCompletedMs: Long,
    val chunks: List<SavedDubbingCheckpointChunk>
)

/**
 * Persists incremental movie dubbing progress & synthesized voice chunks to disk so long 1-2 hour movies
 * resume automatically from the exact interrupted minute if network drops or user pauses.
 */
object DubbingCheckpointStore {

    fun computeMediaKey(uriString: String, totalDurationMs: Long): String {
        val raw = "$uriString|$totalDurationMs"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun checkpointFile(context: Context, mediaKey: String): File {
        val dir = File(context.filesDir, "dub_checkpoints/$mediaKey").apply { mkdirs() }
        return File(dir, "checkpoint.json")
    }

    fun chunkPcmDir(context: Context, mediaKey: String): File =
        File(context.filesDir, "dub_checkpoints/$mediaKey/chunks").apply { mkdirs() }

    fun loadCheckpoint(context: Context, mediaKey: String): MediaDubbingCheckpoint? {
        val file = checkpointFile(context, mediaKey)
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val lastMs = root.optLong("lastCompletedMs", 0L)
            val arr = root.optJSONArray("chunks") ?: JSONArray()
            val list = ArrayList<SavedDubbingCheckpointChunk>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val res = DubbingTranslationResult(
                    hasSpeech = true,
                    detectedLanguage = obj.optString("detectedLanguage", "نامشخص"),
                    originalTranscript = obj.optString("originalTranscript", ""),
                    persianTranslation = obj.optString("persianTranslation", ""),
                    detectedEmotion = VoiceEmotion.fromId(obj.optString("detectedEmotion", "neutral")),
                    speakerCharacter = VoiceCharacterArchetype.fromId(obj.optString("speakerCharacter", "adult_male")),
                    speakerSlotLabel = obj.optString("speakerSlotLabel", ""),
                    expressiveActingText = obj.optString("expressiveActingText", ""),
                    recommendedPitch = obj.optDouble("recommendedPitch", 1.0).toFloat(),
                    recommendedSpeed = obj.optDouble("recommendedSpeed", 1.0).toFloat(),
                    engineUsedName = obj.optString("engineUsedName", "Checkpoint"),
                    failoverOccurred = obj.optBoolean("failoverOccurred", false)
                )
                list += SavedDubbingCheckpointChunk(
                    startMs = obj.optLong("startMs", 0L),
                    endMs = obj.optLong("endMs", 0L),
                    result = res,
                    dubbedPcmFilePath = obj.optString("dubbedPcmFilePath", "").takeIf { it.isNotBlank() }
                )
            }
            MediaDubbingCheckpoint(mediaKey, lastMs, list)
        } catch (_: Exception) {
            null
        }
    }

    fun saveCheckpoint(context: Context, checkpoint: MediaDubbingCheckpoint) {
        try {
            val file = checkpointFile(context, checkpoint.mediaKey)
            val arr = JSONArray()
            checkpoint.chunks.forEach { chunk ->
                arr.put(JSONObject().apply {
                    put("startMs", chunk.startMs)
                    put("endMs", chunk.endMs)
                    put("detectedLanguage", chunk.result.detectedLanguage)
                    put("originalTranscript", chunk.result.originalTranscript)
                    put("persianTranslation", chunk.result.persianTranslation)
                    put("detectedEmotion", chunk.result.detectedEmotion.id)
                    put("speakerCharacter", chunk.result.speakerCharacter.id)
                    put("speakerSlotLabel", chunk.result.speakerSlotLabel)
                    put("expressiveActingText", chunk.result.expressiveActingText)
                    put("recommendedPitch", chunk.result.recommendedPitch.toDouble())
                    put("recommendedSpeed", chunk.result.recommendedSpeed.toDouble())
                    put("engineUsedName", chunk.result.engineUsedName)
                    put("failoverOccurred", chunk.result.failoverOccurred)
                    put("dubbedPcmFilePath", chunk.dubbedPcmFilePath ?: "")
                })
            }
            val root = JSONObject().apply {
                put("lastCompletedMs", checkpoint.lastCompletedMs)
                put("chunks", arr)
            }
            file.writeText(root.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    fun clearCheckpoint(context: Context, mediaKey: String) {
        try {
            File(context.filesDir, "dub_checkpoints/$mediaKey").deleteRecursively()
        } catch (_: Exception) {
        }
    }
}
