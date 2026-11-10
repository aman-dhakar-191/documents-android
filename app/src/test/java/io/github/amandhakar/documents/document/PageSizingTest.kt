package io.github.amandhakar.documents.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSizingTest {
    @Test fun keepsAspectRatio() {
        // A4 in points, rendered 1000px wide.
        assertEquals(BitmapSize(1000, 1415), PageSizing.bitmapSize(595, 842, 1000))
    }

    @Test fun widthIsCapped() {
        val size = PageSizing.bitmapSize(600, 800, 100_000, maxWidthPx = 1200)
        assertEquals(BitmapSize(1200, 1600), size)
    }

    @Test fun tinyOrNegativeTargetStillGivesAtLeastOnePixel() {
        val size = PageSizing.bitmapSize(600, 800, -5)
        assertEquals(1, size.width)
        assertTrue(size.height >= 1)
    }

    @Test fun veryTallPageIsShrunkToThePixelBudget() {
        val size = PageSizing.bitmapSize(100, 100_000, 1000, maxPixels = 1_000_000)
        assertTrue(size.width.toLong() * size.height <= 1_050_000)
        // Aspect ratio is preserved within rounding.
        assertEquals(1000.0, size.height.toDouble() / size.width, 20.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroPageWidthIsRejected() {
        PageSizing.bitmapSize(0, 800, 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroPageHeightIsRejected() {
        PageSizing.bitmapSize(600, 0, 1000)
    }
}
