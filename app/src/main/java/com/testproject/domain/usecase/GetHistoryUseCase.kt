package com.testproject.domain.usecase

import com.testproject.domain.model.HistoryItem
import com.testproject.domain.repository.IHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHistoryUseCase @Inject constructor(
    private val repository: IHistoryRepository
) {
    operator fun invoke(isReceived: Boolean): Flow<List<HistoryItem>> {
        return repository.getRecentHistory(isReceived)
    }
}
