package io.github.amandhakar.documents.officeedit

import io.github.amandhakar.documents.word.DocxBlock
import io.github.amandhakar.documents.word.DocxDocument
import io.github.amandhakar.documents.word.DocxParagraph
import io.github.amandhakar.documents.word.DocxRun
import io.github.amandhakar.documents.word.DocxTable
import io.github.amandhakar.documents.word.DocxTextBox

/** One change to a Word document. Paragraphs are named by [DocxParagraph.id]; new ones get a made-up id from [DocxEdits.TEMP_BASE] up. */
sealed interface DocxEdit

data class ReplaceParagraph(val id: Int, val text: String) : DocxEdit

data class DeleteParagraph(val id: Int) : DocxEdit

data class InsertParagraph(val id: Int, val afterId: Int, val text: String) : DocxEdit

/** A paragraph added by the person, after paragraph [afterId]. */
data class Insert(val id: Int, val afterId: Int, val text: String)

/** The edits boiled down: what each changed paragraph says now, which are gone, and what was added where. */
class ResolvedDocxEdits(val replaced: Map<Int, String>, val deleted: Set<Int>, val inserts: List<Insert>) {
    fun insertsAfter(id: Int): List<Insert> = inserts.filter { it.afterId == id }
    val changeCount: Int get() = replaced.size + deleted.size + inserts.size
}

/**
 * The changes made to a Word document so far, as an undoable list. Nothing here touches the file: [DocxEditModel]
 * shows them on screen, and [DocxEditor] writes them. Free of Android calls so it can be unit tested.
 */
data class DocxEdits(val ops: List<DocxEdit> = emptyList()) {
    val count: Int get() = resolved().changeCount
    val isEmpty: Boolean get() = ops.isEmpty()

    fun replace(id: Int, text: String) = copy(ops = ops + ReplaceParagraph(id, text))

    fun delete(id: Int) = copy(ops = ops + DeleteParagraph(id))

    /** Adds a paragraph after [afterId]. The new paragraph's own id is [lastInsertId] of the result. */
    fun insertAfter(afterId: Int, text: String) = copy(ops = ops + InsertParagraph(nextTempId(), afterId, text))

    /** Takes back the latest change. */
    fun undo() = copy(ops = ops.dropLast(1))

    private fun nextTempId() = TEMP_BASE + ops.count { it is InsertParagraph }

    val lastInsertId: Int? get() = (ops.lastOrNull() as? InsertParagraph)?.id

    /** What a paragraph says now: its replaced text, or null if it has not been replaced. */
    fun textOf(id: Int): String? = resolved().let { r -> r.replaced[id] ?: r.inserts.firstOrNull { it.id == id }?.text }

    fun resolved(): ResolvedDocxEdits {
        val replaced = LinkedHashMap<Int, String>()
        val deleted = LinkedHashSet<Int>()
        val inserts = ArrayList<Insert>()
        for (op in ops) {
            when (op) {
                is ReplaceParagraph -> if (op.id >= TEMP_BASE) {
                    val i = inserts.indexOfFirst { it.id == op.id }
                    if (i >= 0) inserts[i] = inserts[i].copy(text = op.text)
                } else {
                    replaced[op.id] = op.text
                }
                is DeleteParagraph -> if (op.id >= TEMP_BASE) {
                    removeInsertAndWhatHangsOffIt(inserts, op.id)
                } else {
                    deleted += op.id
                    replaced.remove(op.id)
                }
                is InsertParagraph -> inserts += Insert(op.id, op.afterId, op.text)
            }
        }
        return ResolvedDocxEdits(replaced, deleted, inserts)
    }

    private fun removeInsertAndWhatHangsOffIt(inserts: MutableList<Insert>, id: Int) {
        val gone = inserts.filter { it.id == id }
        inserts.removeAll(gone)
        for (g in gone) inserts.filter { it.afterId == g.id }.forEach { removeInsertAndWhatHangsOffIt(inserts, it.id) }
    }

    companion object {
        /** Ids from here up belong to paragraphs the person added; real paragraphs are numbered from 0. */
        const val TEMP_BASE = 1_000_000
    }
}

/** Shows [DocxEdits] on a read [DocxDocument], so the screen can preview them before anything is saved. */
object DocxEditModel {
    fun apply(doc: DocxDocument, edits: DocxEdits): DocxDocument {
        if (edits.isEmpty) return doc
        val r = edits.resolved()
        return DocxDocument(mapBlocks(doc.blocks, r), doc.truncated, doc.page, doc.headerFooter)
    }

    private fun mapBlocks(blocks: List<DocxBlock>, r: ResolvedDocxEdits): List<DocxBlock> {
        val out = ArrayList<DocxBlock>()
        for (b in blocks) {
            when {
                b is DocxParagraph && b.id >= 0 -> {
                    if (b.id !in r.deleted) out += if (b.id in r.replaced) withText(b, r.replaced.getValue(b.id)) else b
                    out += chain(b.id, b, r)
                }
                b is DocxTextBox -> out += DocxTextBox(mapBlocks(b.blocks, r), b.widthPt, b.lineWidthPt, b.lineHex, b.fillHex)
                b is DocxTable -> out += b.copy(rows = b.rows.map { row -> row.map { cell -> mapBlocks(cell, r) } })
                else -> out += b
            }
        }
        return out
    }

    /** The paragraphs added after [id], each followed by the ones added after it. [template] gives them its look. */
    private fun chain(id: Int, template: DocxParagraph, r: ResolvedDocxEdits): List<DocxBlock> {
        val out = ArrayList<DocxBlock>()
        for (ins in r.insertsAfter(id)) {
            val p = withText(template.copy(id = ins.id, pageBreakBefore = false, pageBreakAfter = false, columnBreakAfter = false, editable = true), ins.text)
            out += p
            out += chain(ins.id, p, r)
        }
        return out
    }

    /** The paragraph with new [text], in the look of its first run. */
    fun withText(p: DocxParagraph, text: String): DocxParagraph {
        val look = p.runs.firstOrNull() ?: DocxRun("")
        return p.copy(runs = if (text.isEmpty()) emptyList() else listOf(look.copy(text = text)))
    }
}
