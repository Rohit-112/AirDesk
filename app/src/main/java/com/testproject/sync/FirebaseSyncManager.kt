package com.testproject.sync

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.testproject.data.FirebaseRepository
import com.testproject.utils.AppsConst.FB_GUEST_CLIPBOARD
import com.testproject.utils.AppsConst.FB_GUEST_ONLINE
import com.testproject.utils.AppsConst.FB_HOST_CLIPBOARD
import com.testproject.utils.AppsConst.FB_HOST_ONLINE
import com.testproject.utils.AppsConst.FILE_PROTOCOL_PREFIX
import com.testproject.utils.EncryptionHelper
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
    private val encryptionHelper: EncryptionHelper,
    private val repo: FirebaseRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentSession: String? = null
    private var isHost: Boolean = true
    
    private var clipboardListener: com.google.firebase.database.ValueEventListener? = null
    private var presenceListener: com.google.firebase.database.ValueEventListener? = null

    private var lastSentText: String? = null
    private var lastReceivedText: String? = null

    fun bind(lifecycleOwner: LifecycleOwner) {
        viewModel.sessionCode.observe(lifecycleOwner) { code ->
            scope.launch { updateSession(code, viewModel.isHost.value ?: true) }
        }

        viewModel.isHost.observe(lifecycleOwner) { host ->
            scope.launch { updateSession(viewModel.sessionCode.value, host ?: true) }
        }

        viewModel.lastSentText.observe(lifecycleOwner) { text ->
            handleLocalUpdate(text)
        }
    }

    private fun updateSession(code: String?, host: Boolean) {
        if (code == currentSession && host == isHost) return
        
        cleanup()
        currentSession = code
        isHost = host

        if (code.isNullOrEmpty()) {
            viewModel.setConnected(false)
            viewModel.setPeerConnected(false)
            return
        }

        viewModel.setConnected(true)
        
        val remoteNode = if (host) FB_GUEST_CLIPBOARD else FB_HOST_CLIPBOARD
        val presenceNode = if (host) FB_GUEST_ONLINE else FB_HOST_ONLINE

        clipboardListener = repo.observeClipboard(code, remoteNode) { encrypted ->
            handleRemoteUpdate(encrypted, code, remoteNode)
        }

        presenceListener = repo.observePeerPresence(code, presenceNode) { online ->
            viewModel.setPeerConnected(online)
        }
    }

    private fun handleLocalUpdate(text: String?) {
        val cleanText = text?.trim() ?: return
        if (cleanText == lastReceivedText?.trim()) {
            viewModel.updateText(null)
            return
        }

        lastSentText = cleanText
        currentSession?.let { code ->
            val node = if (isHost) FB_HOST_CLIPBOARD else FB_GUEST_CLIPBOARD
            repo.writeClipboard(code, node, encryptionHelper.encrypt(cleanText))
        }
        viewModel.updateText(null)
    }

    private fun handleRemoteUpdate(encrypted: String?, code: String, node: String) {
        if (encrypted.isNullOrEmpty()) return
        
        val decrypted = encryptionHelper.decrypt(encrypted).trim()
        if (decrypted.isEmpty() || decrypted == lastSentText) return

        lastReceivedText = decrypted
        viewModel.setReceivedContent(decrypted)

        if (!decrypted.startsWith(FILE_PROTOCOL_PREFIX)) {
            clipboardMonitor.setClipboardProgrammatically(decrypted)
        }

        repo.writeClipboard(code, node, "")
    }

    fun shutdown() {
        cleanup()
        if (isHost) currentSession?.let { repo.deleteSession(it) }
        scope.coroutineContext.cancelChildren()
        clipboardMonitor.stop()
    }

    private fun cleanup() {
        val code = currentSession ?: return
        val remoteNode = if (isHost) FB_GUEST_CLIPBOARD else FB_HOST_CLIPBOARD
        val presenceNode = if (isHost) FB_GUEST_ONLINE else FB_HOST_ONLINE

        clipboardListener?.let { repo.removeListener(code, remoteNode, it) }
        presenceListener?.let { repo.removeListener(code, presenceNode, it) }
        
        clipboardListener = null
        presenceListener = null
    }
}
