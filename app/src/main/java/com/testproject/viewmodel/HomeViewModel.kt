package com.testproject.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.testproject.domain.model.HistoryItem
import com.testproject.domain.usecase.*
import com.testproject.utils.AppPreference
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHistoryUseCase: GetHistoryUseCase,
    private val getQueuedItemsUseCase: GetQueuedItemsUseCase,
    private val insertHistoryUseCase: InsertHistoryUseCase,
    private val markAsNotQueuedUseCase: MarkAsNotQueuedUseCase,
    private val createSessionUseCase: CreateSessionUseCase,
    private val joinSessionUseCase: JoinSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val isFileSizeValidUseCase: IsFileSizeValidUseCase,
    private val uploadFileUseCase: UploadFileUseCase,
    private val downloadFileBytesUseCase: DownloadFileBytesUseCase,
    private val deleteFileByUrlUseCase: DeleteFileByUrlUseCase,
    private val appPreference: AppPreference
) : ViewModel() {

    val sharedHistory = getHistoryUseCase(isReceived = false).asLiveData()
    val receivedHistory = getHistoryUseCase(isReceived = true).asLiveData()
    val queuedHistory = getQueuedItemsUseCase().asLiveData()

    private val _uploadProgress = MutableLiveData<Int>()
    val uploadProgress: LiveData<Int> = _uploadProgress

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

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

    fun createSession(deviceId: String, onResult: (String?) -> Unit) {
        createSessionUseCase(deviceId, onResult)
    }

    fun joinSession(code: String, deviceId: String, onResult: (Boolean, String?) -> Unit) {
        joinSessionUseCase(code, deviceId, onResult)
    }

    fun deleteSession(code: String) {
        deleteSessionUseCase(code)
    }

    fun isFileSizeValid(context: Context, uri: Uri): Boolean {
        return isFileSizeValidUseCase(context, uri)
    }

    suspend fun uploadFile(sessionCode: String, uri: Uri, fileName: String): String? {
        return uploadFileUseCase(sessionCode, uri, fileName) { progress ->
            _uploadProgress.postValue(progress)
        }
    }

    suspend fun downloadFileBytes(url: String): ByteArray? {
        return downloadFileBytesUseCase(url)
    }

    suspend fun deleteFileByUrl(url: String) {
        deleteFileByUrlUseCase(url)
    }
    
    suspend fun saveSession(code: String, isHost: Boolean) {
        appPreference.saveSessionCode(code)
        appPreference.setIsHost(isHost)
    }
    
    suspend fun getSessionCode() = appPreference.getSessionCode()
    suspend fun isHost() = appPreference.isHost()
    suspend fun clearPersistedSession() = appPreference.removeSession()
}
