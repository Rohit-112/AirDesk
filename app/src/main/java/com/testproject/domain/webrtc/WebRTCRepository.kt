package com.testproject.domain.webrtc

import kotlinx.coroutines.flow.Flow

interface WebRTCRepository {
    fun initialize(sessionCode: String, isHost: Boolean)
    fun sendFile(fileName: String, fileBytes: ByteArray): Flow<TransferProgress>
    fun observeIncomingFiles(): Flow<IncomingFile>
    fun close()
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
