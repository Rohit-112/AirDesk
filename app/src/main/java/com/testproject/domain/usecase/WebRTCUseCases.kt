package com.testproject.domain.usecase

import com.testproject.domain.webrtc.IncomingFile
import com.testproject.domain.webrtc.TransferProgress
import com.testproject.domain.webrtc.WebRTCRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class InitializeWebRTCUseCase @Inject constructor(private val repository: WebRTCRepository) {
    operator fun invoke(sessionCode: String, isHost: Boolean) = repository.initialize(sessionCode, isHost)
}

class SendFileWebRTCUseCase @Inject constructor(private val repository: WebRTCRepository) {
    operator fun invoke(fileName: String, fileBytes: ByteArray): Flow<TransferProgress> = 
        repository.sendFile(fileName, fileBytes)
}

class ObserveIncomingFilesUseCase @Inject constructor(private val repository: WebRTCRepository) {
    operator fun invoke(): Flow<IncomingFile> = repository.observeIncomingFiles()
}

class CloseWebRTCUseCase @Inject constructor(private val repository: WebRTCRepository) {
    operator fun invoke() = repository.close()
}
