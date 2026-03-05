package com.testproject.base

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.testproject.activity.MainActivity
import com.testproject.helper.CustomBottomSheetDialog
import com.testproject.helper.Progressbar
import com.testproject.network.NetworkUtils


open class BaseFragment : Fragment() {

    private var progressDialog: Dialog? = null
    private lateinit var backCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackPressed()
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            backCallback
        )
    }

    open fun onBackPressed() {
        backCallback.isEnabled = false
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    open fun navigateSafe(destinationId: Int, bundle: Bundle? = null) {
        val navController = findNavController()
        if (navController.currentDestination?.id != destinationId) {
            navController.navigate(destinationId, bundle)
        }
    }

    open fun btmNavShow(isShown: Boolean = true) {
        runCatching { (activity as? MainActivity)?.btmNavShow(isShown) }.onFailure {
            it.printStackTrace()
//            FirebaseCrashlytics.getInstance().recordException(it)
        }
    }

    fun showLoading() {
        if (activity is BaseActivity) {
            (activity as BaseActivity).showLoading()
            return
        }

        if (progressDialog == null) {
            progressDialog = Progressbar.builder(requireContext())
        }

        try {
            if (!progressDialog?.isShowing!!) {
                progressDialog?.show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideLoading() {
        if (activity is BaseActivity) {
            (activity as BaseActivity).hideLoading()
            return
        }
        try {
            progressDialog?.dismiss()
        } catch (e: WindowManager.BadTokenException) {
            e.printStackTrace()
        }
    }

    protected fun showNoInternetBottomSheet(onRetry: () -> Unit) {
        val existing = childFragmentManager.findFragmentByTag("NoInternetDialog")
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
                if (NetworkUtils.isConnected(requireContext())) {
                    onRetry()
                } else {
                    showNoInternetBottomSheet(onRetry)
                }
            }
        )
        dialog.show(childFragmentManager, "NoInternetDialog")
    }

}