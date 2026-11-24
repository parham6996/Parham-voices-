package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VoiceProfileEntity::class,
        AudioSampleEntity::class,
        GeneratedSpeechEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voiceProfileDao(): VoiceProfileDao
    abstract fun audioSampleDao(): AudioSampleDao
    abstract fun generatedSpeechDao(): GeneratedSpeechDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "avaclone_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
