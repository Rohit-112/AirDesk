package com.testproject.service

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView

class SendToActionModeCallback(private val context: Context) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        menu?.add(0, 1, 0, "SendTo")
        return true
    }
    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        if (item?.itemId == 1) {
            if (mode?.customView is TextView) {
                val textView = mode.customView as TextView
                val start = textView.selectionStart
                val end = textView.selectionEnd
                val selectedText = textView.text.substring(start, end)
                HandleSendToText(context, selectedText)
            }
            mode?.finish()
            return true
        }
        return false
    }
    override fun onDestroyActionMode(mode: ActionMode?) { /* noop */ }
}
