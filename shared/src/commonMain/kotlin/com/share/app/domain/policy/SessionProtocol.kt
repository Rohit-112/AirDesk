package com.share.app.domain.policy

import com.share.app.util.UriComponent
import kotlin.uuid.Uuid

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

    /**
     * A receive with no chunk for this long is abandoned. Without it, a sender
     * that dies mid-file leaves the receiver holding the partial bytes for good
     * and refusing every later file as "already in progress".
     */
    const val INCOMING_STALL_TIMEOUT_MS = 30_000L

    /**
     * How long a guest that has gone quiet keeps its claim on the session. Long
     * enough for a browser guest to move to another page of the site and come
     * back, short enough that a code is not left unusable by a phone that simply
     * closed.
     */
    const val GUEST_SLOT_GRACE_MS = 90_000L

    private const val TEXT_PREVIEW_LIMIT = 80
    const val DEFAULT_CONTENT_TYPE = "application/octet-stream"

    fun normalizeTextPreview(text: String): String {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return "Clipboard Text"
        if (cleaned.length <= TEXT_PREVIEW_LIMIT) return cleaned
        return cleaned.take(TEXT_PREVIEW_LIMIT - 3) + "..."
    }

    /**
     * The name a relayed file is stored under. Deliberately says nothing about
     * the file: Storage rules cannot tell who is in a session, so this name is
     * the only thing between a relayed file and anyone who guessed the code. It
     * must not be derivable from the file's name or the time it was sent.
     */
    fun createObjectName(): String = Uuid.random().toString()
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

    /**
     * How many bytes the next file will be, sent just before its name.
     *
     * Optional on purpose: the protocol has no length of its own, so a
     * truncated stream used to look like a complete file. A client that does
     * not recognise it - including the 1.0 apps - ignores it, so sending it
     * costs nothing, and a receiver that understands it can reject a short file.
     */
    private const val SIZE_PREFIX = "SIZE:"

    fun buildFileNameMessage(fileName: String): String = NAME_PREFIX + fileName

    fun parseFileNameMessage(value: String): String? =
        if (value.startsWith(NAME_PREFIX)) value.removePrefix(NAME_PREFIX) else null

    fun buildFileSizeMessage(size: Long): String = SIZE_PREFIX + size

    /**
     * A plain non-negative integer, which is all either client ever sends.
     * Anything else is treated as no announcement at all, so the length check
     * is skipped rather than failing a good file.
     */
    fun parseFileSizeMessage(value: String): Long? {
        if (!value.startsWith(SIZE_PREFIX)) return null
        val digits = value.removePrefix(SIZE_PREFIX).trim()
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
        return digits.toLongOrNull()
    }
}
