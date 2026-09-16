package com.share.app.domain.session

import com.share.app.data.local.InMemoryHistoryRepository
import com.share.app.domain.analytics.AnalyticsEvent
import com.share.app.domain.analytics.AnalyticsLogger
import com.share.app.domain.analytics.FileRoute
import com.share.app.domain.media.FileKind
import com.share.app.domain.media.ImageFormat
import com.share.app.domain.media.ImageProcessor
import com.share.app.domain.media.SerialImageProcessor
import com.share.app.domain.model.HistoryAction
import com.share.app.domain.model.IncomingFile
import com.share.app.domain.model.OutgoingFile
import com.share.app.domain.policy.SessionLimits
import com.share.app.domain.webrtc.ChannelMessage
import com.share.app.domain.webrtc.ChannelState
import com.share.app.domain.webrtc.DataChannelListener
import com.share.app.domain.webrtc.DataChannelPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FileTransferEngineTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    private class FakeChannel : DataChannelPort {
        val sent = mutableListOf<Any>()
        override val state = ChannelState.OPEN
        override val bufferedAmount = 0L
        override fun setListener(listener: DataChannelListener?) = Unit
        override fun sendText(text: String): Boolean = sent.add(text)
        override fun sendBinary(bytes: ByteArray): Boolean = sent.add(bytes)
        override fun close() = Unit
    }

    private object NoImages : ImageProcessor {
        override fun preview(bytes: ByteArray, mimeType: String, maxDimension: Int): ByteArray? = null
        override fun convert(bytes: ByteArray, sourceType: String, format: ImageFormat): ByteArray = error("unused")
    }

    private class Harness(scope: CoroutineScope, clock: () -> Long) {
        val channel = FakeChannel()
        val history = InMemoryHistoryRepository()
        val errors = mutableListOf<String>()
        val received = mutableListOf<IncomingFile>()
        val events = mutableListOf<AnalyticsEvent>()

        val engine = FileTransferEngine(
            scope = scope,
            getChannel = { channel },
            relay = object : RelayPort {
                override fun isAvailable() = false
                override suspend fun send(file: OutgoingFile, bytes: ByteArray, contentType: String, onProgress: (Long) -> Unit) =
                    error("unused")
            },
            history = history,
            previews = FilePreviews(scope, SerialImageProcessor(NoImages), history) { _, _ -> },
            analytics = object : AnalyticsLogger {
                override fun log(event: AnalyticsEvent) {
                    events += event
                }
            },
            onIncomingFile = { received += it },
            onError = { errors += it },
            onPeerDisconnect = {},
            canStillConnect = { false },
            clock = clock,
        )

        fun receive(vararg frames: Any) = frames.forEach { frame ->
            engine.enqueue(if (frame is String) ChannelMessage.Text(frame) else ChannelMessage.Binary(frame as ByteArray))
        }
    }

    private fun TestScope.harness() = Harness(backgroundScope) { testScheduler.currentTime }

    @Test
    fun rejectsAFileThatStoppedShortOfItsAnnouncedSize() = runTest {
        val h = harness()
        h.receive("SIZE:20", "NAME:photo.png", png, "END")
        runCurrent()

        assertEquals(listOf("The file arrived incomplete. Ask the other device to send it again."), h.errors)
        assertTrue(h.history.history.value.isEmpty())
        assertTrue(h.received.isEmpty())
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.FileIncomplete), h.events)
    }

    @Test
    fun keepsACompleteFileAndReadsItsTypeFromTheBytes() = runTest {
        val h = harness()
        // Called .jpg, but the bytes say PNG.
        h.receive("SIZE:${png.size}", "NAME:photo.jpg", png, "END")
        runCurrent()

        assertTrue(h.errors.isEmpty())
        val row = h.history.history.value.single()
        assertEquals(HistoryAction.RECEIVED_FILE, row.action)
        assertEquals("image/png", row.contentType)
        assertEquals("image/png", h.received.single().contentType)
        assertEquals(row.id, h.received.single().localFileId)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.FileReceived(FileRoute.DIRECT, FileKind.IMAGE)), h.events)
    }

    @Test
    fun stillAcceptsASenderThatAnnouncesNothing() = runTest {
        val h = harness()
        h.receive("NAME:notes.txt", "hello".encodeToByteArray(), "END")
        runCurrent()

        assertTrue(h.errors.isEmpty())
        assertEquals("text/plain", h.history.history.value.single().contentType)
    }

    @Test
    fun reportsTheAnnouncedTotalWhileReceiving() = runTest {
        val h = harness()
        h.receive("SIZE:16", "NAME:photo.png")
        runCurrent()
        // Progress is published at most every 120 ms.
        advanceTimeBy(200)
        h.receive(png)
        runCurrent()

        val incoming = h.engine.state.value.incoming
        assertEquals(16L, incoming?.size)
        assertEquals(50, incoming?.progress)
    }

    @Test
    fun abandonsAReceiveThatGoesQuiet() = runTest {
        val h = harness()
        h.receive("SIZE:16", "NAME:photo.png", png)
        runCurrent()

        advanceTimeBy(SessionLimits.INCOMING_STALL_TIMEOUT_MS - 1)
        runCurrent()
        assertTrue(h.errors.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("The incoming file stopped arriving. Ask the other device to send it again."), h.errors)
        assertNull(h.engine.state.value.incoming)

        // And the next file is not refused as "already in progress".
        h.receive("NAME:next.txt", "hi".encodeToByteArray(), "END")
        runCurrent()
        assertEquals(1, h.errors.size)
        assertEquals("next.txt", h.history.history.value.single().title)
    }

    @Test
    fun announcesTheSizeBeforeTheName() = runTest {
        val h = harness()
        val content = ByteArray(40_000) { it.toByte() }
        h.engine.sendFile(OutgoingFile("big.bin", content.size.toLong(), "application/octet-stream") { content })

        assertEquals("SIZE:40000", h.channel.sent.first())
        assertEquals("NAME:big.bin", h.channel.sent[1])
        assertEquals("END", h.channel.sent.last())
        assertEquals(40_000, h.channel.sent.filterIsInstance<ByteArray>().sumOf { it.size })
        assertEquals(HistoryAction.SENT_FILE, h.history.history.value.single().action)
    }

    @Test
    fun recordsTheSniffedTypeOfASentFile() = runTest {
        val h = harness()
        h.engine.sendFile(OutgoingFile("scan", png.size.toLong(), "application/octet-stream") { png })

        assertEquals("image/png", h.history.history.value.single().contentType)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.FileSent(FileRoute.DIRECT, FileKind.IMAGE)), h.events)
    }
}
