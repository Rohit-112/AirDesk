package com.testproject.domain.webrtc

import com.google.gson.annotations.SerializedName

data class SignalingMessage(
    @SerializedName("type") val type: String,
    @SerializedName("sdp") val sdp: String? = null,
    @SerializedName("candidate") val candidate: IceCandidateModel? = null
)

data class IceCandidateModel(
    @SerializedName("sdp") val sdp: String,
    @SerializedName("sdpMid") val sdpMid: String,
    @SerializedName("sdpMLineIndex") val sdpMLineIndex: Int
)
