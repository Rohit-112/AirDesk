package com.share.app.data.crypto

import com.share.app.domain.crypto.SessionCipher
import com.share.app.domain.crypto.SessionKeyPair
import com.share.app.domain.crypto.SharedSessionKey
import com.share.app.util.suspendRunCatching
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import kotlin.io.encoding.Base64

/**
 * ECDH P-256 + AES-GCM-256 via cryptography-kotlin, byte compatible with the
 * web client's WebCrypto implementation:
 *
 * - public keys are the 65-byte uncompressed point, base64
 * - the AES key is the raw 32-byte ECDH secret, which is what WebCrypto's
 *   `deriveKey({name: "ECDH"}, ..., {name: "AES-GCM", length: 256})` uses
 * - ciphertext is base64 of a random 12-byte IV, the ciphertext and the tag
 */
class CryptographySessionCipher(
    provider: CryptographyProvider = CryptographyProvider.Default,
) : SessionCipher {

    private class EcdhKeyPair(
        override val publicKey: String,
        val privateKey: ECDH.PrivateKey,
    ) : SessionKeyPair

    private class AesGcmKey(val key: AES.GCM.Key) : SharedSessionKey

    private val ecdh = provider.get(ECDH)
    private val aesGcm = provider.get(AES.GCM)

    override suspend fun generateKeyPair(): SessionKeyPair {
        val pair = ecdh.keyPairGenerator(EC.Curve.P256).generateKey()
        val raw = pair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW)
        return EcdhKeyPair(Base64.Default.encode(raw), pair.privateKey)
    }

    /** Rebuilds a key pair exported elsewhere as PKCS#8. Only interop tests need this. */
    internal suspend fun importKeyPair(privateKeyPkcs8: ByteArray, publicKey: String): SessionKeyPair {
        val privateKey = ecdh.privateKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PrivateKey.Format.DER, privateKeyPkcs8)
        return EcdhKeyPair(publicKey, privateKey)
    }

    override suspend fun deriveSharedKey(keyPair: SessionKeyPair, peerPublicKey: String): SharedSessionKey {
        val own = keyPair as EcdhKeyPair
        val peer = ecdh.publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PublicKey.Format.RAW, Base64.Default.decode(peerPublicKey))
        val secret = own.privateKey.sharedSecretGenerator().generateSharedSecretToByteArray(peer)
        return AesGcmKey(aesGcm.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, secret))
    }

    override suspend fun encrypt(key: SharedSessionKey, text: String): String? {
        if (text.isEmpty()) return ""
        return suspendRunCatching {
            val sealed = (key as AesGcmKey).key.cipher().encrypt(text.encodeToByteArray())
            Base64.Default.encode(sealed)
        }.getOrNull()
    }

    override suspend fun decrypt(key: SharedSessionKey, payload: String): String? {
        if (payload.isEmpty()) return ""
        return suspendRunCatching {
            val combined = Base64.Default.decode(payload)
            if (combined.size <= IV_LENGTH) return null
            (key as AesGcmKey).key.cipher().decrypt(combined).decodeToString(throwOnInvalidSequence = true)
        }.getOrNull()
    }

    private companion object {
        const val IV_LENGTH = 12
    }
}
