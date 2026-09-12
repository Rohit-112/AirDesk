package com.share.app.domain.policy

import com.share.app.util.UriComponent

/** Limits and wire formats shared with the web client. Change both or neither. */
object SessionLimits {
    const val MAX_FILE_SIZE = 20L * 1024 * 1024

    /** Relayed bytes cost Storage bandwidth, so the relay only carries small files. */
    const val MAX_RELAY_FILE_SIZE = 5L * 1024 * 1024

    const val INACTIVITY_LIMIT_MS = 15L * 60 * 1000
    const val MAX_TEXT_CHARS = 1000
    const val MAX_TRACKED_HISTORY = 100

    /** Every retained payload pins a whole file in memory. */
    const val MAX_DOWNLOADABLE = 5

    private const val TEXT_PREVIEW_LIMIT = 80
    const val DEFAULT_CONTENT_TYPE = "application/octet-stream"

    fun normalizeTextPreview(text: String): String {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return "Clipboard Text"
        if (cleaned.length <= TEXT_PREVIEW_LIMIT) return cleaned
        return cleaned.take(TEXT_PREVIEW_LIMIT - 3) + "..."
    }

    fun safeFileName(name: String): String = name.replace(Regex("[^A-Za-z0-9_.-]+"), "_")

    fun guessContentType(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "zip" -> "application/zip"
            else -> DEFAULT_CONTENT_TYPE
        }
}

/**
 * A relayed file is announced as `FILE:` followed by four URI-encoded fields
 * joined with `|`, encrypted like any other clipboard value.
 */
object FilePayload {
    const val PREFIX = "FILE:"
    private const val SEPARATOR = "|"

    data class Parsed(val storagePath: String, val name: String, val size: Long, val contentType: String)

    fun build(storagePath: String, name: String, size: Long, contentType: String?): String =
        listOf(
            UriComponent.encode(storagePath),
            UriComponent.encode(name),
            size.toString(),
            UriComponent.encode(contentType?.takeIf { it.isNotEmpty() } ?: SessionLimits.DEFAULT_CONTENT_TYPE),
        ).joinToString(SEPARATOR)

    fun parse(payload: String): Parsed? {
        val parts = payload.split(SEPARATOR)
        if (parts.size < 4) return null
        val storagePath = UriComponent.decode(parts[0]) ?: return null
        val name = UriComponent.decode(parts[1]) ?: return null
        val contentType = UriComponent.decode(parts[3]).orEmpty()
        val size = parts[2].toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong() ?: return null
        if (storagePath.isEmpty() || name.isEmpty()) return null
        return Parsed(storagePath, name, size, contentType.ifEmpty { SessionLimits.DEFAULT_CONTENT_TYPE })
    }
}

/** Framing on the WebRTC data channel. Control messages are text frames. */
object FileTransferProtocol {
    const val CHUNK_SIZE = 16 * 1024
    const val END_MESSAGE = "END"
    const val DISCONNECT_MESSAGE = "DISCONNECT"
    private const val NAME_PREFIX = "NAME:"

    fun buildFileNameMessage(fileName: String): String = NAME_PREFIX + fileName

    fun parseFileNameMessage(value: String): String? =
        if (value.startsWith(NAME_PREFIX)) value.removePrefix(NAME_PREFIX) else null
}
