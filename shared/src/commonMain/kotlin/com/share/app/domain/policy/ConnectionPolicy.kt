package com.share.app.domain.policy

import com.share.app.domain.webrtc.PeerState
import kotlin.math.max
import kotlin.math.min

/**
 * Every decision about when a peer connection is healthy, when it is worth
 * waiting and when it is worth restarting, as plain functions so it can be
 * reasoned about and tested without a network. Same numbers as the web client.
 */
object ConnectionPolicy {
    /** How long one connection attempt gets before it is written off. */
    const val CONNECT_TIMEOUT_MS = 12_000L

    /**
     * "disconnected" is not a failure. On mobile networks it fires constantly
     * and recovers on its own, so a dropped route gets a grace period first.
     */
    const val ICE_RECOVERY_GRACE_MS = 6_000L

    /** Attempts for one session, including the first. Worst case is ~40s. */
    const val MAX_CONNECT_ATTEMPTS = 3

    const val HANDSHAKE_RETRY_MS = 1_200L
    const val HANDSHAKE_MAX_ATTEMPTS = 8

    private const val RETRY_BASE_MS = 1_500L
    private const val RETRY_CEILING_MS = 6_000L

    const val WHY_DIRECT_FAILED =
        "The two devices cannot reach each other on these networks - common on mobile data."

    const val HOW_TO_FIX =
        "Put both devices on the same Wi-Fi, or share a hotspot from one of them."

    fun isConnected(state: PeerState): Boolean =
        state == PeerState.CONNECTED || state == PeerState.COMPLETED

    /** States that routinely heal without intervention. */
    fun isRecoverable(state: PeerState): Boolean = state == PeerState.DISCONNECTED

    fun isFatal(state: PeerState): Boolean = state == PeerState.FAILED

    /** Exponential backoff for attempt 2 onwards, capped so it stays responsive. */
    fun retryDelayMs(nextAttempt: Int): Long {
        val step = max(0, nextAttempt - 2)
        return min(RETRY_BASE_MS * (1L shl step), RETRY_CEILING_MS)
    }

    fun shouldRetry(completedAttempts: Int): Boolean = completedAttempts < MAX_CONNECT_ATTEMPTS

    /**
     * While retries remain this stays reassuring, because a retry usually
     * succeeds; only the final message should sound like a problem.
     */
    fun describeFailure(kind: FailureKind, completedAttempts: Int, fallbackAvailable: Boolean): String {
        val base = kind.message
        if (shouldRetry(completedAttempts)) {
            return "$base Retrying (attempt ${completedAttempts + 1} of $MAX_CONNECT_ATTEMPTS)..."
        }
        if (fallbackAvailable) {
            return "$base $WHY_DIRECT_FAILED Smaller files will still go through. " +
                "For anything larger: ${HOW_TO_FIX.lowercase()}"
        }
        return "$base $WHY_DIRECT_FAILED $HOW_TO_FIX"
    }
}

/**
 * What the user sees when something fails. Every one describes the effect -
 * sending is not ready - and none names the machinery underneath or which
 * part of it gave up.
 */
enum class FailureKind(val message: String) {
    TIMEOUT("Could not get ready to send in time."),
    ICE("Could not reach the other device."),
    CHANNEL("Sending stopped working."),
    SETUP("Could not get ready to send."),
    DROPPED("The connection to the other device dropped."),
}
