package com.testproject.helper

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.testproject.R

class CustomBottomSheetDialog : BottomSheetDialogFragment() {

    private var title: String? = null
    private var message: String? = null
    private var okText: String? = null
    private var cancelText: String? = null
    private var showOkButton: Boolean = true
    private var showCancelButton: Boolean = true
    private var isCancellableDialog: Boolean = true

    private var onOkClicked: (() -> Unit)? = null
    private var onCancelClicked: (() -> Unit)? = null

    companion object {
        fun newInstance(
            title: String,
            message: String,
            okText: String = "OK",
            cancelText: String = "Cancel",
            showOkButton: Boolean = true,
            showCancelButton: Boolean = true,
            isCancellable: Boolean = true,
            onOkClicked: () -> Unit = {},
            onCancelClicked: () -> Unit = {}
        ): CustomBottomSheetDialog {

            val fragment = CustomBottomSheetDialog()

            val args = Bundle().apply {
                putString("title", title)
                putString("message", message)
                putString("okText", okText)
                putString("cancelText", cancelText)
                putBoolean("showOkButton", showOkButton)
                putBoolean("showCancelButton", showCancelButton)
                putBoolean("isCancellable", isCancellable)
            }

            fragment.arguments = args
            fragment.onOkClicked = onOkClicked
            fragment.onCancelClicked = onCancelClicked

            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            title = it.getString("title")
            message = it.getString("message")
            okText = it.getString("okText")
            cancelText = it.getString("cancelText")
            showOkButton = it.getBoolean("showOkButton")
            showCancelButton = it.getBoolean("showCancelButton")
            isCancellableDialog = it.getBoolean("isCancellable")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(isCancellableDialog)
        dialog.setCancelable(isCancellableDialog)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialogbox, container, false)

        val titleText = view.findViewById<TextView>(R.id.titleText)
        val messageText = view.findViewById<TextView>(R.id.messageText)
        val btnOk = view.findViewById<AppCompatButton>(R.id.btnOk)
        val btnCancel = view.findViewById<AppCompatButton>(R.id.btnCancel)

        titleText.text = title
        messageText.text = message

        if (showOkButton) {
            btnOk.visibility = View.VISIBLE
            btnOk.text = okText
            btnOk.setOnClickListener {
                onOkClicked?.invoke()
                dismiss()
            }
        } else btnOk.visibility = View.GONE

        if (showCancelButton) {
            btnCancel.visibility = View.VISIBLE
            btnCancel.text = cancelText
            btnCancel.setOnClickListener {
                onCancelClicked?.invoke()
                dismiss()
            }
        } else btnCancel.visibility = View.GONE

        return view
    }
}
