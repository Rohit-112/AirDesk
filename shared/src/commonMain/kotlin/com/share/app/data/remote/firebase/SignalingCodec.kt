package com.share.app.data.remote.firebase

import com.share.app.domain.repository.SignalingMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The JSON envelope the web client writes with `JSON.stringify` and reads with
 * `JSON.parse`. Absent fields are omitted rather than written as null.
 */
internal object SignalingCodec {

    @Serializable
    private data class CandidateDto(
        val sdp: String? = null,
        val sdpMid: String? = null,
        val sdpMLineIndex: Int? = null,
    )

    @Serializable
    private data class EnvelopeDto(
        val type: String,
        val connectionId: String? = null,
        val sdp: String? = null,
        val candidate: CandidateDto? = null,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
    }

    fun encode(message: SignalingMessage): String {
        val envelope = when (message) {
            is SignalingMessage.Handshake -> EnvelopeDto(type = "handshake", connectionId = message.connectionId)
            is SignalingMessage.HandshakeAck -> EnvelopeDto(type = "handshakeAck", connectionId = message.connectionId)
            is SignalingMessage.Offer -> EnvelopeDto(type = "offer", sdp = message.sdp, connectionId = message.connectionId)
            is SignalingMessage.Answer -> EnvelopeDto(type = "answer", sdp = message.sdp, connectionId = message.connectionId)
            is SignalingMessage.Candidate -> EnvelopeDto(
                type = "candidate",
                candidate = CandidateDto(message.sdp, message.sdpMid, message.sdpMLineIndex),
                connectionId = message.connectionId,
            )
            is SignalingMessage.Disconnect -> EnvelopeDto(type = "disconnect", connectionId = message.connectionId)
        }
        return json.encodeToString(EnvelopeDto.serializer(), envelope)
    }

    fun decode(raw: String): SignalingMessage? {
        val envelope = runCatching { json.decodeFromString(EnvelopeDto.serializer(), raw) }.getOrNull() ?: return null
        val connectionId = envelope.connectionId?.takeIf { it.isNotBlank() }
        return when (envelope.type) {
            "handshake" -> connectionId?.let(SignalingMessage::Handshake)
            "handshakeAck" -> connectionId?.let(SignalingMessage::HandshakeAck)
            "offer" -> envelope.sdp?.takeIf { it.isNotEmpty() }?.let { SignalingMessage.Offer(it, connectionId) }
            "answer" -> envelope.sdp?.takeIf { it.isNotEmpty() }?.let { SignalingMessage.Answer(it, connectionId) }
            "candidate" -> {
                val candidate = envelope.candidate ?: return null
                val sdp = candidate.sdp?.takeIf { it.isNotEmpty() } ?: return null
                SignalingMessage.Candidate(sdp, candidate.sdpMid, candidate.sdpMLineIndex, connectionId)
            }
            "disconnect" -> SignalingMessage.Disconnect(connectionId)
            else -> null
        }
    }
}
