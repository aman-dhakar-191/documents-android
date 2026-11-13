package io.github.amandhakar.documents.word

/** A stretch of text with one look, for measuring: its [text], font, weight, slant and size in points. */
class WrapRun(val text: String, val face: WordFace, val bold: Boolean, val italic: Boolean, val sizePt: Float)

/**
 * Where lines break, by adding up real letter widths of the bundled fonts: the way Word fills a line (greedy, breaking
 * after spaces and hyphens, a word too long for a line broken inside it, tabs to the next 36 pt stop, a line break
 * where the text has "\n"). No kerning, as in Word's default. Free of Android calls so it can be unit tested.
 */
object TextWrap {
    private const val TAB_STOP_PT = 36f

    /**
     * The end of each line as a character offset into the runs' joined text (exclusive), so the lines are
     * `[0, ends[0])`, `[ends[0], ends[1])` and so on. There is always at least one line. [firstWidthPt] is the room on
     * the first line, [widthPt] on the others.
     */
    fun lineEnds(runs: List<WrapRun>, firstWidthPt: Float, widthPt: Float): List<Int> {
        val total = runs.sumOf { it.text.length }
        if (total == 0) return listOf(0)
        // Width of every UTF-16 unit (a surrogate pair's second half counts nothing).
        val widths = FloatArray(total)
        val chars = CharArray(total)
        var pos = 0
        for (run in runs) {
            var i = 0
            while (i < run.text.length) {
                val cp = run.text.codePointAt(i)
                val n = Character.charCount(cp)
                chars[pos] = run.text[i]
                widths[pos] = WordFonts.advanceEm(run.face, run.bold, run.italic, cp) * run.sizePt
                if (n == 2) {
                    chars[pos + 1] = run.text[i + 1]
                    widths[pos + 1] = 0f
                }
                pos += n
                i += n
            }
        }
        val ends = ArrayList<Int>()
        var lineStart = 0
        var limit = maxOf(1f, firstWidthPt)
        var x = 0f // width used on this line, spaces after the last word included
        var wordStart = 0 // where the word being built began
        var i = 0
        fun endLine(at: Int) {
            ends += at
            lineStart = at
            wordStart = at
            x = 0f
            limit = maxOf(1f, widthPt)
        }
        while (i < total) {
            val c = chars[i]
            if (c == '\n') {
                endLine(i + 1)
                i++
                continue
            }
            val w = if (c == '\t') TAB_STOP_PT - (x % TAB_STOP_PT) else widths[i]
            val isSpace = c == ' ' || c == '\t'
            if (!isSpace && x + w > limit + 0.01f) {
                // The word doesn't fit: move it to a new line, or if it is the whole line, cut it here.
                if (wordStart > lineStart) {
                    endLine(wordStart)
                    // Measure the word again from the left edge.
                    var j = wordStart
                    while (j < i) {
                        x += if (chars[j] == '\t') TAB_STOP_PT - (x % TAB_STOP_PT) else widths[j]
                        j++
                    }
                    continue
                } else if (i > lineStart) {
                    endLine(i)
                    continue
                }
            }
            x += w
            i++
            // A word starts after a space or a hyphen.
            if (isSpace || c == '-') {
                wordStart = i
            }
        }
        ends += total
        return ends
    }
}
