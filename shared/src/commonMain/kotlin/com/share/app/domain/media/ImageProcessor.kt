package com.share.app.domain.media

import com.share.app.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Why a conversion did not produce a file. */
enum class ImageFailure {
    /** The platform could not read the source at all. */
    DECODE_FAILED,

    /** Read fine, but writing the result failed. */
    ENCODE_FAILED,

    /** This platform cannot write the requested format. */
    UNSUPPORTED_OUTPUT,
}

class ImageProcessingException(val reason: ImageFailure, message: String) : Exception(message)

/** An encoded picture - a thumbnail or a preview - small enough to hold in memory. */
class ImagePreview(val bytes: ByteArray)

/**
 * The pixel work: previews of received files, thumbnails for the activity log
 * and "Save as" conversions. Each platform decodes with its own native codecs,
 * which is what lets Android and iOS open an iPhone's HEIC photos.
 *
 * Implementations may block and may use a lot of memory; callers go through
 * [SerialImageProcessor], which moves the work off the calling thread and runs
 * one job at a time.
 */
interface ImageProcessor {
    /**
     * A picture of an image, or a still frame of a video, no larger than
     * [maxDimension] on its longest side. Null when the platform cannot draw
     * one, which is normal and not worth reporting.
     */
    fun preview(bytes: ByteArray, mimeType: String, maxDimension: Int): ByteArray?

    /** Redraws an image as [format], entirely on this device. */
    @Throws(ImageProcessingException::class)
    fun convert(bytes: ByteArray, sourceType: String, format: ImageFormat): ByteArray
}

/**
 * One job at a time, off the caller's thread.
 *
 * A full-resolution decode of a 48 MP photo is close to 200 MB, and files
 * arriving back to back used to start every decode at once on the web - which
 * is how a phone runs out of memory. The same queue applies here.
 */
class SerialImageProcessor(private val delegate: ImageProcessor) {
    private val mutex = Mutex()

    suspend fun preview(bytes: ByteArray, mimeType: String, maxDimension: Int): ImagePreview? {
        val kind = FileTypes.kindOf(mimeType)
        if (kind != FileKind.IMAGE && kind != FileKind.VIDEO) return null
        return run {
            try {
                delegate.preview(bytes, mimeType, maxDimension)?.let(::ImagePreview)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // Plenty of real files cannot be drawn: a codec the platform
                // lacks, a damaged file. The row keeps its type icon.
                AppLog.i { "No preview for a $mimeType file: ${error::class.simpleName}" }
                null
            }
        }
    }

    suspend fun convert(bytes: ByteArray, sourceType: String, format: ImageFormat): ByteArray = run {
        try {
            delegate.convert(bytes, sourceType, format)
        } catch (error: ImageProcessingException) {
            throw error
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            // OutOfMemoryError included: a damaged or enormous file must not
            // take the app down with it.
            AppLog.w(error) { "Converting a $sourceType file failed" }
            throw ImageProcessingException(ImageFailure.DECODE_FAILED, "This image could not be converted.")
        }
    }

    private suspend fun <T> run(block: () -> T): T =
        mutex.withLock { withContext(Dispatchers.IO) { block() } }

    companion object {
        /**
         * Longest side of an activity thumbnail. Rows show it at 40 dp, so it
         * stays sharp on a 3x screen and weighs a few kilobytes.
         */
        const val THUMBNAIL_MAX_DIMENSION = 192

        /** Longest side of the inbox preview: sharp on a phone, cheap to keep. */
        const val PREVIEW_MAX_DIMENSION = 1080

        fun describeFailure(error: Throwable, format: ImageFormat): String =
            if (error is ImageProcessingException && error.reason == ImageFailure.UNSUPPORTED_OUTPUT) {
                "This device can't create ${format.label} files. Try another format."
            } else {
                "This image couldn't be converted. It may be damaged, or in a format this device can't open."
            }
    }
}
