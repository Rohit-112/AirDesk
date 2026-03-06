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

    fun shutdown() {
        removeRemoteListener()
        val code = currentSession
        if (currentIsHost && !code.isNullOrEmpty()) {
            repo.deleteSession(code)
        }
        job.cancelChildren()
        job.cancel()
        clipboardMonitor.stop()
    }

    private suspend fun handleSessionChange(code: String?, isHost: Boolean) {
        if (code == currentSession && isHost == currentIsHost) return
        
        if (currentIsHost && !currentSession.isNullOrEmpty() && code != currentSession) {
            repo.deleteSession(currentSession!!)
        }

        removeRemoteListener()
        currentSession = code
        currentIsHost = isHost

        if (code.isNullOrEmpty()) {
            viewModel.setConnected(false)
            return
        }

        if (isHost) {
            val ok = suspendCoroutine<Boolean> { cont ->
                repo.ensureSessionNode(code) { cont.resume(it) }
            }
            if (ok) {
                viewModel.setConnected(true)
                attachRemoteListener(code, "guestClipboard")
            } else {
                viewModel.setConnected(false)
            }
        } else {
            viewModel.setConnected(true)
            attachRemoteListener(code, "hostClipboard")
        }
    }

    private fun sendLocalToFirebase(text: String) {
        val code = currentSession ?: return
        val node = if (currentIsHost) "hostClipboard" else "guestClipboard"
        repo.writeClipboard(code, node, text)
    }

    private fun attachRemoteListener(code: String, remoteNode: String) {
        val ref = FirebaseDatabase.getInstance().getReference("sessions").child(code).child(remoteNode)
        remoteListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val text = snapshot.getValue(String::class.java)
                if (!text.isNullOrEmpty()) {
                    clipboardMonitor.setClipboardProgrammatically(text)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                viewModel.setConnected(false)
            }
        }
        ref.addValueEventListener(remoteListener!!)
    }

    private fun removeRemoteListener() {
        val code = currentSession ?: return
        val remoteNode = if (currentIsHost) "guestClipboard" else "hostClipboard"
        if (remoteListener == null) return
        FirebaseDatabase.getInstance().getReference("sessions").child(code).child(remoteNode)
            .removeEventListener(remoteListener!!)
        remoteListener = null
    }
}
