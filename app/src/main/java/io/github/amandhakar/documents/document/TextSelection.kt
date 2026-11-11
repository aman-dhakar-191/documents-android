package io.github.amandhakar.documents.document

/** A spot on a page as fractions of its width and height: (0,0) top-left, (1,1) bottom-right. */
data class NormPoint(val x: Float, val y: Float)

/** A rectangle on a page as fractions of its width and height. */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * One end of a text selection: either a spot the user pointed at, or an end kept from an earlier
 * selection. Dragging one handle keeps the other end exactly where it was by passing it back as
 * [Kept], so it doesn't shift to the nearest letter again.
 */
sealed interface TextEdge {
    data class At(val point: NormPoint) : TextEdge

    /** An end returned with a previous selection. [token] is whatever the document gave out; pass it back unchanged. */
    class Kept(val token: Any) : TextEdge
}

/**
 * Text selected on one page, with the boxes to highlight (one per line). [start] is the earlier
 * end in reading order and [stop] the later one, so the first handle always belongs to [start].
 */
data class PageText(
    val text: String,
    val bounds: List<NormRect>,
    val start: TextEdge.Kept,
    val stop: TextEdge.Kept,
)

/**
 * Maths between a page's own units (points, 1/72 inch) and the fractions the screen code uses.
 * Fractions mean the selection doesn't depend on how big the page is drawn or how far it is zoomed.
 */
object PageGeometry {
    /** [positionPx] as a fraction of [sizePx], kept within 0..1. A zero size gives 0. */
    fun fraction(positionPx: Float, sizePx: Int): Float {
        if (sizePx <= 0 || positionPx.isNaN()) return 0f
        return (positionPx / sizePx).coerceIn(0f, 1f)
    }

    /** A fractional position as whole points inside a page of [pageSize] points. */
    fun toPoints(fraction: Float, pageSize: Int): Int {
        if (pageSize <= 0 || fraction.isNaN()) return 0
        return Math.round(fraction.coerceIn(0f, 1f) * pageSize).coerceIn(0, pageSize)
    }

    /** A box given in points as fractions of the page, kept inside the page. */
    fun normalize(left: Float, top: Float, right: Float, bottom: Float, pageWidth: Int, pageHeight: Int): NormRect {
        if (pageWidth <= 0 || pageHeight <= 0) return NormRect(0f, 0f, 0f, 0f)
        fun x(v: Float) = (v / pageWidth).coerceIn(0f, 1f)
        fun y(v: Float) = (v / pageHeight).coerceIn(0f, 1f)
        return NormRect(x(minOf(left, right)), y(minOf(top, bottom)), x(maxOf(left, right)), y(maxOf(top, bottom)))
    }

    /** Joins the selected text blocks, one per line, without blank lines or stray spaces. */
    fun joinText(parts: List<String>): String =
        parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}
