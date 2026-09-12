package com.share.app.domain.crypto

/**
 * End to end encryption for the text that passes through the database.
 *
 * Each device generates an ECDH P-256 key pair per session and publishes only
 * the public half (65 raw bytes, base64). Both sides derive the same AES-GCM
 * key from their own private key and the peer's public key, so the key is
 * never transmitted. Ciphertext is base64 of `iv(12) || ciphertext || tag` -
 * exactly what the web client's WebCrypto code produces and expects.
 */
interface SessionCipher {
    suspend fun generateKeyPair(): SessionKeyPair
    suspend fun deriveSharedKey(keyPair: SessionKeyPair, peerPublicKey: String): SharedSessionKey

    /** Null when the text could not be encrypted. Empty passes straight through. */
    suspend fun encrypt(key: SharedSessionKey, text: String): String?

    /**
     * Null when the payload cannot be opened - expected right after a key
     * rotation, so treat it as "nothing to show", not as an error.
     */
    suspend fun decrypt(key: SharedSessionKey, payload: String): String?
}

interface SessionKeyPair {
    /** Safe to publish. */
    val publicKey: String
}

/** Opaque: the derived key never leaves the cipher implementation. */
interface SharedSessionKey
