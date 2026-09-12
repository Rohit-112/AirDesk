package com.share.app.data.webrtc

import com.share.app.config.AppConfig
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
import dev.onvoid.webrtc.CreateSessionDescriptionObserver
import dev.onvoid.webrtc.PeerConnectionFactory
import dev.onvoid.webrtc.PeerConnectionObserver
import dev.onvoid.webrtc.RTCAnswerOptions
import dev.onvoid.webrtc.RTCConfiguration
import dev.onvoid.webrtc.RTCDataChannel
import dev.onvoid.webrtc.RTCDataChannelBuffer
import dev.onvoid.webrtc.RTCDataChannelInit
import dev.onvoid.webrtc.RTCDataChannelObserver
import dev.onvoid.webrtc.RTCDataChannelState
import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCIceConnectionState
import dev.onvoid.webrtc.RTCIceGatheringState
import dev.onvoid.webrtc.RTCIceServer
import dev.onvoid.webrtc.RTCIceTransportPolicy
import dev.onvoid.webrtc.RTCOfferOptions
import dev.onvoid.webrtc.RTCPeerConnection
import dev.onvoid.webrtc.RTCPeerConnectionIceErrorEvent
import dev.onvoid.webrtc.RTCPeerConnectionState
import dev.onvoid.webrtc.RTCSdpType
import dev.onvoid.webrtc.RTCSessionDescription
import dev.onvoid.webrtc.RTCSignalingState
import dev.onvoid.webrtc.SetSessionDescriptionObserver
import dev.onvoid.webrtc.media.audio.AudioDeviceModule
import dev.onvoid.webrtc.media.audio.AudioLayer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Desktop WebRTC through webrtc-java, which keeps text and binary frames distinct. */
class WebRtcJavaPeerConnectionFactory(private val config: AppConfig) : PeerConnectionFactoryPort {

    // Data channels only: a dummy audio module keeps the factory off the sound devices.
    private val factory by lazy {
        runCatching { PeerConnectionFactory(AudioDeviceModule(AudioLayer.kDummyAudio)) }
            .getOrElse { PeerConnectionFactory() }
    }

    override fun create(): PeerConnectionPort {
        val rtcConfig = RTCConfiguration().apply {
            iceServers = config.iceServers.map { server ->
                RTCIceServer().apply {
                    urls = server.urls
                    username = server.username
                    password = server.credential
                }
            }
            iceTransportPolicy = if (config.relayOnly) RTCIceTransportPolicy.RELAY else RTCIceTransportPolicy.ALL
        }
        val observer = ForwardingObserver()
        val connection = factory.createPeerConnection(rtcConfig, observer)
            ?: error("Creating the peer connection failed")
        return WebRtcJavaPeerConnection(connection, observer, config.iceServers.flatMap { it.urls })
    }
}

private class ForwardingObserver : PeerConnectionObserver {
    @Volatile
    var listener: PeerConnectionListener? = null

    override fun onIceCandidate(candidate: RTCIceCandidate) {
        listener?.onIceCandidate(IceCandidateData(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex))
    }

    override fun onConnectionChange(state: RTCPeerConnectionState) {
        listener?.onConnectionStateChange(state.toPeerState())
    }

    override fun onIceConnectionChange(state: RTCIceConnectionState) {
        listener?.onIceConnectionStateChange(state.toPeerState())
    }

    override fun onIceGatheringChange(state: RTCIceGatheringState) {
        listener?.onIceGatheringStateChange(state.name.lowercase())
    }

    override fun onSignalingChange(state: RTCSignalingState) {
        listener?.onSignalingStateChange(state.name.lowercase())
    }

    override fun onIceCandidateError(event: RTCPeerConnectionIceErrorEvent) {
        val detail = listOfNotNull(
            event.errorText?.takeIf { it.isNotEmpty() },
            event.url?.takeIf { it.isNotEmpty() }?.let { "url=$it" },
            event.errorCode.takeIf { it != 0 }?.let { "code=$it" },
        ).joinToString(" ")
        listener?.onIceCandidateError(detail)
    }

    override fun onDataChannel(dataChannel: RTCDataChannel) {
        listener?.onDataChannel(JvmDataChannelPort(dataChannel))
    }
}

