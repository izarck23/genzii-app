package com.example.data.chat

import android.content.Context
import android.util.Log
import com.example.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AiChatSessionRepository(private val dao: AiChatDao) {

  private val scope = CoroutineScope(Dispatchers.IO)

  private val _sessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
  val sessions: StateFlow<List<ChatSessionEntity>> = _sessions.asStateFlow()

  private val _activeSessionId = MutableStateFlow<String>("")
  val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

  private val _activeMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
  val activeMessages: StateFlow<List<ChatMessage>> = _activeMessages.asStateFlow()

  init {
    scope.launch {
      dao.getAllSessions().collectLatest { sessionList ->
        _sessions.value = sessionList
        if (sessionList.isEmpty()) {
          createNewSession("Welcome to Genzii AI")
        } else if (_activeSessionId.value.isBlank() || sessionList.none { it.id == _activeSessionId.value }) {
          switchSession(sessionList.first().id)
        }
      }
    }
  }

  suspend fun createNewSession(initialTitle: String = "New Academic Chat"): String = withContext(Dispatchers.IO) {
    val newId = "session-${UUID.randomUUID().toString().take(8)}"
    val session = ChatSessionEntity(
      id = newId,
      title = initialTitle,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis(),
      messageCount = 0,
      lastSnippet = ""
    )
    dao.insertOrUpdateSession(session)
    _activeSessionId.value = newId

    // Insert friendly initial greeting
    val welcomeMsg = ChatMessageEntity(
      id = "msg-${UUID.randomUUID().toString().take(8)}",
      sessionId = newId,
      isUser = false,
      text = "Hello! I'm Genzii, your smart academic AI assistant. I can help you draft essays, summarize PDFs, refine grammar, or check content for originality. What would you like to work on today?",
      timestamp = "Just now",
      createdAt = System.currentTimeMillis()
    )
    dao.insertMessage(welcomeMsg)
    observeSessionMessages(newId)
    newId
  }

  fun switchSession(sessionId: String) {
    if (_activeSessionId.value == sessionId) return
    _activeSessionId.value = sessionId
    observeSessionMessages(sessionId)
  }

  private var observeJob: kotlinx.coroutines.Job? = null

  private fun observeSessionMessages(sessionId: String) {
    observeJob?.cancel()
    observeJob = scope.launch {
      dao.getMessagesForSession(sessionId).collectLatest { entities ->
        _activeMessages.value = entities.map { entity ->
          ChatMessage(
            id = entity.id,
            isUser = entity.isUser,
            text = entity.text,
            timestamp = entity.timestamp,
            attachedFileName = entity.attachedFileName,
            isError = entity.isError,
            suggestionChips = if (!entity.isUser && entities.indexOf(entity) == 0) {
              listOf("Summarize document", "Identify research gaps", "Refine academic tone", "Draft abstract")
            } else emptyList()
          )
        }
      }
    }
  }

  suspend fun recordUserMessage(
    text: String,
    attachedFileName: String?
  ): String = withContext(Dispatchers.IO) {
    var currSessionId = _activeSessionId.value
    if (currSessionId.isBlank()) {
      currSessionId = createNewSession(deriveTitle(text))
    }

    val msgId = "msg-${UUID.randomUUID().toString().take(8)}"
    val entity = ChatMessageEntity(
      id = msgId,
      sessionId = currSessionId,
      isUser = true,
      text = text,
      timestamp = "Just now",
      createdAt = System.currentTimeMillis(),
      attachedFileName = attachedFileName
    )
    dao.insertMessage(entity)

    // Update session title if default
    val session = dao.getSessionById(currSessionId)
    if (session != null && (session.title == "New Academic Chat" || session.title.startsWith("Welcome"))) {
      val smartTitle = deriveTitle(text)
      dao.renameSession(currSessionId, smartTitle)
    }
    dao.updateSessionStats(currSessionId, text.take(60))

    msgId
  }

  suspend fun recordAiMessage(
    text: String,
    isError: Boolean = false
  ): String = withContext(Dispatchers.IO) {
    val currSessionId = _activeSessionId.value
    val msgId = "msg-${UUID.randomUUID().toString().take(8)}"
    val entity = ChatMessageEntity(
      id = msgId,
      sessionId = currSessionId,
      isUser = false,
      text = text,
      timestamp = "Just now",
      createdAt = System.currentTimeMillis(),
      isError = isError
    )
    dao.insertMessage(entity)
    dao.updateSessionStats(currSessionId, text.take(60))
    msgId
  }

  suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
    dao.deleteMessagesForSession(sessionId)
    dao.deleteSession(sessionId)
  }

  suspend fun renameSession(sessionId: String, newTitle: String) = withContext(Dispatchers.IO) {
    dao.renameSession(sessionId, newTitle)
  }

  suspend fun clearActiveSessionMessages() = withContext(Dispatchers.IO) {
    val curr = _activeSessionId.value
    if (curr.isNotBlank()) {
      dao.deleteMessagesForSession(curr)
      // re-insert greeting
      val welcomeMsg = ChatMessageEntity(
        id = "msg-${UUID.randomUUID().toString().take(8)}",
        sessionId = curr,
        isUser = false,
        text = "Conversation cleared. How can I help you today?",
        timestamp = "Just now",
        createdAt = System.currentTimeMillis()
      )
      dao.insertMessage(welcomeMsg)
    }
  }

  private fun deriveTitle(firstPrompt: String): String {
    val cleaned = firstPrompt.trim().replace("\n", " ")
    return if (cleaned.length > 32) {
      cleaned.take(30) + "..."
    } else if (cleaned.isNotBlank()) {
      cleaned
    } else {
      "Academic Research Chat"
    }
  }

  companion object {
    private const val TAG = "AiChatSessionRepo"
    @Volatile
    private var INSTANCE: AiChatSessionRepository? = null

    fun getInstance(context: Context): AiChatSessionRepository {
      return INSTANCE ?: synchronized(this) {
        val db = GenziiRoomDatabase.getInstance(context)
        val instance = AiChatSessionRepository(db.aiChatDao())
        INSTANCE = instance
        instance
      }
    }
  }
}
