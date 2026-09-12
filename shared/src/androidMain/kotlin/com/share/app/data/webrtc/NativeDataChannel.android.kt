package com.share.app.data.webrtc

import com.share.app.domain.webrtc.ChannelMessage
import com.share.app.domain.webrtc.ChannelState
import com.share.app.domain.webrtc.DataChannelListener
import com.share.app.domain.webrtc.DataChannelPort
import com.shepeliev.webrtckmp.DataChannel
import java.nio.ByteBuffer
import kotlin.concurrent.Volatile
import org.webrtc.DataChannel as AndroidDataChannel

internal actual fun createNativeDataChannel(channel: DataChannel): DataChannelPort =
    AndroidDataChannelPort(channel.android)

/**
 * Replaces webrtc-kmp's observer with one that keeps the frame type. The native
 * channel only holds one observer, so this one also owns disposing it.
 */
private class AndroidDataChannelPort(private val native: AndroidDataChannel) : DataChannelPort {

    @Volatile
    private var listener: DataChannelListener? = null

    @Volatile
    private var disposed = false

    init {
        native.registerObserver(object : AndroidDataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                if (disposed) return
                when (runCatching { native.state() }.getOrNull()) {
                    AndroidDataChannel.State.OPEN -> listener?.onOpen()
                    AndroidDataChannel.State.CLOSED -> {
                        listener?.onClose()
                        dispose()
                    }
                    else -> Unit
                }
            }

            override fun onMessage(buffer: AndroidDataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val message = if (buffer.binary) ChannelMessage.Binary(bytes) else ChannelMessage.Text(bytes.decodeToString())
                listener?.onMessage(message)
            }
        })
    }

    override val state: ChannelState
        get() {
            if (disposed) return ChannelState.CLOSED
            return when (runCatching { native.state() }.getOrNull()) {
                AndroidDataChannel.State.CONNECTING -> ChannelState.CONNECTING
                AndroidDataChannel.State.OPEN -> ChannelState.OPEN
                AndroidDataChannel.State.CLOSING -> ChannelState.CLOSING
                else -> ChannelState.CLOSED
            }
        }

    override val bufferedAmount: Long
        get() = if (disposed) 0L else runCatching { native.bufferedAmount() }.getOrDefault(0L)

    override fun setListener(listener: DataChannelListener?) {
        this.listener = listener
    }

    override fun sendText(text: String): Boolean = send(text.encodeToByteArray(), binary = false)

    override fun sendBinary(bytes: ByteArray): Boolean = send(bytes, binary = true)

    private fun send(bytes: ByteArray, binary: Boolean): Boolean {
        if (disposed) return false
        return runCatching { native.send(AndroidDataChannel.Buffer(ByteBuffer.wrap(bytes), binary)) }.getOrDefault(false)
    }

    override fun close() {
        listener = null
        if (disposed) return
        runCatching { native.close() }
    }

    @Synchronized
    private fun dispose() {
        if (disposed) return
        disposed = true
        runCatching { native.unregisterObserver() }
        runCatching { native.dispose() }
    }
}
