package com.share.app.util

import co.touchlab.kermit.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.Uuid

fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun newId(prefix: String): String = "$prefix-${Uuid.random().toHexString().take(12)}"

/** [runCatching] that never swallows cancellation. */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }

val AppLog: Logger = Logger.withTag("Knotic")
