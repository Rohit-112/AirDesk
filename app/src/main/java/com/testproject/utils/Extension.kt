package com.testproject.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.testproject.R
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.showLongToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

inline fun <reified T : Activity> Context.openActivity(
    shouldAnimate: Boolean = true,
    block: Intent.() -> Unit = {}
) {
    val intent = Intent(this, T::class.java).apply(block)
    startActivity(intent)
    if (shouldAnimate && this is Activity) {
        // iOS style: Slide in from right, exit to left
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }
}

/**
 * Call this in Activity.onBackPressed() or when finishing an activity 
 * to get the reverse iOS transition
 */
fun Activity.finishWithAnimation() {
    finish()
    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
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

/**
 * Modern iOS-like spring animation for views appearing from bottom
 */
fun View.slideUp(duration: Long = 600, startDelay: Long = 0) {
    this.visibility = View.VISIBLE
    this.alpha = 0f
    this.translationY = 150f
    this.animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(duration)
        .setStartDelay(startDelay)
        .setInterpolator(DecelerateInterpolator(2f))
        .start()
}

/**
 * Modern iOS-like scale pop animation (Spring effect)
 */
fun View.popIn(duration: Long = 500, startDelay: Long = 0) {
    this.alpha = 0f
    this.scaleX = 0.7f
    this.scaleY = 0.7f
    this.visibility = View.VISIBLE
    this.animate()
        .alpha(1f)
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(duration)
        .setStartDelay(startDelay)
        .setInterpolator(AnticipateOvershootInterpolator(1.2f))
        .start()
}

/**
 * Smooth Fade In
 */
fun View.fadeIn(duration: Long = 400, startDelay: Long = 0) {
    this.alpha = 0f
    this.visibility = View.VISIBLE
    this.animate()
        .alpha(1f)
        .setDuration(duration)
        .setStartDelay(startDelay)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .start()
}

/**
 * Smooth Fade Out
 */
fun View.fadeOut(duration: Long = 400) {
    this.animate()
        .alpha(0f)
        .setDuration(duration)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .withEndAction { this.visibility = View.GONE }
        .start()
}

/**
 * Modern Click Animation (Scale Down/Up) - Very iOS like
 */
@SuppressLint("ClickableViewAccessibility")
fun View.addClickAnimation() {
    this.setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
        }
        false
    }
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