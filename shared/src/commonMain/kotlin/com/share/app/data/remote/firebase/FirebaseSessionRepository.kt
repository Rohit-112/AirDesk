package com.share.app.data.remote.firebase

import com.share.app.domain.model.SessionRole
import com.share.app.domain.repository.SessionRecord
import com.share.app.domain.repository.SessionRemoteRepository
import com.share.app.util.AppLog
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.DataSnapshot
import dev.gitlive.firebase.database.DatabaseReference
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FirebaseSessionRepository : SessionRemoteRepository {

    private fun session(code: String): DatabaseReference = Firebase.database.reference("sessions/$code")

    override fun observeConnection(): Flow<Boolean> =
        Firebase.database.reference(".info/connected").valueEvents
            .map { it.booleanOrFalse() }
            .catch { error ->
                AppLog.w(error) { "Connection state listener failed" }
                emit(false)
            }
            .distinctUntilChanged()

    override suspend fun getSession(code: String): SessionRecord? {
        val snapshot = session(code).get()
        if (!snapshot.exists) return null
        return SessionRecord(
            hostId = snapshot.child("hostId").stringOrNull(),
            hostDeviceId = snapshot.child("hostDeviceId").stringOrNull(),
            hostOnline = snapshot.child("hostOnline").booleanOrFalse(),
            guestId = snapshot.child("guestId").stringOrNull(),
            guestOnline = snapshot.child("guestOnline").booleanOrFalse(),
            hostClipboard = snapshot.child("hostClipboard").stringOrNull(),
            guestClipboard = snapshot.child("guestClipboard").stringOrNull(),
        )
    }

    override suspend fun updateSession(code: String, fields: Map<String, Any?>) {
        session(code).updateChildren(fields)
    }

    override suspend fun removeSession(code: String) {
        session(code).removeValue()
    }

    // A listener that loses permission (the session now belongs to two other
    // accounts) just ends, as it does in the browser.
    override fun observeString(code: String, field: String): Flow<String?> =
        session(code).child(field).valueEvents
            .map { it.stringOrNull() }
            .catch { error -> AppLog.w(error) { "Listener on $field ended" } }

    override fun observeBoolean(code: String, field: String): Flow<Boolean> =
        session(code).child(field).valueEvents
            .map { it.booleanOrFalse() }
            .catch { error -> AppLog.w(error) { "Listener on $field ended" } }

    override suspend fun registerDisconnectCleanup(code: String, role: SessionRole) {
        // Removed rather than set to false: if the other device has already
        // deleted the session, writing a value recreates the node as a shell
        // holding one empty field - which is how a database fills up with codes
        // nobody is connected to. Removing a path that is already gone does
        // nothing, and both clients read an absent flag as offline.
        session(code).child("${role.key}Online").onDisconnect().removeValue()
        session(code).child("${role.key}Clipboard").onDisconnect().removeValue()
    }

    override suspend fun cancelDisconnectCleanup(code: String, role: SessionRole) {
        session(code).child("${role.key}Online").onDisconnect().cancel()
        session(code).child("${role.key}Clipboard").onDisconnect().cancel()
    }

    override suspend fun registerSessionRemovalOnDisconnect(code: String) {
        session(code).onDisconnect().removeValue()
    }

    override suspend fun cancelSessionRemovalOnDisconnect(code: String) {
        session(code).onDisconnect().cancel()
    }
}

internal fun DataSnapshot.stringOrNull(): String? =
    if (!exists) null else runCatching { value<String?>() }.getOrNull()

internal fun DataSnapshot.booleanOrFalse(): Boolean =
    if (!exists) false else runCatching { value<Boolean?>() }.getOrNull() == true
