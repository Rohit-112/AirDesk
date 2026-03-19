package com.testproject.activity

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.testproject.base.BaseActivity
import com.testproject.databinding.ActivitySplashBinding
import com.testproject.utils.AppPreference
import com.testproject.utils.AppsConst.SECURITY_VIOLATION
import com.testproject.utils.SecurityUtils
import com.testproject.utils.popIn
import com.testproject.utils.openActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {

    @Inject
    lateinit var appPreference: AppPreference
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Modern iOS-like scale pop animation for the logo
        binding.ivLogo.popIn(duration = 800)

        checkSecurityAndProceed()
    }

    private fun checkSecurityAndProceed() {
        lifecycleScope.launch {
            // Check for Root
            if (SecurityUtils.isDeviceRooted()) {
                showSecurityError(
                    SECURITY_VIOLATION,
                    "Rooted device detected. For security reasons, this app cannot run on rooted devices."
                )
                return@launch
            }

            delay(2000) // Give user a moment to see splash and animation

            openActivity<MainActivity> { }
            finish()
        }
    }

    private fun showSecurityError(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Exit") { _, _ ->
                finishAffinity()
            }
            .show()
    }
}
