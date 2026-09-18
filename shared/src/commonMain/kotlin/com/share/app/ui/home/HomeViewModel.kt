package com.share.app.ui.home

import androidx.lifecycle.viewModelScope
import com.share.app.base.BaseViewModel
import com.share.app.config.AppConfig
import com.share.app.domain.analytics.ConversionSource
import com.share.app.domain.analytics.JoinMethod
import com.share.app.domain.media.ImageFormat
import com.share.app.domain.model.AppSessionState
import com.share.app.domain.model.SessionRole
import com.share.app.domain.model.SessionStatus
import com.share.app.domain.policy.PairingCode
import com.share.app.domain.policy.SessionLimits
import com.share.app.domain.usecase.ConvertImageUseCase
import com.share.app.domain.usecase.PairingUseCase
import com.share.app.domain.usecase.TransferUseCase
import com.share.app.ui.convert.ConversionController
import com.share.app.util.currentTimeMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HomeViewModel(
    private val pairingUseCase: PairingUseCase,
    private val transferUseCase: TransferUseCase,
    private val convertImageUseCase: ConvertImageUseCase,
    private val config: AppConfig,
) : BaseViewModel<HomeUiState, HomeIntent, HomeEffect>(HomeUiState()) {

    private var previousSession = AppSessionState()
    private var errorDismissJob: Job? = null
    private var linkedFlashJob: Job? = null
    private var sentFlashJob: Job? = null
    private var codeCopiedJob: Job? = null
    private var inboxCopiedJob: Job? = null
    private var historyCopiedJob: Job? = null
    private var lastActivityReportedAt = 0L

    private val conversions = ConversionController(viewModelScope, convertImageUseCase) { conversion ->
        updateState { copy(conversion = conversion) }
    }

    init {
        pairingUseCase.session
            .onEach(::onSessionChanged)
            .launchIn(viewModelScope)

        transferUseCase.history
            .onEach { history ->
                updateState {
                    // A row that no longer holds its file cannot be converted.
                    val panelStillUsable = history.any { it.id == convertPanelId && it.hasPayload }
                    copy(history = history, convertPanelId = convertPanelId.takeIf { panelStillUsable })
                }
            }
            .launchIn(viewModelScope)
    }

    private fun onSessionChanged(session: AppSessionState) {
        val previous = previousSession
        previousSession = session

        updateState {
            copy(
                session = session,
                joinUrl = PairingCode.buildJoinUrl(config.siteUrl, session.sessionCode),
                // A finished join no longer needs the typed digits on screen.
                joinCode = if (session.role == SessionRole.GUEST && session.sessionStatus == SessionStatus.CONNECTED) "" else joinCode,
            )
        }

        if (session.peerOnline && !previous.peerOnline) {
            linkedFlashJob = flash(linkedFlashJob, LINKED_FLASH_MS, { copy(justLinked = true) }, { copy(justLinked = false) })
        }

        // An error stays up long enough to read, then gets out of the way.
        if (session.error != null && session.error != previous.error) {
            errorDismissJob?.cancel()
            errorDismissJob = viewModelScope.launch {
                delay(ERROR_DISMISS_MS)
                pairingUseCase.clearError()
            }
        }
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.JoinCodeChanged -> onJoinCodeChanged(intent.value)
            HomeIntent.CancelJoin -> {
                // Backing out has to hand a working code back - a fresh one, never
                // the code of the session that could not be joined.
                updateState { copy(joinCode = "") }
                pairingUseCase.startNewCode()
            }
            HomeIntent.NewCodeClicked -> {
                if (currentState.busy) return
                updateState { copy(joinCode = "") }
                pairingUseCase.startNewCode()
            }
            HomeIntent.DisconnectClicked -> {
                // Disconnecting leaves this device hosting a fresh code, ready to pair again.
                updateState { copy(joinCode = "") }
                pairingUseCase.disconnect()
            }
            HomeIntent.CopyCodeClicked -> copyCode()
            HomeIntent.ScanClicked -> updateState { copy(isScannerOpen = true) }
            HomeIntent.ScannerDismissed -> updateState { copy(isScannerOpen = false) }
            is HomeIntent.CodeScanned -> {
                updateState { copy(isScannerOpen = false, joinCode = intent.code) }
                pairingUseCase.joinSession(intent.code, JoinMethod.SCAN)
            }
            HomeIntent.RetrySignIn -> pairingUseCase.retrySignIn()

            is HomeIntent.ComposerTextChanged ->
                updateState { copy(composerText = intent.value.take(SessionLimits.MAX_TEXT_CHARS)) }
            HomeIntent.SendTextClicked -> sendText()
            HomeIntent.PasteClicked -> paste()
            HomeIntent.AttachFileClicked -> viewModelScope.launch { transferUseCase.pickAndSendFile() }
            is HomeIntent.FilesDropped -> {
                updateState { copy(isDraggingFile = false) }
                // One file at a time, like the web client.
                if (currentState.linked) intent.files.firstOrNull()?.let(transferUseCase::sendFile)
            }
            is HomeIntent.DragStateChanged -> updateState { copy(isDraggingFile = intent.dragging) }

            HomeIntent.CopyIncomingText -> copyIncomingText()
            HomeIntent.SaveIncomingFile -> viewModelScope.launch { transferUseCase.saveIncomingFile() }
            is HomeIntent.CopyHistoryText -> copyHistoryText(intent.id)
            is HomeIntent.SaveHistoryFile -> viewModelScope.launch { transferUseCase.saveHistoryFile(intent.id) }
            HomeIntent.ToggleActivityExpanded -> updateState { copy(isActivityExpanded = !isActivityExpanded) }

            is HomeIntent.ConvertIncomingFile -> convertIncoming(intent.format)
            is HomeIntent.ConvertHistoryFile -> convertHistory(intent.id, intent.format)
            is HomeIntent.ToggleConvertPanel ->
                updateState { copy(convertPanelId = if (convertPanelId == intent.id) null else intent.id) }
            HomeIntent.SaveConvertedAgain -> conversions.saveAgain()
            HomeIntent.ConvertOnlyClicked -> sendEffect(HomeEffect.NavigateToConvert)

            HomeIntent.ToggleAdvanced -> updateState { copy(isAdvancedOpen = !isAdvancedOpen) }
            HomeIntent.RetryDirectConnection -> pairingUseCase.reconnectFileSharing()

            HomeIntent.DismissError -> pairingUseCase.clearError()
            HomeIntent.UserActivity -> reportActivity()
            HomeIntent.AboutClicked -> sendEffect(HomeEffect.NavigateToAbout)
        }
    }

    private fun onJoinCodeChanged(value: String) {
        val digits = PairingCode.sanitize(value)
        updateState { copy(joinCode = digits) }
        // Six digits is unambiguous intent - no reason to make them press a button.
        if (digits.length == PairingCode.LENGTH) {
            pairingUseCase.joinSession(digits, JoinMethod.CODE)
            return
        }
        // Typing is the first honest signal that this device's own code is being
        // abandoned. Focus alone used to be enough, so moving through the field
        // with a keyboard or a screen reader silently deleted a live pairing.
        if (digits.isNotEmpty() && !currentState.session.joinIntent) pairingUseCase.beginJoin()
    }

    /** The inbox file and its activity row share an id, so either converts the same bytes. */
    private fun convertIncoming(format: ImageFormat) {
        val file = currentState.session.incomingFile ?: return
        val id = file.localFileId ?: return
        conversions.convert(
            key = id,
            source = ConversionSource.INBOX,
            fileName = file.name,
            mimeType = file.contentType,
            format = format,
            loadBytes = { convertImageUseCase.heldFile(id) },
        )
    }

    private fun convertHistory(id: String, format: ImageFormat) {
        val item = currentState.history.firstOrNull { it.id == id } ?: return
        val mimeType = item.contentType ?: return
        conversions.convert(
            key = id,
            source = ConversionSource.ACTIVITY,
            fileName = item.title,
            mimeType = mimeType,
            format = format,
            loadBytes = { convertImageUseCase.heldFile(id) },
        )
    }

    private fun sendText() {
        val state = currentState
        if (!state.canSendText) return
        transferUseCase.sendText(state.composerText)
        updateState { copy(composerText = "") }
        sentFlashJob = flash(sentFlashJob, SENT_FLASH_MS, { copy(justSent = true) }, { copy(justSent = false) })
    }

    private fun paste() {
        viewModelScope.launch {
            val pasted = transferUseCase.readClipboard()?.takeIf { it.isNotEmpty() } ?: return@launch
            updateState { copy(composerText = pasted.take(SessionLimits.MAX_TEXT_CHARS)) }
        }
    }

    private fun copyCode() {
        val code = currentState.session.sessionCode.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            transferUseCase.copyToClipboard(code).onSuccess {
                codeCopiedJob = flash(codeCopiedJob, COPIED_FLASH_MS, { copy(codeCopied = true) }, { copy(codeCopied = false) })
            }
        }
    }

    private fun copyIncomingText() {
        val text = currentState.session.incomingText ?: return
        viewModelScope.launch {
            transferUseCase.copyToClipboard(text).onSuccess {
                inboxCopiedJob = flash(inboxCopiedJob, COPIED_FLASH_MS, { copy(inboxCopied = true) }, { copy(inboxCopied = false) })
            }
        }
    }

    private fun copyHistoryText(id: String) {
        val text = currentState.history.firstOrNull { it.id == id }?.text ?: return
        viewModelScope.launch {
            transferUseCase.copyToClipboard(text).onSuccess {
                historyCopiedJob = flash(historyCopiedJob, COPIED_FLASH_MS, { copy(copiedHistoryId = id) }, { copy(copiedHistoryId = null) })
            }
        }
    }

    /** Touches arrive constantly; the inactivity timer only needs an occasional nudge. */
    private fun reportActivity() {
        val now = currentTimeMillis()
        if (now - lastActivityReportedAt < ACTIVITY_THROTTLE_MS) return
        lastActivityReportedAt = now
        pairingUseCase.onUserActivity()
    }

    private fun flash(
        previous: Job?,
        durationMs: Long,
        on: HomeUiState.() -> HomeUiState,
        off: HomeUiState.() -> HomeUiState,
    ): Job {
        previous?.cancel()
        updateState(on)
        return viewModelScope.launch {
            delay(durationMs)
            updateState(off)
        }
    }

    private companion object {
        const val ERROR_DISMISS_MS = 7_000L
        const val LINKED_FLASH_MS = 1_000L
        const val SENT_FLASH_MS = 1_200L
        const val COPIED_FLASH_MS = 1_600L
        const val ACTIVITY_THROTTLE_MS = 5_000L
    }
}
