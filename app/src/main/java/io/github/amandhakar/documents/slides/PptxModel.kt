package io.github.amandhakar.documents.slides

/** A rectangle in EMU, the unit PowerPoint stores positions in (914400 per inch, 12700 per point). */
data class EmuBox(val x: Long, val y: Long, val w: Long, val h: Long)

data class PptxRun(
    val text: String,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val sizePt: Float,
    /** Six hex digits. */
    val colorHex: String,
)

enum class PptxAlign { Start, Center, End, Justify }

/** One paragraph; [bullet] is the bullet or number text in front of it, or null. [level] is 0 for the top level. */
data class PptxParagraph(val runs: List<PptxRun>, val align: PptxAlign, val level: Int, val bullet: String?) {
    val text: String get() = runs.joinToString("") { it.text }
}

enum class PptxGeometry { Rect, RoundRect, Ellipse }

enum class PptxAnchor { Top, Middle, Bottom }

sealed interface PptxShape {
    val box: EmuBox
}

/** A text box or shape (with or without text). [fillHex] and [lineHex] are null for none. */
class PptxText(
    override val box: EmuBox,
    val paragraphs: List<PptxParagraph>,
    val fillHex: String?,
    val lineHex: String?,
    val geometry: PptxGeometry,
    val anchor: PptxAnchor,
) : PptxShape

class PptxPicture(override val box: EmuBox, val name: String, val bytes: ByteArray) : PptxShape

class PptxTableCell(val paragraphs: List<PptxParagraph>, val fillHex: String?)

class PptxTableRow(val heightEmu: Long, val cells: List<PptxTableCell>)

class PptxTable(override val box: EmuBox, val columnWidthsEmu: List<Long>, val rows: List<PptxTableRow>) : PptxShape

/** Something that is part of the slide but isn't drawn (a chart, SmartArt, an object): a labelled grey box, never dropped silently. */
class PptxNotShown(override val box: EmuBox, val label: String) : PptxShape

class PptxSlide(val shapes: List<PptxShape>, val backgroundHex: String, val notes: String) {
    /** The slide's text, one paragraph per line (table cells separated by tabs). */
    fun plainText(): String = shapes.flatMap { shape ->
        when (shape) {
            is PptxText -> shape.paragraphs.map { it.text }
            is PptxTable -> shape.rows.map { row -> row.cells.joinToString("\t") { c -> c.paragraphs.joinToString(" ") { it.text } } }
            is PptxPicture, is PptxNotShown -> emptyList()
        }
    }.filter { it.isNotBlank() }.joinToString("\n")

    /** A title for lists: the first line of text, shortened. */
    fun title(maxLength: Int = 60): String = plainText().lineSequence().firstOrNull().orEmpty().take(maxLength)
}

/** A presentation: the slide size in EMU, and the slides. [truncated] is true when it had more slides than the app shows. */
class PptxDeck(val widthEmu: Long, val heightEmu: Long, val slides: List<PptxSlide>, val truncated: Boolean = false)
