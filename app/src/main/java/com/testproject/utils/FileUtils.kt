package com.testproject.utils

import com.testproject.R

object FileUtils {
    fun getFileIcon(fileName: String?): Int {
        if (fileName == null) return R.drawable.default_icon // Default icon

        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> R.drawable.pdf_icon // Replace with actual PDF icon if exists
            "jpg", "jpeg", "png", "gif", "webp" -> R.drawable.image_icon // Image icon
            "doc", "docx" -> R.drawable.connected // Word icon
            "xls", "xlsx" -> R.drawable.connected // Excel icon
            "ppt", "pptx" -> R.drawable.connected // PPT icon
            "zip", "rar", "7z" -> R.drawable.archive_icon // Archive icon
            "mp3", "wav", "m4a" -> R.drawable.audio_icon // Audio icon
            "mp4", "mkv", "avi" -> R.drawable.video_icon // Video icon
            "txt" -> R.drawable.txt_icon // Text file icon
            else -> R.drawable.default_icon // Default file icon
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
}
