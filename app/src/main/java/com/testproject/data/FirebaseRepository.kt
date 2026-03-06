package com.testproject.data

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * Handles all Firebase Realtime Database operations.
 * Centralizing this logic reduces complexity in Fragments and Managers.
 */
class FirebaseRepository {

    private val TAG = "FirebaseRepo"
    private val sessionsRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("sessions")

    /**
     * Generates a unique 6-digit session code and initializes the node.
     */
    fun createSession(onResult: (String?) -> Unit) {
        val code = (100000..999999).random().toString()
        sessionsRef.child(code).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                createSession(onResult) // Collision found, retry
            } else {
                val initialData = mapOf(
                    "hostOnline" to true,
                    "guestOnline" to false,
                    "hostClipboard" to "",
                    "guestClipboard" to ""
                )
                sessionsRef.child(code).setValue(initialData)
                    .addOnSuccessListener {
                        sessionsRef.child(code).onDisconnect().removeValue()
                        onResult(code)
                    }
                    .addOnFailureListener { onResult(null) }
            }
        }.addOnFailureListener { onResult(null) }
    }

    /**
     * Attempts to join an existing session as a guest.
     */
    fun joinSession(code: String, onResult: (Boolean, String?) -> Unit) {
        val sessionRef = sessionsRef.child(code)
        sessionRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                onResult(false, "Session not found")
                return@addOnSuccessListener
            }

            val isOccupied = snapshot.child("guestOnline").getValue(Boolean::class.java) ?: false
            if (isOccupied) {
                onResult(false, "Session is full")
            } else {
                sessionRef.child("guestOnline").setValue(true)
                sessionRef.child("guestOnline").onDisconnect().setValue(false)
                onResult(true, null)
            }
        }.addOnFailureListener { onResult(false, it.message) }
    }

    fun ensureSessionNode(code: String, onComplete: (Boolean) -> Unit) {
        // Used by SyncManager to ensure node exists on reconnect
        sessionsRef.child(code).child("hostOnline").setValue(true)
            .addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    fun writeClipboard(code: String, node: String, text: String) {
        sessionsRef.child(code).child(node).setValue(text)
    }

    fun deleteSession(code: String) {
        sessionsRef.child(code).removeValue()
    }

    fun sessionExists(code: String, callback: (Boolean) -> Unit) {
        sessionsRef.child(code).get().addOnSuccessListener { callback(it.exists()) }
    }
}
