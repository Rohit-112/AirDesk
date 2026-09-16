package com.share.app.data.media

import com.share.app.domain.media.FileTypes
import com.share.app.domain.media.ImageFailure
import com.share.app.domain.media.ImageFormat
import com.share.app.domain.media.ImageProcessingException
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SkiaImageProcessorTest {
    private val processor = SkiaImageProcessor()

    /** Left half red, right half transparent. */
    private fun png(width: Int, height: Int): ByteArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.clear(Color.TRANSPARENT)
        surface.canvas.drawRect(Rect.makeWH(width / 2f, height.toFloat()), Paint().apply { color = Color.RED })
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }

    private fun decode(bytes: ByteArray): Image = Image.makeFromEncoded(bytes)

    @Test
    fun convertsToEveryFormatItOffers() {
        val source = png(40, 20)
        ImageFormat.entries.forEach { format ->
            val converted = processor.convert(source, "image/png", format)
            assertEquals(format.mimeType, FileTypes.sniffMimeType(converted), format.name)
            val image = decode(converted)
            assertEquals(40, image.width)
            assertEquals(20, image.height)
        }
    }

    @Test
    fun paintsTransparencyWhiteForJpeg() {
        val jpeg = processor.convert(png(40, 20), "image/png", ImageFormat.JPEG)
        val pixels = org.jetbrains.skia.Bitmap.makeFromImage(decode(jpeg))
        val right = pixels.getColor(35, 10)
        // Lossy, so close to white rather than exactly.
        assert(Color.getR(right) > 240 && Color.getG(right) > 240 && Color.getB(right) > 240) { "was ${right.toUInt().toString(16)}" }
    }

    @Test
    fun keepsThumbnailsWithinTheirSize() {
        val preview = assertNotNull(processor.preview(png(800, 400), "image/png", 192))
        val image = decode(preview)
        assertEquals(192, image.width)
        assertEquals(96, image.height)
    }

    @Test
    fun appliesTheExifRotationACameraWrites() {
        // A 40x20 JPEG whose EXIF says it has to be turned 90 degrees to be upright.
        val rotated = withExifOrientation(processor.convert(png(40, 20), "image/png", ImageFormat.JPEG), orientation = 6)
        val converted = decode(processor.convert(rotated, "image/jpeg", ImageFormat.PNG))
        assertEquals(20, converted.width)
        assertEquals(40, converted.height)
    }

    @Test
    fun saysSoWhenItCannotReadTheFile() {
        val error = assertFailsWith<ImageProcessingException> {
            processor.convert("not an image".encodeToByteArray(), "image/png", ImageFormat.JPEG)
        }
        assertEquals(ImageFailure.DECODE_FAILED, error.reason)
    }

    @Test
    fun drawsNoPreviewForWhatItCannotDraw() {
        assertNull(processor.preview(png(10, 10), "video/mp4", 192))
        assertNull(processor.preview("%PDF-1.7".encodeToByteArray(), "application/pdf", 192))
    }

    /** Inserts a minimal APP1 Exif segment carrying only the orientation tag. */
    private fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val tiff = byteArrayOf(
            0x4d, 0x4d, 0x00, 0x2a, 0x00, 0x00, 0x00, 0x08, // big-endian header, first IFD at 8
            0x00, 0x01, // one entry
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, orientation.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, // no next IFD
        )
        val payload = "Exif".encodeToByteArray() + byteArrayOf(0, 0) + tiff
        val length = payload.size + 2
        val segment = byteArrayOf(0xff.toByte(), 0xe1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        // Straight after the start-of-image marker.
        return jpeg.copyOfRange(0, 2) + segment + jpeg.copyOfRange(2, jpeg.size)
    }
}
