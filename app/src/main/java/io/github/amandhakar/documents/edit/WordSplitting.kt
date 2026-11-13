package io.github.amandhakar.documents.edit

/** One character as the PDF places it: its text, where it starts, how wide it is and its size, all in points. */
data class Glyph(val text: String, val left: Float, val width: Float, val fontSize: Float)

/**
 * Splits the characters PdfBox hands over as one chunk into pieces wherever there is a wide gap. PdfBox can give a
 * whole row across the page as one chunk (for example the same row of two newspaper columns, or table cells), so
 * without this the text of separate columns is glued into one line. Free of Android calls so it can be unit tested.
 */
object WordSplitting {
    /** A gap wider than this many font sizes between two characters starts a new piece (the same rule as joining words into lines). */
    const val GAP_IN_SIZES = 1.5f

    fun split(glyphs: List<Glyph>): List<List<Glyph>> {
        if (glyphs.isEmpty()) return emptyList()
        val pieces = ArrayList<MutableList<Glyph>>()
        var current = mutableListOf(glyphs.first())
        for (i in 1 until glyphs.size) {
            val prev = glyphs[i - 1]
            val g = glyphs[i]
            val gap = g.left - (prev.left + prev.width)
            if (gap > maxOf(prev.fontSize, g.fontSize) * GAP_IN_SIZES) {
                pieces += current
                current = mutableListOf(g)
            } else {
                current += g
            }
        }
        pieces += current
        return pieces
    }

    /** True when the pieces' text adds up to [original] (ignoring spaces), so splitting lost and added nothing. */
    fun sameText(pieces: List<List<Glyph>>, original: String): Boolean =
        pieces.joinToString("") { p -> p.joinToString("") { it.text } }.filterNot { it.isWhitespace() } == original.filterNot { it.isWhitespace() }
}
