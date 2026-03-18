package com.testproject.worker

import android.content.Context
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
 */
class CleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val encryptionHelper by lazy { EncryptionHelper(applicationContext) }

    override suspend fun doWork(): Result {
        return try {
            val storage = FirebaseStorage.getInstance()
            val database = FirebaseDatabase.getInstance().getReference(FB_SESSIONS)
            val storageSessionsRoot = storage.reference.child(FB_SESSIONS)
            
            val storageResult = storageSessionsRoot.listAll().await()
            val now = System.currentTimeMillis()
            val oneHourAgo = now - (60 * 60 * 1000)
            val gracePeriod = now - (5 * 60 * 1000)

            for (sessionFolder in storageResult.prefixes) {
                val sessionCode = sessionFolder.name
                if (sessionCode.isEmpty()) continue

                val dbSnapshot = database.child(sessionCode).get().await()
                
                if (!dbSnapshot.exists()) {
                    deleteAllInCloudFolder(sessionFolder)
                } else {
                    val hostClipEnc = dbSnapshot.child(FB_HOST_CLIPBOARD).getValue(String::class.java) ?: ""
                    val guestClipEnc = dbSnapshot.child(FB_GUEST_CLIPBOARD).getValue(String::class.java) ?: ""
                    
                    val hostClip = if (hostClipEnc.isNotEmpty()) encryptionHelper.decrypt(hostClipEnc) else ""
                    val guestClip = if (guestClipEnc.isNotEmpty()) encryptionHelper.decrypt(guestClipEnc) else ""

                    cleanupLiveSessionCloudFiles(sessionFolder, hostClip, guestClip, oneHourAgo, gracePeriod)
                }
            }
            Result.success()
        } catch (e: Exception) {
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
                
                val fileSignature = "$FILE_PROTOCOL_PREFIX$fileName$FILE_PROTOCOL_SEPARATOR"
                val isStillPending = hostClip.contains(fileSignature) || guestClip.contains(fileSignature)
                
                val isOld = creationTime < oneHourAgo
                val isOutsideGrace = creationTime < gracePeriod

                if ((!isStillPending && isOutsideGrace) || isOld) {
                    fileRef.delete().await() 
                }
            } catch (e: Exception) {
            }
        }
    }

    private suspend fun deleteAllInCloudFolder(folder: StorageReference) {
        if (folder.path == FB_SESSIONS || folder.parent?.name != FB_SESSIONS) return

        val result = folder.listAll().await()
        for (file in result.items) {
            try { file.delete().await() } catch (_: Exception) {}
        }
        for (subFolder in result.prefixes) {
            deleteAllInCloudFolder(subFolder)
        }
    }
}
