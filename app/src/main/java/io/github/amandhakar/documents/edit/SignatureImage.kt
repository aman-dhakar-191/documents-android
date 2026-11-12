package io.github.amandhakar.documents.edit

/** A rectangle of pixels: [left] and [top] included, [right] and [bottom] not. */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** Turns a photo or scan of a signature into a see-through picture. Plain numbers (ARGB ints), so it can be unit tested. */
object SignatureImage {
    /** At or below this brightness a pixel is kept as it is; at or above [CLEAR_FROM] it becomes see-through. */
    const val KEEP_UNTIL = 150
    const val CLEAR_FROM = 225
    private const val VISIBLE_ALPHA = 24

    private fun brightness(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /** The same pixels with light ones (the paper) made see-through, softly so the pen edges stay smooth. */
    fun whiteToTransparent(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
        val argb = pixels[i]
        val light = brightness(argb)
        val keep = when {
            light <= KEEP_UNTIL -> 1f
            light >= CLEAR_FROM -> 0f
            else -> (CLEAR_FROM - light).toFloat() / (CLEAR_FROM - KEEP_UNTIL)
        }
        val alpha = (((argb ushr 24) and 0xFF) * keep).toInt()
        (alpha shl 24) or (argb and 0x00FFFFFF)
    }

    /** The smallest box holding every visible pixel, or null when nothing is visible. */
    fun inkBounds(pixels: IntArray, width: Int, height: Int): PixelBox? {
        require(width > 0 && height > 0 && pixels.size == width * height) { "pixels don't match the size" }
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (((pixels[y * width + x] ushr 24) and 0xFF) >= VISIBLE_ALPHA) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        return if (right < 0) null else PixelBox(left, top, right + 1, bottom + 1)
    }

    /** [box] made [pad] bigger on every side but kept inside a [width] x [height] picture. */
    fun padded(box: PixelBox, pad: Int, width: Int, height: Int): PixelBox = PixelBox(
        (box.left - pad).coerceAtLeast(0),
        (box.top - pad).coerceAtLeast(0),
        (box.right + pad).coerceAtMost(width),
        (box.bottom + pad).coerceAtMost(height),
    )

    /** The biggest power of two to divide both sides by while the longer side stays at least [maxSide]. 1 means don't shrink. */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        val longest = maxOf(width, height)
        if (longest <= 0 || maxSide <= 0) return 1
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}
