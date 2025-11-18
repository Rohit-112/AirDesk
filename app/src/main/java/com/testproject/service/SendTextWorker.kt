package com.testproject.service

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class SendTextWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val text = inputData.getString("text") ?: return Result.failure()
        Log.d("SendTextWorker", "Received text: $text")

        val prefs = applicationContext.getSharedPreferences("myAppPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("lastSentText", text).apply()

        return Result.success()
    }
}