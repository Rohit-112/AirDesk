package com.share.app.data.analytics

import com.share.app.domain.analytics.AnalyticsEvent
import com.share.app.domain.analytics.AnalyticsLogger
import com.share.app.domain.analytics.CrashReporter
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.crashlytics.crashlytics

/**
 * Firebase Analytics, on Android and iOS.
 *
 * Whether anything is actually collected is decided before this runs: debug
 * Android builds switch collection off in their manifest, because the SDK
 * starts from a content provider before any app code does.
 */
class FirebaseAnalyticsLogger : AnalyticsLogger {
    override fun log(event: AnalyticsEvent) {
        // Reporting must never be the reason a feature fails.
        runCatching { Firebase.analytics.logEvent(event.name, event.params) }
    }
}

class FirebaseCrashReporter : CrashReporter {
    override fun log(message: String) {
        runCatching { Firebase.crashlytics.log(message) }
    }

    override fun recordException(throwable: Throwable) {
        runCatching { Firebase.crashlytics.recordException(throwable) }
    }
}
