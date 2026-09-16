package com.share.app.domain.media

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The image formats every platform can write, and the rules for turning one
 * image into another. A port of the web client's `domain/imageFormats.ts`;
 * the pixel work itself is each platform's [ImageProcessor].
 */
enum class ImageFormat(
    val mimeType: String,
    val extension: String,
    val label: String,
    /** Encoder quality for lossy formats, 0 to 100. PNG is lossless and ignores it. */
    val quality: Int,
    /** JPEG has no alpha channel, so transparent areas need a colour painted under them. */
    val supportsTransparency: Boolean,
) {
    // Most compatible first, which is also the order the buttons appear in.
    JPEG("image/jpeg", "jpg", "JPG", quality = 92, supportsTransparency = false),
    PNG("image/png", "png", "PNG", quality = 100, supportsTransparency = true),
    WEBP("image/webp", "webp", "WEBP", quality = 90, supportsTransparency = true),
}

data class PixelSize(val width: Int, val height: Int)

object ImageFormats {
    private val HEIF_TYPES = setOf("image/heic", "image/heif")

    /**
     * Sources worth offering a conversion for. TIFF is left out, as on the web:
     * not every platform can open it, and a button that always fails is worse
     * than none.
     */
    private val CONVERTIBLE_TYPES = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif",
        "image/bmp",
        "image/avif",
        "image/svg+xml",
        "image/x-icon",
    ) + HEIF_TYPES

    /**
     * The same ceiling the web client uses: about 16.7 million pixels. iOS
     * refuses larger canvases, and on a phone a 48 MP photo decoded at full size
     * is nearly 200 MB, so anything larger is scaled down to fit first.
     */
    const val MAX_PIXELS = 16_777_216L

    /** A single side is capped too, so a long panorama cannot slip under the area budget. */
    const val MAX_SIDE = 16_384

    fun isHeif(mimeType: String): Boolean = mimeType in HEIF_TYPES

    /** The formats worth offering for a file, never including the one it already is. */
    fun conversionTargets(mimeType: String): List<ImageFormat> =
        if (mimeType in CONVERTIBLE_TYPES) ImageFormat.entries.filter { it.mimeType != mimeType } else emptyList()

    /**
     * "IMG_0042.HEIC" becomes "IMG_0042.jpg"; a name without an extension just
     * gains one. A file that already carries the target extension - a PNG named
     * ".jpg" - is marked, so the result never shares the original's name.
     */
    fun convertedFileName(fileName: String, format: ImageFormat): String {
        val extension = FileTypes.extensionOf(fileName)
        val baseName = (if (extension.isNotEmpty()) fileName.dropLast(extension.length + 1) else fileName).trim()
        val suffix = if (extension == format.extension) "-converted" else ""
        return "${baseName.ifEmpty { "image" }}$suffix.${format.extension}"
    }

    /** Scales down, never up, keeping the aspect ratio. */
    fun fitWithin(width: Int, height: Int, maxPixels: Long? = null, maxDimension: Int? = null): PixelSize {
        var scale = 1.0
        val area = width.toLong() * height.toLong()
        if (maxPixels != null && area > maxPixels) {
            scale = sqrt(maxPixels.toDouble() / area.toDouble())
        }
        if (maxDimension != null) {
            scale = min(scale, maxDimension.toDouble() / max(width, height).toDouble())
        }
        return PixelSize(
            width = max(1, floor(width * scale).toInt()),
            height = max(1, floor(height * scale).toInt()),
        )
    }

    /**
     * How a converted file compares with the original, to the nearest percent.
     * Empty when there is nothing to compare against.
     */
    fun describeSizeChange(before: Long, after: Long): String {
        if (before <= 0) return ""
        val change = kotlin.math.round((after - before).toDouble() / before * 100).toInt()
        return when {
            change == 0 -> "same size"
            change < 0 -> "${-change}% smaller"
            else -> "$change% larger"
        }
    }
}
