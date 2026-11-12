package io.github.amandhakar.documents.edit

/** A page row in the list: its index, where it starts (px from the top of the visible area) and how tall it is. */
data class PageRow(val index: Int, val offset: Int, val size: Int)

object VisiblePage {
    /** The page showing the most inside a viewport of [viewport] px, or null when nothing is showing. */
    fun mostVisible(rows: List<PageRow>, viewport: Int): Int? =
        rows.maxByOrNull { visiblePart(it, viewport) }?.takeIf { visiblePart(it, viewport) > 0 }?.index

    private fun visiblePart(row: PageRow, viewport: Int): Int =
        (minOf(row.offset + row.size, viewport) - maxOf(row.offset, 0)).coerceAtLeast(0)
}
