package com.share.app.domain.repository

import com.share.app.domain.model.HistoryItem
import com.share.app.domain.model.OutgoingFile
import com.share.app.domain.model.SessionRole
import com.share.app.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    /** The anonymous account id, or null while signed out. */
    fun observeUserId(): Flow<String?>
    suspend fun signInAnonymously()
}

/** A session node as read once, before joining or hosting it. */
data class SessionRecord(
    val hostId: String?,
    val hostDeviceId: String?,
    val hostOnline: Boolean,
    val guestId: String?,
    val guestOnline: Boolean,
    val hostClipboard: String?,
    val guestClipboard: String?,
)

/** `sessions/{code}` in the Realtime Database - the only data path the app uses. */
interface SessionRemoteRepository {
    fun observeConnection(): Flow<Boolean>
    suspend fun getSession(code: String): SessionRecord?
    suspend fun updateSession(code: String, fields: Map<String, Any?>)
    suspend fun removeSession(code: String)

    /**
     * Watch a single field. Listening to the whole node would ship every
     * signalling write back to both devices.
     */
    fun observeString(code: String, field: String): Flow<String?>
    fun observeBoolean(code: String, field: String): Flow<Boolean>

    /** Server-side presence cleanup for when this device vanishes. */
    suspend fun registerDisconnectCleanup(code: String, role: SessionRole)
    suspend fun cancelDisconnectCleanup(code: String, role: SessionRole)

    /** Deletes the whole session the moment this client's socket goes away. */
    suspend fun registerSessionRemovalOnDisconnect(code: String)
    suspend fun cancelSessionRemovalOnDisconnect(code: String)
}

sealed interface SignalingMessage {
    data class Handshake(val connectionId: String) : SignalingMessage
    data class HandshakeAck(val connectionId: String) : SignalingMessage
    data class Offer(val sdp: String, val connectionId: String?) : SignalingMessage
    data class Answer(val sdp: String, val connectionId: String?) : SignalingMessage
    data class Candidate(
        val sdp: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int?,
        val connectionId: String?,
    ) : SignalingMessage
    data class Disconnect(val connectionId: String?) : SignalingMessage
}

/** WebRTC offer, answer and candidate envelopes, relayed through the session node. */
interface SignalingRepository {
    suspend fun send(code: String, role: SessionRole, message: SignalingMessage)

    /** Messages addressed to [role]. Each is deleted once read. */
    fun observe(code: String, role: SessionRole): Flow<SignalingMessage>
    suspend fun clear(code: String)
}

/** Cloud Storage handoff for files that cannot go peer to peer. */
interface RelayRepository {
    suspend fun upload(path: String, bytes: ByteArray, contentType: String, onProgress: (Long) -> Unit)
    suspend fun download(path: String, maxBytes: Long): ByteArray
    suspend fun delete(path: String)
}

interface PreferencesRepository {
    val themePreference: Flow<ThemePreference>
    suspend fun setThemePreference(preference: ThemePreference)

    /** A stable id for this install, to tell devices apart under one account. */
    suspend fun deviceId(): String
}

/** A short activity log, plus the bytes of the last few received files. */
interface HistoryRepository {
    val history: StateFlow<List<HistoryItem>>
    fun add(item: HistoryItem, payload: ByteArray? = null)
    fun payload(id: String): ByteArray?
}

interface FileSystemRepository {
    /** Null when the user cancels. */
    suspend fun pickFile(): OutgoingFile?

    /** False when the user cancels. */
    suspend fun saveFile(name: String, bytes: ByteArray): Boolean
}

interface ClipboardRepository {
    suspend fun readText(): String?
    suspend fun writeText(text: String)
}
