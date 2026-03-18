package com.testproject.data.local

import com.testproject.domain.model.HistoryItem
import com.testproject.domain.repository.IHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(private val historyDao: HistoryDao) : IHistoryRepository {

    override fun getRecentHistory(isReceived: Boolean): Flow<List<HistoryItem>> {
        return historyDao.getRecentHistory(isReceived).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun getQueuedItems(): Flow<List<HistoryItem>> {
        return historyDao.getQueuedItems().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun insertHistory(item: HistoryItem) {
        val entity = HistoryEntity(
            id = item.id,
            content = item.content,
            timestamp = item.timestamp,
            isReceived = item.isReceived,
            isFile = item.isFile,
            fileName = item.fileName,
            isQueued = item.isQueued
        )
        historyDao.insert(entity)
        if (!entity.isQueued) {
            historyDao.deleteOldItems(entity.isReceived)
        }
    }

    override suspend fun markAsNotQueued(id: Int) {
        historyDao.markAsNotQueued(id)
    }

    private fun HistoryEntity.toDomain() = HistoryItem(
        id = id,
        content = content,
        timestamp = timestamp,
        isFile = isFile,
        fileName = fileName,
        isReceived = isReceived,
        isQueued = isQueued
    )
}
