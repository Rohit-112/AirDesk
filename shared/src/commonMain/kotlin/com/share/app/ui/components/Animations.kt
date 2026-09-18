package com.share.app.ui.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * A value that loops for as long as someone is looking at it.
 *
 * An endless animation asks for a new frame sixty times a second, forever -
 * on the desktop that was a fifth of a processor core spent on a window left
 * open in the background. Dropping out of composition while the window is
 * unfocused stops the frames entirely; the value freezes where a still frame
 * looks right, and picks up again when the window is focused.
 */
@Composable
fun rememberLoopingFloat(
    durationMillis: Int,
    label: String,
    initialValue: Float = 0f,
    targetValue: Float = 1f,
    easing: Easing = FastOutSlowInEasing,
    repeatMode: RepeatMode = RepeatMode.Restart,
    /** Where the value rests while nothing is animating. */
    restingValue: Float = initialValue,
): State<Float> =
    if (LocalWindowInfo.current.isWindowFocused) {
        rememberInfiniteTransition(label = label).animateFloat(
            initialValue = initialValue,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(tween(durationMillis, easing = easing), repeatMode),
            label = "$label-value",
        )
    } else {
        remember(restingValue) { mutableStateOf(restingValue) }
    }

/** The same, for a component that only wants the number. */
@Composable
fun loopingFloat(
    durationMillis: Int,
    label: String,
    initialValue: Float = 0f,
    targetValue: Float = 1f,
    easing: Easing = FastOutSlowInEasing,
    repeatMode: RepeatMode = RepeatMode.Restart,
    restingValue: Float = initialValue,
): Float {
    val value by rememberLoopingFloat(
        durationMillis = durationMillis,
        label = label,
        initialValue = initialValue,
        targetValue = targetValue,
        easing = easing,
        repeatMode = repeatMode,
        restingValue = restingValue,
    )
    return value
}
