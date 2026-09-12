package com.share.app.domain.session

import com.share.app.domain.model.ActiveFileTransfer
import com.share.app.domain.model.HistoryAction
import com.share.app.domain.model.HistoryItem
import com.share.app.domain.model.HistoryStatus
import com.share.app.domain.model.IncomingFile
import com.share.app.domain.model.OutgoingFile
import com.share.app.domain.policy.FileTransferProtocol
import com.share.app.domain.policy.SessionLimits
import com.share.app.domain.repository.HistoryRepository
import com.share.app.domain.webrtc.ChannelMessage
import com.share.app.domain.webrtc.ChannelState
import com.share.app.domain.webrtc.DataChannelPort
import com.share.app.util.AppLog
import com.share.app.util.currentTimeMillis
import com.share.app.util.newId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.math.min
import kotlin.math.roundToInt

internal data class TransferState(
    val outgoing: ActiveFileTransfer? = null,
    val incoming: ActiveFileTransfer? = null,
)

/** Uploads a file to Storage and announces it to the peer. */
internal interface RelayPort {
    fun isAvailable(): Boolean
    suspend fun send(file: OutgoingFile, bytes: ByteArray, onProgress: (Long) -> Unit)
}

/**
 * Moves files over the data channel in 16 KB chunks, and falls back to the
 * relay when there is no direct route. A port of the web `useFileTransfer`.
 *
 * Runs on the engine's confined dispatcher. Incoming frames are queued so they
 * are handled strictly in arrival order.
 */
