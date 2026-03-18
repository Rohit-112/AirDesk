package com.testproject.domain.usecase

import com.google.firebase.database.ValueEventListener
import com.testproject.domain.repository.ISessionRepository
import javax.inject.Inject

class CreateSessionUseCase @Inject constructor(private val repository: ISessionRepository) {
    operator fun invoke(deviceId: String, onResult: (String?) -> Unit) = repository.createSession(deviceId, onResult)
}

class JoinSessionUseCase @Inject constructor(private val repository: ISessionRepository) {
    operator fun invoke(code: String, deviceId: String, onResult: (Boolean, String?) -> Unit) = repository.joinSession(code, deviceId, onResult)
}

class DeleteSessionUseCase @Inject constructor(private val repository: ISessionRepository) {
    operator fun invoke(code: String) = repository.deleteSession(code)
}

class ObserveClipboardUseCase @Inject constructor(private val repository: ISessionRepository) {
    operator fun invoke(code: String, node: String, onData: (String?) -> Unit) = repository.observeClipboard(code, node, onData)
}

class ObservePeerPresenceUseCase @Inject constructor(private val repository: ISessionRepository) {
    operator fun invoke(code: String, node: String, onStatus: (Boolean) -> Unit) = repository.observePeerPresence(code, node, onStatus)
}

class WriteClipboardUseCase @Inject constructor(private val repository: ISessionRepository) {
    operator fun invoke(code: String, node: String, text: String) = repository.writeClipboard(code, node, text)
}

class RemoveSessionListenerUseCase @Inject constructor(private val repository: ISessionRepository) {
    operator fun invoke(code: String, node: String, listener: ValueEventListener) = repository.removeListener(code, node, listener)
}
