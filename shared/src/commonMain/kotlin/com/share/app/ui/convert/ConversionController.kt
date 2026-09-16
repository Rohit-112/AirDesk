package com.share.app.ui.convert

import com.share.app.domain.analytics.ConversionSource
import com.share.app.domain.media.ImageFormat
import com.share.app.domain.media.ImageFormats
import com.share.app.domain.media.SerialImageProcessor
import com.share.app.domain.usecase.ConvertImageUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface ConversionStatus {
    data object Idle : ConversionStatus

    data class Working(val format: ImageFormat) : ConversionStatus

    data class Done(
        val format: ImageFormat,
        val fileName: String,
        val size: Long,
        val originalSize: Long,
        /** False until the save dialog was confirmed; the result is kept either way. */
        val saved: Boolean,
        val saveFailed: Boolean = false,
    ) : ConversionStatus

    data class Failed(val format: ImageFormat, val message: String) : ConversionStatus
}

/** One conversion at a time, tied to the file it was started for. */
data class ConversionState(
    /** Which file the status belongs to: an activity row id, or the picked file. */
    val sourceKey: String? = null,
    val status: ConversionStatus = ConversionStatus.Idle,
) {
    fun statusFor(key: String?): ConversionStatus =
        if (key != null && key == sourceKey) status else ConversionStatus.Idle
}

/**
 * Converts an image the app already holds, then offers to save the result.
 *
 * The result is kept so it can be saved again without converting twice: a
 * large HEIC can take a few seconds. Starting another conversion abandons the
 * one in flight, so a slow result that is no longer wanted is dropped rather
 * than saved - the same guarantee the web client's run counter gives.
 */
class ConversionController(
    private val scope: CoroutineScope,
    private val useCase: ConvertImageUseCase,
    private val onChange: (ConversionState) -> Unit,
) {
    private class Result(val key: String, val fileName: String, val bytes: ByteArray)

    private var job: Job? = null
    private var result: Result? = null
    private var state = ConversionState()

    fun convert(
        key: String,
        source: ConversionSource,
        fileName: String,
        mimeType: String,
        format: ImageFormat,
        loadBytes: suspend () -> ByteArray?,
    ) {
        job?.cancel()
        result = null
        publish(ConversionState(key, ConversionStatus.Working(format)))

        job = scope.launch {
            val original = loadBytes()
            if (original == null) {
                publish(ConversionState(key, ConversionStatus.Failed(format, "That file is no longer held on this device.")))
                return@launch
            }

            val converted = try {
                useCase.convert(original, mimeType, format, source)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                publish(ConversionState(key, ConversionStatus.Failed(format, SerialImageProcessor.describeFailure(error, format))))
                return@launch
            }

            val outputName = ImageFormats.convertedFileName(fileName, format)
            result = Result(key, outputName, converted)
            val done = ConversionStatus.Done(
                format = format,
                fileName = outputName,
                size = converted.size.toLong(),
                originalSize = original.size.toLong(),
                saved = false,
            )
            publish(ConversionState(key, done))
            save(key, done, converted)
        }
    }

    /** Offers the last result to the save dialog again. */
    fun saveAgain() {
        val kept = result ?: return
        val done = state.status as? ConversionStatus.Done ?: return
        if (state.sourceKey != kept.key || job?.isActive == true) return
        job = scope.launch { save(kept.key, done, kept.bytes) }
    }

    /** Forgets everything, when the file it was about is gone. */
    fun reset() {
        job?.cancel()
        job = null
        result = null
        publish(ConversionState())
    }

    private suspend fun save(key: String, done: ConversionStatus.Done, bytes: ByteArray) {
        val outcome = useCase.save(done.fileName, bytes)
        val saved = outcome.getOrDefault(false)
        publish(
            ConversionState(
                key,
                done.copy(saved = done.saved || saved, saveFailed = outcome.isFailure),
            ),
        )
    }

    private fun publish(next: ConversionState) {
        state = next
        onChange(next)
    }
}
