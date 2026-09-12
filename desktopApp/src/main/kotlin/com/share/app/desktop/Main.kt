package com.share.app.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.share.app.App
import com.share.app.config.Brand
import com.share.app.platform.DesktopFirebaseConfig
import com.share.app.platform.KnoticDesktop
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import java.util.Properties

fun main(args: Array<String>) {
    val firebase = Properties().apply {
        load(readResource("firebase.properties").inputStream())
    }
    KnoticDesktop.initialize(DesktopFirebaseConfig.from(firebase), args)

    val icon = loadAppIcon()

    application {
        Window(
            title = Brand.NAME,
            // The window, taskbar and Alt-Tab icon. The installed launcher takes
            // the same mark from icons/knotic.ico.
            icon = icon,
            state = rememberWindowState(width = 520.dp, height = 860.dp),
            onCloseRequest = {
                // Clear presence first; bounded so closing never hangs.
                runBlocking { KnoticDesktop.shutdown() }
                exitApplication()
            },
        ) {
            App()
        }
    }
}

private fun loadAppIcon(): Painter =
    BitmapPainter(Image.makeFromEncoded(readResource("knotic-icon.png")).toComposeImageBitmap())

private fun readResource(name: String): ByteArray {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(name)
        ?: error("$name is missing from desktopApp/src/main/resources")
    return stream.use { it.readBytes() }
}
