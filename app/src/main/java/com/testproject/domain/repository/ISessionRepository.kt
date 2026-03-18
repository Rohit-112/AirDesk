package com.testproject.domain.repository

import com.google.firebase.database.ValueEventListener

interface ISessionRepository {
    fun createSession(deviceId: String, onResult: (String?) -> Unit)
    fun joinSession(code: String, deviceId: String, onResult: (Boolean, String?) -> Unit)
    fun deleteSession(code: String)
    fun observeClipboard(code: String, node: String, onData: (String?) -> Unit): ValueEventListener
    fun observePeerPresence(code: String, node: String, onStatus: (Boolean) -> Unit): ValueEventListener
    fun removeListener(code: String, node: String, listener: ValueEventListener)
    fun writeClipboard(code: String, node: String, text: String)
}
