package com.share.app.data.analytics

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.share.app.domain.analytics.CrashReporter

/**
 * Sends log output to crash reporting: informational lines as breadcrumbs, and
 * real failures as non-fatals.
 *
 * Two rules:
 *
 *  1. Verbose and debug lines are dropped. Crashlytics keeps only the last
 *     64 KB of breadcrumbs per report, so a chatty debug line evicts the lines
 *     that explain the crash.
 *
 *  2. A non-fatal is filed only for an error that carries a throwable.
 *     Warnings here are expected failures - a dropped route, a cancelled save,
 *     a file the platform cannot preview - and filing them would bury the real
 *     crashes under noise.
 */
class CrashReportingLogWriter(private val reporter: CrashReporter) : LogWriter() {

    override fun isLoggable(tag: String, severity: Severity): Boolean = severity >= Severity.Info

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        reporter.log("${severity.name.first()}/$tag: $message")
        if (severity >= Severity.Error && throwable != null) {
            reporter.recordException(throwable)
        }
    }
}
