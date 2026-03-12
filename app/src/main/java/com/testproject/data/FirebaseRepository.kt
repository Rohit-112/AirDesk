package com.testproject.data

import com.google.firebase.database.*
import com.testproject.utils.AppsConst.FB_GUEST_CLIPBOARD
import com.testproject.utils.AppsConst.FB_GUEST_ID
import com.testproject.utils.AppsConst.FB_GUEST_ONLINE
import com.testproject.utils.AppsConst.FB_HOST_CLIPBOARD
import com.testproject.utils.AppsConst.FB_HOST_ID
import com.testproject.utils.AppsConst.FB_HOST_ONLINE
import com.testproject.utils.AppsConst.FB_SESSIONS
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRepository @Inject constructor() {

    private val database = FirebaseDatabase.getInstance().getReference(FB_SESSIONS)

    fun createSession(deviceId: String, onResult: (String?) -> Unit) {
        val code = (100000..999999).random().toString()
        database.child(code).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                createSession(deviceId, onResult)
            } else {
                val data = mapOf(
                    FB_HOST_ID to deviceId,
                    FB_HOST_ONLINE to true,
                    FB_GUEST_ONLINE to false,
                    FB_HOST_CLIPBOARD to "",
                    FB_GUEST_CLIPBOARD to ""
                )
                database.child(code).setValue(data).addOnSuccessListener {
                    database.child(code).onDisconnect().removeValue()
                    onResult(code)
                }.addOnFailureListener { onResult(null) }
            }
        }.addOnFailureListener { onResult(null) }
    }

    fun joinSession(code: String, deviceId: String, onResult: (Boolean, String?) -> Unit) {
        database.child(code).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                onResult(false, "Session not found")
                return@addOnSuccessListener
            }
            if (snapshot.child(FB_HOST_ID).getValue(String::class.java) == deviceId) {
                onResult(false, "Cannot join own session")
                return@addOnSuccessListener
            }
            if (snapshot.child(FB_GUEST_ONLINE).getValue(Boolean::class.java) == true) {
                onResult(false, "Session full")
            } else {
                val updates = mapOf(FB_GUEST_ID to deviceId, FB_GUEST_ONLINE to true)
                database.child(code).updateChildren(updates)
                database.child(code).child(FB_GUEST_ONLINE).onDisconnect().setValue(false)
                onResult(true, null)
            }
        }.addOnFailureListener { onResult(false, it.message) }
    }

    fun observeClipboard(code: String, node: String, onData: (String?) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) = onData(s.getValue(String::class.java))
            override fun onCancelled(e: DatabaseError) {}
        }
        database.child(code).child(node).addValueEventListener(listener)
        return listener
    }

    fun observePeerPresence(code: String, node: String, onStatus: (Boolean) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) = onStatus(s.getValue(Boolean::class.java) ?: false)
            override fun onCancelled(e: DatabaseError) = onStatus(false)
        }
        database.child(code).child(node).addValueEventListener(listener)
        return listener
    }

    fun removeListener(code: String, node: String, listener: ValueEventListener) {
        database.child(code).child(node).removeEventListener(listener)
    }

    fun writeClipboard(code: String, node: String, text: String) {
        database.child(code).child(node).setValue(text)
    }

    fun deleteSession(code: String) {
        database.child(code).removeValue()
    }
}
