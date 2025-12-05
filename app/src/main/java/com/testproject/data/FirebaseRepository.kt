package com.testproject.data

import android.util.Log
import com.google.firebase.database.*

/**
 * Thin repository that performs Firebase reads/writes.
 * Keeps direct Firebase usage in one place for testability.
 */
class FirebaseRepository {

    private val TAG = "FirebaseRepo"
    private val rootRef: DatabaseReference = FirebaseDatabase.getInstance().getReference("sessions")

    fun ensureSessionNode(code: String, onComplete: (Boolean) -> Unit) {
        val node = mapOf(
            "hostOnline" to true,
            "guestOnline" to false,
            "hostClipboard" to "",
            "guestClipboard" to ""
        )
        rootRef.child(code).setValue(node)
            .addOnSuccessListener {
                Log.d(TAG, "Session node created/ensured: $code")
                onComplete(true)
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to create session node: $code", it)
                onComplete(false)
            }
    }

    fun sessionExists(code: String, callback: (Boolean) -> Unit) {
        rootRef.child(code).get()
            .addOnSuccessListener { snapshot -> callback(snapshot.exists()) }
            .addOnFailureListener {
                Log.e(TAG, "sessionExists: failure", it)
                callback(false)
            }
    }

    fun writeClipboard(code: String, node: String, text: String, onComplete: ((Boolean) -> Unit)? = null) {
        rootRef.child(code).child(node).setValue(text)
            .addOnSuccessListener { onComplete?.invoke(true) }
            .addOnFailureListener {
                Log.e(TAG, "writeClipboard failed: $code/$node", it)
                onComplete?.invoke(false)
            }
    }

    fun attachListener(code: String, node: String, listener: ValueEventListener) {
        rootRef.child(code).child(node).addValueEventListener(listener)
    }

    fun removeListener(code: String, node: String, listener: ValueEventListener) {
        try {
            rootRef.child(code).child(node).removeEventListener(listener)
        } catch (_: Exception) { /* ignore */ }
    }
}
