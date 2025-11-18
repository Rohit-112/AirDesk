package com.testproject.base

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.testproject.R
import com.testproject.helper.CustomBottomSheetDialog
import com.testproject.helper.Progressbar
import com.testproject.network.NetworkUtils
import kotlinx.coroutines.launch

open class BaseActivity : AppCompatActivity() {
    private var progressBar: Dialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
        initProgressBar()
    }

    open fun navigateSafe(destinationId: Int, bundle: Bundle? = null) {
        val navController = findNavController(R.id.nav_host_fragment)
        if (navController.currentDestination?.id != destinationId) {
            navController.navigate(destinationId, bundle)
        }
    }

    private fun initProgressBar() {
        progressBar = Progressbar.builder(this)
    }

    fun hideLoading() {
        try {
            progressBar?.dismiss()
        } catch (e: WindowManager.BadTokenException) {
            e.printStackTrace()
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    fun showLoading() {
        try {
            lifecycleScope.launch {
                progressBar?.show()
            }
        } catch (e: WindowManager.BadTokenException) {
            hideLoading()
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    protected fun showNoInternetBottomSheet(onRetry: () -> Unit) {
        val existing = supportFragmentManager.findFragmentByTag("NoInternetDialog")
        if (existing != null && existing is CustomBottomSheetDialog) {
            existing.dismissAllowingStateLoss()
        }

        val dialog = CustomBottomSheetDialog.newInstance(
            title = "No Internet",
            message = "Please check your connection.",
            showOkButton = false,
            okText = "Close",
            cancelText = "Retry",
            showCancelButton = true,
            isCancellable = false,
            onCancelClicked = {
                if (NetworkUtils.isConnected(this)) {
                    onRetry()
                } else {
                    showNoInternetBottomSheet(onRetry)
                }
            }
        )
        dialog.show(supportFragmentManager, "NoInternetDialog")
    }

    override fun onDestroy() {
        hideLoading()
        progressBar = null
        super.onDestroy()
    }
}