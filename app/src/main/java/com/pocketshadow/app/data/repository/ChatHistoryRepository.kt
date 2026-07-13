package com.pocketshadow.app.data.repository

import com.pocketshadow.app.data.db.SearchResult
import com.pocketshadow.app.data.db.dao.ChatDao
import com.pocketshadow.app.data.db.dao.InputHistoryDao
import com.pocketshadow.app.data.db.entity.ChatMessageEntity
import com.pocketshadow.app.data.db.entity.ChatSessionEntity
import com.pocketshadow.app.data.db.entity.InputHistoryEntity
import com.pocketshadow.app.data.db.entity.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatHistoryRepository @Inject constructor(
    private val dao: ChatDao,
    private val inputHistoryDao: InputHistoryDao
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

    /** Delete a single message (per-message delete from the bubble menu). */
    suspend fun deleteMessage(messageId: String) = dao.deleteMessage(messageId)

    /** Replace a message's content in place (used by Regenerate variant). */
    suspend fun updateMessageContent(messageId: String, newContent: String) =
        dao.updateMessageContent(messageId, newContent)

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

    // ── Search ────────────────────────────────────────────────────────────────

    /** Returns up to 50 matches ordered by recency. */
    suspend fun searchMessages(query: String): List<SearchResult> =
        dao.searchMessages(query)

    // ── Input history (MRU user prompts) ─────────────────────────────────────

    fun getInputHistory(): Flow<List<InputHistoryEntity>> = inputHistoryDao.getAll()

    /** Persist a user-typed prompt (dedup'd by text, bumps lastUsedAt). */
    suspend fun recordInput(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        inputHistoryDao.upsert(
            InputHistoryEntity(
                id = UUID.nameUUIDFromBytes(trimmed.lowercase().toByteArray()).toString(),
                text = trimmed,
                lastUsedAt = System.currentTimeMillis()
            )
        )
        inputHistoryDao.trim(InputHistoryEntity.MAX_ENTRIES)
    }

    suspend fun deleteInputHistory(id: String) = inputHistoryDao.delete(id)

    // ── Encrypted local archive ──────────────────────────────────────────────

    suspend fun exportEncryptedArchive(passphrase: String): ByteArray = withContext(Dispatchers.IO) {
        require(passphrase.length >= MIN_ARCHIVE_PASSPHRASE_CHARS) {
            "Use at least $MIN_ARCHIVE_PASSPHRASE_CHARS characters for the archive password."
        }
        val sessions = dao.getAllSessionsOnce()
        val payload = JSONObject().apply {
            put("version", ARCHIVE_PAYLOAD_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("sessions", JSONArray().also { array ->
                sessions.forEach { session ->
                    val messages = dao.getAllMessagesForSessionOnce(session.sessionId)
                    array.put(JSONObject().apply {
                        put("sessionId", session.sessionId)
                        put("title", session.title)
                        put("createdAt", session.createdAt)
                        put("messages", JSONArray().also { msgArray ->
                            messages.forEach { msg ->
                                msgArray.put(JSONObject().apply {
                                    put("messageId", msg.messageId)
                                    put("role", msg.role.name)
                                    put("content", msg.content)
                                    put("timestamp", msg.timestamp)
                                })
                            }
                        })
                    })
                }
            })
        }
        encryptArchive(payload.toString().toByteArray(Charsets.UTF_8), passphrase)
    }

    suspend fun importEncryptedArchive(bytes: ByteArray, passphrase: String): Int = withContext(Dispatchers.IO) {
        require(passphrase.length >= MIN_ARCHIVE_PASSPHRASE_CHARS) {
            "Use at least $MIN_ARCHIVE_PASSPHRASE_CHARS characters for the archive password."
        }
        val payload = JSONObject(String(decryptArchive(bytes, passphrase), Charsets.UTF_8))
        require(payload.optInt("version") == ARCHIVE_PAYLOAD_VERSION) {
            "This archive version is not supported."
        }

        val sessions = payload.getJSONArray("sessions")
        var imported = 0
        for (i in 0 until sessions.length()) {
            val rawSession = sessions.getJSONObject(i)
            val sessionId = rawSession.getString("sessionId")
            val session = ChatSessionEntity(
                sessionId = sessionId,
                title     = rawSession.optString("title", "Imported Chat"),
                createdAt = rawSession.optLong("createdAt", System.currentTimeMillis())
            )
            val rawMessages = rawSession.getJSONArray("messages")
            val messages = buildList {
                for (j in 0 until rawMessages.length()) {
                    val raw = rawMessages.getJSONObject(j)
                    add(
                        ChatMessageEntity(
                            messageId = raw.optString("messageId").ifBlank { UUID.randomUUID().toString() },
                            sessionId = sessionId,
                            role      = runCatching {
                                MessageRole.valueOf(raw.getString("role"))
                            }.getOrDefault(MessageRole.USER),
                            content   = raw.getString("content"),
                            timestamp = raw.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }
            dao.importSessionWithMessages(session, messages)
            imported++
        }
        imported
    }

    private fun encryptArchive(plainText: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(16).also(secureRandom::nextBytes)
        val iv = ByteArray(12).also(secureRandom::nextBytes)
        val key = deriveArchiveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plainText)
        return JSONObject().apply {
            put("format", ARCHIVE_FORMAT)
            put("version", ARCHIVE_ENVELOPE_VERSION)
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterations", ARCHIVE_KDF_ITERATIONS)
            put("salt", base64.encodeToString(salt))
            put("iv", base64.encodeToString(iv))
            put("ciphertext", base64.encodeToString(cipherText))
        }.toString(2).toByteArray(Charsets.UTF_8)
    }

    private fun decryptArchive(bytes: ByteArray, passphrase: String): ByteArray {
        val envelope = JSONObject(String(bytes, Charsets.UTF_8))
        require(envelope.optString("format") == ARCHIVE_FORMAT) {
            "This is not a PocketShadow encrypted chat archive."
        }
        require(envelope.optInt("version") == ARCHIVE_ENVELOPE_VERSION) {
            "This archive version is not supported."
        }
        val salt = base64Decoder.decode(envelope.getString("salt"))
        val iv = base64Decoder.decode(envelope.getString("iv"))
        val cipherText = base64Decoder.decode(envelope.getString("ciphertext"))
        val key = deriveArchiveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }

    private fun deriveArchiveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ARCHIVE_KDF_ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        return SecretKeySpec(bytes, "AES")
    }

    private companion object {
        /** Cap on messages restored into memory when reopening a session. */
        const val MAX_RESTORED_MESSAGES = 200
        const val ARCHIVE_FORMAT = "PocketShadowEncryptedChatArchive"
        const val ARCHIVE_ENVELOPE_VERSION = 1
        const val ARCHIVE_PAYLOAD_VERSION = 1
        const val ARCHIVE_KDF_ITERATIONS = 120_000
        const val MIN_ARCHIVE_PASSPHRASE_CHARS = 8
        val secureRandom = SecureRandom()
        val base64: Base64.Encoder = Base64.getEncoder()
        val base64Decoder: Base64.Decoder = Base64.getDecoder()
    }
}
