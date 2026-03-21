package com.testproject.utils

import android.content.Context
import com.testproject.network.NetworkUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isConnected(): Boolean {
        return NetworkUtils.isConnected(context)
    }

    fun checkNetworkWithToast(): Boolean {
        return if (isConnected()) {
            true
        } else {
            context.showToast("No internet connection")
            false
        }
    }
}
