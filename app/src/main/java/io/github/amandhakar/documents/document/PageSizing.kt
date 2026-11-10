package io.github.amandhakar.documents.document

import kotlin.math.roundToInt
import kotlin.math.sqrt

data class BitmapSize(val width: Int, val height: Int)

/** Works out how big a rendered page bitmap should be, without letting it grow past memory limits. */
object PageSizing {
    const val MAX_WIDTH_PX = 2400
    const val MAX_PIXELS = 16_000_000

    fun bitmapSize(
        pageWidth: Int,
        pageHeight: Int,
        targetWidthPx: Int,
        maxWidthPx: Int = MAX_WIDTH_PX,
        maxPixels: Int = MAX_PIXELS,
    ): BitmapSize {
        require(pageWidth > 0 && pageHeight > 0) { "page size must be positive" }
        var width = targetWidthPx.coerceIn(1, maxWidthPx)
        var height = (width.toLong() * pageHeight / pageWidth).toInt().coerceAtLeast(1)
        val pixels = width.toLong() * height
        if (pixels > maxPixels) {
            // Very tall or very large page: shrink both sides, keeping the aspect ratio.
            val scale = sqrt(maxPixels.toDouble() / pixels)
            width = (width * scale).roundToInt().coerceAtLeast(1)
            height = (height * scale).roundToInt().coerceAtLeast(1)
        }
        return BitmapSize(width, height)
    }
}
