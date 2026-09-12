package com.share.app.data.crypto

import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** Mirrors the web client's sessionCrypto tests, so both ends hold the same guarantees. */
class SessionCipherTest {
    private val cipher = CryptographySessionCipher()

    private suspend fun pairUp() = run {
        val host = cipher.generateKeyPair()
        val guest = cipher.generateKeyPair()
        Triple(cipher.deriveSharedKey(host, guest.publicKey), cipher.deriveSharedKey(guest, host.publicKey), host)
    }

    @Test
    fun bothSidesReachTheSameKey() = runTest {
        val (hostKey, guestKey) = pairUp()
        assertEquals("meet me at 6", cipher.decrypt(guestKey, cipher.encrypt(hostKey, "meet me at 6")!!))
        assertEquals("on my way", cipher.decrypt(hostKey, cipher.encrypt(guestKey, "on my way")!!))
    }

    @Test
    fun publishesAnUncompressedP256Point() = runTest {
        val pair = cipher.generateKeyPair()
        val raw = Base64.Default.decode(pair.publicKey)
        assertEquals(65, raw.size)
        assertEquals(0x04, raw[0].toInt())
        assertEquals(88, pair.publicKey.length)
    }

    @Test
    fun everySessionHasADifferentKey() = runTest {
        val first = pairUp()
        val second = pairUp()
        assertNull(cipher.decrypt(second.second, cipher.encrypt(first.first, "secret")!!))
    }

    @Test
    fun roundTripsUnicodeAndNeverRepeatsCiphertext() = runTest {
        val (hostKey, guestKey) = pairUp()
        val text = "नमस्ते duniya"
        assertEquals(text, cipher.decrypt(guestKey, cipher.encrypt(hostKey, text)!!))
        assertNotEquals(cipher.encrypt(hostKey, "same"), cipher.encrypt(hostKey, "same"))
    }

    @Test
    fun passesEmptyValuesAndRejectsJunk() = runTest {
        val (hostKey, guestKey) = pairUp()
        assertEquals("", cipher.encrypt(hostKey, ""))
        assertEquals("", cipher.decrypt(hostKey, ""))
        assertNull(cipher.decrypt(hostKey, "AAAA"))
        val payload = cipher.encrypt(hostKey, "do not modify")!!
        val tampered = payload.dropLast(4) + if (payload.endsWith("A")) "BBBB" else "AAAA"
        assertNull(cipher.decrypt(guestKey, tampered))
    }
}
