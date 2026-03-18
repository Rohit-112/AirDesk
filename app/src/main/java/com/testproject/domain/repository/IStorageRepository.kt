package com.testproject.domain.repository

import android.content.Context
import android.net.Uri

interface IStorageRepository {
    fun isFileSizeValid(context: Context, uri: Uri): Boolean
    suspend fun uploadFile(
        sessionCode: String,
        fileUri: Uri,
        fileName: String,
        onProgress: (Int) -> Unit
    ): String?
    suspend fun downloadFileBytes(url: String): ByteArray?
    suspend fun deleteFileByUrl(url: String): Boolean
    suspend fun deleteSessionStorage(sessionCode: String)
}
