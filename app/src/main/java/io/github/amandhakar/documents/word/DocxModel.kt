package io.github.amandhakar.documents.word

/** One stretch of text with the same look. Sizes are in half points, as Word stores them. */
data class DocxRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val sizeHalfPoints: Int? = null,
    /** Six hex digits, e.g. "FF0000", or null for the default colour. */
    val colorHex: String? = null,
)

enum class DocxAlign { Start, Center, End, Justify }

/** The bullet or number in front of a list item, and how far it is indented (0 = outermost). */
data class DocxListMark(val level: Int, val label: String)

sealed interface DocxBlock

data class DocxParagraph(
    val runs: List<DocxRun>,
    /** 1 to 9 for headings (the Title style counts as 1), 0 for normal text. */
    val headingLevel: Int = 0,
    val list: DocxListMark? = null,
    val align: DocxAlign = DocxAlign.Start,
    /** The paragraph starts a new page ("page break before"). */
    val pageBreakBefore: Boolean = false,
    /** A page break comes after this paragraph's text. */
    val pageBreakAfter: Boolean = false,
) : DocxBlock {
    val text: String get() = runs.joinToString("") { it.text }
}

/** Rows of cells; each cell holds blocks (paragraphs, or even another table). */
data class DocxTable(val rows: List<List<List<DocxBlock>>>) : DocxBlock

/** A picture. [bytes] is the original png / jpeg / gif / bmp / webp file. */
class DocxImage(val name: String, val bytes: ByteArray) : DocxBlock

/** The paper: size and margins in points (72 to the inch). Defaults to A4 with 2.54 cm (1 inch) margins. */
data class PageSetup(
    val widthPt: Float = A4_WIDTH_PT,
    val heightPt: Float = A4_HEIGHT_PT,
    val marginTopPt: Float = DEFAULT_MARGIN_PT,
    val marginRightPt: Float = DEFAULT_MARGIN_PT,
    val marginBottomPt: Float = DEFAULT_MARGIN_PT,
    val marginLeftPt: Float = DEFAULT_MARGIN_PT,
) {
    val contentWidthPt: Float get() = widthPt - marginLeftPt - marginRightPt
    val contentHeightPt: Float get() = heightPt - marginTopPt - marginBottomPt

    companion object {
        const val A4_WIDTH_PT = 595.3f
        const val A4_HEIGHT_PT = 841.9f
        const val DEFAULT_MARGIN_PT = 72f

        private const val MIN_PAGE_TWIPS = 2000 // about 3.5 cm: smaller is nonsense
        private const val MAX_PAGE_TWIPS = 60000 // about 106 cm

        /**
         * A page from Word's numbers (twentieths of a point). Anything missing or absurd falls back to A4 (size) or
         * one inch (margins), so a damaged file can't make an unusable page.
         */
        fun fromTwips(width: Int?, height: Int?, top: Int?, right: Int?, bottom: Int?, left: Int?): PageSetup {
            val sizeOk = width != null && height != null && width in MIN_PAGE_TWIPS..MAX_PAGE_TWIPS && height in MIN_PAGE_TWIPS..MAX_PAGE_TWIPS
            val w = if (sizeOk) width!! / 20f else A4_WIDTH_PT
            val h = if (sizeOk) height!! / 20f else A4_HEIGHT_PT
            fun margin(v: Int?): Float? = v?.takeIf { it >= 0 }?.let { it / 20f }
            val t = margin(top)
            val r = margin(right)
            val b = margin(bottom)
            val l = margin(left)
            val marginsOk = t != null && r != null && b != null && l != null &&
                w - l - r >= 100f && h - t - b >= 100f
            return if (marginsOk) PageSetup(w, h, t!!, r!!, b!!, l!!) else PageSetup(w, h)
        }
    }
}

/** What was read from a .docx. [truncated] is true when the file was bigger than the app shows. */
class DocxDocument(val blocks: List<DocxBlock>, val truncated: Boolean = false, val page: PageSetup = PageSetup()) {
    /** All the text, one paragraph per line (tables: cells separated by tabs). */
    fun plainText(): String = blocks.joinToString("\n") { plain(it) }

    private fun plain(block: DocxBlock): String = when (block) {
        is DocxParagraph -> block.text
        is DocxTable -> block.rows.joinToString("\n") { row -> row.joinToString("\t") { cell -> cell.joinToString(" ") { plain(it) } } }
        is DocxImage -> ""
    }
}

/** Why a file couldn't be read. The message is safe to show. */
class DocxException(message: String) : java.io.IOException(message)
