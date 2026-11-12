package io.github.amandhakar.documents.edit

/** A page row in the list: its index, where it starts (px from the top of the visible area) and how tall it is. */
data class PageRow(val index: Int, val offset: Int, val size: Int)

/**
 * Works out which page a signature that floats above the document ended up over, and where on that
 * page it sits. All lengths are in pixels. Plain numbers, so it can be unit tested.
 */
object PageHit {
    /**
     * The signature's box on screen is ([left], [top]) with size [width] x [height]. [scrollX] is how far the
     * pages are scrolled sideways (when zoomed in) and [pageWidth] how wide a page is on screen. The page it
     * overlaps the most (vertically) gets it, and it is kept inside that page. Null when it isn't over any page.
     */
    fun resolve(
        rows: List<PageRow>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scrollX: Float,
        pageWidth: Float,
        signatureAspect: Float,
    ): PlacedSignature? {
        if (pageWidth <= 0f || width <= 0f || height <= 0f) return null
        val bottom = top + height
        val best = rows.filter { it.size > 0 }.maxByOrNull { overlap(it, top, bottom) } ?: return null
        if (overlap(best, top, bottom) <= 0f) return null
        val placed = PlacedSignature(
            page = best.index,
            left = (left + scrollX) / pageWidth,
            top = (top - best.offset) / best.size,
            width = width / pageWidth,
        )
        return Placement.fit(placed, signatureAspect, pageWidth / best.size)
    }

    private fun overlap(row: PageRow, top: Float, bottom: Float): Float =
        (minOf(bottom, (row.offset + row.size).toFloat()) - maxOf(top, row.offset.toFloat())).coerceAtLeast(0f)
}
