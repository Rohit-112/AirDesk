package com.testproject.activity

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.CaptureManager
import com.testproject.databinding.ActivityCustomScannerBinding

class CustomScannerActivity : AppCompatActivity() {

    private lateinit var capture: CaptureManager
    private lateinit var binding: ActivityCustomScannerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        capture = CaptureManager(this, binding.zxingBarcodeScanner)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.decode()

        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return binding.zxingBarcodeScanner.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }
}
