package io.github.amandhakar.documents.edit

/**
 * A signature placed on page [page] (0-based). [left] and [top] are where its top-left corner is, and
 * [width] how wide it is, all as fractions of the page as shown on screen (0 to 1, from the top-left).
 * Its height follows from [width] and the shapes of the signature and the page, see [Placement.height].
 */
data class PlacedSignature(val page: Int, val left: Float, val top: Float, val width: Float)

/** Where a signature sits on a page, and keeping it on the page. Plain numbers, so it can be unit tested. */
object Placement {
    const val START_WIDTH = 0.35f
    const val MIN_WIDTH = 0.03f
    const val MAX_WIDTH = 0.9f

    /**
     * The signature's height as a fraction of the page height. [signatureAspect] is the signature's
     * width / height and [pageAspect] the page's width / height as shown.
     */
    fun height(width: Float, signatureAspect: Float, pageAspect: Float): Float {
        require(signatureAspect > 0f && pageAspect > 0f) { "aspect ratios must be positive" }
        return width * pageAspect / signatureAspect
    }

    /** A new signature (or text) on [page], centred, [width] wide (a fraction of the visible area). */
    fun initial(page: Int, signatureAspect: Float, pageAspect: Float, width: Float = START_WIDTH): PlacedSignature =
        fit(PlacedSignature(page, 0f, 0f, width), signatureAspect, pageAspect).let {
            val h = height(it.width, signatureAspect, pageAspect)
            fit(it.copy(left = (1f - it.width) / 2f, top = (1f - h) / 2f), signatureAspect, pageAspect)
        }

    /** [placed] moved by [dx] and [dy] (fractions of the page), kept on the page. */
    fun moved(placed: PlacedSignature, dx: Float, dy: Float, signatureAspect: Float, pageAspect: Float): PlacedSignature =
        fit(placed.copy(left = placed.left + dx, top = placed.top + dy), signatureAspect, pageAspect)

    /** [placed] made [dWidth] wider (or narrower if negative), keeping its top-left corner, within limits. */
    fun resized(placed: PlacedSignature, dWidth: Float, signatureAspect: Float, pageAspect: Float): PlacedSignature =
        fit(placed.copy(width = placed.width + dWidth), signatureAspect, pageAspect)

    /** Clamps the width, then moves the signature so all of it is on the page. */
    fun fit(placed: PlacedSignature, signatureAspect: Float, pageAspect: Float): PlacedSignature {
        var width = placed.width.coerceIn(MIN_WIDTH, MAX_WIDTH)
        var h = height(width, signatureAspect, pageAspect)
        if (h > 1f) { // a very tall signature on a short page: shrink it to the page height
            width = (width / h).coerceAtLeast(MIN_WIDTH)
            h = height(width, signatureAspect, pageAspect)
        }
        val left = placed.left.coerceIn(0f, (1f - width).coerceAtLeast(0f))
        val top = placed.top.coerceIn(0f, (1f - h).coerceAtLeast(0f))
        return PlacedSignature(placed.page, left, top, width)
    }
}
