@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.share.app.data.webrtc

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import WebRTC.RTCDataBuffer
import WebRTC.RTCDataChannel
import WebRTC.RTCDataChannelDelegateProtocol
import WebRTC.RTCDataChannelState
import com.share.app.domain.webrtc.ChannelMessage
import com.share.app.domain.webrtc.ChannelState
import com.share.app.domain.webrtc.DataChannelListener
import com.share.app.domain.webrtc.DataChannelPort
import com.share.app.platform.toByteArray
import com.share.app.platform.toNSData
import com.shepeliev.webrtckmp.DataChannel
import platform.darwin.NSObject
import platform.posix.uint64_t
import kotlin.concurrent.Volatile

internal actual fun createNativeDataChannel(channel: DataChannel): DataChannelPort =
    IosDataChannelPort(channel.ios)

/** Replaces webrtc-kmp's delegate with one that keeps the frame type. */
private class IosDataChannelPort(private val native: RTCDataChannel) : DataChannelPort {

    @Volatile
    private var listener: DataChannelListener? = null

    // RTCDataChannel holds its delegate weakly; this keeps it alive.
    private val delegate = Delegate()

    init {
        native.delegate = delegate
    }

    override val state: ChannelState
        get() = when (native.readyState) {
            RTCDataChannelState.RTCDataChannelStateConnecting -> ChannelState.CONNECTING
            RTCDataChannelState.RTCDataChannelStateOpen -> ChannelState.OPEN
            RTCDataChannelState.RTCDataChannelStateClosing -> ChannelState.CLOSING
            else -> ChannelState.CLOSED
        }

    override val bufferedAmount: Long
        get() = native.bufferedAmount.toLong()

    override fun setListener(listener: DataChannelListener?) {
        this.listener = listener
    }

    override fun sendText(text: String): Boolean = native.sendData(RTCDataBuffer(text.encodeToByteArray().toNSData(), false))

    override fun sendBinary(bytes: ByteArray): Boolean = native.sendData(RTCDataBuffer(bytes.toNSData(), true))

    override fun close() {
        listener = null
        native.close()
    }

    private inner class Delegate : NSObject(), RTCDataChannelDelegateProtocol {
        override fun dataChannel(dataChannel: RTCDataChannel, didChangeBufferedAmount: uint64_t) = Unit

        override fun dataChannel(dataChannel: RTCDataChannel, didReceiveMessageWithBuffer: RTCDataBuffer) {
            val bytes = didReceiveMessageWithBuffer.data.toByteArray()
            val message = if (didReceiveMessageWithBuffer.isBinary) {
                ChannelMessage.Binary(bytes)
            } else {
                ChannelMessage.Text(bytes.decodeToString())
            }
            listener?.onMessage(message)
        }

        override fun dataChannelDidChangeState(dataChannel: RTCDataChannel) {
            when (dataChannel.readyState) {
                RTCDataChannelState.RTCDataChannelStateOpen -> listener?.onOpen()
                RTCDataChannelState.RTCDataChannelStateClosed -> {
                    listener?.onClose()
                    dataChannel.delegate = null
                }
                else -> Unit
            }
        }
    }
}
