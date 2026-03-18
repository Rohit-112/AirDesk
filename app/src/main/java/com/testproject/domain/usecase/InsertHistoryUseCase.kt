package com.testproject.domain.usecase

import com.testproject.domain.model.HistoryItem
import com.testproject.domain.repository.IHistoryRepository
import javax.inject.Inject

class InsertHistoryUseCase @Inject constructor(
    private val repository: IHistoryRepository
) {
    suspend operator fun invoke(item: HistoryItem) {
        repository.insertHistory(item)
    }
}
