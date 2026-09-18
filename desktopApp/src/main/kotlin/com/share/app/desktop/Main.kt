package com.share.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.util.Properties
import kotlin.system.exitProcess

/**
 * Only two things happen before the window: the object graph, and the icon.
 * Firebase, signing in and hosting a code all run on a background thread while
 * Compose is still starting up, because none of them draw anything - and doing
 * them first held the window back by half a second.
 */
fun main(args: Array<String>) {
    KnoticDesktop.startDependencies()
    val icon = loadAppIcon()

    application {
        var ready by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val firebase = Properties().apply {
                    load(readResource("firebase.properties").inputStream())
                }
                KnoticDesktop.startBackend(DesktopFirebaseConfig.from(firebase), args)
            }
            ready = true
        }

        Window(
            title = Brand.NAME,
            // The window, taskbar and Alt-Tab icon. The installed launcher takes
            // the same mark from icons/knotic.ico.
            icon = icon,
            state = rememberWindowState(width = 520.dp, height = 860.dp),
            /**
             * Closing does no cleanup of its own, so the window goes the moment
             * it is asked to. The session already carries instructions the
             * server runs as soon as this connection drops - the same ones that
             * cover the app being killed or the machine losing power - and
             * waiting for a tidier goodbye only ever made closing feel broken.
             */
            onCloseRequest = { exitProcess(0) },
        ) {
            if (ready) App() else StartupBackground()
        }
    }
}

/**
 * The window's own background, in the theme the system asks for, for the
 * moment before the app is ready. Anything more would flash and be gone.
 */
@Composable
private fun StartupBackground() {
    val colour = if (isSystemInDarkTheme()) Color(0xFF08090C) else Color(0xFFF6F7F9)
    Box(Modifier.fillMaxSize().background(colour))
}

private fun loadAppIcon(): Painter =
    BitmapPainter(Image.makeFromEncoded(readResource("knotic-icon.png")).toComposeImageBitmap())

private fun readResource(name: String): ByteArray {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(name)
        ?: error("$name is missing from desktopApp/src/main/resources")
    return stream.use { it.readBytes() }
}

