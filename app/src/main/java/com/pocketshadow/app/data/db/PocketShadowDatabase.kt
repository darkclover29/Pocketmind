package com.pocketshadow.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pocketshadow.app.data.db.dao.ChatDao
import com.pocketshadow.app.data.db.dao.InputHistoryDao
import com.pocketshadow.app.data.db.entity.ChatMessageEntity
import com.pocketshadow.app.data.db.entity.ChatSessionEntity
import com.pocketshadow.app.data.db.entity.InputHistoryEntity

@Database(
    entities  = [ChatSessionEntity::class, ChatMessageEntity::class, InputHistoryEntity::class],
    version   = 2,
    exportSchema = true          // set to false only in library modules
)
abstract class PocketShadowDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun inputHistoryDao(): InputHistoryDao

    companion object {
        const val DATABASE_NAME = "pocketshadow.db"

        /** Adds prompt history without touching existing chats or messages. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS input_history " +
                        "(id TEXT NOT NULL, text TEXT NOT NULL, lastUsedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(id))"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_input_history_text " +
                        "ON input_history(text)"
                )
            }
        }
    }
}
