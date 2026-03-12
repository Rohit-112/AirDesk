package com.testproject.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_table WHERE isReceived = :isReceived AND isQueued = 0 ORDER BY timestamp DESC LIMIT 10")
    fun getRecentHistory(isReceived: Boolean): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_table WHERE isQueued = 1 ORDER BY timestamp DESC")
    fun getQueuedItems(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HistoryEntity)

    @Query("UPDATE history_table SET isQueued = 0 WHERE id = :id")
    suspend fun markAsNotQueued(id: Int)

    @Query("DELETE FROM history_table WHERE id NOT IN (SELECT id FROM history_table WHERE isReceived = :isReceived AND isQueued = 0 ORDER BY timestamp DESC LIMIT 10) AND isReceived = :isReceived AND isQueued = 0")
    suspend fun deleteOldItems(isReceived: Boolean)
}
