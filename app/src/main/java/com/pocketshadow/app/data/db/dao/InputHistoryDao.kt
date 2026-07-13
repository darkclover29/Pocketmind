package com.pocketshadow.app.data.db.dao

import androidx.room.*
import com.pocketshadow.app.data.db.entity.InputHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InputHistoryDao {

    /** Reactive MRU list — newest first. The input field cycles via swipe-up. */
    @Query("SELECT * FROM input_history ORDER BY lastUsedAt DESC LIMIT ${InputHistoryEntity.MAX_ENTRIES}")
    fun getAll(): Flow<List<InputHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: InputHistoryEntity)

    @Query("DELETE FROM input_history WHERE id = :id")
    suspend fun delete(id: String)

    /** Trims old rows beyond [keep] count; keeps the table bounded. */
    @Query("DELETE FROM input_history WHERE id NOT IN (SELECT id FROM input_history ORDER BY lastUsedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}
