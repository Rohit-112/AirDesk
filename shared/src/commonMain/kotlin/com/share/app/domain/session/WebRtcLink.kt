package com.share.app.domain.session

import com.share.app.domain.model.SessionRole
import com.share.app.domain.model.WebRtcDiagnostics
import com.share.app.domain.model.WebRtcStatus
import com.share.app.domain.policy.ConnectionPolicy
import com.share.app.domain.policy.FailureKind
import com.share.app.domain.repository.SignalingMessage
import com.share.app.domain.webrtc.ChannelMessage
import com.share.app.domain.webrtc.ChannelState
import com.share.app.domain.webrtc.DataChannelListener
import com.share.app.domain.webrtc.DataChannelPort
import com.share.app.domain.webrtc.IceCandidateData
import com.share.app.domain.webrtc.PeerConnectionFactoryPort
import com.share.app.domain.webrtc.PeerConnectionListener
import com.share.app.domain.webrtc.PeerConnectionPort
import com.share.app.domain.webrtc.PeerState
import com.share.app.domain.webrtc.SdpType
import com.share.app.util.AppLog
import com.share.app.util.currentTimeMillis
import com.share.app.util.suspendRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.random.Random

/** The signalling transport one link needs, narrowed to what it actually calls. */
internal interface SignalingPort {
    suspend fun send(message: SignalingMessage)
    fun messages(): Flow<SignalingMessage>
    suspend fun clear()

    /** Empties this side's own inbox, leaving anything the peer is waiting on. */
    suspend fun clearOwnInbox()
}

internal interface WebRtcLinkCallbacks {
    fun onStatus(status: WebRtcStatus, message: String?, exhausted: Boolean)
    fun onDiagnostics(patch: (WebRtcDiagnostics) -> WebRtcDiagnostics)
    fun onChannelStateChange(open: Boolean)

    /** Called on the native thread that received the frame, in arrival order. */
    fun onMessage(message: ChannelMessage)
}

/**
 * One peer connection's lifetime for one session: handshake, offer and answer,
 * candidate exchange, recovery grace, timeouts and bounded retries.
 *
 * A port of the web client's `createWebRtcSession`, speaking the same
 * signalling protocol so either end can be a browser. Every handler runs on
 * [scope], which must be confined to a single thread; that is what stands in
 * for the browser's event loop. Native callbacks are hopped onto it and
 * ignored if they belong to an attempt that has since been torn down.
 */
