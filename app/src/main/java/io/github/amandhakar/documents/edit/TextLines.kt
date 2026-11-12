package io.github.amandhakar.documents.edit

/**
 * One word as the PDF stores it, in points measured from the top-left corner of the page: where it starts,
 * the line it sits on ([baseline] is the distance from the top of the page), how wide and tall it is.
 */
data class TextWord(
    val text: String,
    val left: Float,
    val baseline: Float,
    val width: Float,
    val height: Float,
    val fontSize: Float,
)

/** A line of text on a page, in points from the top-left corner of the page. */
data class TextLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val baseline: Float,
    val fontSize: Float,
)

/** The lines of one page, with the page's size in points. [supported] is false for pages this can't handle (rotated ones). */
data class PageLines(val widthPt: Float, val heightPt: Float, val lines: List<TextLine>, val supported: Boolean = true)

/**
 * A change to a line of text: the old line is covered by a box of [cover] colour and [text] is drawn in its place.
 * The box is ([left], [top]) with size [width] x [height], and [baseline] is where the text sits, all as fractions of
 * the page (0 to 1 from the top-left). [fontSizePt] is the original size in points and [pageWidthPt] the page's width
 * in points (to scale the preview).
 */
data class TextEdit(
    val page: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val baseline: Float,
    val fontSizePt: Float,
    val pageWidthPt: Float,
    val text: String,
    val cover: Int,
) {
    /** True when [other] covers the same original line (so a new edit replaces this one). */
    fun sameLineAs(other: TextEdit): Boolean =
        page == other.page && kotlin.math.abs(left - other.left) < 0.002f && kotlin.math.abs(top - other.top) < 0.002f

    companion object {
        fun forLine(page: Int, page0: PageLines, line: TextLine, text: String, cover: Int): TextEdit = TextEdit(
            page = page,
            left = line.left / page0.widthPt,
            top = line.top / page0.heightPt,
            width = (line.right - line.left) / page0.widthPt,
            height = (line.bottom - line.top) / page0.heightPt,
            baseline = line.baseline / page0.heightPt,
            fontSizePt = line.fontSize,
            pageWidthPt = page0.widthPt,
            text = text,
            cover = cover,
        )
    }
}

/** Plain-number helpers for finding, fitting and checking text. */
object LineGrouping {
    /** Words (in reading order) joined into lines: same baseline and close together. Blank words are dropped. */
    fun group(words: List<TextWord>): List<TextLine> {
        val lines = mutableListOf<TextLine>()
        var current: TextLine? = null
        for (word in words) {
            if (word.text.isBlank()) continue
            val wordLine = TextLine(
                text = word.text,
                left = word.left,
                top = word.baseline - word.height,
                right = word.left + word.width,
                bottom = word.baseline + word.height * DESCENT,
                baseline = word.baseline,
                fontSize = word.fontSize,
            )
            val c = current
            if (c != null && sameLine(c, wordLine)) {
                current = c.copy(
                    text = c.text + " " + word.text,
                    left = minOf(c.left, wordLine.left),
                    top = minOf(c.top, wordLine.top),
                    right = maxOf(c.right, wordLine.right),
                    bottom = maxOf(c.bottom, wordLine.bottom),
                    fontSize = maxOf(c.fontSize, wordLine.fontSize),
                )
            } else {
                if (c != null) lines += c
                current = wordLine
            }
        }
        current?.let { lines += it }
        return lines
    }

    private const val DESCENT = 0.25f

    private fun sameLine(line: TextLine, word: TextLine): Boolean {
        val size = maxOf(line.fontSize, word.fontSize)
        val gap = word.left - line.right
        return kotlin.math.abs(word.baseline - line.baseline) <= size * 0.3f && gap >= -size && gap <= size * 1.5f
    }
}

object LineHit {
    /** The line at ([x], [y]) points from the page's top-left, allowing [slop] points around it; the closest if several. */
    fun find(lines: List<TextLine>, x: Float, y: Float, slop: Float = 3f): TextLine? =
        lines.filter { x >= it.left - slop && x <= it.right + slop && y >= it.top - slop && y <= it.bottom + slop }
            .minByOrNull {
                val dx = x - (it.left + it.right) / 2f
                val dy = y - (it.top + it.bottom) / 2f
                dx * dx + dy * dy
            }
}

object TextFit {
    const val SMALLEST = 0.6f

    /** The size to draw text at: [fontSize] if it fits in [boxWidth], else smaller (never below 60%) so it does. */
    fun size(fontSize: Float, boxWidth: Float, textWidth: Float): Float {
        if (fontSize <= 0f || boxWidth <= 0f || textWidth <= boxWidth) return fontSize
        return maxOf(fontSize * boxWidth / textWidth, fontSize * SMALLEST)
    }
}

object PdfText {
    // What the standard Helvetica font can draw: printable ASCII, Latin-1, and the extra WinAnsi characters.
    private val EXTRA = "€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ"

    /** [text] with line breaks and tabs turned into spaces and the ends trimmed (a text change is one line). */
    fun clean(text: String): String = text.map { if (it == '\n' || it == '\r' || it == '\t') ' ' else it }.joinToString("").trim()

    private fun supported(c: Char): Boolean = c.code in 0x20..0x7E || c.code in 0xA0..0xFF || c in EXTRA

    /** The distinct characters in [text] that can't be drawn yet (for example Hindi or emoji), in order. */
    fun unsupported(text: String): List<Char> = text.filter { !supported(it) }.toList().distinct()
}

object ColorSample {
    /** The average of [colors] (ARGB ints) as an opaque colour; white when there are none. */
    fun average(colors: List<Int>): Int {
        if (colors.isEmpty()) return 0xFFFFFFFF.toInt()
        var r = 0
        var g = 0
        var b = 0
        for (c in colors) {
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        val n = colors.size
        return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }
}
