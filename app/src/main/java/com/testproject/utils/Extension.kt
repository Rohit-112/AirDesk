package com.testproject.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.showLongToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

inline fun <reified T : Activity> Context.openActivity(block: Intent.() -> Unit = {}) {
    startActivity(Intent(this, T::class.java).apply(block))
}

fun View.hide() {
    this.visibility = View.GONE
}

fun View.show() {
    this.visibility = View.VISIBLE
}

fun View.show(isShow: Boolean) {
    this.isVisible = isShow
}

fun String.toRequestBody(): RequestBody =
    this.toRequestBody("text/plain".toMediaTypeOrNull())

fun <T> LifecycleOwner.launchCoroutine(
    block: suspend () -> T,
    onResult: (T) -> Unit
) {
    lifecycleScope.launch {
        val result = block()
        onResult(result)
    }
}

fun LifecycleOwner.launchCoroutine(
    block: suspend () -> Unit
) {
    lifecycleScope.launch {
        block()
    }
}

inline fun String?.letIfNotNullOrEmpty(block: (String) -> Unit) {
    if (!this.isNullOrEmpty()) {
        block(this)
    }
}

fun <T> insertCustomItems(
    originalList: List<T>,
    createItem: (Int) -> T?,
    includeInitial: (() -> T?)? = null
): List<T> {
    val resultList = mutableListOf<T>()

    includeInitial?.invoke()?.let { resultList.add(it) }

    originalList.forEachIndexed { index, item ->
        resultList.add(item)
        createItem(index)?.let { resultList.add(it) }
    }

    return resultList
}

fun String.extractYoutubeId(): String? {
    val regexList = listOf(
        "(?:https?://)?(?:www\\.)?youtube\\.com/watch\\?v=([\\w-]{11})",
        "(?:https?://)?(?:www\\.)?youtu\\.be/([\\w-]{11})",
        "(?:https?://)?(?:www\\.)?youtube\\.com/embed/([\\w-]{11})",
        "(?:https?://)?(?:www\\.)?youtube\\.com/shorts/([\\w-]{11})",
        "^([\\w-]{11})$"
    )

    for (pattern in regexList) {
        val match = Regex(pattern).find(this)
        if (match != null && match.groupValues.size > 1) {
            return match.groupValues[1]
        }
    }
    return null
}


fun String.convertEpochToDateTime(): String {
    if (this.isEmpty()) return "Unknown Date"
    return try {
        val timestamp = this.toLong()
        val date = Date(timestamp)
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(date)
    } catch (_: Exception) {
        "Unknown Date"
    }
}



