package com.testproject.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.testproject.domain.webrtc.*
import com.testproject.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebRTCViewModel @Inject constructor(
    private val initializeWebRTCUseCase: InitializeWebRTCUseCase,
    private val sendFileWebRTCUseCase: SendFileWebRTCUseCase,
    private val observeIncomingFilesUseCase: ObserveIncomingFilesUseCase,
    private val observeWebRTCStateUseCase: ObserveWebRTCStateUseCase,
    private val closeWebRTCUseCase: CloseWebRTCUseCase
) : ViewModel() {

    val connectionState: StateFlow<WebRTCState> = observeWebRTCStateUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WebRTCState.Idle)

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

    fun disconnect() {
        closeWebRTCUseCase()
    }

    override fun onCleared() {
        super.onCleared()
        closeWebRTCUseCase()
    }
}
