package com.testproject.domain.webrtc

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface WebRTCRepository {
    val connectionState: StateFlow<WebRTCState>
    fun initialize(sessionCode: String, isHost: Boolean)
    fun sendFile(fileName: String, fileBytes: ByteArray): Flow<TransferProgress>
    fun observeIncomingFiles(): Flow<IncomingFile>
    fun close()
}

sealed class WebRTCState {
    object Idle : WebRTCState()
    object Connecting : WebRTCState()
    object Connected : WebRTCState()
    data class Disconnected(val reason: String? = null) : WebRTCState()
    data class Error(val message: String) : WebRTCState()
}

sealed class TransferProgress {
    data class Progress(val percentage: Int) : TransferProgress()
    data class Success(val fileName: String) : TransferProgress()
    data class Error(val message: String) : TransferProgress()
}

data class IncomingFile(
    val fileName: String,
    val fileBytes: ByteArray
)
