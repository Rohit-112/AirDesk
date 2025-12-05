package com.testproject.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

/**
 * Simple clipboard monitor that updates listener when user copies text.
 * Use setClipboardProgrammatically to set clipboard and suppress the next local event.
 */
class ClipboardMonitor(private val context: Context, private val onUserCopy: (String) -> Unit) :
    ClipboardManager.OnPrimaryClipChangedListener {

    private val TAG = "ClipboardMonitor"
    private val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @Volatile
    private var suppressNext = false

    fun start() {
        Log.d(TAG, "start monitoring")
        clipboard.addPrimaryClipChangedListener(this)
    }

    fun stop() {
        Log.d(TAG, "stop monitoring")
        try {
            clipboard.removePrimaryClipChangedListener(this)
        } catch (_: Exception) { /* ignore */
        }
    }

    override fun onPrimaryClipChanged() {
        if (suppressNext) {
            Log.d(TAG, "onPrimaryClipChanged: suppressed one event")
            suppressNext = false
            return
        }
        val clip = clipboard.primaryClip
        val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()
        Log.d(TAG, "onPrimaryClipChanged: $text")
        if (!text.isNullOrEmpty()) {
            onUserCopy(text)
        }
    }

    /**
     * Programmatically set clipboard and suppress the next onPrimaryClipChanged callback
     * to avoid echoing remote updates back to Firebase.
     */
    fun setClipboardProgrammatically(text: String) {
        Log.d(TAG, "setClipboardProgrammatically: $text (suppressing next)")
        suppressNext = true
        val clip = ClipData.newPlainText("AirDesk", text)
        clipboard.setPrimaryClip(clip)
    }
}
