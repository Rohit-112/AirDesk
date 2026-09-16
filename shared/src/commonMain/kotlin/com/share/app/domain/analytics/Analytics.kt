package com.share.app.domain.analytics

import com.share.app.domain.media.FileKind
import com.share.app.domain.media.ImageFormat

/**
 * Parameter names, collected so the same fact is never sent under two
 * spellings - a second spelling silently splits a report in two.
 */
object AnalyticsParam {
    const val SCREEN_NAME = "screen_name"
    const val METHOD = "method"
    const val ROLE = "role"
    const val ROUTE = "route"
    const val KIND = "kind"
    const val FORMAT = "format"
    const val SOURCE = "source"
}

/** How a code reached the join: typed, scanned in the app, or opened from a link. */
enum class JoinMethod(val value: String) { CODE("code"), SCAN("scan"), LINK("link") }

/** Which path a file took. */
enum class FileRoute(val value: String) { DIRECT("direct"), RELAY("relay") }

/** Where a conversion was started from. */
enum class ConversionSource(val value: String) { INBOX("inbox"), ACTIVITY("activity"), CONVERT_SCREEN("convert") }

/**
 * Every event this app is allowed to send.
 *
 * A closed set rather than a free-form `log(name, params)`, because Firebase
 * allows 500 event names per app and silently drops the rest, and because
 * parameter values must stay bounded. Nothing here may ever carry the text
 * that was sent, a file name or a pairing code: those are what the user
 * trusted this app with, and an analytics backend is a third party.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap()) {

    /** Uses Firebase's reserved name, so it feeds the built-in screen reports. */
    data class ScreenView(val screen: String) : AnalyticsEvent("screen_view", mapOf(AnalyticsParam.SCREEN_NAME to screen))

    data class SessionJoined(val method: JoinMethod) :
        AnalyticsEvent("session_joined", mapOf(AnalyticsParam.METHOD to method.value))

    /** The other device appeared: the moment the product has done its first job. */
    data class DevicesLinked(val hosting: Boolean) :
        AnalyticsEvent("devices_linked", mapOf(AnalyticsParam.ROLE to if (hosting) "host" else "guest"))

    data object TextSent : AnalyticsEvent("text_sent")

    data class FileSent(val route: FileRoute, val kind: FileKind) :
        AnalyticsEvent("file_sent", mapOf(AnalyticsParam.ROUTE to route.value, AnalyticsParam.KIND to kind.value))

    data class FileSendFailed(val route: FileRoute) :
        AnalyticsEvent("file_send_failed", mapOf(AnalyticsParam.ROUTE to route.value))

    data class FileReceived(val route: FileRoute, val kind: FileKind) :
        AnalyticsEvent("file_received", mapOf(AnalyticsParam.ROUTE to route.value, AnalyticsParam.KIND to kind.value))

    /** A received file that arrived short of the size its sender announced. */
    data object FileIncomplete : AnalyticsEvent("file_incomplete")

    data class ImageConverted(val format: ImageFormat, val source: ConversionSource) : AnalyticsEvent(
        "image_converted",
        mapOf(AnalyticsParam.FORMAT to format.extension, AnalyticsParam.SOURCE to source.value),
    )

    data class ImageConversionFailed(val format: ImageFormat) :
        AnalyticsEvent("image_conversion_failed", mapOf(AnalyticsParam.FORMAT to format.extension))

    /** Closed after 15 minutes without activity. */
    data object SessionTimedOut : AnalyticsEvent("session_timed_out")
}

private val FileKind.value: String get() = name.lowercase()

/**
 * Product analytics. An interface so the domain never reaches into Firebase,
 * tests can assert on what was sent, and desktop - where Firebase has no
 * analytics - simply sends nothing.
 */
interface AnalyticsLogger {
    fun log(event: AnalyticsEvent)
}

/**
 * Crash and non-fatal reporting.
 *
 * Kept apart from [AnalyticsLogger] because they answer different questions:
 * what people do, and what broke. Mixing them is how expected failures - a
 * dropped connection, a cancelled save - end up counted as crashes.
 */
interface CrashReporter {
    /** A breadcrumb shown with whatever report comes next. */
    fun log(message: String)

    /** Only for a failure that should not have happened. */
    fun recordException(throwable: Throwable)
}

object NoOpAnalyticsLogger : AnalyticsLogger {
    override fun log(event: AnalyticsEvent) = Unit
}

object NoOpCrashReporter : CrashReporter {
    override fun log(message: String) = Unit
    override fun recordException(throwable: Throwable) = Unit
}
