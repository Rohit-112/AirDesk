package com.testproject.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.testproject.domain.webrtc.IncomingFile
import com.testproject.domain.webrtc.TransferProgress
import com.testproject.domain.usecase.CloseWebRTCUseCase
import com.testproject.domain.usecase.InitializeWebRTCUseCase
import com.testproject.domain.usecase.ObserveIncomingFilesUseCase
import com.testproject.domain.usecase.SendFileWebRTCUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebRTCViewModel @Inject constructor(
    private val initializeWebRTCUseCase: InitializeWebRTCUseCase,
    private val sendFileWebRTCUseCase: SendFileWebRTCUseCase,
    private val observeIncomingFilesUseCase: ObserveIncomingFilesUseCase,
    private val closeWebRTCUseCase: CloseWebRTCUseCase
) : ViewModel() {

    private val _transferProgress = MutableLiveData<TransferProgress>()
    val transferProgress: LiveData<TransferProgress> = _transferProgress

    private val _incomingFile = MutableLiveData<IncomingFile>()
    val incomingFile: LiveData<IncomingFile> = _incomingFile

    fun initConnection(sessionCode: String, isHost: Boolean) {
        initializeWebRTCUseCase(sessionCode, isHost)
        observeIncoming()
    }

    fun sendFile(fileName: String, fileBytes: ByteArray) {
        viewModelScope.launch {
            sendFileWebRTCUseCase(fileName, fileBytes).collectLatest {
                _transferProgress.postValue(it)
            }
        }
    }

    private fun observeIncoming() {
        viewModelScope.launch {
            observeIncomingFilesUseCase().collectLatest {
                _incomingFile.postValue(it)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeWebRTCUseCase()
    }
}
