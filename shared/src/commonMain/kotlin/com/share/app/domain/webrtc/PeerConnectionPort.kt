package com.share.app.domain.webrtc

/**
 * The peer connection, narrowed to what the transport actually calls.
 *
 * Android and iOS implement it with webrtc-kmp, desktop with webrtc-java.
 * Callbacks may arrive on any thread; the transport hops them onto its own.
 */
interface PeerConnectionPort {
    val iceServerUrls: List<String>
    val connectionState: PeerState
    val iceConnectionState: PeerState
    val hasRemoteDescription: Boolean

    fun setListener(listener: PeerConnectionListener?)
    fun createDataChannel(label: String, ordered: Boolean): DataChannelPort
    suspend fun createOffer(): String
    suspend fun createAnswer(): String
    suspend fun setLocalDescription(type: SdpType, sdp: String)
    suspend fun setRemoteDescription(type: SdpType, sdp: String)
    suspend fun addIceCandidate(candidate: IceCandidateData)
    fun close()
}

fun interface PeerConnectionFactoryPort {
    fun create(): PeerConnectionPort
}

interface PeerConnectionListener {
    fun onIceCandidate(candidate: IceCandidateData)
    fun onConnectionStateChange(state: PeerState)
    fun onIceConnectionStateChange(state: PeerState)
    fun onIceGatheringStateChange(state: String)
    fun onSignalingStateChange(state: String)
    fun onIceCandidateError(detail: String)
    fun onDataChannel(channel: DataChannelPort)
}

/**
 * The web client tells control messages from file bytes by frame type, so the
 * port keeps text and binary frames distinct in both directions.
 */
interface DataChannelPort {
    val state: ChannelState
    val bufferedAmount: Long

    fun setListener(listener: DataChannelListener?)
    fun sendText(text: String): Boolean
    fun sendBinary(bytes: ByteArray): Boolean
    fun close()
}

interface DataChannelListener {
    fun onOpen()
    fun onClose()
    fun onMessage(message: ChannelMessage)
}

sealed interface ChannelMessage {
    data class Text(val value: String) : ChannelMessage
    class Binary(val bytes: ByteArray) : ChannelMessage
}

data class IceCandidateData(val sdp: String, val sdpMid: String?, val sdpMLineIndex: Int?)

enum class SdpType { OFFER, ANSWER }

enum class ChannelState(val label: String) {
    CONNECTING("connecting"),
    OPEN("open"),
    CLOSING("closing"),
    CLOSED("closed"),
}

/** Connection and ICE states share one vocabulary, as they do in the browser. */
enum class PeerState(val label: String) {
    NEW("new"),
    CHECKING("checking"),
    CONNECTING("connecting"),
    CONNECTED("connected"),
    COMPLETED("completed"),
    DISCONNECTED("disconnected"),
    FAILED("failed"),
    CLOSED("closed"),
    UNKNOWN("unknown"),
}
