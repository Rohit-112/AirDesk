package com.share.app.domain.usecase

import com.share.app.domain.model.AppSessionState
import com.share.app.domain.model.HistoryItem
import com.share.app.domain.model.OutgoingFile
import com.share.app.domain.model.ThemePreference
import com.share.app.domain.repository.ClipboardRepository
import com.share.app.domain.repository.FileSystemRepository
import com.share.app.domain.repository.HistoryRepository
import com.share.app.domain.repository.PreferencesRepository
import com.share.app.domain.session.SessionEngine
import com.share.app.util.suspendRunCatching
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** Pairing: hosting, joining and leaving a session. */
class PairingUseCase(private val engine: SessionEngine) {
    val session: StateFlow<AppSessionState> get() = engine.state

    fun startNewCode() = engine.startNewCode()
    fun createSession() = engine.createSession()
    fun joinSession(code: String) = engine.joinSession(code)
    fun beginJoin() = engine.beginJoin()
    fun disconnect() = engine.disconnect()
    fun reconnectFileSharing() = engine.reconnectFileSharing()
    fun retrySignIn() = engine.retrySignIn()
    fun clearError() = engine.clearError()
    fun onUserActivity() = engine.onUserActivity()
    fun handleDeepLink(raw: String) = engine.handleDeepLink(raw)
}

/** Moving text and files, and getting them back out of the app. */
class TransferUseCase(
    private val engine: SessionEngine,
    private val fileSystemRepository: FileSystemRepository,
    private val clipboardRepository: ClipboardRepository,
    private val historyRepository: HistoryRepository,
) {
    val history: StateFlow<List<HistoryItem>> get() = historyRepository.history

    fun sendText(text: String) = engine.sendText(text)
    fun sendFile(file: OutgoingFile) = engine.sendFile(file)

    /** Opens the system picker; nothing happens if the user backs out. */
    suspend fun pickAndSendFile(): Result<Unit> = suspendRunCatching {
        val file = fileSystemRepository.pickFile() ?: return@suspendRunCatching
        engine.sendFile(file)
    }

    suspend fun saveIncomingFile() = engine.saveIncomingFile()
    suspend fun saveHistoryFile(id: String) = engine.saveHistoryFile(id)

    suspend fun copyToClipboard(text: String): Result<Unit> =
        suspendRunCatching { clipboardRepository.writeText(text) }

    suspend fun readClipboard(): String? =
        suspendRunCatching { clipboardRepository.readText() }.getOrNull()
}

class PreferencesUseCase(private val preferencesRepository: PreferencesRepository) {
    val themePreference: Flow<ThemePreference> get() = preferencesRepository.themePreference

    suspend fun cycleThemePreference() {
        val current = preferencesRepository.themePreference.first()
        preferencesRepository.setThemePreference(current.next)
    }
}
