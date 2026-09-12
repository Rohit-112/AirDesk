package com.share.app.domain.model

/**
 * The one surface the UI is allowed to read.
 *
 * Mirrors the web client's `AppSession` contract: screens observe this and
 * send actions through the use cases, and never reach into Firebase, WebRTC or
 * storage directly. Adding a field is cheap; changing one means every screen
 * has to be checked.
 */
data class AppSessionState(
    /* ---- connection state ---- */
    val authStatus: AuthStatus = AuthStatus.AUTHENTICATING,
    val sessionStatus: SessionStatus = SessionStatus.DISCONNECTED,
    val role: SessionRole = SessionRole.HOST,
    val userId: String? = null,
    /** The six-digit pairing code. */
    val sessionCode: String = "",
    /** The other device is present in the session. */
    val peerOnline: Boolean = false,
    /** The user is entering someone else's code; their own session was released. */
    val joinIntent: Boolean = false,
    /** The realtime backend is reachable. */
    val backendConnected: Boolean = false,
    /** The end to end key exchange has completed. Text waits for this. */
    val secureChannelReady: Boolean = false,

    /* ---- messages ---- */
    /** A transient message about the last failed action. */
    val error: String? = null,
    /** A message about pairing specifically, shown next to the code entry. */
    val sessionError: String? = null,

    /* ---- payloads ---- */
    val incomingText: String? = null,
    val incomingFile: IncomingFile? = null,
    val outgoingTransfer: ActiveFileTransfer? = null,
    val incomingTransfer: ActiveFileTransfer? = null,

    /* ---- transfer capability ---- */
    /** A direct peer connection is open right now. */
    val fileTransferReady: Boolean = false,
    val transportMode: TransportMode = TransportMode.UNAVAILABLE,
    val webrtcStatus: WebRtcStatus = WebRtcStatus.IDLE,
    val webrtcError: String? = null,
    val webrtcDiagnostics: WebRtcDiagnostics = WebRtcDiagnostics(),
) {
    val isLinked: Boolean get() = sessionStatus == SessionStatus.CONNECTED && peerOnline
}
