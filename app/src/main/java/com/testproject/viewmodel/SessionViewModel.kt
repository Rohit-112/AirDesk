package com.testproject.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.ValueEventListener
import com.testproject.domain.usecase.*
import com.testproject.utils.AppsConst.FB_GUEST_CLIPBOARD
import com.testproject.utils.AppsConst.FB_GUEST_ONLINE
import com.testproject.utils.AppsConst.FB_HOST_CLIPBOARD
import com.testproject.utils.AppsConst.FB_HOST_ONLINE
import com.testproject.utils.EncryptionHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val createSessionUseCase: CreateSessionUseCase,
    private val joinSessionUseCase: JoinSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    private val saveSessionUseCase: SaveSessionUseCase,
    private val getSessionCodeUseCase: GetSessionCodeUseCase,
    private val isHostUseCase: IsHostUseCase,
    private val clearPersistedSessionUseCase: ClearPersistedSessionUseCase,
    private val observeClipboardUseCase: ObserveClipboardUseCase,
    private val observePeerPresenceUseCase: ObservePeerPresenceUseCase,
    private val writeClipboardUseCase: WriteClipboardUseCase,
    private val removeSessionListenerUseCase: RemoveSessionListenerUseCase,
    private val encryptionHelper: EncryptionHelper
) : ViewModel() {

    private val _sessionCode = MutableLiveData<String?>()
    val sessionCode: LiveData<String?> = _sessionCode

    private val _isHost = MutableLiveData<Boolean>()
    val isHost: LiveData<Boolean> = _isHost

    private val _peerOnline = MutableLiveData<Boolean>()
    val peerOnline: LiveData<Boolean> = _peerOnline

    private val _receivedContent = MutableLiveData<String?>()
    val receivedContent: LiveData<String?> = _receivedContent

    private val _inactivityTimeout = MutableLiveData<Boolean>()
    val inactivityTimeout: LiveData<Boolean> = _inactivityTimeout

    private var lastSentEncrypted: String? = null
    private var currentlyObservingCode: String? = null

    private var clipboardListener: ValueEventListener? = null
    private var presenceListener: ValueEventListener? = null
    
    private var inactivityJob: Job? = null
    private val INACTIVITY_DELAY = 15 * 60 * 1000L // 15 minutes

    fun createSession(deviceId: String, onResult: (String?) -> Unit) {
        cleanupListeners()
        _sessionCode.value = null
        
        createSessionUseCase(deviceId) { code ->
            if (code != null) {
                viewModelScope.launch {
                    saveSessionUseCase(code, true)
                    _isHost.postValue(true)
                    _sessionCode.postValue(code)
                    startObserving(code, true)
                    resetInactivityTimer()
                }
            }
            onResult(code)
        }
    }

    fun joinSession(code: String, deviceId: String, onResult: (Boolean, String?) -> Unit) {
        cleanupListeners()
        _sessionCode.value = null

        joinSessionUseCase(code, deviceId) { success, error ->
            if (success) {
                viewModelScope.launch {
                    saveSessionUseCase(code, false)
                    _isHost.postValue(false)
                    _sessionCode.postValue(code)
                    startObserving(code, false)
                    resetInactivityTimer()
                }
            }
            onResult(success, error)
        }
    }

    private fun startObserving(code: String, isHost: Boolean) {
        if (currentlyObservingCode == code) return
        
        cleanupListeners()
        currentlyObservingCode = code
        
        val remoteNode = if (isHost) FB_GUEST_CLIPBOARD else FB_HOST_CLIPBOARD
        val presenceNode = if (isHost) FB_GUEST_ONLINE else FB_HOST_ONLINE

        clipboardListener = observeClipboardUseCase(code, remoteNode) { encrypted ->
            // Fix: Check if encrypted is not null AND actually has content to decrypt
            // This prevents clearing an already empty node and causing an infinite loop
            if (!encrypted.isNullOrEmpty() && encrypted != lastSentEncrypted) {
                val decrypted = encryptionHelper.decrypt(encrypted)
                if (decrypted.isNotEmpty()) {
                    _receivedContent.postValue(decrypted)
                    // We only clear if we actually received something meaningful
                    clearRemoteNode(code, remoteNode)
                    resetInactivityTimer()
                }
            }
        }

        presenceListener = observePeerPresenceUseCase(code, presenceNode) { online ->
            _peerOnline.postValue(online)
            if (online) resetInactivityTimer()
        }
    }

    private fun clearRemoteNode(code: String, node: String) {
        // Fix: Use a check to ensure we don't clear if it's already empty
        writeClipboardUseCase(code, node, "")
    }

    fun sendContent(content: String) {
        val code = _sessionCode.value ?: return
        val isHost = _isHost.value ?: return
        val localNode = if (isHost) FB_HOST_CLIPBOARD else FB_GUEST_CLIPBOARD
        
        val encrypted = encryptionHelper.encrypt(content)
        if (encrypted.isNotEmpty()) {
            lastSentEncrypted = encrypted
            writeClipboardUseCase(code, localNode, encrypted)
            resetInactivityTimer()
        }
    }

    fun deleteSession(code: String) {
        cleanupListeners()
        deleteSessionUseCase(code)
        viewModelScope.launch {
            clearPersistedSessionUseCase()
            _sessionCode.postValue(null)
            _isHost.postValue(false)
            _peerOnline.postValue(false)
        }
    }

    private fun cleanupListeners() {
        inactivityJob?.cancel()
        val code = currentlyObservingCode ?: return
        val isHost = _isHost.value ?: true
        
        val remoteNode = if (isHost) FB_GUEST_CLIPBOARD else FB_HOST_CLIPBOARD
        val presenceNode = if (isHost) FB_GUEST_ONLINE else FB_HOST_ONLINE

        clipboardListener?.let { removeSessionListenerUseCase(code, remoteNode, it) }
        presenceListener?.let { removeSessionListenerUseCase(code, presenceNode, it) }
        
        clipboardListener = null
        presenceListener = null
        currentlyObservingCode = null
    }

    fun loadPersistedSession() {
        if (currentlyObservingCode != null) return
        
        viewModelScope.launch {
            val code = getSessionCodeUseCase()
            val host = isHostUseCase()
            if (code != null) {
                _isHost.value = host
                _sessionCode.value = code
                startObserving(code, host)
                resetInactivityTimer()
            }
        }
    }
    
    fun consumeReceivedContent() {
        _receivedContent.value = null
    }

    fun resetInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch {
            delay(INACTIVITY_DELAY)
            if (_sessionCode.value != null) {
                _inactivityTimeout.postValue(true)
                deleteSession(_sessionCode.value!!)
            }
        }
    }

    fun consumeInactivityTimeout() {
        _inactivityTimeout.value = false
    }

    override fun onCleared() {
        super.onCleared()
        cleanupListeners()
    }
}
