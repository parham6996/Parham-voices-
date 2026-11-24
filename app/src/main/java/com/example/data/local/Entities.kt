package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val elevenLabsVoiceId: String,
    val name: String,
    val description: String,
    val accentId: String,
    val sampleCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audio_samples")
data class AudioSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voiceProfileId: Long? = null,
    val title: String,
    val filePath: String,
    val mimeType: String,
    val durationSeconds: Int,
    val sourceType: String, // "RECORDED" or "IMPORTED"
    val qualityScore: Int? = null,
    val clarityNote: String? = null,
    val detectedAccent: String? = null,
    val naturalPitchLabel: String? = null,
    val improvementTip: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "generated_speeches")
data class GeneratedSpeechEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voiceProfileId: Long,
    val voiceName: String,
    val originalText: String,
    val adaptedText: String,
    val accentId: String,
    val emotionId: String,
    val pitch: Float,
    val speed: Float,
    val stability: Float,
    val similarityBoost: Float,
    val audioFilePath: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
