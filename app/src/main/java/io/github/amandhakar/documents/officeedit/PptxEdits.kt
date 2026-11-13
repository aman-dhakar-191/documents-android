package io.github.amandhakar.documents.officeedit

import io.github.amandhakar.documents.slides.PptxDeck
import io.github.amandhakar.documents.slides.PptxParagraph
import io.github.amandhakar.documents.slides.PptxRun
import io.github.amandhakar.documents.slides.PptxShape
import io.github.amandhakar.documents.slides.PptxSlide
import io.github.amandhakar.documents.slides.PptxTable
import io.github.amandhakar.documents.slides.PptxTableCell
import io.github.amandhakar.documents.slides.PptxTableRow
import io.github.amandhakar.documents.slides.PptxText

/** A change to the text of one paragraph of a slide ([slidePath] is the slide's file in the .pptx, [id] its paragraph in that file). */
data class PptxEdit(val slidePath: String, val id: Int, val text: String)

/**
 * The text changes made to a presentation so far, as an undoable list. Nothing here touches the file: [PptxEditModel]
 * shows them on screen and [PptxEditor] writes them. Free of Android calls so it can be unit tested.
 */
data class PptxEdits(val ops: List<PptxEdit> = emptyList()) {
    val isEmpty: Boolean get() = ops.isEmpty()
    val count: Int get() = resolved().size

    fun replace(slidePath: String, id: Int, text: String) = copy(ops = ops + PptxEdit(slidePath, id, text))

    fun undo() = copy(ops = ops.dropLast(1))

    /** The latest text for each changed paragraph. */
    fun resolved(): Map<Pair<String, Int>, String> {
        val m = LinkedHashMap<Pair<String, Int>, String>()
        for (op in ops) m[op.slidePath to op.id] = op.text
        return m
    }

    fun textOf(slidePath: String, id: Int): String? = resolved()[slidePath to id]
}

/** Shows [PptxEdits] on a read [PptxDeck], so the screen can preview them before anything is saved. */
object PptxEditModel {
    fun apply(deck: PptxDeck, edits: PptxEdits): PptxDeck {
        if (edits.isEmpty) return deck
        val changes = edits.resolved()
        return PptxDeck(deck.widthEmu, deck.heightEmu, deck.slides.map { slide(it, changes) }, deck.truncated)
    }

    private fun slide(s: PptxSlide, changes: Map<Pair<String, Int>, String>): PptxSlide {
        fun p(para: PptxParagraph): PptxParagraph {
            val text = changes[s.path to para.id] ?: return para
            return withText(para, text)
        }
        val shapes: List<PptxShape> = s.shapes.map { shape ->
            when (shape) {
                is PptxText -> PptxText(shape.box, shape.paragraphs.map(::p), shape.fillHex, shape.lineHex, shape.geometry, shape.anchor)
                is PptxTable -> PptxTable(
                    shape.box, shape.columnWidthsEmu,
                    shape.rows.map { row -> PptxTableRow(row.heightEmu, row.cells.map { PptxTableCell(it.paragraphs.map(::p), it.fillHex) }) },
                )
                else -> shape
            }
        }
        return PptxSlide(shapes, s.backgroundHex, s.notes, s.path)
    }

    /** The paragraph with new [text], in the look of its first run. */
    fun withText(p: PptxParagraph, text: String): PptxParagraph {
        val look = p.runs.firstOrNull() ?: PptxRun("", false, false, false, 18f, "000000")
        return p.copy(runs = if (text.isEmpty()) emptyList() else listOf(look.copy(text = text)))
    }
}
