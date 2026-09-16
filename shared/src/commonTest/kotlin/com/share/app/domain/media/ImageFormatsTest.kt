package com.share.app.domain.media

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The same cases as the web client's `imageFormats.test.ts`. */
class ImageFormatsTest {
    @Test
    fun offersEveryFormatExceptTheOneTheFileAlreadyIs() {
        assertEquals(listOf(ImageFormat.JPEG, ImageFormat.WEBP), ImageFormats.conversionTargets("image/png"))
        assertEquals(listOf(ImageFormat.PNG, ImageFormat.WEBP), ImageFormats.conversionTargets("image/jpeg"))
    }

    @Test
    fun offersAllThreeForFormatsNothingCanWriteBack() {
        assertEquals(ImageFormat.entries, ImageFormats.conversionTargets("image/heic"))
        assertEquals(ImageFormat.entries, ImageFormats.conversionTargets("image/gif"))
    }

    @Test
    fun offersNothingItCouldNotDeliver() {
        assertEquals(emptyList(), ImageFormats.conversionTargets("application/pdf"))
        assertEquals(emptyList(), ImageFormats.conversionTargets("video/mp4"))
        assertEquals(emptyList(), ImageFormats.conversionTargets("image/tiff"))
    }

    @Test
    fun knowsWhichSourcesAreHeif() {
        assertTrue(ImageFormats.isHeif("image/heic"))
        assertTrue(ImageFormats.isHeif("image/heif"))
        assertFalse(ImageFormats.isHeif("image/png"))
    }

    @Test
    fun swapsTheExtension() {
        assertEquals("IMG_0042.jpg", ImageFormats.convertedFileName("IMG_0042.HEIC", ImageFormat.JPEG))
        assertEquals("holiday.photo.webp", ImageFormats.convertedFileName("holiday.photo.png", ImageFormat.WEBP))
    }

    @Test
    fun addsOneWhenThereWasNone() {
        assertEquals("scan.png", ImageFormats.convertedFileName("scan", ImageFormat.PNG))
    }

    @Test
    fun neverHandsBackTheOriginalsNameForAMisnamedFile() {
        assertEquals("IMG_misnamed-converted.jpg", ImageFormats.convertedFileName("IMG_misnamed.jpg", ImageFormat.JPEG))
        assertEquals("photo.jpg", ImageFormats.convertedFileName("photo.jpeg", ImageFormat.JPEG))
    }

    @Test
    fun neverScalesUp() {
        assertEquals(PixelSize(100, 50), ImageFormats.fitWithin(100, 50, ImageFormats.MAX_PIXELS, 256))
    }

    @Test
    fun capsTheLongestSide() {
        assertEquals(PixelSize(256, 192), ImageFormats.fitWithin(4000, 3000, maxDimension = 256))
        assertEquals(PixelSize(192, 256), ImageFormats.fitWithin(3000, 4000, maxDimension = 256))
    }

    @Test
    fun bringsA48MpPhotoUnderThePixelLimitWithoutDistortingIt() {
        val size = ImageFormats.fitWithin(8000, 6000, maxPixels = ImageFormats.MAX_PIXELS)
        assertTrue(size.width.toLong() * size.height <= ImageFormats.MAX_PIXELS)
        assertTrue(abs(size.width.toDouble() / size.height - 4.0 / 3.0) < 0.001)
    }

    @Test
    fun saysWhichWayTheSizeWent() {
        assertEquals("62% smaller", ImageFormats.describeSizeChange(1000, 380))
        assertEquals("12% larger", ImageFormats.describeSizeChange(1000, 1120))
        assertEquals("same size", ImageFormats.describeSizeChange(1000, 1004))
        assertEquals("", ImageFormats.describeSizeChange(0, 10))
    }
}
