package com.testproject.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.testproject.R
import java.io.File

object FileUtils {
    fun getFileIcon(fileName: String?): Int {
        if (fileName == null) return R.drawable.default_icon

        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> R.drawable.pdf_icon
            "jpg", "jpeg", "png", "gif", "webp" -> R.drawable.image_icon
            "doc", "docx" -> R.drawable.connected
            "xls", "xlsx" -> R.drawable.connected
            "ppt", "pptx" -> R.drawable.connected
            "zip", "rar", "7z" -> R.drawable.archive_icon
            "mp3", "wav", "m4a" -> R.drawable.audio_icon
            "mp4", "mkv", "avi" -> R.drawable.video_icon
            "txt" -> R.drawable.txt_icon
            else -> R.drawable.default_icon
        }
    }

    fun getFileTypeLabel(fileName: String?): String {
        if (fileName == null) return "Text"
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "PDF"
            "jpg", "jpeg", "png", "gif", "webp" -> "Image"
            "doc", "docx" -> "Document"
            "xls", "xlsx" -> "Spreadsheet"
            "ppt", "pptx" -> "Presentation"
            "zip", "rar", "7z" -> "Archive"
            "mp3", "wav", "m4a" -> "Audio"
            "mp4", "mkv", "avi" -> "Video"
            "txt" -> "Text File"
            else -> "File"
        }
    }

    fun getMimeType(fileName: String?): String {
        if (fileName == null) return "*/*"
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg", "png", "gif", "webp" -> "image/*"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "zip", "rar", "7z" -> "application/zip"
            "mp3", "wav", "m4a" -> "audio/*"
            "mp4", "mkv", "avi" -> "video/*"
            "txt" -> "text/plain"
            else -> "*/*"
        }
    }

    fun openFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            context.showToast("No app found to open this file type")
        }
    }
}
