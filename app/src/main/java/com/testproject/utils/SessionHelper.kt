package com.testproject.utils

import android.content.Context
import com.testproject.viewmodel.SessionViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceHelper: DeviceHelper
) {
    fun createSession(viewModel: SessionViewModel) {
        viewModel.createSession(deviceHelper.deviceId) { code ->
            if (code == null) context.showToast("Failed to generate code")
        }
    }

    fun joinSession(
        code: String,
        viewModel: SessionViewModel,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModel.joinSession(code, deviceHelper.deviceId) { success, error ->
            if (success) onSuccess() else onError(error ?: "Unknown error")
        }
    }

    fun unlinkSession(viewModel: SessionViewModel) {
        viewModel.sessionCode.value?.let { viewModel.deleteSession(it) }
    }
}
