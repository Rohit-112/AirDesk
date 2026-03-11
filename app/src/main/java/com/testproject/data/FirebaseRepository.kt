package com.testproject.data

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.testproject.utils.AppsConst.FB_GUEST_ID
import com.testproject.utils.AppsConst.FB_GUEST_ONLINE
import com.testproject.utils.AppsConst.FB_HOST_CLIPBOARD
import com.testproject.utils.AppsConst.FB_GUEST_CLIPBOARD
import com.testproject.utils.AppsConst.FB_HOST_ID
import com.testproject.utils.AppsConst.FB_HOST_ONLINE
import com.testproject.utils.AppsConst.FB_SESSIONS
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRepository @Inject constructor() {

    private val sessionsRef: DatabaseReference = FirebaseDatabase.getInstance().getReference(FB_SESSIONS)

    fun createSession(deviceId: String, onResult: (String?) -> Unit) {
        val code = (100000..999999).random().toString()
        
        val initialData = mapOf(
            FB_HOST_ID to deviceId,
            FB_HOST_ONLINE to true,
            FB_GUEST_ONLINE to false,
            FB_HOST_CLIPBOARD to "",
            FB_GUEST_CLIPBOARD to ""
        )

        sessionsRef.child(code).setValue(initialData)
            .addOnSuccessListener {
                Log.d("FirebaseRepo", "Session created successfully: $code")
                sessionsRef.child(code).onDisconnect().removeValue()
                onResult(code)
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseRepo", "Failed to create session", e)
                onResult(null)
            }
    }

    fun joinSession(code: String, deviceId: String, onResult: (Boolean, String?) -> Unit) {
        val sessionRef = sessionsRef.child(code)
        sessionRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                onResult(false, "Session not found")
                return@addOnSuccessListener
            }

            val hostId = snapshot.child(FB_HOST_ID).getValue(String::class.java)
            if (hostId == deviceId) {
                onResult(false, "You cannot join your own session")
                return@addOnSuccessListener
            }

            val isOccupied = snapshot.child(FB_GUEST_ONLINE).getValue(Boolean::class.java) ?: false
            if (isOccupied) {
                onResult(false, "Session is full")
            } else {
                val updates = mapOf(
                    FB_GUEST_ID to deviceId,
                    FB_GUEST_ONLINE to true
                )
                sessionRef.updateChildren(updates)
                sessionRef.child(FB_GUEST_ONLINE).onDisconnect().setValue(false)
                onResult(true, null)
            }
        }.addOnFailureListener { onResult(false, it.message) }
    }

    fun ensureSessionNode(code: String, onComplete: (Boolean) -> Unit) {
        sessionsRef.child(code).child(FB_HOST_ONLINE).setValue(true)
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun writeClipboard(code: String, node: String, text: String) {
        val value = text.ifEmpty { "" }
        sessionsRef.child(code).child(node).setValue(value)
    }

    fun deleteSession(code: String) {
        sessionsRef.child(code).removeValue()
    }
}
