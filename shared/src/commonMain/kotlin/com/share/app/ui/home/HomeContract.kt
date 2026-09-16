package com.share.app.ui.home

import com.share.app.base.UiEffect
import com.share.app.base.UiIntent
import com.share.app.base.UiState
import com.share.app.domain.media.ImageFormat
import com.share.app.domain.model.AppSessionState
import com.share.app.domain.model.AuthStatus
import com.share.app.domain.model.HistoryItem
import com.share.app.domain.model.OutgoingFile
import com.share.app.domain.model.SessionStatus
import com.share.app.domain.policy.SessionLimits
import com.share.app.ui.convert.ConversionState
import com.share.app.ui.platform.isQrScanSupported

data class HomeUiState(
    val session: AppSessionState = AppSessionState(),
    val history: List<HistoryItem> = emptyList(),
    /** The URL the QR encodes; any camera app can open it. */
    val joinUrl: String = "",
    /** Digits typed into the "their code" field. */
    val joinCode: String = "",
    val composerText: String = "",
    val justSent: Boolean = false,
    /** One sweep of light the moment the other device appears. */
    val justLinked: Boolean = false,
    val codeCopied: Boolean = false,
    val inboxCopied: Boolean = false,
    val copiedHistoryId: String? = null,
    val isScannerOpen: Boolean = false,
    val isAdvancedOpen: Boolean = false,
    val showConnectionDetail: Boolean = false,
    val isActivityExpanded: Boolean = false,
    val isDraggingFile: Boolean = false,
    val canScan: Boolean = isQrScanSupported,
    /** The one "Save as" in progress or just finished, and which file it is for. */
    val conversion: ConversionState = ConversionState(),
    /** The activity row whose "Save as" panel is open. */
    val convertPanelId: String? = null,
) : UiState {
    val linked: Boolean get() = session.isLinked

    /** Files ride the peer connection; text waits for the key exchange too. */
    val secureReady: Boolean get() = linked && session.secureChannelReady

    val canSendText: Boolean
        get() = secureReady && composerText.isNotBlank() && composerText.length <= SessionLimits.MAX_TEXT_CHARS

    /** Reaching for the join field takes over the pairing card. */
    val joining: Boolean get() = session.joinIntent || joinCode.isNotEmpty()

    val busy: Boolean
        get() = session.authStatus != AuthStatus.READY || session.sessionStatus == SessionStatus.CONNECTING

    val connected: Boolean get() = session.sessionStatus == SessionStatus.CONNECTED
}

sealed interface HomeIntent : UiIntent {
    /* Pairing */
    data class JoinCodeChanged(val value: String) : HomeIntent
    data object CancelJoin : HomeIntent
    data object NewCodeClicked : HomeIntent
    data object DisconnectClicked : HomeIntent
    data object CopyCodeClicked : HomeIntent
    data object ScanClicked : HomeIntent
    data object ScannerDismissed : HomeIntent
    data class CodeScanned(val code: String) : HomeIntent
    data object RetrySignIn : HomeIntent

    /* Sending */
    data class ComposerTextChanged(val value: String) : HomeIntent
    data object SendTextClicked : HomeIntent
    data object PasteClicked : HomeIntent
    data object AttachFileClicked : HomeIntent
    data class FilesDropped(val files: List<OutgoingFile>) : HomeIntent
    data class DragStateChanged(val dragging: Boolean) : HomeIntent

    /* Receiving */
    data object CopyIncomingText : HomeIntent
    data object SaveIncomingFile : HomeIntent
    data class CopyHistoryText(val id: String) : HomeIntent
    data class SaveHistoryFile(val id: String) : HomeIntent
    data object ToggleActivityExpanded : HomeIntent

    /* Converting */
    data class ConvertIncomingFile(val format: ImageFormat) : HomeIntent
    data class ConvertHistoryFile(val id: String, val format: ImageFormat) : HomeIntent
    data class ToggleConvertPanel(val id: String) : HomeIntent
    data object SaveConvertedAgain : HomeIntent
    data object ConvertOnlyClicked : HomeIntent

    /* Connection detail */
    data object ToggleAdvanced : HomeIntent
    data object ToggleConnectionDetail : HomeIntent
    data object RetryDirectConnection : HomeIntent

    /* Chrome */
    data object DismissError : HomeIntent
    data object UserActivity : HomeIntent
    data object AboutClicked : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data object NavigateToAbout : HomeEffect
    data object NavigateToConvert : HomeEffect
}
