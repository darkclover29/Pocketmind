package com.pocketmind.data.repository

import com.pocketmind.data.db.dao.ChatDao
import com.pocketmind.data.db.entity.ChatMessageEntity
import com.pocketmind.data.db.entity.ChatSessionEntity
import com.pocketmind.data.db.entity.MessageRole
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatHistoryRepository @Inject constructor(
    private val dao: ChatDao
) {
    // ── Sessions ──────────────────────────────────────────────────────────────

    fun getAllSessions(): Flow<List<ChatSessionEntity>> = dao.getAllSessions()

    suspend fun createSession(): ChatSessionEntity {
        val session = ChatSessionEntity(sessionId = UUID.randomUUID().toString())
        dao.insertSession(session)
        return session
    }

    suspend fun updateTitle(sessionId: String, title: String) {
        dao.updateSessionTitle(sessionId, title)
    }

    suspend fun deleteSession(sessionId: String) {
        dao.deleteSession(sessionId)  // FK cascade deletes messages too
    }

    /** One-shot session lookup (for title/createdAt when restoring). */
    suspend fun getSession(sessionId: String): ChatSessionEntity? =
        dao.getSession(sessionId)

    // ── Messages ──────────────────────────────────────────────────────────────

    fun getMessages(sessionId: String): Flow<List<ChatMessageEntity>> =
        dao.getMessagesForSession(sessionId)

    /**
     * One-shot fetch that restores an entire session's messages into the
     * in-memory [ChatUiState]. Called when the user picks a session from the
     * history drawer.
     */
    suspend fun loadSessionMessages(sessionId: String): List<ChatMessageEntity> =
        dao.getMessagesForSessionOnce(sessionId, MAX_RESTORED_MESSAGES)

    /**
     * ✅ Streaming-trap-safe: only called once generation is complete.
     *
     * Both messages are persisted atomically in a single DB transaction,
     * so a crash mid-write can never leave an orphaned user message without
     * its paired AI response.
     */
    suspend fun saveCompleteInteraction(
        sessionId  : String,
        userText   : String,
        finalAiText: String
    ) {
        val now = System.currentTimeMillis()
        dao.saveCompleteInteraction(
            userMessage = ChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role      = MessageRole.USER,
                content   = userText,
                timestamp = now
            ),
            aiMessage = ChatMessageEntity(
                messageId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role      = MessageRole.AI,
                content   = finalAiText,
                timestamp = now + 1   // +1ms so ordering is deterministic
            )
        )
    }

    private companion object {
        /** Cap on messages restored into memory when reopening a session. */
        const val MAX_RESTORED_MESSAGES = 200
    }
}
