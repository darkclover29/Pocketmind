package com.pocketshadow.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * MRU list of user-typed prompts. Tapping a row (or swiping up in the input
 * field) replays it back into the composer. Capped at [MAX_ENTRIES] via DB
 * trim in [com.pocketshadow.app.data.repository.ChatHistoryRepository].
 */
@Entity(
    tableName = "input_history",
    indices = [Index(value = ["text"], unique = true)]
)
data class InputHistoryEntity(
    @PrimaryKey
    val id        : String,           // UUID string
    val text      : String,           // unique — duplicate insert replaces
    val lastUsedAt: Long   = System.currentTimeMillis()
) {
    companion object {
        const val MAX_ENTRIES = 10
    }
}
