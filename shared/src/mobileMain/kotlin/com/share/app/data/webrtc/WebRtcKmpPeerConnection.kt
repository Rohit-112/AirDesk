package com.share.app.data.webrtc

import com.share.app.config.AppConfig
import com.share.app.domain.webrtc.DataChannelPort
import com.share.app.domain.webrtc.IceCandidateData
import com.share.app.domain.webrtc.PeerConnectionFactoryPort
import com.share.app.domain.webrtc.PeerConnectionListener
import com.share.app.domain.webrtc.PeerConnectionPort
import com.share.app.domain.webrtc.PeerState
import com.share.app.domain.webrtc.SdpType
import com.shepeliev.webrtckmp.DataChannel
import com.shepeliev.webrtckmp.IceCandidate
import com.shepeliev.webrtckmp.IceConnectionState
import com.shepeliev.webrtckmp.IceServer
import com.shepeliev.webrtckmp.IceTransportPolicy
import com.shepeliev.webrtckmp.OfferAnswerOptions
import com.shepeliev.webrtckmp.PeerConnection
import com.shepeliev.webrtckmp.PeerConnectionState
import com.shepeliev.webrtckmp.RtcConfiguration
import com.shepeliev.webrtckmp.SessionDescription
import com.shepeliev.webrtckmp.SessionDescriptionType
import com.shepeliev.webrtckmp.onConnectionStateChange
import com.shepeliev.webrtckmp.onDataChannel
import com.shepeliev.webrtckmp.onIceCandidate
import com.shepeliev.webrtckmp.onIceConnectionStateChange
import com.shepeliev.webrtckmp.onIceGatheringState
import com.shepeliev.webrtckmp.onSignalingStateChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * webrtc-kmp always sends binary frames and drops the frame type on receive,
 * but the web client needs text frames for control messages. Each platform
 * therefore drives its native data channel directly.
 */
internal expect fun createNativeDataChannel(channel: DataChannel): DataChannelPort

class WebRtcKmpPeerConnectionFactory(private val config: AppConfig) : PeerConnectionFactoryPort {
    override fun create(): PeerConnectionPort {
        val iceServers = config.iceServers.map { IceServer(urls = it.urls, username = it.username, password = it.credential) }
        val connection = PeerConnection(
            RtcConfiguration(
                iceServers = iceServers,
                // Gather up front so the handshake is not waiting on network round trips.
                iceCandidatePoolSize = 4,
                iceTransportPolicy = if (config.relayOnly) IceTransportPolicy.Relay else IceTransportPolicy.All,
            ),
        )
        return WebRtcKmpPeerConnection(connection, iceServers.flatMap { it.urls })
    }
}

private class WebRtcKmpPeerConnection(
    private val connection: PeerConnection,
    override val iceServerUrls: List<String>,
) : PeerConnectionPort {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var listener: PeerConnectionListener? = null

    init {
        // Subscribed synchronously: the underlying flows do not replay.
        collect { connection.onIceCandidate.collect { listener?.onIceCandidate(IceCandidateData(it.candidate, it.sdpMid, it.sdpMLineIndex)) } }
        collect { connection.onConnectionStateChange.collect { listener?.onConnectionStateChange(it.toPeerState()) } }
        collect { connection.onIceConnectionStateChange.collect { listener?.onIceConnectionStateChange(it.toPeerState()) } }
        collect { connection.onIceGatheringState.collect { listener?.onIceGatheringStateChange(it.name.lowercase()) } }
        collect { connection.onSignalingStateChange.collect { listener?.onSignalingStateChange(it.name.lowercase()) } }
        collect { connection.onDataChannel.collect { listener?.onDataChannel(createNativeDataChannel(it)) } }
    }

    private fun collect(block: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { block() }
    }

    override val connectionState: PeerState
        get() = runCatching { connection.connectionState.toPeerState() }.getOrDefault(PeerState.CLOSED)

    override val iceConnectionState: PeerState
        get() = runCatching { connection.iceConnectionState.toPeerState() }.getOrDefault(PeerState.CLOSED)

    override val hasRemoteDescription: Boolean
        get() = connection.remoteDescription != null

    override fun setListener(listener: PeerConnectionListener?) {
        this.listener = listener
    }

    override fun createDataChannel(label: String, ordered: Boolean): DataChannelPort {
        val channel = connection.createDataChannel(label, ordered = ordered) ?: error("Creating the data channel failed")
        return createNativeDataChannel(channel)
    }

    override suspend fun createOffer(): String = connection.createOffer(OfferAnswerOptions()).sdp

    override suspend fun createAnswer(): String = connection.createAnswer(OfferAnswerOptions()).sdp

    override suspend fun setLocalDescription(type: SdpType, sdp: String) {
        connection.setLocalDescription(SessionDescription(type.toKmp(), sdp))
    }

    override suspend fun setRemoteDescription(type: SdpType, sdp: String) {
        connection.setRemoteDescription(SessionDescription(type.toKmp(), sdp))
    }

    override suspend fun addIceCandidate(candidate: IceCandidateData) {
        connection.addIceCandidate(
            IceCandidate(
                sdpMid = candidate.sdpMid ?: "",
                sdpMLineIndex = candidate.sdpMLineIndex ?: 0,
                candidate = candidate.sdp,
            ),
        )
    }

    override fun close() {
        listener = null
        scope.cancel()
        runCatching { connection.close() }
    }
}

private fun SdpType.toKmp(): SessionDescriptionType = when (this) {
    SdpType.OFFER -> SessionDescriptionType.Offer
    SdpType.ANSWER -> SessionDescriptionType.Answer
}

private fun PeerConnectionState.toPeerState(): PeerState = when (this) {
    PeerConnectionState.New -> PeerState.NEW
    PeerConnectionState.Connecting -> PeerState.CONNECTING
    PeerConnectionState.Connected -> PeerState.CONNECTED
    PeerConnectionState.Disconnected -> PeerState.DISCONNECTED
    PeerConnectionState.Failed -> PeerState.FAILED
    PeerConnectionState.Closed -> PeerState.CLOSED
}

private fun IceConnectionState.toPeerState(): PeerState = when (this) {
    IceConnectionState.New -> PeerState.NEW
    IceConnectionState.Checking -> PeerState.CHECKING
    IceConnectionState.Connected -> PeerState.CONNECTED
    IceConnectionState.Completed -> PeerState.COMPLETED
    IceConnectionState.Failed -> PeerState.FAILED
    IceConnectionState.Disconnected -> PeerState.DISCONNECTED
    IceConnectionState.Closed -> PeerState.CLOSED
    IceConnectionState.Count -> PeerState.UNKNOWN
}
