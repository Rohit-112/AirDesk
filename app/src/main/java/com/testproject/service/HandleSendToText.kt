package com.testproject.service

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class HandleSendToText(context: Context, text: String) {

    init {
        val workData = Data.Builder()
            .putString("text", text)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SendTextWorker>()
            .setInputData(workData)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}