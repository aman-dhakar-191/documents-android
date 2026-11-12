package io.github.amandhakar.documents.edit

/**
 * A new piece of text placed on a page. [place] says where (its width is how wide the text is; its top-left corner
 * is where the text box starts), [aspect] is the text picture's width / height and [baselineRatio] how far down that
 * picture the line the letters sit on is (0 to 1), so the real text can be drawn at the same spot.
 */
data class PlacedText(
    val place: PlacedSignature,
    val text: String,
    val aspect: Float,
    val baselineRatio: Float,
    /** The font family, weight and slant to draw it in. */
    val font: FontStyleChoice = FontStyleChoice(),
    /** The colour of the text. */
    val ink: Int = 0xFF000000.toInt(),
    /** The font size in points when it was copied from nearby text; null when the width chosen sets the size. */
    val fontSizePt: Float? = null,
)

/** What new text copies from the text nearest to where it was dropped. */
data class NearbyStyle(val line: TextLine, val pageWidthPt: Float, val ink: Int)

/** Plain-number sizing of a new text box. */
object TextBoxMath {
    const val MIN_PT = 4f
    const val MAX_PT = 200f
    const val MAX_LENGTH = 200

    /**
     * The font size in points that makes the text span [widthFraction] of a page [pageWidthPt] wide, given how
     * wide the text is at 1 point ([textWidthPerPt]). Kept between [MIN_PT] and [MAX_PT].
     */
    fun fontSizePt(widthFraction: Float, pageWidthPt: Float, textWidthPerPt: Float): Float {
        require(textWidthPerPt > 0f) { "text has no width" }
        return (widthFraction * pageWidthPt / textWidthPerPt).coerceIn(MIN_PT, MAX_PT)
    }

    /** The baseline as a fraction of the page height from the top: the box top plus the part of the box above the line. */
    fun baselineFromTop(topFraction: Float, heightFraction: Float, baselineRatio: Float): Float =
        topFraction + heightFraction * baselineRatio

    /** A typical line of text is this many points tall (12 point text), and a typical page this wide (A4). */
    const val LINE_PT = 14f
    const val REFERENCE_PAGE_PT = 595f

    /**
     * How wide new text should start, as a fraction of the visible area, so that it looks about like normal 12 point text at the
     * current [zoom] (1 = the page fills the screen width), whatever its length. [aspect] is the text picture's width / height.
     */
    fun startWidth(aspect: Float, zoom: Float): Float =
        (aspect * LINE_PT / REFERENCE_PAGE_PT * zoom).coerceIn(Placement.MIN_WIDTH, Placement.MAX_WIDTH)

    /** The width, as a fraction of the page, of text drawn at [sizePt] when it is [widthPerPt] wide at 1 point. */
    fun widthFraction(sizePt: Float, widthPerPt: Float, pageWidthPt: Float): Float {
        require(pageWidthPt > 0f) { "page has no width" }
        return sizePt * widthPerPt / pageWidthPt
    }

    /** [text] made fit for a text box: one line, trimmed, at most [MAX_LENGTH] characters. */
    fun prepare(text: String): String = PdfText.clean(text).take(MAX_LENGTH)
}
