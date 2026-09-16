package com.share.app.data.media

import com.share.app.domain.media.FileKind
import com.share.app.domain.media.FileTypes
import com.share.app.domain.media.ImageFailure
import com.share.app.domain.media.ImageFormat
import com.share.app.domain.media.ImageFormats
import com.share.app.domain.media.ImageProcessingException
import com.share.app.domain.media.ImageProcessor
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

/**
 * Desktop pixel work, on the Skia that already draws the window.
 *
 * Skia reads JPEG, PNG, WEBP, GIF (first frame), BMP and ICO, and writes JPEG,
 * PNG and WEBP. It has no HEIC or AVIF decoder and no video decoder, so those
 * files get a type icon instead of a picture, and converting a HEIC says the
 * image could not be opened.
 */
class SkiaImageProcessor : ImageProcessor {

    override fun preview(bytes: ByteArray, mimeType: String, maxDimension: Int): ByteArray? {
        if (FileTypes.kindOf(mimeType) != FileKind.IMAGE) return null
        return render(bytes, maxDimension, EncodedImageFormat.WEBP, PREVIEW_QUALITY, background = null)
    }

    override fun convert(bytes: ByteArray, sourceType: String, format: ImageFormat): ByteArray {
        val target = when (format) {
            ImageFormat.JPEG -> EncodedImageFormat.JPEG
            ImageFormat.PNG -> EncodedImageFormat.PNG
            ImageFormat.WEBP -> EncodedImageFormat.WEBP
        }
        return render(
            bytes = bytes,
            maxDimension = ImageFormats.MAX_SIDE,
            target = target,
            quality = format.quality,
            // JPEG has no alpha, so transparent areas would otherwise turn black.
            background = if (format.supportsTransparency) null else Color.WHITE,
        )
    }

    private fun render(
        bytes: ByteArray,
        maxDimension: Int,
        target: EncodedImageFormat,
        quality: Int,
        background: Int?,
    ): ByteArray {
        val codec = try {
            Codec.makeFromData(Data.makeFromBytes(bytes))
        } catch (error: Exception) {
            throw ImageProcessingException(ImageFailure.DECODE_FAILED, "This image could not be opened.")
        }
        val bitmap = try {
            codec.readPixels()
        } catch (error: Exception) {
            codec.close()
            throw ImageProcessingException(ImageFailure.DECODE_FAILED, "This image could not be opened.")
        }

        // Camera JPEGs are stored sideways with a note saying which way is up.
        val origin = codec.encodedOrigin
        val sourceWidth = bitmap.width
        val sourceHeight = bitmap.height
        val (width, height) = if (origin.swapsWidthHeight()) sourceHeight to sourceWidth else sourceWidth to sourceHeight
        val size = ImageFormats.fitWithin(width, height, ImageFormats.MAX_PIXELS, maxDimension)

        val image = Image.makeFromBitmap(bitmap.setImmutable())
        val surface = Surface.makeRasterN32Premul(size.width, size.height)
        try {
            val canvas = surface.canvas
            canvas.clear(background ?: Color.TRANSPARENT)
            canvas.scale(size.width / width.toFloat(), size.height / height.toFloat())
            canvas.concat(origin.toMatrix(sourceWidth, sourceHeight))
            val bounds = Rect.makeWH(sourceWidth.toFloat(), sourceHeight.toFloat())
            // Mipmaps, or a 48 MP photo shrunk to a thumbnail comes out aliased.
            canvas.drawImageRect(image, bounds, bounds, FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), null, true)

            val snapshot = surface.makeImageSnapshot()
            try {
                val data = snapshot.encodeToData(target, quality)
                    ?: throw ImageProcessingException(
                        ImageFailure.UNSUPPORTED_OUTPUT,
                        "This device cannot create ${target.name} files.",
                    )
                return data.bytes
            } finally {
                snapshot.close()
            }
        } finally {
            surface.close()
            image.close()
            bitmap.close()
            codec.close()
        }
    }

    private companion object {
        const val PREVIEW_QUALITY = 75
    }
}
