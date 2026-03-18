package com.testproject.domain.repository

import com.testproject.domain.model.HistoryItem
import kotlinx.coroutines.flow.Flow

interface IHistoryRepository {
    fun getRecentHistory(isReceived: Boolean): Flow<List<HistoryItem>>
    fun getQueuedItems(): Flow<List<HistoryItem>>
    suspend fun insertHistory(item: HistoryItem)
    suspend fun markAsNotQueued(id: Int)
}
