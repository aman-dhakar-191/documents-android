package io.github.amandhakar.documents.edit

/** A point on a page as fractions of the page (0 to 1 from the top-left). */
data class MarkPoint(val x: Float, val y: Float)

/** Something drawn on a page: a highlight or a pen stroke. */
sealed interface PageMark {
    val page: Int
}

/** A translucent box over a line of text. The box is ([left], [top]) with size [width] x [height], as fractions of the page. */
data class Highlight(
    override val page: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val color: Int,
) : PageMark {
    /** True when [other] is on the same line of the same page. */
    fun sameLineAs(other: Highlight): Boolean =
        page == other.page && kotlin.math.abs(left - other.left) < 0.002f && kotlin.math.abs(top - other.top) < 0.002f
}

/** A line drawn with a finger. [widthFraction] is the pen width as a fraction of the page width. */
data class PenStroke(
    override val page: Int,
    val points: List<MarkPoint>,
    val widthFraction: Float,
    val color: Int,
) : PageMark

/** Plain-number helpers for highlights and pen strokes. */
object Marks {
    val HIGHLIGHT_COLOR: Int = 0xFFFFEB3B.toInt()
    const val HIGHLIGHT_ALPHA = 0.4f
    const val PEN_WIDTH_FRACTION = 0.004f

    val PEN_COLORS: List<Int> = listOf(0xFF1A237E.toInt(), 0xFFC62828.toInt(), 0xFF000000.toInt())

    /** A highlight over [line] of a page. */
    fun highlightFor(page: Int, pageLines: PageLines, line: TextLine): Highlight = Highlight(
        page = page,
        left = line.left / pageLines.widthPt,
        top = line.top / pageLines.heightPt,
        width = (line.right - line.left) / pageLines.widthPt,
        height = (line.bottom - line.top) / pageLines.heightPt,
        color = HIGHLIGHT_COLOR,
    )

    /** [marks] with [highlight] added, or taken away again if that line was already highlighted. */
    fun toggle(marks: List<PageMark>, highlight: Highlight): List<PageMark> {
        val already = marks.any { it is Highlight && it.sameLineAs(highlight) }
        return if (already) marks.filterNot { it is Highlight && it.sameLineAs(highlight) } else marks + highlight
    }

    /** Fewer points for a stroke: the first, the last, and any point at least [minStep] (page fraction) from the last one kept. */
    fun thin(points: List<MarkPoint>, minStep: Float = 0.002f): List<MarkPoint> {
        if (points.size <= 2) return points
        val kept = mutableListOf(points.first())
        for (p in points.subList(1, points.size - 1)) {
            val last = kept.last()
            val dx = p.x - last.x
            val dy = p.y - last.y
            if (dx * dx + dy * dy >= minStep * minStep) kept += p
        }
        kept += points.last()
        return kept
    }

    /** A stroke needs at least two points (a tap draws nothing). */
    fun isStroke(points: List<MarkPoint>): Boolean = points.size >= 2 && points.any { it != points.first() }
}
