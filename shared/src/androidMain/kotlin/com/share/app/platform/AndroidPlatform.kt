package com.share.app.platform

import android.app.Application
import android.content.Intent
import androidx.activity.ComponentActivity
import com.share.app.di.handleIncomingLink
import com.share.app.di.initKoin
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import org.koin.android.ext.koin.androidContext

/** Android entry points into the shared module. */
object KnoticAndroid {
    /** From `Application.onCreate`. Firebase initialises itself from google-services.json. */
    fun initialize(application: Application) {
        initKoin { androidContext(application) }
    }

    /** From `Activity.onCreate`, before `setContent`. */
    fun attach(activity: ComponentActivity) {
        FileKit.init(activity)
        handleIntent(activity.intent)
    }

    /** A `https://getknotic.web.app/?code=` or `knotic://join?code=` link. */
    fun handleIntent(intent: Intent?) {
        intent?.dataString?.let(::handleIncomingLink)
    }
}
