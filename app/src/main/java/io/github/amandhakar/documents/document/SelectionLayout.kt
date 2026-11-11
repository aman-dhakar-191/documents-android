package io.github.amandhakar.documents.document

/** A position in pixels. */
data class PxPoint(val x: Float, val y: Float)

/**
 * Where the two selection handles and the floating menu go, worked out from the highlight boxes.
 * Free of Android calls so it can be unit tested.
 */
object SelectionLayout {
    /** The start handle sits under the beginning of the first line. */
    fun startTip(bounds: List<NormRect>): NormPoint? = bounds.firstOrNull()?.let { NormPoint(it.left, it.bottom) }

    /** The end handle sits under the end of the last line. */
    fun endTip(bounds: List<NormRect>): NormPoint? = bounds.lastOrNull()?.let { NormPoint(it.right, it.bottom) }

    /**
     * The spot at the start of the first line, halfway up the text: what the magnifier shows while
     * the start handle is dragged (the handle itself is below the line, where the finger covers it).
     */
    fun startAnchor(bounds: List<NormRect>): NormPoint? =
        bounds.firstOrNull()?.let { NormPoint(it.left, (it.top + it.bottom) / 2f) }

    /** The spot at the end of the last line, halfway up the text: what the magnifier shows for the end handle. */
    fun endAnchor(bounds: List<NormRect>): NormPoint? =
        bounds.lastOrNull()?.let { NormPoint(it.right, (it.top + it.bottom) / 2f) }

    fun top(bounds: List<NormRect>): Float? = bounds.minOfOrNull { it.top }

    fun bottom(bounds: List<NormRect>): Float? = bounds.maxOfOrNull { it.bottom }

    /** Halfway between the leftmost and rightmost edge of the highlight. */
    fun centerX(bounds: List<NormRect>): Float? {
        val left = bounds.minOfOrNull { it.left } ?: return null
        val right = bounds.maxOfOrNull { it.right } ?: return null
        return (left + right) / 2f
    }

    /**
     * The top-left corner for the floating menu, all in pixels on the page: centred over the
     * selection and above it if it fits, otherwise below the handles. It is always kept inside the
     * page, because touches outside the page's bounds never reach it.
     */
    fun menuPosition(
        selectionTop: Float,
        selectionBottom: Float,
        selectionCenterX: Float,
        menuWidth: Float,
        menuHeight: Float,
        pageWidth: Float,
        pageHeight: Float,
        gap: Float,
        handleHeight: Float,
    ): PxPoint {
        val x = (selectionCenterX - menuWidth / 2f).coerceIn(0f, maxOf(0f, pageWidth - menuWidth))
        val above = selectionTop - gap - menuHeight
        val y = if (above >= 0f) above else selectionBottom + handleHeight + gap
        return PxPoint(x, y.coerceIn(0f, maxOf(0f, pageHeight - menuHeight)))
    }
}
