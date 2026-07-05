package com.pocketmind.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey
    val sessionId : String,           // UUID string
    val title     : String = "New Chat",
    val createdAt : Long   = System.currentTimeMillis()
)
