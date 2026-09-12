package com.share.app.data.crypto

import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A message encrypted by WebCrypto with the web client's exact parameters
 * (ECDH P-256 -> AES-GCM-256, base64 of iv || ciphertext || tag), generated in
 * Node. If this passes, a phone running the app can read what a browser sends.
 */
class WebCryptoInteropTest {
    private val cipher = CryptographySessionCipher()

    @Test
    fun opensTextEncryptedByTheWebClient() = runTest {
        val app = cipher.importKeyPair(Base64.Default.decode(APP_PRIVATE_KEY_PKCS8), APP_PUBLIC_KEY)
        val key = cipher.deriveSharedKey(app, WEB_PUBLIC_KEY)
        assertEquals(MESSAGE, cipher.decrypt(key, CIPHERTEXT))
    }

    @Test
    fun whatTheAppSendsUsesTheSameLayout() = runTest {
        val app = cipher.importKeyPair(Base64.Default.decode(APP_PRIVATE_KEY_PKCS8), APP_PUBLIC_KEY)
        val key = cipher.deriveSharedKey(app, WEB_PUBLIC_KEY)
        val sealed = Base64.Default.decode(cipher.encrypt(key, MESSAGE)!!)
        // 12-byte IV + UTF-8 plaintext + 16-byte tag, exactly as the web decrypts it.
        assertEquals(12 + MESSAGE.encodeToByteArray().size + 16, sealed.size)
        assertEquals(Base64.Default.decode(CIPHERTEXT).size, sealed.size)
    }

    private companion object {
        const val WEB_PUBLIC_KEY =
            "BCdma4Ryhlcod04wL2t425QWLYI0gOROMlWqjWurr9wIRWNHKl9Ixg79q0t8JRo51SOGTmywcKQMi+kmMlKbsvw="
        const val APP_PUBLIC_KEY =
            "BHXn7Rp3Q53ZAEjW2DObpU53Rdu5gk+XL70GR+7cYqT0OusoJZvgvpxUGtz6pcdSpQJ+dvPFlnVxYLVjtQefksE="
        const val APP_PRIVATE_KEY_PKCS8 =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgwwsu49SG/nYLM9CsB4keGJsA7vxNFuC02SqaLPrekQyhRANCAAR15+0ad0Od2QBI1tgzm6VOd0XbuYJPly+9Bkfu3GKk9DrrKCWb4L6cVBrc+qXHUqUCfnbzxZZ1cWC1Y7UHn5LB"
        const val CIPHERTEXT =
            "RdkbjPDcU5/1JYN3JP/zxY8FaoqZEFW9DbliXrScj4OfVPUC0kwxFkVCXNqyNomaZjZdMlER/48hHsoWcU2hoD7jShCrQk/GAAbflQ=="
        const val MESSAGE = "hello from the web client ✓ नमस्ते"
    }
}
