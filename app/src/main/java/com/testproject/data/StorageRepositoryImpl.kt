package com.testproject.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.testproject.domain.repository.IStorageRepository
import com.testproject.utils.AppsConst.FB_SESSIONS
import com.testproject.utils.AppsConst.MAX_FILE_SIZE_BYTES
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepositoryImpl @Inject constructor() : IStorageRepository {

    private val storage = FirebaseStorage.getInstance()

    private fun getStorageRef(): StorageReference {
        return storage.reference.child(FB_SESSIONS)
    }

    override fun isFileSizeValid(context: Context, uri: Uri): Boolean {
        val fileSize = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                cursor.getLong(sizeIndex)
            } else 0L
        } ?: 0L
        return fileSize <= MAX_FILE_SIZE_BYTES
    }

    override suspend fun uploadFile(
        sessionCode: String,
        fileUri: Uri,
        fileName: String,
        onProgress: (Int) -> Unit
    ): String? {
        return try {
            val fileRef = getStorageRef().child(sessionCode).child(fileName)
            val uploadTask = fileRef.putFile(fileUri)

            uploadTask.addOnProgressListener { taskSnapshot ->
                if (taskSnapshot.totalByteCount > 0) {
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                    onProgress(progress)
                }
            }.await()

            fileRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun downloadFileBytes(url: String): ByteArray? {
        return try {
            val ref = storage.getReferenceFromUrl(url)
            ref.getBytes(MAX_FILE_SIZE_BYTES.toLong()).await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun deleteFileByUrl(url: String): Boolean {
        return try {
            storage.getReferenceFromUrl(url).delete().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun deleteSessionStorage(sessionCode: String) {
        try {
            val sessionRef = getStorageRef().child(sessionCode)
            val result = sessionRef.listAll().await()
            for (file in result.items) {
                file.delete().await()
            }
            for (prefix in result.prefixes) {
                deleteRecursive(prefix)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun deleteRecursive(ref: StorageReference) {
        val result = ref.listAll().await()
        for (file in result.items) file.delete().await()
        for (prefix in result.prefixes) deleteRecursive(prefix)
    }
}
