@file:OptIn(ExperimentalForeignApi::class)

package com.share.app.di

import kotlinx.cinterop.ExperimentalForeignApi
import com.share.app.data.analytics.FirebaseAnalyticsLogger
import com.share.app.data.analytics.FirebaseCrashReporter
import com.share.app.data.local.PREFERENCES_FILE_NAME
import com.share.app.data.local.createPreferencesDataStore
import com.share.app.data.media.UIKitImageProcessor
import com.share.app.data.webrtc.WebRtcKmpPeerConnectionFactory
import com.share.app.domain.analytics.AnalyticsLogger
import com.share.app.domain.analytics.CrashReporter
import com.share.app.domain.media.ImageProcessor
import com.share.app.domain.repository.ClipboardRepository
import com.share.app.domain.webrtc.PeerConnectionFactoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIPasteboard

actual val platformModule: Module = module {
    single { createPreferencesDataStore { documentsPath() + "/" + PREFERENCES_FILE_NAME } }
    single<ClipboardRepository> { IosClipboardRepository() }
    single<PeerConnectionFactoryPort> { WebRtcKmpPeerConnectionFactory(get()) }
    single<ImageProcessor> { UIKitImageProcessor() }
    single<AnalyticsLogger> { FirebaseAnalyticsLogger() }
    single<CrashReporter> { FirebaseCrashReporter() }
}

private fun documentsPath(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(url?.path) { "Documents directory is unavailable" }
}

private class IosClipboardRepository : ClipboardRepository {
    override suspend fun readText(): String? = withContext(Dispatchers.Main) {
        UIPasteboard.generalPasteboard.string
    }

    override suspend fun writeText(text: String) = withContext(Dispatchers.Main) {
        UIPasteboard.generalPasteboard.string = text
    }
}