internal class WebRtcLink(
    private val role: SessionRole,
    private val peerConnectionFactory: PeerConnectionFactoryPort,
    private val signaling: SignalingPort,
    private val isSignalingOnline: () -> Boolean,
    private val fallbackAvailable: Boolean,
    private val scope: CoroutineScope,
    private val callbacks: WebRtcLinkCallbacks,
    private val clock: () -> Long = ::currentTimeMillis,
    private val newConnectionId: () -> String = ::defaultConnectionId,
) {
    private var stopped = false
    private var started = false
    private var completedAttempts = 0

    // Per-attempt state.
    private var peer: PeerConnectionPort? = null
    private var channel: DataChannelPort? = null
    private var signalingJob: Job? = null
    private var connectionId: String? = null
    private var handshakeAcked = false
    private var offerSent = false
    private val pendingCandidates = mutableListOf<IceCandidateData>()
    private var everConnected = false
    private var localCandidateCount = 0
    private var remoteCandidateCount = 0
    private var candidateErrorCount = 0

    /** Bumped whenever an attempt opens or closes; stale callbacks compare against it. */
    @Volatile
    private var attemptToken = 0

    private var connectTimeoutJob: Job? = null
    private var handshakeJob: Job? = null
    private var recoveryJob: Job? = null
    private var retryJob: Job? = null

    /** Begin connecting. Safe to call repeatedly; only the first call starts. */
    fun start() {
        if (started || stopped) return
        started = true
        openAttempt()
    }

    /** Tear everything down. The link cannot be started again afterwards. */
    fun stop(notifyPeer: Boolean, clearSignaling: Boolean) {
        if (stopped) return
        stopped = true
        retryJob?.cancel()
        retryJob = null
        closeAttempt(notifyPeer)
        if (clearSignaling) {
            scope.launch { suspendRunCatching { signaling.clear() } }
        }
        callbacks.onStatus(WebRtcStatus.IDLE, null, exhausted = false)
    }

    fun channel(): DataChannelPort? = channel

    private fun clearAttemptTimers() {
        connectTimeoutJob?.cancel()
        handshakeJob?.cancel()
        recoveryJob?.cancel()
        connectTimeoutJob = null
        handshakeJob = null
        recoveryJob = null
    }

    private fun emitStatus(status: WebRtcStatus, message: String?, exhausted: Boolean = false) {
        if (stopped && status != WebRtcStatus.IDLE) return
        callbacks.onStatus(status, message, exhausted)
    }

    private fun detachPeerConnection() {
        channel?.let { current ->
            current.setListener(null)
            runCatching { current.close() }
        }
        channel = null
        peer?.let { current ->
            current.setListener(null)
            runCatching { current.close() }
        }
        peer = null
    }

    private fun closeAttempt(notifyPeer: Boolean) {
        clearAttemptTimers()

        val id = connectionId
        if (notifyPeer && id != null) {
            scope.launch { suspendRunCatching { signaling.send(SignalingMessage.Disconnect(id)) } }
        }

        signalingJob?.cancel()
        signalingJob = null
        attemptToken += 1

        detachPeerConnection()

        connectionId = null
        handshakeAcked = false
        offerSent = false
        pendingCandidates.clear()
        everConnected = false
        callbacks.onChannelStateChange(false)
    }

    /**
     * [requireLiveAttempt] is what stops a second failure for the same attempt
     * - a timeout firing while a setup call is still in flight - from burning a
     * retry and starting a second attempt in parallel: closeAttempt() has
     * already cleared [peer] by then. Only a peer connection that could not be
     * created at all fails without one.
     */
    private fun failAttempt(kind: FailureKind, requireLiveAttempt: Boolean = true) {
        if (stopped) return
        if (requireLiveAttempt && peer == null) return

        val wasConnected = everConnected
        closeAttempt(notifyPeer = false)
        completedAttempts += 1

        val message = ConnectionPolicy.describeFailure(kind, completedAttempts, fallbackAvailable)
        callbacks.onDiagnostics { it.copy(lastFailureReason = message, lastStateUpdate = clock()) }

        if (!ConnectionPolicy.shouldRetry(completedAttempts)) {
            emitStatus(WebRtcStatus.FAILED, message, exhausted = true)
            return
        }

        emitStatus(WebRtcStatus.CONNECTING, message)
        // A link that worked once is worth picking up again quickly.
        val delayMs = if (wasConnected) {
            ConnectionPolicy.retryDelayMs(2)
        } else {
            ConnectionPolicy.retryDelayMs(completedAttempts + 1)
        }
        retryJob = scope.launch {
            delay(delayMs)
            retryJob = null
            if (!stopped) openAttempt()
        }
    }

    /**
     * A dropped route is given a grace period before it counts as a failure,
     * because ICE reconnects on its own far more often than not.
     */
    private fun beginRecoveryWindow() {
        if (recoveryJob != null || stopped) return
        emitStatus(WebRtcStatus.CONNECTING, "Connection dropped, trying to recover...")
        recoveryJob = scope.launch {
            delay(ConnectionPolicy.ICE_RECOVERY_GRACE_MS)
            recoveryJob = null
            val state = peer?.iceConnectionState
            if (state != null && ConnectionPolicy.isConnected(state)) return@launch
            failAttempt(if (everConnected) FailureKind.DROPPED else FailureKind.ICE)
        }
    }

    private fun endRecoveryWindow() {
        val wasRecovering = recoveryJob != null
        recoveryJob?.cancel()
        recoveryJob = null
        if (wasRecovering && channel?.state == ChannelState.OPEN) {
            emitStatus(WebRtcStatus.CONNECTED, null)
        }
    }

    private suspend fun addRemoteCandidate(candidate: IceCandidateData) {
        val connection = peer ?: return
        if (candidate.sdp.isEmpty()) return
        if (!connection.hasRemoteDescription) {
            pendingCandidates += candidate
            return
        }
        // Stale candidates from a previous attempt are expected to fail here.
        suspendRunCatching { connection.addIceCandidate(candidate) }
    }

    private suspend fun flushPendingCandidates() {
        val connection = peer ?: return
        if (!connection.hasRemoteDescription || pendingCandidates.isEmpty()) return
        val queued = pendingCandidates.toList()
        pendingCandidates.clear()
        queued.forEach { addRemoteCandidate(it) }
    }

    private fun attachChannel(next: DataChannelPort, token: Int) {
        channel = next
        callbacks.onDiagnostics { it.copy(dataChannelState = next.state.label) }

        next.setListener(object : DataChannelListener {
            override fun onOpen() = onEvent(token) { handleChannelOpen() }

            override fun onClose() = onEvent(token) {
                callbacks.onDiagnostics { it.copy(dataChannelState = "closed", lastStateUpdate = clock()) }
                callbacks.onChannelStateChange(false)
                // The peer may just be renegotiating; give the route a chance first.
                beginRecoveryWindow()
            }

            override fun onMessage(message: ChannelMessage) {
                if (token == attemptToken) callbacks.onMessage(message)
            }
        })

        // The remote side may already have opened it before we got here.
        if (next.state == ChannelState.OPEN) handleChannelOpen()
    }

    private fun handleChannelOpen() {
        clearAttemptTimers()
        everConnected = true
        callbacks.onDiagnostics { it.copy(dataChannelState = "open", lastStateUpdate = clock()) }
        callbacks.onChannelStateChange(true)
        emitStatus(WebRtcStatus.CONNECTED, null)
    }

    private suspend fun sendOffer() {
        val connection = peer ?: return
        val id = connectionId ?: return
        if (offerSent) return
        offerSent = true
        suspendRunCatching {
            val sdp = connection.createOffer()
            connection.setLocalDescription(SdpType.OFFER, sdp)
            signaling.send(SignalingMessage.Offer(sdp, id))
        }.onFailure { error ->
            AppLog.w(error) { "Creating the offer failed" }
            if (peer === connection) failAttempt(FailureKind.SETUP)
        }
    }

    private suspend fun sendHandshake(attempt: Int) {
        val id = connectionId
        if (stopped || role != SessionRole.HOST || id == null || handshakeAcked) return

        if (!isSignalingOnline()) {
            scheduleHandshake(attempt)
            return
        }

        suspendRunCatching { signaling.send(SignalingMessage.Handshake(id)) }

        if (handshakeAcked || connectionId != id || stopped) return

        if (attempt >= ConnectionPolicy.HANDSHAKE_MAX_ATTEMPTS) {
            // The peer never acknowledged. Offer anyway - an older client may be
            // waiting for one without taking part in the handshake.
            sendOffer()
            return
        }

        scheduleHandshake(attempt + 1)
    }

    private fun scheduleHandshake(attempt: Int) {
        handshakeJob = scope.launch {
            delay(ConnectionPolicy.HANDSHAKE_RETRY_MS)
            handshakeJob = null
            sendHandshake(attempt)
        }
    }

    private suspend fun handleSignalingMessage(message: SignalingMessage) {
        val connection = peer ?: return
        if (stopped) return

        when (message) {
            is SignalingMessage.Handshake -> {
                if (role != SessionRole.GUEST) return
                if (connectionId != null && connectionId != message.connectionId) {
                    // The host restarted; follow it onto the new connection.
                    pendingCandidates.clear()
                }
                connectionId = message.connectionId
                suspendRunCatching { signaling.send(SignalingMessage.HandshakeAck(message.connectionId)) }
            }

            is SignalingMessage.HandshakeAck -> {
                val id = connectionId
                if (role != SessionRole.HOST || id == null || message.connectionId != id) return
                handshakeAcked = true
                handshakeJob?.cancel()
                handshakeJob = null
                sendOffer()
            }

            is SignalingMessage.Offer -> {
                if (role != SessionRole.GUEST) return
                val id = message.connectionId ?: connectionId ?: return
                connectionId = id
                suspendRunCatching {
                    connection.setRemoteDescription(SdpType.OFFER, message.sdp)
                    val answer = connection.createAnswer()
                    connection.setLocalDescription(SdpType.ANSWER, answer)
                    signaling.send(SignalingMessage.Answer(answer, id))
                    flushPendingCandidates()
                }.onFailure { error ->
                    AppLog.w(error) { "Answering the offer failed" }
                    if (peer === connection) failAttempt(FailureKind.SETUP)
                }
            }

            is SignalingMessage.Answer -> {
                val id = connectionId
                if (role != SessionRole.HOST || id == null) return
                if (message.connectionId != null && message.connectionId != id) return
                suspendRunCatching {
                    connection.setRemoteDescription(SdpType.ANSWER, message.sdp)
                    flushPendingCandidates()
                }.onFailure { error ->
                    AppLog.w(error) { "Applying the answer failed" }
                    if (peer === connection) failAttempt(FailureKind.SETUP)
                }
            }

            is SignalingMessage.Candidate -> {
                val id = connectionId
                if (message.connectionId != null && id != null && message.connectionId != id) return
                if (id == null && message.connectionId != null) connectionId = message.connectionId
                remoteCandidateCount += 1
                val count = remoteCandidateCount
                callbacks.onDiagnostics { it.copy(remoteCandidateCount = count, lastStateUpdate = clock()) }
                addRemoteCandidate(IceCandidateData(message.sdp, message.sdpMid, message.sdpMLineIndex))
            }

            is SignalingMessage.Disconnect -> {
                val id = connectionId
                if (message.connectionId != null && id != null && message.connectionId != id) return
                // The peer left deliberately. Closing the attempt also drops the
                // signalling listener, so a new attempt has to be opened - or
                // their next handshake would never be heard and this side would
                // sit idle for good.
                closeAttempt(notifyPeer = false)
                emitStatus(WebRtcStatus.IDLE, null)
                retryJob?.cancel()
                retryJob = scope.launch {
                    delay(ConnectionPolicy.retryDelayMs(2))
                    retryJob = null
                    if (!stopped) openAttempt()
                }
            }
        }
    }

    private fun handlePeerState(state: PeerState) {
        when {
            ConnectionPolicy.isConnected(state) -> {
                everConnected = true
                endRecoveryWindow()
            }
            ConnectionPolicy.isRecoverable(state) -> beginRecoveryWindow()
            ConnectionPolicy.isFatal(state) -> failAttempt(if (everConnected) FailureKind.DROPPED else FailureKind.ICE)
        }
    }

    /** Runs [block] on the link's thread, unless the attempt it belongs to is gone. */
    private fun onEvent(token: Int, block: suspend () -> Unit) {
        scope.launch {
            if (token != attemptToken || stopped) return@launch
            block()
        }
    }

    private fun openAttempt() {
        if (stopped) return

        attemptToken += 1
        val token = attemptToken

        val connection = try {
            peerConnectionFactory.create()
        } catch (error: Throwable) {
            AppLog.e(error) { "Creating the peer connection failed" }
            failAttempt(FailureKind.SETUP, requireLiveAttempt = false)
            return
        }

        peer = connection
        connectionId = if (role == SessionRole.HOST) newConnectionId() else null
        handshakeAcked = role != SessionRole.HOST
        offerSent = false
        pendingCandidates.clear()
        everConnected = false
        localCandidateCount = 0
        remoteCandidateCount = 0
        candidateErrorCount = 0

        val iceServers = connection.iceServerUrls
        val hasTurn = iceServers.any { it.startsWith("turn:") || it.startsWith("turns:") }
        val startedAt = clock()

        callbacks.onDiagnostics {
            WebRtcDiagnostics(
                connectionState = connection.connectionState.label,
                iceConnectionState = connection.iceConnectionState.label,
                hasTurn = hasTurn,
                iceServers = iceServers,
                startedAt = startedAt,
                timeoutMs = ConnectionPolicy.CONNECT_TIMEOUT_MS,
                lastStateUpdate = startedAt,
            )
        }

        emitStatus(
            WebRtcStatus.CONNECTING,
            if (completedAttempts > 0) "Reconnecting file sharing..." else null,
        )

        connectTimeoutJob = scope.launch {
            delay(ConnectionPolicy.CONNECT_TIMEOUT_MS)
            connectTimeoutJob = null
            if (channel?.state == ChannelState.OPEN) return@launch
            failAttempt(FailureKind.TIMEOUT)
        }

        connection.setListener(object : PeerConnectionListener {
            override fun onIceCandidate(candidate: IceCandidateData) = onEvent(token) {
                val id = connectionId ?: return@onEvent
                localCandidateCount += 1
                val count = localCandidateCount
                callbacks.onDiagnostics { it.copy(localCandidateCount = count, lastStateUpdate = clock()) }
                suspendRunCatching {
                    signaling.send(
                        SignalingMessage.Candidate(
                            sdp = candidate.sdp,
                            sdpMid = candidate.sdpMid ?: "",
                            sdpMLineIndex = candidate.sdpMLineIndex ?: 0,
                            connectionId = id,
                        ),
                    )
                }
            }

            override fun onConnectionStateChange(state: PeerState) = onEvent(token) {
                callbacks.onDiagnostics { it.copy(connectionState = state.label, lastStateUpdate = clock()) }
                handlePeerState(state)
            }

            override fun onIceConnectionStateChange(state: PeerState) = onEvent(token) {
                callbacks.onDiagnostics { it.copy(iceConnectionState = state.label, lastStateUpdate = clock()) }
                handlePeerState(state)
            }

            override fun onIceGatheringStateChange(state: String) = onEvent(token) {
                callbacks.onDiagnostics { it.copy(iceGatheringState = state) }
            }

            override fun onSignalingStateChange(state: String) = onEvent(token) {
                callbacks.onDiagnostics { it.copy(signalingState = state) }
            }

            override fun onIceCandidateError(detail: String) = onEvent(token) {
                // Never fatal on its own: one server failing is normal.
                candidateErrorCount += 1
                val count = candidateErrorCount
                val now = clock()
                callbacks.onDiagnostics {
                    it.copy(
                        candidateErrorCount = count,
                        lastCandidateError = detail.ifEmpty { "Route error" },
                        lastCandidateErrorAt = now,
                        lastStateUpdate = now,
                    )
                }
            }

            override fun onDataChannel(channel: DataChannelPort) = onEvent(token) {
                if (role == SessionRole.GUEST) attachChannel(channel, token)
            }
        })

        if (role == SessionRole.HOST) {
            val created = try {
                connection.createDataChannel(DATA_CHANNEL_LABEL, ordered = true)
            } catch (error: Throwable) {
                AppLog.e(error) { "Creating the data channel failed" }
                failAttempt(FailureKind.SETUP)
                return
            }
            attachChannel(created, token)
        }

        signalingJob = scope.launch {
            signaling.messages().collect { message ->
                if (token == attemptToken) handleSignalingMessage(message)
            }
        }

        if (role == SessionRole.HOST) {
            scope.launch {
                // Only our own leftovers. The guest may already have answered
                // into its outbox, and wiping that would lose the reply.
                suspendRunCatching { signaling.clearOwnInbox() }
                if (token == attemptToken) sendHandshake(1)
            }
        }
    }

    private companion object {
        const val DATA_CHANNEL_LABEL = "fileTransfer"

        fun defaultConnectionId(): String {
            val suffix = (1..8).map { "abcdefghijklmnopqrstuvwxyz0123456789"[Random.nextInt(36)] }.joinToString("")
            return "${currentTimeMillis()}-$suffix"
        }
    }
}
