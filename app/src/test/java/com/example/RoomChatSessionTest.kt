package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.chat.AiChatDao
import com.example.data.chat.ChatMessageEntity
import com.example.data.chat.ChatSessionEntity
import com.example.data.chat.GenziiRoomDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomChatSessionTest {

  private lateinit var database: GenziiRoomDatabase
  private lateinit var chatDao: AiChatDao

  @Before
  fun createDb() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, GenziiRoomDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    chatDao = database.aiChatDao()
  }

  @After
  @Throws(IOException::class)
  fun closeDb() {
    database.close()
  }

  @Test
  fun testInsertAndRetrieveChatSession() = runBlocking {
    val session = ChatSessionEntity(
      id = "test-session-1",
      title = "Literature Review Thread",
      createdAt = 1000L,
      updatedAt = 1000L,
      messageCount = 0,
      lastSnippet = ""
    )
    chatDao.insertOrUpdateSession(session)

    val retrieved = chatDao.getSessionById("test-session-1")
    assertNotNull(retrieved)
    assertEquals("Literature Review Thread", retrieved?.title)

    val allSessions = chatDao.getAllSessions().first()
    assertEquals(1, allSessions.size)
    assertEquals("test-session-1", allSessions[0].id)
  }

  @Test
  fun testInsertMessagesAndCascade() = runBlocking {
    val session = ChatSessionEntity(
      id = "test-session-2",
      title = "Thesis AI Discussion",
      createdAt = 2000L,
      updatedAt = 2000L,
      messageCount = 0,
      lastSnippet = ""
    )
    chatDao.insertOrUpdateSession(session)

    val msg1 = ChatMessageEntity(
      id = "msg-1",
      sessionId = "test-session-2",
      isUser = true,
      text = "Can you summarize quantum computing trends?",
      timestamp = "10:00 AM",
      createdAt = 2001L
    )
    val msg2 = ChatMessageEntity(
      id = "msg-2",
      sessionId = "test-session-2",
      isUser = false,
      text = "Quantum computing has seen key breakthroughs in qubit coherence.",
      timestamp = "10:01 AM",
      createdAt = 2002L
    )

    chatDao.insertMessage(msg1)
    chatDao.insertMessage(msg2)

    val messages = chatDao.getMessagesSnapshot("test-session-2")
    assertEquals(2, messages.size)
    assertEquals("msg-1", messages[0].id)
    assertEquals(true, messages[0].isUser)
    assertEquals("msg-2", messages[1].id)
    assertEquals(false, messages[1].isUser)

    // Test session stats update
    chatDao.updateSessionStats("test-session-2", "Quantum computing...", 2005L)
    val updatedSession = chatDao.getSessionById("test-session-2")
    assertEquals(1, updatedSession?.messageCount)
    assertEquals("Quantum computing...", updatedSession?.lastSnippet)

    // Test rename
    chatDao.renameSession("test-session-2", "Quantum Breakthroughs Thread", 2010L)
    val renamedSession = chatDao.getSessionById("test-session-2")
    assertEquals("Quantum Breakthroughs Thread", renamedSession?.title)

    // Test delete messages
    chatDao.deleteMessagesForSession("test-session-2")
    val emptyMessages = chatDao.getMessagesSnapshot("test-session-2")
    assertTrue(emptyMessages.isEmpty())

    // Test delete session
    chatDao.deleteSession("test-session-2")
    val deletedSession = chatDao.getSessionById("test-session-2")
    assertNull(deletedSession)
  }
}
