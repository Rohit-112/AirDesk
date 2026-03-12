package com.testproject.data.local

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(private val historyDao: HistoryDao) {

    fun getRecentHistory(isReceived: Boolean): Flow<List<HistoryEntity>> {
        return historyDao.getRecentHistory(isReceived)
    }

    fun getQueuedItems(): Flow<List<HistoryEntity>> {
        return historyDao.getQueuedItems()
    }

    suspend fun insertHistory(entity: HistoryEntity) {
        historyDao.insert(entity)
        if (!entity.isQueued) {
            historyDao.deleteOldItems(entity.isReceived)
        }
    }

    suspend fun markAsNotQueued(id: Int) {
        historyDao.markAsNotQueued(id)
    }
}
