package com.testproject.utils

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.testproject.utils.AppsConst.DATA_STORE
import com.testproject.utils.AppsConst.IS_HOST_KEY
import com.testproject.utils.AppsConst.IS_LOGGED_IN
import com.testproject.utils.AppsConst.LAST_SENT_TEXT_KEY
import com.testproject.utils.AppsConst.SESSION_CODE_KEY
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(DATA_STORE)

@Singleton
class AppPreference @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.dataStore

    private val aead: Aead by lazy {
        AeadConfig.register()
        try {
            createAead()
        } catch (e: Exception) {
            e.printStackTrace()
            context.getSharedPreferences("master_key_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
            createAead()
        }
    }

    private fun createAead(): Aead {
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, "master_keyset", "master_key_prefs")
            .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
            .withMasterKeyUri("android-keystore://master_key")
            .build()
            .keysetHandle

        return keysetHandle.getPrimitive(Aead::class.java)
    }

    private fun prefKey(key: String) = stringPreferencesKey(key)

    private fun encrypt(value: String): String =
        Base64.encodeToString(aead.encrypt(value.toByteArray(), null), Base64.NO_WRAP)

    private fun decrypt(value: String): String? =
        runCatching {
            String(aead.decrypt(Base64.decode(value, Base64.NO_WRAP), null))
        }.onFailure { it.printStackTrace() }.getOrNull()
    
    suspend fun saveSessionCode(code: String) {
        dataStore.edit { it[prefKey(SESSION_CODE_KEY)] = encrypt(code) }
    }

    suspend fun getSessionCode(): String? {
        val encrypted = dataStore.data.first()[prefKey(SESSION_CODE_KEY)]
        return encrypted?.let { decrypt(it) }
    }

    suspend fun setIsHost(isHost: Boolean) {
        dataStore.edit { it[prefKey(IS_HOST_KEY)] = isHost.toString() }
    }

    suspend fun isHost(): Boolean {
        return dataStore.data.first()[prefKey(IS_HOST_KEY)]?.toBoolean() ?: true
    }
    
    suspend fun setLoggedIn(value: Boolean) {
        dataStore.edit { it[prefKey(IS_LOGGED_IN)] = value.toString() }
    }

    suspend fun isLoggedIn(): Boolean {
        return dataStore.data.first()[prefKey(IS_LOGGED_IN)]?.toBoolean() ?: false
    }
    
    suspend fun saveLastSentText(text: String) {
        dataStore.edit { it[prefKey(LAST_SENT_TEXT_KEY)] = text }
    }

    suspend fun getLastSentText(): String? {
        return dataStore.data.first()[prefKey(LAST_SENT_TEXT_KEY)]
    }
    
    suspend fun removeSession() {
        dataStore.edit {
            it.remove(prefKey(SESSION_CODE_KEY))
            it.remove(prefKey(IS_HOST_KEY))
        }
    }
    
    suspend fun clearPreferences() {
        dataStore.edit { it.clear() }
    }
}
