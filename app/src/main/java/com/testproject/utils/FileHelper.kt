package com.testproject.utils

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.testproject.viewmodel.FileTransferViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @SuppressLint("Range")
    fun getFileName(uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = cursor.getString(index)
                }
            }
        }
        return name
    }

    suspend fun saveFileToPublicDirectory(fileName: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "*/*")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)?.let { uri ->
                    resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    true
                } ?: false
            } else {
                val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                FileOutputStream(file).use { it.write(bytes) }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    suspend fun downloadAndSaveFile(
        fileName: String, 
        downloadUrl: String, 
        viewModel: FileTransferViewModel,
        onStart: () -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        onStart()
        val bytes = viewModel.downloadFileBytes(downloadUrl)
        if (bytes != null) {
            val success = saveFileToPublicDirectory(fileName, bytes)
            if (success) {
                viewModel.deleteFileByUrl(downloadUrl)
            }
            onComplete(success)
        } else {
            onComplete(false)
        }
    }
}
