@file:OptIn(ExperimentalForeignApi::class)

package com.share.app.data.media

import com.share.app.domain.media.FileKind
import com.share.app.domain.media.FileTypes
import com.share.app.domain.media.ImageFailure
import com.share.app.domain.media.ImageFormat
import com.share.app.domain.media.ImageFormats
import com.share.app.domain.media.ImageProcessingException
import com.share.app.domain.media.ImageProcessor
import com.share.app.platform.toByteArray
import com.share.app.platform.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image as SkiaImage
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIRectFill
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/**
 * iOS pixel work, on UIKit.
 *
 * UIImage reads everything the Photos app produces, HEIC included, and
 * redrawing it through a renderer applies the EXIF rotation. iOS has no WebP
 * encoder, so a WEBP is drawn by UIKit and encoded by the Skia that Compose
 * already ships.
 */
class UIKitImageProcessor : ImageProcessor {

    override fun preview(bytes: ByteArray, mimeType: String, maxDimension: Int): ByteArray? {
        val image = when (FileTypes.kindOf(mimeType)) {
            FileKind.IMAGE -> UIImage.imageWithData(bytes.toNSData())
            FileKind.VIDEO -> videoFrame(bytes, mimeType)
            else -> null
        } ?: return null
        // PNG keeps transparency; at thumbnail size it is still only kilobytes.
        return UIImagePNGRepresentation(redraw(image, maxDimension, opaque = false))?.toByteArray()
    }

    override fun convert(bytes: ByteArray, sourceType: String, format: ImageFormat): ByteArray {
        val image = UIImage.imageWithData(bytes.toNSData())
            ?: throw ImageProcessingException(ImageFailure.DECODE_FAILED, "This image could not be opened.")
        // JPEG has no alpha, so transparent areas are painted white first.
        val drawn = redraw(image, ImageFormats.MAX_SIDE, opaque = !format.supportsTransparency)
        val encoded = when (format) {
            ImageFormat.JPEG -> UIImageJPEGRepresentation(drawn, format.quality / 100.0)?.toByteArray()
            ImageFormat.PNG -> UIImagePNGRepresentation(drawn)?.toByteArray()
            ImageFormat.WEBP -> UIImagePNGRepresentation(drawn)?.toByteArray()?.let { toWebp(it, format.quality) }
        }
        return encoded ?: throw ImageProcessingException(ImageFailure.ENCODE_FAILED, "The image could not be saved.")
    }

    /** Draws at pixel scale 1, so the output has exactly the pixels asked for. */
    private fun redraw(image: UIImage, maxDimension: Int, opaque: Boolean): UIImage {
        val (pointWidth, pointHeight) = image.size.useContents { width to height }
        val scale = image.scale
        val size = ImageFormats.fitWithin(
            width = (pointWidth * scale).roundToInt().coerceAtLeast(1),
            height = (pointHeight * scale).roundToInt().coerceAtLeast(1),
            maxPixels = ImageFormats.MAX_PIXELS,
            maxDimension = maxDimension,
        )
        val width = size.width.toDouble()
        val height = size.height.toDouble()

        val format = UIGraphicsImageRendererFormat.defaultFormat()
        format.scale = 1.0
        format.opaque = opaque
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width, height), format = format)
        return renderer.imageWithActions { _ ->
            if (opaque) {
                UIColor.whiteColor.setFill()
                UIRectFill(CGRectMake(0.0, 0.0, width, height))
            }
            image.drawInRect(CGRectMake(0.0, 0.0, width, height))
        }
    }

    private fun toWebp(png: ByteArray, quality: Int): ByteArray {
        val webp = try {
            val image = SkiaImage.makeFromEncoded(png)
            try {
                image.encodeToData(EncodedImageFormat.WEBP, quality)?.bytes
            } finally {
                image.close()
            }
        } catch (error: Exception) {
            null
        }
        return webp ?: throw ImageProcessingException(ImageFailure.UNSUPPORTED_OUTPUT, "This device cannot create WEBP files.")
    }

    /**
     * A still frame half a second in. AVFoundation only reads from a file, so
     * the bytes are written to a temporary one for the moment it takes.
     */
    private fun videoFrame(bytes: ByteArray, mimeType: String): UIImage? {
        val extension = if (mimeType == "video/quicktime") "mov" else "mp4"
        val path = NSTemporaryDirectory() + "knotic-preview-${Uuid.random()}.$extension"
        if (!bytes.toNSData().writeToFile(path, atomically = true)) return null
        try {
            val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)
            val generator = AVAssetImageGenerator(asset = asset)
            generator.appliesPreferredTrackTransform = true
            @Suppress("DEPRECATION")
            val frame = generator.copyCGImageAtTime(CMTimeMake(value = 1, timescale = 2), actualTime = null, error = null)
                ?: return null
            try {
                return UIImage.imageWithCGImage(frame)
            } finally {
                CGImageRelease(frame)
            }
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }
}
