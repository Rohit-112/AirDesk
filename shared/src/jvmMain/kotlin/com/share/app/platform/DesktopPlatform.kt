package com.share.app.platform

import android.app.Application
import co.touchlab.kermit.Logger
import com.google.firebase.FirebasePlatform
import com.share.app.di.handleIncomingLink
import com.share.app.di.initKoin
import com.share.app.domain.session.SessionEngine
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import org.koin.mp.KoinPlatform
import java.io.File
import java.util.Properties

internal object DesktopPaths {
    val dataDir: File by lazy {
        File(System.getProperty("user.home"), ".knotic").apply { mkdirs() }
    }
}

/**
 * Firebase settings for the desktop build. There is no google-services file on
 * the JVM, so these are read from a properties file shipped with the app.
 */
data class DesktopFirebaseConfig(
    val applicationId: String,
    val apiKey: String,
    val projectId: String,
    val databaseUrl: String,
    val storageBucket: String,
    val gcmSenderId: String,
    val authDomain: String,
) {
    companion object {
        fun from(properties: Properties): DesktopFirebaseConfig {
            fun value(key: String) = properties.getProperty(key)?.trim().orEmpty()
            return DesktopFirebaseConfig(
                applicationId = value("firebase.appId"),
                apiKey = value("firebase.apiKey"),
                projectId = value("firebase.projectId"),
                databaseUrl = value("firebase.databaseUrl"),
                storageBucket = value("firebase.storageBucket"),
                gcmSenderId = value("firebase.messagingSenderId"),
                authDomain = value("firebase.authDomain"),
            )
        }
    }
}

/** Desktop entry points into the shared module. */
object KnoticDesktop {

    fun initialize(firebase: DesktopFirebaseConfig, args: Array<String>) {
        FirebasePlatform.initializeFirebasePlatform(PropertiesFirebasePlatform(DesktopPaths.dataDir))
        Firebase.initialize(
            Application(),
            FirebaseOptions(
                applicationId = firebase.applicationId,
                apiKey = firebase.apiKey,
                databaseUrl = firebase.databaseUrl,
                storageBucket = firebase.storageBucket,
                projectId = firebase.projectId,
                gcmSenderId = firebase.gcmSenderId,
                authDomain = firebase.authDomain,
            ),
        )
        initKoin()
        // `Knotic --code 123456`, or a join URL passed by the OS.
        args.firstOrNull { it.any(Char::isDigit) }?.let(::handleIncomingLink)
    }

    /** Clears presence before the window goes away. Bounded, so closing never hangs. */
    suspend fun shutdown() {
        KoinPlatform.getKoinOrNull()?.get<SessionEngine>()?.shutdown()
    }
}

/** Persists the Firebase auth session between launches, like the browser's local storage. */
private class PropertiesFirebasePlatform(private val directory: File) : FirebasePlatform() {
    private val file = directory.resolve("firebase-platform.properties")
    private val log = Logger.withTag("Firebase")
    private val properties = Properties().apply {
        if (file.exists()) file.inputStream().use(::load)
    }

    @Synchronized
    override fun store(key: String, value: String) {
        properties.setProperty(key, value)
        save()
    }

    @Synchronized
    override fun retrieve(key: String): String? = properties.getProperty(key)

    @Synchronized
    override fun clear(key: String) {
        properties.remove(key)
        save()
    }

    override fun log(msg: String) {
        log.d { msg }
    }

    override fun getDatabasePath(name: String): File = directory.resolve(name)

    private fun save() {
        file.outputStream().use { properties.store(it, "Knotic desktop Firebase state") }
    }
}
