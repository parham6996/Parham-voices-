package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceProfileDao {
    @Query("SELECT * FROM voice_profiles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<VoiceProfileEntity>>

    @Query("SELECT * FROM voice_profiles WHERE elevenLabsVoiceId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): VoiceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: VoiceProfileEntity): Long

    @Update
    suspend fun update(profile: VoiceProfileEntity)

    @Query("DELETE FROM voice_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AudioSampleDao {
    @Query("SELECT * FROM audio_samples ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AudioSampleEntity>>

    @Query("SELECT * FROM audio_samples WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<AudioSampleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: AudioSampleEntity): Long

    @Update
    suspend fun update(sample: AudioSampleEntity)

    @Query("UPDATE audio_samples SET voiceProfileId = :profileId WHERE id IN (:sampleIds)")
    suspend fun assignToProfile(sampleIds: List<Long>, profileId: Long)

    @Query("DELETE FROM audio_samples WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface GeneratedSpeechDao {
    @Query("SELECT * FROM generated_speeches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GeneratedSpeechEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(speech: GeneratedSpeechEntity): Long

    @Query("DELETE FROM generated_speeches WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllSync(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}