internal class FileTransferEngine(
    private val scope: CoroutineScope,
    private val getChannel: () -> DataChannelPort?,
    private val relay: RelayPort,
    private val history: HistoryRepository,
    private val onIncomingFile: (IncomingFile) -> Unit,
    private val onError: (String) -> Unit,
    private val onPeerDisconnect: () -> Unit,
    private val canStillConnect: () -> Boolean,
    private val clock: () -> Long = ::currentTimeMillis,
) {
    private class IncomingTransfer(val name: String) {
        var receivedBytes = 0L
        val chunks = ArrayList<ByteArray>()
        var ignore = false
    }

    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private val inbox = Channel<ChannelMessage>(Channel.UNLIMITED)
    private var outgoingActive = false
    private var incoming: IncomingTransfer? = null
    private var lastIncomingProgressAt = 0L

    init {
        scope.launch {
            for (message in inbox) handleChannelMessage(message)
        }
    }

    /** Safe to call from any thread. */
    fun enqueue(message: ChannelMessage) {
        inbox.trySend(message)
    }

    fun resetTransfers() {
        outgoingActive = false
        incoming?.chunks?.clear()
        incoming = null
        _state.value = TransferState()
    }

    private fun finishIncoming(transfer: IncomingTransfer) {
        if (transfer.ignore) return
        if (transfer.receivedBytes > SessionLimits.MAX_FILE_SIZE) {
            onError("Incoming file exceeds 20 MB limit.")
            return
        }

        val bytes = ByteArray(transfer.receivedBytes.toInt())
        var offset = 0
        transfer.chunks.forEach { chunk ->
            chunk.copyInto(bytes, offset)
            offset += chunk.size
        }

        val id = newId("file")
        val size = bytes.size.toLong()
        history.add(
            HistoryItem(
                id = id,
                action = HistoryAction.RECEIVED_FILE,
                title = transfer.name,
                timestampMillis = clock(),
                size = size,
                status = HistoryStatus.SUCCESS,
                hasPayload = true,
            ),
            payload = bytes,
        )
        onIncomingFile(
            IncomingFile(
                name = transfer.name,
                size = size,
                contentType = SessionLimits.guessContentType(transfer.name),
                localFileId = id,
            ),
        )
    }

    private fun handleChannelMessage(message: ChannelMessage) {
        when (message) {
            is ChannelMessage.Text -> handleText(message.value)
            is ChannelMessage.Binary -> handleChunk(message.bytes)
        }
    }

    private fun handleText(data: String) {
        if (data == FileTransferProtocol.DISCONNECT_MESSAGE) {
            onPeerDisconnect()
            return
        }

        if (data == FileTransferProtocol.END_MESSAGE) {
            val transfer = incoming ?: return
            finishIncoming(transfer)
            transfer.chunks.clear()
            incoming = null
            _state.update { it.copy(incoming = null) }
            return
        }

        val fileName = FileTransferProtocol.parseFileNameMessage(data) ?: return
        if (incoming != null) {
            onError("Another incoming file is already being processed.")
            return
        }

        incoming = IncomingTransfer(fileName)
        lastIncomingProgressAt = 0L
        _state.update { it.copy(incoming = ActiveFileTransfer(fileName, progress = 0, transferredBytes = 0)) }
    }

    private fun handleChunk(bytes: ByteArray) {
        val transfer = incoming ?: return
        if (transfer.ignore) return

        transfer.receivedBytes += bytes.size
        if (transfer.receivedBytes > SessionLimits.MAX_FILE_SIZE) {
            transfer.ignore = true
            transfer.chunks.clear()
            onError("Incoming file exceeds 20 MB limit.")
            return
        }

        transfer.chunks += bytes

        // Publishing per chunk would starve the transfer itself; throttle it.
        val now = clock()
        if (now - lastIncomingProgressAt >= PROGRESS_UPDATE_INTERVAL_MS) {
            lastIncomingProgressAt = now
            _state.update {
                it.copy(incoming = ActiveFileTransfer(transfer.name, progress = 0, transferredBytes = transfer.receivedBytes))
            }
        }
    }

    /**
     * Prefers the direct peer connection and falls back to the relay, so a file
     * still moves on networks where WebRTC cannot find a route at all.
     */
    suspend fun sendFile(file: OutgoingFile) {
        if (outgoingActive) {
            onError("Another file transfer is already in progress.")
            return
        }
        if (file.size > SessionLimits.MAX_FILE_SIZE) {
            onError("File size exceeds the 20 MB limit.")
            return
        }

        // If the direct channel is still negotiating, give it a moment before
        // relaying - otherwise a quick user always ends up on the slower path.
        var channel = getChannel()
        if (channel?.state != ChannelState.OPEN && canStillConnect()) {
            val deadline = clock() + CHANNEL_WAIT_MS
            while (clock() < deadline && getChannel()?.state != ChannelState.OPEN) {
                delay(CHANNEL_POLL_MS)
            }
            channel = getChannel()
        }

        val openChannel = channel?.takeIf { it.state == ChannelState.OPEN }
        val useChannel = openChannel != null
        val relayAvailable = relay.isAvailable()
        if (!useChannel) {
            if (!relayAvailable) {
                onError(
                    "No direct connection to the other device, and the cloud relay is off. " +
                        "Put both devices on the same Wi-Fi and try again.",
                )
                return
            }
            if (file.size > SessionLimits.MAX_RELAY_FILE_SIZE) {
                onError(
                    "No direct connection, so this would go through the cloud relay - which is limited to 5 MB. " +
                        "Put both devices on the same Wi-Fi to send the full 20 MB.",
                )
                return
            }
        }

        outgoingActive = true
        _state.update { it.copy(outgoing = ActiveFileTransfer(file.name, progress = 0, transferredBytes = 0, size = file.size)) }

        try {
            val bytes = file.readBytes()
            if (openChannel != null) {
                sendOverChannel(file, bytes, openChannel)
            } else {
                relay.send(file, bytes) { transferred -> publishOutgoing(file, transferred) }
            }
            history.add(historyItem(file, HistoryAction.SENT_FILE, HistoryStatus.SUCCESS))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            AppLog.w(error) { "Sending ${file.name} failed" }
            history.add(historyItem(file, HistoryAction.FAILED_UPLOAD, HistoryStatus.FAILED))
            onError(
                if (useChannel && relayAvailable) {
                    "Direct transfer failed. Try again to send it through the cloud relay."
                } else {
                    "Unable to send file right now."
                },
            )
        } finally {
            outgoingActive = false
            _state.update { it.copy(outgoing = null) }
        }
    }

    private suspend fun sendOverChannel(file: OutgoingFile, bytes: ByteArray, channel: DataChannelPort) {
        check(channel.sendText(FileTransferProtocol.buildFileNameMessage(file.name))) { "Data channel send failed." }

        var offset = 0
        var chunkCount = 0
        var lastProgressAt = 0L
        while (offset < bytes.size) {
            check(channel.state == ChannelState.OPEN) { "Data channel closed." }

            val end = min(offset + FileTransferProtocol.CHUNK_SIZE, bytes.size)
            check(channel.sendBinary(bytes.copyOfRange(offset, end))) { "Data channel send failed." }
            offset = end
            chunkCount += 1

            val now = clock()
            if (now - lastProgressAt >= PROGRESS_UPDATE_INTERVAL_MS) {
                lastProgressAt = now
                publishOutgoing(file, offset.toLong())
            }

            while (channel.state == ChannelState.OPEN && channel.bufferedAmount > BUFFERED_AMOUNT_LOW_THRESHOLD) {
                delay(BUFFER_POLL_MS)
            }

            // Let incoming frames and state changes through during a long send.
            if (chunkCount % YIELD_EVERY_CHUNKS == 0) yield()
        }

        check(channel.sendText(FileTransferProtocol.END_MESSAGE)) { "Data channel send failed." }
    }

    private fun publishOutgoing(file: OutgoingFile, transferred: Long) {
        val progress = if (file.size > 0) ((transferred.toDouble() / file.size) * 100).roundToInt() else 100
        _state.update {
            it.copy(outgoing = ActiveFileTransfer(file.name, progress, transferred, file.size))
        }
    }

    private fun historyItem(file: OutgoingFile, action: HistoryAction, status: HistoryStatus) = HistoryItem(
        id = newId("hist"),
        action = action,
        title = file.name,
        timestampMillis = clock(),
        size = file.size,
        status = status,
    )

    private companion object {
        const val BUFFERED_AMOUNT_LOW_THRESHOLD = 4L * 1024 * 1024
        const val PROGRESS_UPDATE_INTERVAL_MS = 120L
        const val CHANNEL_WAIT_MS = 3_000L
        const val CHANNEL_POLL_MS = 150L
        const val BUFFER_POLL_MS = 15L
        const val YIELD_EVERY_CHUNKS = 64
    }
}
