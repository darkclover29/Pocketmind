package com.pocketshadow.app.data.db.dao

import androidx.room.*
import com.pocketshadow.app.data.db.SearchResult
import com.pocketshadow.app.data.db.entity.ChatMessageEntity
import com.pocketshadow.app.data.db.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // ── Sessions ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    /** Reactive list — the drawer updates automatically via Flow. */
    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC")
    suspend fun getAllSessionsOnce(): List<ChatSessionEntity>

    /** One-shot fetch for a single session (for restoring on load). */
    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    @Query("UPDATE chat_sessions SET title = :title WHERE sessionId = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, title: String)

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)   // cascades to messages via FK

    // ── Messages ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    /** Delete a single message by id (for per-message delete in the chat). */
    @Query("DELETE FROM chat_messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)

    /** Replace an existing message's content (used by Regenerate). */
    @Query("UPDATE chat_messages SET content = :newContent WHERE messageId = :messageId")
    suspend fun updateMessageContent(messageId: String, newContent: String)

    /**
     * Reactive per-session query.
     * The LazyColumn recomposes automatically whenever new rows are committed.
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    /**
     * One-shot fetch for restoring a session's messages into the in-memory UI state.
     * Called when the user selects a session from the history drawer.
     * Limited to the most recent [limit] rows (returned in chronological order)
     * so a very long chat doesn't load thousands of messages into memory.
     */
    @Query(
        """
        SELECT * FROM (
            SELECT * FROM chat_messages
            WHERE sessionId = :sessionId
            ORDER BY timestamp DESC
            LIMIT :limit
        ) ORDER BY timestamp ASC
        """
    )
    suspend fun getMessagesForSessionOnce(sessionId: String, limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getAllMessagesForSessionOnce(sessionId: String): List<ChatMessageEntity>

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * LIKE-based search across all messages, joined with the session title so
     * the drawer can render a self-contained result row.
     * Returns the first 50 matches; plenty for a "jump to" UI.
     */
    @Query(
        """
        SELECT m.messageId AS messageId,
               m.sessionId AS sessionId,
               s.title     AS sessionTitle,
               m.content   AS content,
               m.role      AS role,
               m.timestamp AS timestamp
        FROM chat_messages m
        INNER JOIN chat_sessions s ON m.sessionId = s.sessionId
        WHERE m.content LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY m.timestamp DESC
        LIMIT 50
        """
    )
    suspend fun searchMessages(query: String): List<SearchResult>

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

    @Transaction
    suspend fun importSessionWithMessages(
        session : ChatSessionEntity,
        messages: List<ChatMessageEntity>
    ) {
        insertSession(session)
        insertMessages(messages)
    }
}
