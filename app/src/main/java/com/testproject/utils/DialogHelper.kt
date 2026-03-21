package com.testproject.utils

import androidx.fragment.app.FragmentManager
import com.testproject.helper.CustomBottomSheetDialog

class DialogHelper(private val fragmentManager: FragmentManager) {

    fun showDownloadDialog(fileName: String, url: String, onDownload: () -> Unit) {
        CustomBottomSheetDialog.newInstance(
            title = "File Received",
            message = "You received: $fileName. Download?",
            okText = "Download",
            cancelText = "Ignore",
            showOkButton = true,
            showCancelButton = true,
            onOkClicked = onDownload
        ).show(fragmentManager, "dl")
    }

    fun showFileSizeError() {
        CustomBottomSheetDialog.newInstance(
            title = "File Too Large",
            message = "Max size 5MB.",
            showOkButton = false,
            cancelText = "Got it",
            showCancelButton = true
        ).show(fragmentManager, "error")
    }

    fun showErrorDialog(msg: String) {
        CustomBottomSheetDialog.newInstance(
            title = "Error",
            message = msg,
            showOkButton = true,
            showCancelButton = false
        ).show(fragmentManager, "err")
    }
    
    fun showNotReadyToShareDialog(msg: String) {
        CustomBottomSheetDialog.newInstance(
            title = "Not Ready to Share",
            message = msg,
            okText = "Got it",
            showCancelButton = false
        ).show(fragmentManager, "not_connected")
    }
}
