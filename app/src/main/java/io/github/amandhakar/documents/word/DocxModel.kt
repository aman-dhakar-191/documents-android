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
) : DocxBlock {
    val text: String get() = runs.joinToString("") { it.text }
}

/** Rows of cells; each cell holds blocks (paragraphs, or even another table). */
data class DocxTable(val rows: List<List<List<DocxBlock>>>) : DocxBlock

/** A picture. [bytes] is the original png / jpeg / gif / bmp / webp file. */
class DocxImage(val name: String, val bytes: ByteArray) : DocxBlock

/** What was read from a .docx. [truncated] is true when the file was bigger than the app shows. */
class DocxDocument(val blocks: List<DocxBlock>, val truncated: Boolean = false) {
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
