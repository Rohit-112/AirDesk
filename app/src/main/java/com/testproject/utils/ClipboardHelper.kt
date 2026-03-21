package com.testproject.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val clipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    fun copyToClipboard(text: String, label: String = "AirDesk") {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager.setPrimaryClip(clip)
    }

    fun getTextFromClipboard(): String? {
        val clip = clipboardManager.primaryClip
        if (clip != null && clip.itemCount > 0) {
            return clip.getItemAt(0).coerceToText(context).toString()
        }
        return null
    }
}