private class WebRtcJavaPeerConnection(
    private val connection: RTCPeerConnection,
    private val observer: ForwardingObserver,
    override val iceServerUrls: List<String>,
) : PeerConnectionPort {

    override val connectionState: PeerState
        get() = runCatching { connection.connectionState.toPeerState() }.getOrDefault(PeerState.CLOSED)

    override val iceConnectionState: PeerState
        get() = runCatching { connection.iceConnectionState.toPeerState() }.getOrDefault(PeerState.CLOSED)

    override val hasRemoteDescription: Boolean
        get() = runCatching { connection.remoteDescription != null }.getOrDefault(false)

    override fun setListener(listener: PeerConnectionListener?) {
        observer.listener = listener
    }

    override fun createDataChannel(label: String, ordered: Boolean): DataChannelPort {
        val init = RTCDataChannelInit().apply { this.ordered = ordered }
        return JvmDataChannelPort(connection.createDataChannel(label, init))
    }

    override suspend fun createOffer(): String = suspendCancellableCoroutine { continuation ->
        connection.createOffer(RTCOfferOptions(), sessionObserver(continuation))
    }

    override suspend fun createAnswer(): String = suspendCancellableCoroutine { continuation ->
        connection.createAnswer(RTCAnswerOptions(), sessionObserver(continuation))
    }

    override suspend fun setLocalDescription(type: SdpType, sdp: String) = suspendCancellableCoroutine { continuation ->
        connection.setLocalDescription(RTCSessionDescription(type.toRtc(), sdp), setObserver(continuation))
    }

    override suspend fun setRemoteDescription(type: SdpType, sdp: String) = suspendCancellableCoroutine { continuation ->
        connection.setRemoteDescription(RTCSessionDescription(type.toRtc(), sdp), setObserver(continuation))
    }

    override suspend fun addIceCandidate(candidate: IceCandidateData) {
        connection.addIceCandidate(RTCIceCandidate(candidate.sdpMid ?: "", candidate.sdpMLineIndex ?: 0, candidate.sdp))
    }

    override fun close() {
        observer.listener = null
        runCatching { connection.close() }
    }

    private fun sessionObserver(continuation: kotlinx.coroutines.CancellableContinuation<String>) =
        object : CreateSessionDescriptionObserver {
            override fun onSuccess(description: RTCSessionDescription) {
                if (continuation.isActive) continuation.resume(description.sdp)
            }

            override fun onFailure(error: String) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
            }
        }

    private fun setObserver(continuation: kotlinx.coroutines.CancellableContinuation<Unit>) =
        object : SetSessionDescriptionObserver {
            override fun onSuccess() {
                if (continuation.isActive) continuation.resume(Unit)
            }

            override fun onFailure(error: String) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
            }
        }
}

private class JvmDataChannelPort(private val native: RTCDataChannel) : DataChannelPort {

    @Volatile
    private var listener: DataChannelListener? = null

    @Volatile
    private var disposed = false

    init {
        native.registerObserver(object : RTCDataChannelObserver {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                if (disposed) return
                when (runCatching { native.state }.getOrNull()) {
                    RTCDataChannelState.OPEN -> listener?.onOpen()
                    RTCDataChannelState.CLOSED -> {
                        listener?.onClose()
                        dispose()
                    }
                    else -> Unit
                }
            }

            override fun onMessage(buffer: RTCDataChannelBuffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                listener?.onMessage(if (buffer.binary) ChannelMessage.Binary(bytes) else ChannelMessage.Text(bytes.decodeToString()))
            }
        })
    }

    override val state: ChannelState
        get() {
            if (disposed) return ChannelState.CLOSED
            return when (runCatching { native.state }.getOrNull()) {
                RTCDataChannelState.CONNECTING -> ChannelState.CONNECTING
                RTCDataChannelState.OPEN -> ChannelState.OPEN
                RTCDataChannelState.CLOSING -> ChannelState.CLOSING
                else -> ChannelState.CLOSED
            }
        }

    override val bufferedAmount: Long
        get() = if (disposed) 0L else runCatching { native.bufferedAmount }.getOrDefault(0L)

    override fun setListener(listener: DataChannelListener?) {
        this.listener = listener
    }

    override fun sendText(text: String): Boolean = send(text.encodeToByteArray(), binary = false)

    override fun sendBinary(bytes: ByteArray): Boolean = send(bytes, binary = true)

    private fun send(bytes: ByteArray, binary: Boolean): Boolean {
        if (disposed) return false
        return runCatching { native.send(RTCDataChannelBuffer(ByteBuffer.wrap(bytes), binary)) }.isSuccess
    }

    override fun close() {
        listener = null
        if (!disposed) runCatching { native.close() }
    }

    @Synchronized
    private fun dispose() {
        if (disposed) return
        disposed = true
        runCatching { native.unregisterObserver() }
        runCatching { native.dispose() }
    }
}

private fun SdpType.toRtc(): RTCSdpType = when (this) {
    SdpType.OFFER -> RTCSdpType.OFFER
    SdpType.ANSWER -> RTCSdpType.ANSWER
}

private fun RTCPeerConnectionState.toPeerState(): PeerState = when (this) {
    RTCPeerConnectionState.NEW -> PeerState.NEW
    RTCPeerConnectionState.CONNECTING -> PeerState.CONNECTING
    RTCPeerConnectionState.CONNECTED -> PeerState.CONNECTED
    RTCPeerConnectionState.DISCONNECTED -> PeerState.DISCONNECTED
    RTCPeerConnectionState.FAILED -> PeerState.FAILED
    RTCPeerConnectionState.CLOSED -> PeerState.CLOSED
}

private fun RTCIceConnectionState.toPeerState(): PeerState = when (this) {
    RTCIceConnectionState.NEW -> PeerState.NEW
    RTCIceConnectionState.CHECKING -> PeerState.CHECKING
    RTCIceConnectionState.CONNECTED -> PeerState.CONNECTED
    RTCIceConnectionState.COMPLETED -> PeerState.COMPLETED
    RTCIceConnectionState.FAILED -> PeerState.FAILED
    RTCIceConnectionState.DISCONNECTED -> PeerState.DISCONNECTED
    RTCIceConnectionState.CLOSED -> PeerState.CLOSED
}
