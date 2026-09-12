package com.share.app.domain.session

import com.share.app.domain.model.SessionRole
import com.share.app.domain.model.WebRtcDiagnostics
import com.share.app.domain.model.WebRtcStatus
import com.share.app.domain.policy.ConnectionPolicy
import com.share.app.domain.repository.SignalingMessage
import com.share.app.domain.repository.SignalingRepository
import com.share.app.domain.webrtc.ChannelMessage
import com.share.app.domain.webrtc.DataChannelPort
import com.share.app.domain.webrtc.PeerConnectionFactoryPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class TransportInputs(
    val sessionCode: String = "",
    val role: SessionRole = SessionRole.HOST,
    /** Both peers present and the session live. */
    val active: Boolean = false,
    /** The realtime backend is reachable, so signalling can actually flow. */
    val signalingOnline: Boolean = false,
    /** A relay path exists, which softens the final failure message. */
    val fallbackAvailable: Boolean = false,
)

internal data class TransportState(
    val status: WebRtcStatus = WebRtcStatus.IDLE,
    val error: String? = null,
    val ready: Boolean = false,
    val exhausted: Boolean = false,
    val diagnostics: WebRtcDiagnostics = WebRtcDiagnostics(timeoutMs = ConnectionPolicy.CONNECT_TIMEOUT_MS),
)

/**
 * Owns the lifetime of one [WebRtcLink] for the current session: starts it when
 * both peers are present, replaces it when the session or role changes or a
 * restart is asked for, and maps it onto observable state.
 *
 * Must only be called on the engine's confined dispatcher.
 */
internal class WebRtcTransport(
    private val scope: CoroutineScope,
    private val peerConnectionFactory: PeerConnectionFactoryPort,
    private val signalingRepository: SignalingRepository,
    private val onMessage: (ChannelMessage) -> Unit,
    private val onClosed: () -> Unit,
) {
    private data class EffectKey(val restartToken: Int, val role: SessionRole, val running: Boolean, val code: String)

    private var inputs = TransportInputs()
    private var restartToken = 0
    private var effectKey: EffectKey? = null
    private var link: WebRtcLink? = null
    private var linkRole = SessionRole.HOST

    // What the live link reports. What callers see is derived in publish().
    private var linkStatus = WebRtcStatus.IDLE
    private var linkError: String? = null
    private var channelOpen = false
    private var attemptsSpent = false
    private var diagnostics = WebRtcDiagnostics(timeoutMs = ConnectionPolicy.CONNECT_TIMEOUT_MS)

    private val _state = MutableStateFlow(TransportState())
    val state: StateFlow<TransportState> = _state.asStateFlow()

    fun update(next: TransportInputs) {
        inputs = next
        reconcile()
        publish()
    }

    /** Force a fresh attempt at the direct connection. */
    fun restart() {
        restartToken += 1
        reconcile()
        publish()
    }

    fun channel(): DataChannelPort? = link?.channel()

    private val running: Boolean
        get() = inputs.active && inputs.sessionCode.isNotEmpty() && inputs.signalingOnline

    private fun reconcile() {
        val key = EffectKey(restartToken, inputs.role, running, inputs.sessionCode)
        if (key == effectKey) return
        effectKey = key

        link?.let { old ->
            link = null
            old.stop(notifyPeer = true, clearSignaling = linkRole == SessionRole.HOST)
            linkStatus = WebRtcStatus.IDLE
            linkError = null
            attemptsSpent = false
            if (channelOpen) {
                channelOpen = false
            }
            onClosed()
        }

        if (!key.running) return

        val code = inputs.sessionCode
        val role = inputs.role
        lateinit var created: WebRtcLink
        created = WebRtcLink(
            role = role,
            peerConnectionFactory = peerConnectionFactory,
            signaling = object : SignalingPort {
                override suspend fun send(message: SignalingMessage) = signalingRepository.send(code, role, message)
                override fun messages() = signalingRepository.observe(code, role)
                override suspend fun clear() = signalingRepository.clear(code)
            },
            isSignalingOnline = { inputs.signalingOnline },
            fallbackAvailable = inputs.fallbackAvailable,
            scope = scope,
            callbacks = object : WebRtcLinkCallbacks {
                override fun onStatus(status: WebRtcStatus, message: String?, exhausted: Boolean) {
                    if (link !== created) return
                    linkStatus = status
                    linkError = message
                    attemptsSpent = exhausted
                    publish()
                }

                override fun onDiagnostics(patch: (WebRtcDiagnostics) -> WebRtcDiagnostics) {
                    if (link !== created) return
                    diagnostics = patch(diagnostics)
                    publish()
                }

                override fun onChannelStateChange(open: Boolean) {
                    if (link !== created) return
                    channelOpen = open
                    if (!open) onClosed()
                    publish()
                }

                override fun onMessage(message: ChannelMessage) = this@WebRtcTransport.onMessage(message)
            },
        )
        link = created
        linkRole = role
        created.start()
    }

    private fun publish() {
        val isRunning = running
        _state.value = TransportState(
            status = if (isRunning) linkStatus else WebRtcStatus.IDLE,
            error = when {
                isRunning -> linkError
                inputs.active && inputs.sessionCode.isNotEmpty() && !inputs.signalingOnline ->
                    "Waiting for the signalling channel."
                else -> null
            },
            ready = isRunning && channelOpen,
            exhausted = isRunning && attemptsSpent,
            diagnostics = diagnostics,
        )
    }
}
