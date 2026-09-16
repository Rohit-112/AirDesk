package com.share.app.domain.model

import com.share.app.domain.media.ImagePreview

/** Anonymous sign-in progress. Nothing works until this is [READY]. */
enum class AuthStatus { AUTHENTICATING, READY, ERROR }

enum class SessionStatus { DISCONNECTED, CONNECTING, CONNECTED }

/** Whether this device is hosting the session or joined someone else's. */
enum class SessionRole(val key: String) {
    HOST("host"),
    GUEST("guest");

    val peer: SessionRole get() = if (this == HOST) GUEST else HOST
}

enum class WebRtcStatus { IDLE, CONNECTING, CONNECTED, FAILED }

/** Which route a file would take if sent this instant. */
enum class TransportMode { P2P, RELAY, UNAVAILABLE }

/**
 * Something that arrived and is waiting for the user to act on it.
 *
 * A file sent peer to peer is already on this device and referenced by
 * [localFileId]; a relayed one is still in Cloud Storage at [storagePath].
 */
data class IncomingFile(
    val name: String,
    val size: Long,
    /** Read from the bytes once they are here; the sender's claim until then. */
    val contentType: String,
    val storagePath: String? = null,
    val localFileId: String? = null,
    /** The picture itself, when the platform could draw one. */
    val preview: ImagePreview? = null,
)

data class ActiveFileTransfer(
    val name: String,
    val progress: Int,
    val transferredBytes: Long,
    /**
     * The total. Unknown on the receiving side unless the sender announced it,
     * which older clients do not.
     */
    val size: Long? = null,
)

/** Raw connection detail. Only a diagnostics panel should need this. */
data class WebRtcDiagnostics(
    val connectionState: String = "unknown",
    val iceConnectionState: String = "unknown",
    val iceGatheringState: String = "unknown",
    val signalingState: String = "unknown",
    val dataChannelState: String = "unknown",
    val localCandidateCount: Int = 0,
    val remoteCandidateCount: Int = 0,
    val candidateErrorCount: Int = 0,
    val lastCandidateError: String? = null,
    val lastCandidateErrorAt: Long? = null,
    val lastFailureReason: String? = null,
    val hasTurn: Boolean? = null,
    val iceServers: List<String> = emptyList(),
    val startedAt: Long? = null,
    val timeoutMs: Long = 0,
    val lastStateUpdate: Long? = null,
)

/** A file the user picked or dropped, read only when it is actually sent. */
class OutgoingFile(
    val name: String,
    val size: Long,
    val contentType: String,
    val readBytes: suspend () -> ByteArray,
)
