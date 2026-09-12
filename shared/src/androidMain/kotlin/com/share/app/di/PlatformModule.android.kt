package com.share.app.di

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.share.app.data.local.PREFERENCES_FILE_NAME
import com.share.app.data.local.createPreferencesDataStore
import com.share.app.data.webrtc.WebRtcKmpPeerConnectionFactory
import com.share.app.domain.repository.ClipboardRepository
import com.share.app.domain.webrtc.PeerConnectionFactoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { createPreferencesDataStore { androidContext().filesDir.resolve(PREFERENCES_FILE_NAME).absolutePath } }
    single<ClipboardRepository> { AndroidClipboardRepository(androidContext()) }
    single<PeerConnectionFactoryPort> { WebRtcKmpPeerConnectionFactory(get()) }
}

private class AndroidClipboardRepository(private val context: Context) : ClipboardRepository {
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)

    override suspend fun readText(): String? = withContext(Dispatchers.Main) {
        clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }

    override suspend fun writeText(text: String) = withContext(Dispatchers.Main) {
        clipboard?.setPrimaryClip(ClipData.newPlainText("Knotic", text))
        Unit
    }
}
