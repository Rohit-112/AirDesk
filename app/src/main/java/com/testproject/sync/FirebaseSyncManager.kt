package com.testproject.sync

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.testproject.data.FirebaseRepository
import com.testproject.viewmodel.SharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Coroutine-based, lifecycle-aware Firebase sync manager.
 *
 * Responsibilities:
 * - Observes SharedViewModel (sessionCode, isHost, lastSentText) via lifecycleScope and reacts
 * - Writes local clipboard text to the correct firebase node using suspending functions
 * - Attaches/removes ValueEventListener for remote updates (guestClipboard/hostClipboard)
 * - On remote update: programmatically set clipboard (ClipboardMonitor handles suppression)
 *
 * Usage:
 *   val manager = FirebaseSyncManager(context, viewModel, clipboardMonitor)
 *   manager.bind(lifecycleOwner)
 *   ...
 *   manager.shutdown() // when you want to stop listeners and cancel coroutines
 */
class FirebaseSyncManager(
    private val context: Context,
    private val viewModel: SharedViewModel,
    private val clipboardMonitor: ClipboardMonitor,
    private val repo: FirebaseRepository = FirebaseRepository()
) {

    private val TAG = "FirebaseSyncManager"

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var currentSession: String? = null
    private var currentIsHost: Boolean = true
    private var remoteListener: ValueEventListener? = null

    private var dbRef: DatabaseReference? = null

    /**
     * Bind manager to lifecycleOwner. Observers are attached to lifecycleOwner.lifecycleScope,
     * which avoids leaks when the owner is destroyed.
     */
    fun bind(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            viewModel.sessionCode.observe(lifecycleOwner) { code ->
                scope.launch { handleSessionChange(code, viewModel.isHost.value ?: true) }
            }

            viewModel.isHost.observe(lifecycleOwner) { host ->
                scope.launch { handleSessionChange(viewModel.sessionCode.value, host ?: true) }
            }

            viewModel.lastSentText.observe(lifecycleOwner) { text ->
                if (!text.isNullOrEmpty()) {
                    scope.launch { sendLocalToFirebase(text) }
                }
            }
        }
    }

    /**
     * Gracefully stop everything: remove firebase listeners and cancel coroutines.
     */
    fun shutdown() {
        Log.d(TAG, "shutdown: removing listener and cancelling scope")
        removeRemoteListener()
        job.cancelChildren()
        job.cancel()
        clipboardMonitor.stop()
    }

    private suspend fun handleSessionChange(code: String?, isHost: Boolean) {
        if (code == currentSession && isHost == currentIsHost) return

        Log.d(TAG, "handleSessionChange -> code=$code isHost=$isHost")

        removeRemoteListener()

        currentSession = code
        currentIsHost = isHost

        if (code.isNullOrEmpty()) {
            Log.d(TAG, "session cleared")
            viewModel.setConnected(false)
            return
        }

        dbRef = FirebaseDatabase.getInstance().getReference("sessions").child(code)

        if (isHost) {
            try {
                val ok = ensureSessionNodeSuspend(code)
                if (ok) {
                    viewModel.setConnected(true)
                    attachRemoteListener(code, "guestClipboard")
                } else {
                    viewModel.setConnected(false)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "handleSessionChange - ensureSession failed", t)
                viewModel.setConnected(false)
            }
        } else {
            viewModel.setConnected(true)
            attachRemoteListener(code, "hostClipboard")
        }
    }

    private suspend fun ensureSessionNodeSuspend(code: String): Boolean = suspendCoroutine { cont ->
        repo.ensureSessionNode(code) { success ->
            cont.resume(success)
        }
    }

    private suspend fun writeClipboardSuspend(code: String, node: String, text: String): Boolean =
        suspendCoroutine { cont ->
            repo.writeClipboard(code, node, text) { success ->
                cont.resume(success)
            }
        }

    private fun sendLocalToFirebase(text: String) {
        val code = currentSession
        if (code.isNullOrEmpty()) {
            Log.w(TAG, "sendLocalToFirebase: no session set; ignoring local text")
            return
        }

        val node = if (currentIsHost) "hostClipboard" else "guestClipboard"
        Log.d(TAG, "sendLocalToFirebase -> sessions/$code/$node : $text")

        scope.launch {
            try {
                val ok = writeClipboardSuspend(code, node, text)
                if (!ok) Log.w(TAG, "sendLocalToFirebase: write failed for $code/$node")
            } catch (t: Throwable) {
                Log.e(TAG, "sendLocalToFirebase: exception", t)
            }
        }
    }

    private fun attachRemoteListener(code: String, remoteNode: String) {
        Log.d(TAG, "attachRemoteListener -> $code/$remoteNode")
        val ref =
            FirebaseDatabase.getInstance().getReference("sessions").child(code).child(remoteNode)
        remoteListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val text = snapshot.getValue(String::class.java)
                Log.d(TAG, "remote [$remoteNode] changed -> $text")
                if (!text.isNullOrEmpty()) {
                    try {
                        clipboardMonitor.setClipboardProgrammatically(text)
                    } catch (t: Throwable) {
                        Log.e(TAG, "error setting clipboard programmatically", t)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "remote listener cancelled: ${error.message}")
                viewModel.setConnected(false)
            }
        }
        ref.addValueEventListener(remoteListener!!)
    }

    private fun removeRemoteListener() {
        val code = currentSession ?: return
        val remoteNode = if (currentIsHost) "guestClipboard" else "hostClipboard"
        if (remoteListener == null) return

        try {
            Log.d(TAG, "removeRemoteListener -> $code/$remoteNode")
            FirebaseDatabase.getInstance().getReference("sessions").child(code).child(remoteNode)
                .removeEventListener(remoteListener!!)
        } catch (t: Throwable) {
            Log.w(TAG, "removeRemoteListener error", t)
        } finally {
            remoteListener = null
        }
    }
}
