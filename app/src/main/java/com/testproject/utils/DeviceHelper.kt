package com.testproject.utils

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val deviceId: String by lazy {
        @SuppressLint("HardwareIds")
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }
}
