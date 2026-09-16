package com.share.app.domain.usecase

import com.share.app.domain.analytics.AnalyticsEvent
import com.share.app.domain.analytics.AnalyticsLogger
import com.share.app.domain.analytics.ConversionSource
import com.share.app.domain.analytics.JoinMethod
import com.share.app.domain.media.ImageFormat
import com.share.app.domain.media.ImagePreview
import com.share.app.domain.media.SerialImageProcessor
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** Pairing: hosting, joining and leaving a session. */
class PairingUseCase(private val engine: SessionEngine) {
    val session: StateFlow<AppSessionState> get() = engine.state

    fun startNewCode() = engine.startNewCode()
    fun joinSession(code: String, method: JoinMethod) = engine.joinSession(code, method)
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

/**
 * "Save as" another format, done entirely on this device: the picture is
 * redrawn locally and never uploaded anywhere to be converted.
 */
class ConvertImageUseCase(
    private val engine: SessionEngine,
    private val processor: SerialImageProcessor,
    private val fileSystemRepository: FileSystemRepository,
    private val analytics: AnalyticsLogger,
) {
    /** A received file's bytes, while the app still holds them. */
    suspend fun heldFile(id: String): ByteArray? = engine.heldFile(id)

    /** Throws [com.share.app.domain.media.ImageProcessingException] when it cannot. */
    suspend fun convert(bytes: ByteArray, sourceType: String, format: ImageFormat, source: ConversionSource): ByteArray {
        val converted = try {
            processor.convert(bytes, sourceType, format)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            analytics.log(AnalyticsEvent.ImageConversionFailed(format))
            throw error
        }
        analytics.log(AnalyticsEvent.ImageConverted(format, source))
        return converted
    }

    suspend fun preview(bytes: ByteArray, mimeType: String): ImagePreview? =
        processor.preview(bytes, mimeType, SerialImageProcessor.PREVIEW_MAX_DIMENSION)

    /** Opens the save dialog. False when the user backs out; a failure when writing failed. */
    suspend fun save(name: String, bytes: ByteArray): Result<Boolean> =
        suspendRunCatching { fileSystemRepository.saveFile(name, bytes) }

    suspend fun pickImage(): Result<OutgoingFile?> =
        suspendRunCatching { fileSystemRepository.pickFile(imagesOnly = true) }
}

class PreferencesUseCase(private val preferencesRepository: PreferencesRepository) {
    val themePreference: Flow<ThemePreference> get() = preferencesRepository.themePreference

    suspend fun cycleThemePreference() {
        val current = preferencesRepository.themePreference.first()
        preferencesRepository.setThemePreference(current.next)
    }
}
