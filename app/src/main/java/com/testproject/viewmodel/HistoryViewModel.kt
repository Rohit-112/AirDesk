package com.testproject.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.testproject.domain.model.HistoryItem
import com.testproject.domain.usecase.GetHistoryUseCase
import com.testproject.domain.usecase.GetQueuedItemsUseCase
import com.testproject.domain.usecase.InsertHistoryUseCase
import com.testproject.domain.usecase.MarkAsNotQueuedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getHistoryUseCase: GetHistoryUseCase,
    private val getQueuedItemsUseCase: GetQueuedItemsUseCase,
    private val insertHistoryUseCase: InsertHistoryUseCase,
    private val markAsNotQueuedUseCase: MarkAsNotQueuedUseCase
) : ViewModel() {

    val sharedHistory = getHistoryUseCase(isReceived = false).asLiveData()
    val receivedHistory = getHistoryUseCase(isReceived = true).asLiveData()
    val queuedHistory = getQueuedItemsUseCase().asLiveData()

    fun saveToHistory(item: HistoryItem) {
        viewModelScope.launch {
            insertHistoryUseCase(item)
        }
    }

    fun markAsNotQueued(id: Int) {
        viewModelScope.launch {
            markAsNotQueuedUseCase(id)
        }
    }
}
