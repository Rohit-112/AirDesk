package com.testproject.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.testproject.utils.AppsConst.FB_GUEST_CLIPBOARD
import com.testproject.utils.AppsConst.FB_HOST_CLIPBOARD
import com.testproject.utils.AppsConst.FB_SESSIONS
import com.testproject.utils.AppsConst.FILE_PROTOCOL_PREFIX
import com.testproject.utils.AppsConst.FILE_PROTOCOL_SEPARATOR
import com.testproject.utils.EncryptionHelper
import kotlinx.coroutines.tasks.await

/**
 * CleanupWorker: Manages Firebase Storage footprint to stay within Spark limits.
 * SECURITY: Scoped strictly to session sub-folders. NEVER deletes local device files.
 */
class CleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val encryptionHelper by lazy { EncryptionHelper(applicationContext) }

    override suspend fun doWork(): Result {
        return try {
            val storage = FirebaseStorage.getInstance()
            val database = FirebaseDatabase.getInstance().getReference(FB_SESSIONS)
            val storageSessionsRoot = storage.reference.child(FB_SESSIONS)
            
            // 1. List all session folders currently in Storage
            val storageResult = storageSessionsRoot.listAll().await()
            val now = System.currentTimeMillis()
            val oneHourAgo = now - (60 * 60 * 1000)
            val gracePeriod = now - (5 * 60 * 1000) // 5 min grace to allow for upload-then-update sync

            for (sessionFolder in storageResult.prefixes) {
                val sessionCode = sessionFolder.name
                
                // Safety: Skip empty or root-level names
                if (sessionCode.isEmpty()) continue

                // 2. Check if this specific session is still active in the Database
                val dbSnapshot = database.child(sessionCode).get().await()
                
                if (!dbSnapshot.exists()) {
                    // SESSION IS DEAD: Wipe cloud storage for THIS session only
                    Log.d("CleanupWorker", "Session $sessionCode is dead. Wiping its cloud folder.")
                    deleteAllInCloudFolder(sessionFolder)
                } else {
                    // SESSION IS ALIVE: Selective cleanup based on "received" status or age
                    val hostClipEnc = dbSnapshot.child(FB_HOST_CLIPBOARD).getValue(String::class.java) ?: ""
                    val guestClipEnc = dbSnapshot.child(FB_GUEST_CLIPBOARD).getValue(String::class.java) ?: ""
                    
                    val hostClip = if (hostClipEnc.isNotEmpty()) encryptionHelper.decrypt(hostClipEnc) else ""
                    val guestClip = if (guestClipEnc.isNotEmpty()) encryptionHelper.decrypt(guestClipEnc) else ""

                    cleanupLiveSessionCloudFiles(sessionFolder, hostClip, guestClip, oneHourAgo, gracePeriod)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("CleanupWorker", "Background cloud cleanup failed", e)
            Result.retry()
        }
    }

    private suspend fun cleanupLiveSessionCloudFiles(
        folder: StorageReference,
        hostClip: String,
        guestClip: String,
        oneHourAgo: Long,
        gracePeriod: Long
    ) {
        val files = folder.listAll().await()
        for (fileRef in files.items) {
            try {
                val metadata = fileRef.metadata.await()
                val creationTime = metadata.creationTimeMillis
                val fileName = fileRef.name
                
                // Precise check: Is the file signature "FILE:filename|" in the decrypted clipboard?
                val fileSignature = "$FILE_PROTOCOL_PREFIX$fileName$FILE_PROTOCOL_SEPARATOR"
                val isStillPending = hostClip.contains(fileSignature) || guestClip.contains(fileSignature)
                
                val isOld = creationTime < oneHourAgo
                val isOutsideGrace = creationTime < gracePeriod

                // Delete from Firebase Storage if:
                // - It was received (not in clipboard) AND it's not a brand-new upload race condition
                // - OR it's over 1 hour old regardless of state (to stay in Spark limits)
                if ((!isStillPending && isOutsideGrace) || isOld) {
                    Log.d("CleanupWorker", "Purging cloud file: $fileName (Stale: $isOld, Pending: $isStillPending)")
                    fileRef.delete().await() 
                }
            } catch (e: Exception) {
                Log.e("CleanupWorker", "Failed to check cloud file ${fileRef.name}", e)
            }
        }
    }

    private suspend fun deleteAllInCloudFolder(folder: StorageReference) {
        // Double-check: Ensure we are inside the sessions/ folder to prevent accidental root deletion
        if (folder.path == FB_SESSIONS || folder.parent?.name != FB_SESSIONS) {
            Log.e("CleanupWorker", "Blocked out-of-scope deletion attempt on: ${folder.path}")
            return
        }

        val result = folder.listAll().await()
        for (file in result.items) {
            try { file.delete().await() } catch (_: Exception) {}
        }
        for (subFolder in result.prefixes) {
            deleteAllInCloudFolder(subFolder)
        }
    }
}
