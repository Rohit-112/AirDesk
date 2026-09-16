package com.share.app.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import com.share.app.domain.media.FileKind
import com.share.app.domain.media.FileTypes
import com.share.app.domain.media.ImageFailure
import com.share.app.domain.media.ImageFormat
import com.share.app.domain.media.ImageFormats
import com.share.app.domain.media.ImageProcessingException
import com.share.app.domain.media.ImageProcessor
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Android pixel work, on the platform's own codecs.
 *
 * From Android 9, ImageDecoder reads HEIC (and AVIF from Android 12), applies
 * the EXIF rotation, and decodes straight to the target size, so a 48 MP photo
 * never exists in memory at full resolution just to become a thumbnail.
 * Android 8 falls back to BitmapFactory, which can do neither of the first two.
 */
class AndroidImageProcessor : ImageProcessor {

    override fun preview(bytes: ByteArray, mimeType: String, maxDimension: Int): ByteArray? {
        val bitmap = when (FileTypes.kindOf(mimeType)) {
            FileKind.IMAGE -> decode(bytes, maxDimension)
            FileKind.VIDEO -> videoFrame(bytes, maxDimension)
            else -> null
        } ?: return null
        return try {
            encode(bitmap, webpFormat(), PREVIEW_QUALITY)
        } finally {
            bitmap.recycle()
        }
    }

    override fun convert(bytes: ByteArray, sourceType: String, format: ImageFormat): ByteArray {
        val decoded = decode(bytes, ImageFormats.MAX_SIDE)
        // JPEG has no alpha, so transparent areas would otherwise turn black.
        val bitmap = if (!format.supportsTransparency && decoded.hasAlpha()) onWhite(decoded) else decoded
        try {
            val target = when (format) {
                ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageFormat.WEBP -> webpFormat()
            }
            return encode(bitmap, target, format.quality)
        } finally {
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }
    }

    private fun decode(bytes: ByteArray, maxDimension: Int): Bitmap =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeWithImageDecoder(bytes, maxDimension)
            else decodeWithBitmapFactory(bytes, maxDimension)
        } catch (error: ImageProcessingException) {
            throw error
        } catch (error: Exception) {
            throw ImageProcessingException(ImageFailure.DECODE_FAILED, "This image could not be opened.")
        }

    private fun decodeWithImageDecoder(bytes: ByteArray, maxDimension: Int): Bitmap {
        val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Hardware bitmaps cannot be drawn onto or compressed from freely.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val size = ImageFormats.fitWithin(info.size.width, info.size.height, ImageFormats.MAX_PIXELS, maxDimension)
            if (size.width != info.size.width || size.height != info.size.height) {
                decoder.setTargetSize(size.width, size.height)
            }
        }
    }

    private fun decodeWithBitmapFactory(bytes: ByteArray, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ImageProcessingException(ImageFailure.DECODE_FAILED, "This image could not be opened.")
        }
        val size = ImageFormats.fitWithin(bounds.outWidth, bounds.outHeight, ImageFormats.MAX_PIXELS, maxDimension)

        // Halve while the result stays at least as large as the target, then
        // finish with one smooth scale.
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size.width && bounds.outHeight / (sample * 2) >= size.height) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw ImageProcessingException(ImageFailure.DECODE_FAILED, "This image could not be opened.")
        if (decoded.width == size.width && decoded.height == size.height) return decoded
        return Bitmap.createScaledBitmap(decoded, size.width, size.height, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    /** A still frame a little way in, because the very first one is often black. */
    private fun videoFrame(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(ByteArrayMediaSource(bytes))
            val frame = retriever.getFrameAtTime(VIDEO_FRAME_MICROS, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return null
            val size = ImageFormats.fitWithin(frame.width, frame.height, maxDimension = maxDimension)
            if (size.width == frame.width && size.height == frame.height) return frame
            return Bitmap.createScaledBitmap(frame, size.width, size.height, true).also {
                if (it !== frame) frame.recycle()
            }
        } catch (error: Exception) {
            return null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun onWhite(source: Bitmap): Bitmap {
        val flat = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        return flat
    }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(format, quality, out)) {
            throw ImageProcessingException(ImageFailure.ENCODE_FAILED, "The image could not be saved.")
        }
        return out.toByteArray()
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        // Before Android 11 the plain constant is the lossy encoder.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    private class ByteArrayMediaSource(private val bytes: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(size.toLong(), bytes.size - position).toInt()
            System.arraycopy(bytes, position.toInt(), buffer, offset, count)
            return count
        }

        override fun getSize(): Long = bytes.size.toLong()

        override fun close() = Unit
    }

    private companion object {
        const val PREVIEW_QUALITY = 75
        const val VIDEO_FRAME_MICROS = 500_000L
    }
}
