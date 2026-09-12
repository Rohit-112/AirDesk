package com.share.app.di

import com.share.app.data.local.PREFERENCES_FILE_NAME
import com.share.app.data.local.createPreferencesDataStore
import com.share.app.data.webrtc.WebRtcJavaPeerConnectionFactory
import com.share.app.domain.repository.ClipboardRepository
import com.share.app.domain.webrtc.PeerConnectionFactoryPort
import com.share.app.platform.DesktopPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual val platformModule: Module = module {
    single { createPreferencesDataStore { DesktopPaths.dataDir.resolve(PREFERENCES_FILE_NAME).absolutePath } }
    single<ClipboardRepository> { AwtClipboardRepository() }
    single<PeerConnectionFactoryPort> { WebRtcJavaPeerConnectionFactory(get()) }
}

private class AwtClipboardRepository : ClipboardRepository {
    private val clipboard get() = Toolkit.getDefaultToolkit().systemClipboard

    override suspend fun readText(): String? = withContext(Dispatchers.Main) {
        runCatching { clipboard.getData(DataFlavor.stringFlavor) as? String }.getOrNull()
    }

    override suspend fun writeText(text: String) = withContext(Dispatchers.Main) {
        clipboard.setContents(StringSelection(text), null)
    }
}
