package com.testproject.domain.usecase

import com.testproject.domain.repository.IHistoryRepository
import javax.inject.Inject

class MarkAsNotQueuedUseCase @Inject constructor(
    private val repository: IHistoryRepository
) {
    suspend operator fun invoke(id: Int) {
        repository.markAsNotQueued(id)
    }
}
