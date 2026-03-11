package com.testproject.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Single source of truth for clipboard text + session.
 * UI observes this. Sync manager observes this.
 */
class SharedViewModel : ViewModel() {

    private val _lastSentText = MutableLiveData<String?>()
    val lastSentText: LiveData<String?> = _lastSentText

    private val _sessionCode = MutableLiveData<String?>()
    val sessionCode: LiveData<String?> = _sessionCode

    private val _isHost = MutableLiveData<Boolean?>()
    val isHost: LiveData<Boolean?> = _isHost

    private val _connected = MutableLiveData(false)
    val connected: LiveData<Boolean> = _connected

    // New: Tracks if the OTHER device is actually online
    private val _peerConnected = MutableLiveData(false)
    val peerConnected: LiveData<Boolean> = _peerConnected

    fun updateText(text: String?) {
        _lastSentText.value = text
    }

    fun setSession(code: String?, host: Boolean) {
        _sessionCode.value = code
        _isHost.value = host
    }

    fun clearSession() {
        _sessionCode.value = null
        _isHost.value = null
        _connected.value = false
        _peerConnected.value = false
    }

    fun setConnected(value: Boolean) {
        _connected.postValue(value)
    }

    fun setPeerConnected(value: Boolean) {
        _peerConnected.postValue(value)
    }
}
