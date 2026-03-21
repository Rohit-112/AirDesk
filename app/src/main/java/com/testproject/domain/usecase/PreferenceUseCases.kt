package com.testproject.domain.usecase

import com.testproject.domain.repository.IPreferenceRepository
import javax.inject.Inject

class SaveSessionUseCase @Inject constructor(private val repository: IPreferenceRepository) {
    suspend operator fun invoke(code: String, isHost: Boolean) {
        repository.saveSessionCode(code)
        repository.setIsHost(isHost)
    }
}

class GetSessionCodeUseCase @Inject constructor(private val repository: IPreferenceRepository) {
    suspend operator fun invoke(): String? = repository.getSessionCode()
}

class IsHostUseCase @Inject constructor(private val repository: IPreferenceRepository) {
    suspend operator fun invoke(): Boolean = repository.isHost()
}

class ClearPersistedSessionUseCase @Inject constructor(private val repository: IPreferenceRepository) {
    suspend operator fun invoke() = repository.removeSession()
}
