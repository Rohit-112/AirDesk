package com.testproject.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Simple clipboard monitor that updates listener when user copies text.
 * Use setClipboardProgrammatically to set clipboard and suppress the next local event.
 */
class ClipboardMonitor(private val context: Context, private val onUserCopy: (String) -> Unit) :
    ClipboardManager.OnPrimaryClipChangedListener {

    private val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    @Volatile
    private var suppressNext = false

    fun start() {
        clipboard.addPrimaryClipChangedListener(this)
    }

    fun stop() {
        try {
            clipboard.removePrimaryClipChangedListener(this)
        } catch (_: Exception) { /* ignore */
        }
    }

    override fun onPrimaryClipChanged() {
        if (suppressNext) {
            suppressNext = false
            return
        }
        val clip = clipboard.primaryClip
        val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()
        if (!text.isNullOrEmpty()) {
            onUserCopy(text)
        }
    }

    /**
     * Programmatically set clipboard and suppress the next onPrimaryClipChanged callback
     * to avoid echoing remote updates back to Firebase.
     */
    fun setClipboardProgrammatically(text: String) {
        suppressNext = true
        val clip = ClipData.newPlainText("AirDesk", text)
        clipboard.setPrimaryClip(clip)
    }
}
