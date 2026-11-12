package io.github.amandhakar.documents.edit

/**
 * A new piece of text placed on a page. [place] says where (its width is how wide the text is; its top-left corner
 * is where the text box starts), [aspect] is the text picture's width / height and [baselineRatio] how far down that
 * picture the line the letters sit on is (0 to 1), so the real text can be drawn at the same spot.
 */
data class PlacedText(val place: PlacedSignature, val text: String, val aspect: Float, val baselineRatio: Float)

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

    /** [text] made fit for a text box: one line, trimmed, at most [MAX_LENGTH] characters. */
    fun prepare(text: String): String = PdfText.clean(text).take(MAX_LENGTH)
}
