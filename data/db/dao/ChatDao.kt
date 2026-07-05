package com.pocketmind.data.db.dao

import androidx.room.*
import com.pocketmind.data.db.entity.ChatMessageEntity
import com.pocketmind.data.db.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // ── Sessions ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    /** Reactive list — the sidebar updates automatically via Flow. */
    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("UPDATE chat_sessions SET title = :title WHERE sessionId = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, title: String)

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)   // cascades to messages via FK

    // ── Messages ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    /**
     * Reactive per-session query.
     * The LazyColumn recomposes automatically whenever new rows are committed —
     * no manual refresh needed.
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    /**
     * Inserts both the user prompt and the completed AI response in a single
     * atomic transaction. Called only on generation complete / stop — never
     * during token streaming (avoids the streaming-thrash problem).
     */
    @Transaction
    suspend fun saveCompleteInteraction(
        userMessage: ChatMessageEntity,
        aiMessage  : ChatMessageEntity
    ) {
        insertMessage(userMessage)
        insertMessage(aiMessage)
    }
}
