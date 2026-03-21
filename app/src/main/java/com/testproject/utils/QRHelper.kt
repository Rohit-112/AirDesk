package com.testproject.utils

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.testproject.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QRHelper @Inject constructor() {

    fun generateQRCode(content: String, size: Int = 512): Bitmap? {
        return try {
            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val encoder = BarcodeEncoder()
            encoder.createBitmap(bitMatrix)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun showQrCodeDialog(context: Context, code: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_qr_code, null)
        val ivQr = dialogView.findViewById<ImageView>(R.id.ivQrCode)
        val tvCode = dialogView.findViewById<TextView>(R.id.tvCode)
        val btnClose = dialogView.findViewById<View>(R.id.btnClose)

        tvCode.text = code.chunked(3).joinToString(" ")
        
        val bitmap = generateQRCode(code)
        if (bitmap != null) {
            ivQr.setImageBitmap(bitmap)
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
