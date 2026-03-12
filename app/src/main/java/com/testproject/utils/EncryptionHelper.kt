package com.testproject.utils

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EncryptionHelper ensures that data synced via Firebase is encrypted.
 * Uses AES-GCM for authenticated encryption.
 */
@Singleton
class EncryptionHelper @Inject constructor(context: Context) {

    // A fixed key for cross-device sync. 
    // In a production app, this could be derived from the session code for better security.
    private val KEY_BYTES = "AirDesk_Secure_Sync_Key_2024_!@#".toByteArray() 
    private val algorithm = "AES/GCM/NoPadding"
    private val tagLength = 128
    private val ivLength = 12
    private val secureRandom = SecureRandom()

    fun encrypt(text: String): String {
        return try {
            if (text.isEmpty()) return ""
            
            val iv = ByteArray(ivLength)
            secureRandom.nextBytes(iv)
            
            val cipher = Cipher.getInstance(algorithm)
            val keySpec = SecretKeySpec(KEY_BYTES, "AES")
            val gcmSpec = GCMParameterSpec(tagLength, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            
            val ciphertext = cipher.doFinal(text.toByteArray())
            val combined = iv + ciphertext
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun decrypt(encryptedText: String): String {
        return try {
            if (encryptedText.isEmpty()) return ""
            
            val decoded = Base64.decode(encryptedText, Base64.NO_WRAP)
            if (decoded.size <= ivLength) return ""
            
            val iv = decoded.sliceArray(0 until ivLength)
            val ciphertext = decoded.sliceArray(ivLength until decoded.size)
            
            val cipher = Cipher.getInstance(algorithm)
            val keySpec = SecretKeySpec(KEY_BYTES, "AES")
            val gcmSpec = GCMParameterSpec(tagLength, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
