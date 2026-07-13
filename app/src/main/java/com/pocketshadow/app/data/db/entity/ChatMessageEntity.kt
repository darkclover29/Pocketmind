package com.pocketshadow.app.data.db.entity

import androidx.room.*

// Mirrors the UI-layer Role enum but kept separate so the DB layer has
// no dependency on the presentation layer.
enum class MessageRole { USER, AI }

@Entity(
    tableName   = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity        = ChatSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns  = ["sessionId"],
            // ✅ Cascade delete: removing a session wipes all its messages,
            //    preventing orphaned rows that silently eat storage.
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]    // speeds up per-session queries
)
data class ChatMessageEntity(
    @PrimaryKey
    val messageId : String,           // UUID string
    val sessionId : String,           // FK → ChatSessionEntity.sessionId
    val role      : MessageRole,
    val content   : String,
    val timestamp : Long = System.currentTimeMillis()
)
