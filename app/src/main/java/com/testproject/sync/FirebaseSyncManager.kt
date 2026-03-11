package com.testproject.sync

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.testproject.data.FirebaseRepository
import com.testproject.viewmodel.SharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

class FirebaseSyncManager(
    private val context: Context,
    private val viewModel: SharedViewModel,
    private val clipboardMonitor: ClipboardMonitor,
    private val repo: FirebaseRepository = FirebaseRepository()
) {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var currentSession: String? = null
    private var currentIsHost: Boolean = true
    private var remoteClipboardListener: ValueEventListener? = null
    private var peerPresenceListener: ValueEventListener? = null

    fun bind(lifecycleOwner: LifecycleOwner) {
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

    fun shutdown() {
        removeRemoteListeners()
        val code = currentSession
        if (currentIsHost && !code.isNullOrEmpty()) {
            repo.deleteSession(code)
        }
        job.cancelChildren()
        job.cancel()
        clipboardMonitor.stop()
    }

    private fun handleSessionChange(code: String?, isHost: Boolean) {
        if (code == currentSession && isHost == currentIsHost) return
        
        removeRemoteListeners()
        currentSession = code
        currentIsHost = isHost

        if (code.isNullOrEmpty()) {
            viewModel.setConnected(false)
            viewModel.setPeerConnected(false)
            return
        }

        viewModel.setConnected(true)

        // Mapping based on FirebaseRepository shorthands
        // ho -> hostOnline, go -> guestOnline
        attachPeerPresenceListener(code, isHost)
        
        // hc -> hostClipboard, gc -> guestClipboard
        val remoteNode = if (isHost) "gc" else "hc"
        attachRemoteClipboardListener(code, remoteNode)
    }

    private fun sendLocalToFirebase(text: String) {
        val code = currentSession ?: return
        val node = if (currentIsHost) "hc" else "gc"
        repo.writeClipboard(code, node, text)
    }

    private fun attachRemoteClipboardListener(code: String, remoteNode: String) {
        val ref = FirebaseDatabase.getInstance().getReference("s").child(code).child(remoteNode)
        remoteClipboardListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val text = snapshot.getValue(String::class.java)
                if (!text.isNullOrEmpty()) {
                    clipboardMonitor.setClipboardProgrammatically(text)
                    // Zero-trace: clear the text from Firebase immediately after receipt
                    repo.writeClipboard(code, remoteNode, "") 
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(remoteClipboardListener!!)
    }

    private fun attachPeerPresenceListener(code: String, isHost: Boolean) {
        val peerNode = if (isHost) "go" else "ho"
        val ref = FirebaseDatabase.getInstance().getReference("s").child(code).child(peerNode)
        
        peerPresenceListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isOnline = snapshot.getValue(Boolean::class.java) ?: false
                viewModel.setPeerConnected(isOnline)
            }
            override fun onCancelled(error: DatabaseError) {
                viewModel.setPeerConnected(false)
            }
        }
        ref.addValueEventListener(peerPresenceListener!!)
    }

    private fun removeRemoteListeners() {
        val code = currentSession ?: return
        val sessionRef = FirebaseDatabase.getInstance().getReference("s").child(code)
        
        remoteClipboardListener?.let {
            val remoteNode = if (currentIsHost) "gc" else "hc"
            sessionRef.child(remoteNode).removeEventListener(it)
        }
        
        peerPresenceListener?.let {
            val peerNode = if (currentIsHost) "go" else "ho"
            sessionRef.child(peerNode).removeEventListener(it)
        }
        
        remoteClipboardListener = null
        peerPresenceListener = null
    }
}
