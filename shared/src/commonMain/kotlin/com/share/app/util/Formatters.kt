package com.share.app.util

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/** Bytes as something a person reads at a glance: 0 B, 4.2 KB, 20 MB. */
fun humanFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val exponent = min(floor(ln(bytes.toDouble()) / ln(1024.0)).toInt(), units.lastIndex)
    val value = bytes / 1024.0.pow(exponent)
    val text = if (value >= 10 || exponent == 0) {
        value.roundToLong().toString()
    } else {
        val tenths = (value * 10).roundToLong()
        "${tenths / 10}.${tenths % 10}"
    }
    return "$text ${units[exponent]}"
}

fun relativeTime(timestampMillis: Long, nowMillis: Long = currentTimeMillis()): String {
    val seconds = maxOf(0L, (nowMillis - timestampMillis + 500) / 1000)
    if (seconds < 60) return "just now"
    val minutes = (seconds + 30) / 60
    if (minutes < 60) return "${minutes}m ago"
    val hours = (minutes + 30) / 60
    if (hours < 24) return "${hours}h ago"
    return "${(hours + 12) / 24}d ago"
}
