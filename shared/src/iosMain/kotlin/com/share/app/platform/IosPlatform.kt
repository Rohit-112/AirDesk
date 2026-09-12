@file:OptIn(ExperimentalForeignApi::class)

package com.share.app.platform

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import com.share.app.App
import com.share.app.di.handleIncomingLink
import com.share.app.di.initKoin
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIViewController
import platform.posix.memcpy

private var initialized = false

/**
 * iOS entry point. Configures Firebase from GoogleService-Info.plist, starts DI
 * and the session engine, and returns the shared Compose UI.
 */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    if (!initialized) {
        initialized = true
        Firebase.initialize()
        initKoin()
    }
    return ComposeUIViewController { App() }
}

/** From `onOpenURL`: a `knotic://join?code=` or universal link. */
@Suppress("unused")
fun handleOpenUrl(url: String) = handleIncomingLink(url)

@OptIn(BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.convert()) }
}

internal fun NSData.toByteArray(): ByteArray {
    val length = length.toInt()
    if (length == 0) return ByteArray(0)
    return ByteArray(length).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length) }
    }
}
