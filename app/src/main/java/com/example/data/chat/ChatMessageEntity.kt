package com.example.data.chat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "chat_messages",
  foreignKeys = [
    ForeignKey(
      entity = ChatSessionEntity::class,
      parentColumns = ["id"],
      childColumns = ["sessionId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index(value = ["sessionId"])]
)
data class ChatMessageEntity(
  @PrimaryKey val id: String,
  val sessionId: String,
  val isUser: Boolean,
  val text: String,
  val timestamp: String,
  val createdAt: Long = System.currentTimeMillis(),
  val attachedFileName: String? = null,
  val isError: Boolean = false
)
