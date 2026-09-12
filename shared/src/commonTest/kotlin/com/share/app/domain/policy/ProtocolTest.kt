package com.share.app.domain.policy

import com.share.app.data.remote.firebase.SignalingCodec
import com.share.app.domain.repository.SignalingMessage
import com.share.app.util.UriComponent
import com.share.app.util.humanFileSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingCodeTest {
    @Test
    fun generatesSixDigitCodesInRange() {
        repeat(500) {
            val code = PairingCode.generate()
            assertTrue(PairingCode.isValid(code), code)
            assertTrue(code.toInt() in 100_000..999_999)
        }
    }

    @Test
    fun extractsCodesTheWayTheWebClientDoes() {
        assertEquals("123456", PairingCode.extract("123456"))
        assertEquals("123456", PairingCode.extract("https://getknotic.web.app/?code=123456"))
        assertEquals("654321", PairingCode.extract("knotic://join?code=654321"))
        assertEquals("123456", PairingCode.extract("code: 123456."))
        assertNull(PairingCode.extract("1234567"))
        assertNull(PairingCode.extract("https://example.com/?id=123456"))
    }

    @Test
    fun buildsTheSameJoinUrlAsTheWeb() {
        assertEquals("https://getknotic.web.app/?code=123456", PairingCode.buildJoinUrl("https://getknotic.web.app/", "123456"))
    }
}

class FilePayloadTest {
    @Test
    fun roundTripsWithUriEncoding() {
        val built = FilePayload.build("sessions/123456/1-a b.png", "a b|ä.png", 2048, "image/png")
        assertEquals("sessions%2F123456%2F1-a%20b.png|a%20b%7C%C3%A4.png|2048|image%2Fpng", built)
        val parsed = FilePayload.parse(built)
        assertEquals("a b|ä.png", parsed?.name)
        assertEquals(2048L, parsed?.size)
    }

    @Test
    fun matchesEncodeUriComponentUnreservedSet() {
        assertEquals("-_.!~*'()", UriComponent.encode("-_.!~*'()"))
        assertNull(UriComponent.decode("%E0%A4%A"))
    }
}

class ConnectionPolicyTest {
    @Test
    fun backsOffAndCaps() {
        assertEquals(1_500L, ConnectionPolicy.retryDelayMs(2))
        assertEquals(3_000L, ConnectionPolicy.retryDelayMs(3))
        assertEquals(6_000L, ConnectionPolicy.retryDelayMs(10))
    }

    @Test
    fun onlyTheFinalMessageSoundsLikeAProblem() {
        assertTrue(ConnectionPolicy.describeFailure(FailureKind.TIMEOUT, 1, false).contains("attempt 2 of 3"))
        assertTrue(ConnectionPolicy.describeFailure(FailureKind.TIMEOUT, 3, false).contains(ConnectionPolicy.HOW_TO_FIX))
    }
}

class SignalingCodecTest {
    @Test
    fun readsWhatTheBrowserWrites() {
        val candidate = SignalingCodec.decode(
            """{"type":"candidate","candidate":{"sdp":"candidate:1 1 udp 1 1.2.3.4 5 typ host","sdpMid":"0","sdpMLineIndex":0},"connectionId":"17-abc"}""",
        )
        assertEquals(SignalingMessage.Candidate("candidate:1 1 udp 1 1.2.3.4 5 typ host", "0", 0, "17-abc"), candidate)
        assertNull(SignalingCodec.decode("""{"type":"handshake"}"""))
        assertNull(SignalingCodec.decode("not json"))
    }

    @Test
    fun omitsAbsentFields() {
        assertEquals("""{"type":"disconnect"}""", SignalingCodec.encode(SignalingMessage.Disconnect(null)))
        assertEquals(
            SignalingMessage.Offer("v=0", "1-a"),
            SignalingCodec.decode(SignalingCodec.encode(SignalingMessage.Offer("v=0", "1-a"))),
        )
    }
}

class FormatTest {
    @Test
    fun formatsLikeTheWebClient() {
        assertEquals("0 B", humanFileSize(0))
        assertEquals("512 B", humanFileSize(512))
        assertEquals("4.2 KB", humanFileSize(4300))
        assertEquals("20 MB", humanFileSize(20L * 1024 * 1024))
    }
}
