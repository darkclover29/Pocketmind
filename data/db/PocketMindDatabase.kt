package com.pocketmind.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pocketmind.data.db.dao.ChatDao
import com.pocketmind.data.db.entity.ChatMessageEntity
import com.pocketmind.data.db.entity.ChatSessionEntity

@Database(
    entities  = [ChatSessionEntity::class, ChatMessageEntity::class],
    version   = 1,
    exportSchema = true          // set to false only in library modules
)
abstract class PocketMindDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        const val DATABASE_NAME = "pocketmind.db"
    }
}
