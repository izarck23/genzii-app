package com.example.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {
  @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
  fun getAllSessions(): Flow<List<ChatSessionEntity>>

  @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
  suspend fun getSessionById(sessionId: String): ChatSessionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdateSession(session: ChatSessionEntity)

  @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
  suspend fun deleteSession(sessionId: String)

  @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
  fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

  @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
  suspend fun getMessagesSnapshot(sessionId: String): List<ChatMessageEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMessage(message: ChatMessageEntity)

  @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
  suspend fun deleteMessagesForSession(sessionId: String)

  @Query("UPDATE chat_sessions SET title = :newTitle, updatedAt = :updatedAt WHERE id = :sessionId")
  suspend fun renameSession(sessionId: String, newTitle: String, updatedAt: Long = System.currentTimeMillis())

  @Query("UPDATE chat_sessions SET updatedAt = :updatedAt, lastSnippet = :lastSnippet, messageCount = messageCount + 1 WHERE id = :sessionId")
  suspend fun updateSessionStats(sessionId: String, lastSnippet: String, updatedAt: Long = System.currentTimeMillis())
}
