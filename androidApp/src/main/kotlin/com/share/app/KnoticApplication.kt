package com.share.app

import android.app.Application
import com.share.app.platform.KnoticAndroid

class KnoticApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        KnoticAndroid.initialize(this)
    }
}
