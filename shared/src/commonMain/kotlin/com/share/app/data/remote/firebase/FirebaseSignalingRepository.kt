package com.share.app.data.remote.firebase

import com.share.app.domain.model.SessionRole
import com.share.app.domain.repository.SignalingMessage
import com.share.app.domain.repository.SignalingRepository
import com.share.app.util.AppLog
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.ChildEvent
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

class FirebaseSignalingRepository : SignalingRepository {

    /** Consumed messages are deleted without holding up the next one. */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun inbox(code: String, role: SessionRole) =
        Firebase.database.reference("sessions/$code/${role.key}Signaling")

    override suspend fun send(code: String, role: SessionRole, message: SignalingMessage) {
        inbox(code, role.peer).push().setValue(SignalingCodec.encode(message))
    }

    override fun observe(code: String, role: SessionRole): Flow<SignalingMessage> =
        inbox(code, role).childEvents(ChildEvent.Type.ADDED)
            .mapNotNull { event ->
                val snapshot = event.snapshot
                cleanupScope.launch { runCatching { snapshot.ref.removeValue() } }
                snapshot.stringOrNull()?.let(SignalingCodec::decode)
            }
            .catch { error -> AppLog.w(error) { "Signalling listener ended" } }

    override suspend fun clear(code: String) {
        inbox(code, SessionRole.HOST).removeValue()
        inbox(code, SessionRole.GUEST).removeValue()
    }

    override suspend fun clearInbox(code: String, role: SessionRole) {
        inbox(code, role).removeValue()
    }
}
