package com.share.app.domain.model

enum class HistoryAction(val label: String) {
    SENT_TEXT("Sent Text"),
    RECEIVED_TEXT("Received Text"),
    SENT_FILE("Sent File"),
    RECEIVED_FILE("Received File"),
    FAILED_UPLOAD("Failed Upload");

    val isOutgoing: Boolean get() = this == SENT_TEXT || this == SENT_FILE || this == FAILED_UPLOAD
}

enum class HistoryStatus { SUCCESS, FAILED }

/** One row of the activity log. Nothing here outlives the app process. */
data class HistoryItem(
    val id: String,
    val action: HistoryAction,
    /** The file name, or a one-line preview of the text. */
    val title: String,
    val timestampMillis: Long,
    val size: Long,
    val status: HistoryStatus,
    /** The message itself, so an older one can still be copied. */
    val text: String? = null,
    /** A received file whose bytes are still held, so it can be saved again. */
    val hasPayload: Boolean = false,
)

enum class ThemePreference(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    val next: ThemePreference
        get() = when (this) {
            SYSTEM -> LIGHT
            LIGHT -> DARK
            DARK -> SYSTEM
        }

    companion object {
        fun fromKey(key: String?): ThemePreference = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
