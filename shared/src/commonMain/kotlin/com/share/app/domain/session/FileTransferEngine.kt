package com.share.app.domain.session

import com.share.app.domain.analytics.AnalyticsEvent
import com.share.app.domain.analytics.AnalyticsLogger
import com.share.app.domain.analytics.FileRoute
import com.share.app.domain.media.DetectedFileType
import com.share.app.domain.media.FileTypes
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
import kotlinx.coroutines.Job
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
    suspend fun send(file: OutgoingFile, bytes: ByteArray, contentType: String, onProgress: (Long) -> Unit)
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
    private val previews: FilePreviews,
    private val analytics: AnalyticsLogger,
    private val onIncomingFile: (IncomingFile) -> Unit,
    private val onError: (String) -> Unit,
    private val onPeerDisconnect: () -> Unit,
    private val canStillConnect: () -> Boolean,
    private val clock: () -> Long = ::currentTimeMillis,
) {
    private class IncomingTransfer(
        val name: String,
        /** What the sender said to expect, when it said anything. */
        val expectedBytes: Long?,
    ) {
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

    /** Announced by a `SIZE:` frame, and claimed by the `NAME:` frame after it. */
    private var announcedSize: Long? = null
    private var stallJob: Job? = null

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
        clearStallTimer()
        announcedSize = null
        outgoingActive = false
        incoming?.chunks?.clear()
        incoming = null
        _state.value = TransferState()
    }

    private fun clearStallTimer() {
        stallJob?.cancel()
        stallJob = null
    }

    /** Restarted on every chunk; only a silent channel ever reaches the end of it. */
    private fun armStallTimer() {
        clearStallTimer()
        stallJob = scope.launch {
            delay(SessionLimits.INCOMING_STALL_TIMEOUT_MS)
            stallJob = null
            if (incoming == null) return@launch
            resetTransfers()
            onError("The incoming file stopped arriving. Ask the other device to send it again.")
        }
    }

    private fun finishIncoming(transfer: IncomingTransfer) {
        if (transfer.ignore) return
        if (transfer.receivedBytes > SessionLimits.MAX_FILE_SIZE) {
            onError("Incoming file exceeds 20 MB limit.")
            return
        }
        // A sender that announced a size and then stopped short sent a broken
        // file; saving it as though it were whole is worse than saying so.
        if (transfer.expectedBytes != null && transfer.receivedBytes != transfer.expectedBytes) {
            analytics.log(AnalyticsEvent.FileIncomplete)
            onError("The file arrived incomplete. Ask the other device to send it again.")
            return
        }

        val bytes = ByteArray(transfer.receivedBytes.toInt())
        var offset = 0
        transfer.chunks.forEach { chunk ->
            chunk.copyInto(bytes, offset)
            offset += chunk.size
        }

        // The channel carries only the name, so the type is read from the bytes.
        val detected = FileTypes.detect(transfer.name, head = bytes)
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
                contentType = detected.mimeType,
            ),
            payload = bytes,
        )
        onIncomingFile(
            IncomingFile(
                name = transfer.name,
                size = size,
                contentType = detected.mimeType,
                localFileId = id,
            ),
        )
        previews.attach(id, bytes, detected.mimeType, forInbox = true)
        analytics.log(AnalyticsEvent.FileReceived(FileRoute.DIRECT, detected.kind))
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

        val size = FileTransferProtocol.parseFileSizeMessage(data)
        if (size != null) {
            announcedSize = size
            return
        }

        if (data == FileTransferProtocol.END_MESSAGE) {
            val transfer = incoming ?: return
            clearStallTimer()
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

        val expected = announcedSize
        announcedSize = null
        incoming = IncomingTransfer(fileName, expected)
        lastIncomingProgressAt = 0L
        armStallTimer()
        _state.update {
            it.copy(incoming = ActiveFileTransfer(fileName, progress = 0, transferredBytes = 0, size = expected))
        }
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
        armStallTimer()

        // Publishing per chunk would starve the transfer itself; throttle it.
        val now = clock()
        if (now - lastIncomingProgressAt >= PROGRESS_UPDATE_INTERVAL_MS) {
            lastIncomingProgressAt = now
            val received = transfer.receivedBytes
            val expected = transfer.expectedBytes
            _state.update {
                it.copy(incoming = ActiveFileTransfer(transfer.name, percentOf(received, expected), received, expected))
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
        // Claimed here rather than after the wait below: a second file picked
        // while the channel was still negotiating used to pass this guard too,
        // and both then went down the same channel interleaved.
        outgoingActive = true

        if (file.size > SessionLimits.MAX_FILE_SIZE) {
            outgoingActive = false
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
                outgoingActive = false
                onError(
                    "This device cannot reach the other one right now. " +
                        "Put both devices on the same Wi-Fi and try again.",
                )
                return
            }
            if (file.size > SessionLimits.MAX_RELAY_FILE_SIZE) {
                outgoingActive = false
                onError(
                    "On these networks the limit for this file drops to 5 MB. " +
                        "Put both devices on the same Wi-Fi to send the full 20 MB.",
                )
                return
            }
        }

        val route = if (useChannel) FileRoute.DIRECT else FileRoute.RELAY
        _state.update {
            it.copy(outgoing = ActiveFileTransfer(file.name, progress = 0, transferredBytes = 0, size = file.size))
        }

        // Until the bytes are read the name is all there is - so even the row
        // for a file that could not be read says what it was.
        var detected = FileTypes.detect(file.name, declaredType = file.contentType)
        var bytes: ByteArray? = null
        try {
            val content = file.readBytes()
            bytes = content
            detected = FileTypes.detect(file.name, head = content, declaredType = file.contentType)
            if (openChannel != null) {
                sendOverChannel(file, content, openChannel)
            } else {
                relay.send(file, content, detected.mimeType) { transferred -> publishOutgoing(file, transferred) }
            }
            recordSent(file, detected, content, HistoryAction.SENT_FILE, HistoryStatus.SUCCESS)
            analytics.log(AnalyticsEvent.FileSent(route, detected.kind))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            AppLog.w(error) { "Sending a file failed" }
            recordSent(file, detected, bytes, HistoryAction.FAILED_UPLOAD, HistoryStatus.FAILED)
            analytics.log(AnalyticsEvent.FileSendFailed(route))
            onError(
                if (useChannel && relayAvailable) {
                    "Sending failed. Try again - it will take another route this time."
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
        // Sent before the name, so the receiver can tell a truncated file from a
        // complete one. Anything that does not understand it ignores it.
        check(channel.sendText(FileTransferProtocol.buildFileSizeMessage(bytes.size.toLong()))) { "Data channel send failed." }
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
        _state.update {
            it.copy(outgoing = ActiveFileTransfer(file.name, percentOf(transferred, file.size), transferred, file.size))
        }
    }

    private fun recordSent(
        file: OutgoingFile,
        detected: DetectedFileType,
        bytes: ByteArray?,
        action: HistoryAction,
        status: HistoryStatus,
    ) {
        val id = newId("hist")
        history.add(
            HistoryItem(
                id = id,
                action = action,
                title = file.name,
                timestampMillis = clock(),
                size = file.size,
                status = status,
                contentType = detected.mimeType,
            ),
        )
        if (bytes != null) previews.attach(id, bytes, detected.mimeType, forInbox = false)
    }

    private fun percentOf(transferred: Long, total: Long?): Int = when {
        total == null -> 0
        total <= 0 -> 100
        else -> ((transferred.toDouble() / total) * 100).roundToInt().coerceIn(0, 100)
    }

    private companion object {
        const val BUFFERED_AMOUNT_LOW_THRESHOLD = 4L * 1024 * 1024
        const val PROGRESS_UPDATE_INTERVAL_MS = 120L
        const val CHANNEL_WAIT_MS = 3_000L
        const val CHANNEL_POLL_MS = 150L
        const val BUFFER_POLL_MS = 15L
        const val YIELD_EVERY_CHUNKS = 64
    }
}
