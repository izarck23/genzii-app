package com.example.data.chat

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [ChatSessionEntity::class, ChatMessageEntity::class],
  version = 1,
  exportSchema = false
)
abstract class GenziiRoomDatabase : RoomDatabase() {
  abstract fun aiChatDao(): AiChatDao

  companion object {
    @Volatile
    private var INSTANCE: GenziiRoomDatabase? = null

    fun getInstance(context: Context): GenziiRoomDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          GenziiRoomDatabase::class.java,
          "genzii_ai_database"
        )
        .fallbackToDestructiveMigration()
        .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
