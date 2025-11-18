package com.testproject.base

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.testproject.activity.MainActivity
import com.testproject.helper.CustomBottomSheetDialog
import com.testproject.network.NetworkUtils


open class BaseFragment : Fragment() {

    open fun navigateSafe(destinationId: Int, bundle: Bundle? = null) {
        val navController = findNavController()
        if (navController.currentDestination?.id != destinationId) {
            navController.navigate(destinationId, bundle)
        }
    }

    open fun btmNavShow(isShown: Boolean = true) {
        runCatching { (activity as? MainActivity)?.btmNavShow(isShown) }.onFailure {
            it.printStackTrace()
            FirebaseCrashlytics.getInstance().recordException(it)
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