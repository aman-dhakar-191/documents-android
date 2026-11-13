package io.github.amandhakar.documents.markdown

import io.github.amandhakar.documents.word.BorderLine
import io.github.amandhakar.documents.word.CellAlign
import io.github.amandhakar.documents.word.CellBorders
import io.github.amandhakar.documents.word.CellProps
import io.github.amandhakar.documents.word.DocxAlign
import io.github.amandhakar.documents.word.DocxBlock
import io.github.amandhakar.documents.word.DocxDocument
import io.github.amandhakar.documents.word.DocxListMark
import io.github.amandhakar.documents.word.DocxParagraph
import io.github.amandhakar.documents.word.DocxRun
import io.github.amandhakar.documents.word.DocxTable
import io.github.amandhakar.documents.word.LineSpacing
import io.github.amandhakar.documents.word.PageSetup
import io.github.amandhakar.documents.word.ParagraphLayout
import io.github.amandhakar.documents.word.TableLayout

/**
 * Reads Markdown (CommonMark with GitHub's tables, task lists and strikethrough) into the same document model Word
 * files use, so the Word reading view, page view and Save as PDF show it. Headings, paragraphs, lists (nested, numbered,
 * task lists), block quotes, fenced and indented code, tables, horizontal rules and front matter are read; raw HTML is
 * shown as written and never run, images are shown as their description and nothing is fetched. Free of Android calls
 * so it can be unit tested.
 */
object MarkdownParser {
    private const val MAX_LINES = 200_000
    private const val MAX_NESTING = 12
    private const val CONTENT_WIDTH_PT = 487f
    private const val INDENT_PT = 18f
    private const val QUOTE_PT = 14f
    const val QUOTE_COLOR = "5A606C"
    private val RULE_LINE = BorderLine(1f, "BFC3CA")
    private val HEADING_LINE = BorderLine(0.5f, "D0D4DA")
    private val HEADING_SIZES = intArrayOf(0, 56, 44, 36, 30, 26, 24) // half points for levels 1 to 6

    /** The page the converted document uses: A4 with 54 pt margins. */
    val PAGE = PageSetup(PageSetup.A4_WIDTH_PT, PageSetup.A4_HEIGHT_PT, 54f, 54f, 54f, 54f)

    fun parse(source: String): DocxDocument {
        val text = source.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n').let { if (it.size > MAX_LINES) it.take(MAX_LINES) else it }.map { expandTabs(it) }
        val body = ArrayList<DocxBlock>()
        var start = 0
        // Front matter (the --- block some notes start with) is shown as code, not as a rule and a heading.
        if (lines.firstOrNull()?.trim() == "---") {
            val end = (1 until minOf(lines.size, 200)).firstOrNull { lines[it].trim() == "---" || lines[it].trim() == "..." }
            if (end != null && end > 1) {
                body += code(lines.subList(1, end).joinToString("\n"))
                start = end + 1
            }
        }
        val refs = HashMap<String, String>()
        val content = lines.subList(start, lines.size).filter { line ->
            val m = REF_DEF.matchEntire(line)
            if (m != null) {
                refs.putIfAbsent(m.groupValues[1].trim().lowercase(), m.groupValues[2])
                false
            } else {
                true
            }
        }
        body += Context(refs).blocks(content, 0, 0, 0)
        return DocxDocument(body, page = PAGE)
    }

    private val REF_DEF = Regex("^ {0,3}\\[([^\\]]+)]:\\s*<?([^\\s>]+)>?(?:\\s+[\"'(].*)?\\s*$")
    private val FENCE = Regex("^( {0,3})(`{3,}|~{3,})\\s*([^`]*)$")
    private val ATX = Regex("^ {0,3}(#{1,6})(?:[ \\t]+(.*?))?(?:[ \\t]+#+)?[ \\t]*$")
    private val RULE = Regex("^ {0,3}([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$")
    private val SETEXT = Regex("^ {0,3}(=+|-+)[ \\t]*$")
    private val BULLET = Regex("^( *)([-+*])( +|$)(.*)$")
    private val ORDERED = Regex("^( *)(\\d{1,9})([.)])( +|$)(.*)$")
    private val TABLE_SEPARATOR = Regex("^ *\\|? *:?-+:? *(\\| *:?-+:? *)*\\|? *$")
    private val QUOTE = Regex("^ {0,3}> ?(.*)$")

    private fun expandTabs(line: String): String {
        if ('\t' !in line) return line
        val out = StringBuilder()
        for (c in line) {
            if (c == '\t') out.append(" ".repeat(4 - out.length % 4)) else out.append(c)
        }
        return out.toString()
    }

    private fun indentOf(line: String) = line.length - line.trimStart(' ').length

    private fun code(text: String, indentPt: Float = 0f): DocxParagraph = DocxParagraph(
        listOf(DocxRun(text.ifEmpty { " " }, fontName = MarkdownInline.MONO)),
        layout = ParagraphLayout(
            spaceBeforePt = 2f, spaceAfterPt = 10f, line = LineSpacing.Multiple(1.1f), leftPt = indentPt,
            sizeHalfPoints = 20, fontName = MarkdownInline.MONO, shadingHex = "F3F4F6",
        ),
    )

    private class Context(val refs: Map<String, String>) {
        fun inline(text: String, base: InlineBase = InlineBase()) = MarkdownInline.parse(text, base, refs)

        /** Reads [lines] as blocks. [level] is the list nesting, [quote] the quote depth, [depth] guards against runaway nesting. */
        fun blocks(lines: List<String>, level: Int, quote: Int, depth: Int): List<DocxBlock> {
            val out = ArrayList<DocxBlock>()
            if (depth > MAX_NESTING) {
                // Too deep to read as structure: show what is left as plain text.
                val text = lines.joinToString("\n").trim()
                if (text.isNotEmpty()) out += paragraph(listOf(DocxRun(text)), level, quote)
                return out
            }
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.isBlank()) {
                    i++
                    continue
                }
                val indent = indentOf(line)
                // fenced code
                FENCE.matchEntire(line)?.let { m ->
                    val fence = m.groupValues[2]
                    val body = ArrayList<String>()
                    var j = i + 1
                    while (j < lines.size) {
                        val l = lines[j]
                        if (l.trimStart().startsWith(fence[0].toString().repeat(fence.length)) && l.trim().all { it == fence[0] }) break
                        body += if (indentOf(l) >= m.groupValues[1].length) l.drop(m.groupValues[1].length) else l.trimStart()
                        j++
                    }
                    out += code(body.joinToString("\n"), leftOf(level, quote))
                    i = minOf(j + 1, lines.size)
                    return@let
                } ?: run {
                    // ATX heading
                    val atx = if (indent < 4) ATX.matchEntire(line) else null
                    if (atx != null) {
                        val lv = atx.groupValues[1].length
                        out += heading(atx.groupValues[2], lv, out.isEmpty() && level == 0)
                        i++
                        return@run
                    }
                    if (indent < 4 && RULE.matches(line)) {
                        out += DocxParagraph(
                            emptyList(),
                            layout = ParagraphLayout(spaceBeforePt = 6f, spaceAfterPt = 10f, sizeHalfPoints = 12, bottomBorder = RULE_LINE),
                        )
                        i++
                        return@run
                    }
                    if (indent < 4 && QUOTE.matches(line)) {
                        val inner = ArrayList<String>()
                        var j = i
                        while (j < lines.size) {
                            val q = QUOTE.matchEntire(lines[j])
                            if (q != null) {
                                inner += q.groupValues[1]
                            } else if (lines[j].isNotBlank() && inner.lastOrNull()?.isNotBlank() == true && !startsBlock(lines[j])) {
                                inner += lines[j].trim() // a lazy continuation of the quoted paragraph
                            } else {
                                break
                            }
                            j++
                        }
                        out += blocks(inner, level, quote + 1, depth + 1)
                        i = j
                        return@run
                    }
                    if (indent < 4 && (BULLET.matches(line) || ORDERED.matches(line))) {
                        i = list(lines, i, level, quote, depth, out)
                        return@run
                    }
                    if (i + 1 < lines.size && line.contains('|') && TABLE_SEPARATOR.matches(lines[i + 1]) && lines[i + 1].contains('-')) {
                        val head = splitRow(line)
                        val sep = splitRow(lines[i + 1])
                        if (head.size == sep.size && head.isNotEmpty() && (line.contains('|') && (sep.size > 1 || lines[i + 1].contains('|')))) {
                            val rows = ArrayList<List<String>>()
                            var j = i + 2
                            while (j < lines.size && lines[j].isNotBlank() && lines[j].contains('|') && !startsBlock(lines[j])) {
                                rows += splitRow(lines[j])
                                j++
                            }
                            out += table(head, sep, rows)
                            i = j
                            return@run
                        }
                    }
                    if (indent >= 4) {
                        val body = ArrayList<String>()
                        var j = i
                        while (j < lines.size && (lines[j].isBlank() || indentOf(lines[j]) >= 4)) {
                            body += if (lines[j].isBlank()) "" else lines[j].drop(4)
                            j++
                        }
                        while (body.lastOrNull()?.isEmpty() == true) body.removeAt(body.size - 1)
                        out += code(body.joinToString("\n"), leftOf(level, quote))
                        i = j
                        return@run
                    }
                    if (line.trimStart().startsWith("<!--")) {
                        var j = i
                        while (j < lines.size && !lines[j].contains("-->")) j++
                        i = minOf(j + 1, lines.size)
                        return@run
                    }
                    // a paragraph (or a setext heading)
                    val para = ArrayList<String>()
                    para += line.trim()
                    var j = i + 1
                    var setext = 0
                    while (j < lines.size && lines[j].isNotBlank()) {
                        val s = SETEXT.matchEntire(lines[j])
                        if (s != null && indentOf(lines[j]) < 4) {
                            setext = if (s.groupValues[1].startsWith("=")) 1 else 2
                            j++
                            break
                        }
                        if (startsBlock(lines[j])) break
                        para += lines[j].trim()
                        j++
                    }
                    out += if (setext > 0) heading(para.joinToString(" "), setext, out.isEmpty() && level == 0) else paragraph(inline(hardBreaks(para, lines, i), quoteBase(quote)), level, quote)
                    i = j
                }
            }
            return out
        }

        private fun hardBreaks(para: List<String>, lines: List<String>, first: Int): String {
            // Lines were trimmed; put back the two-spaces hard break where the source had it.
            val sb = StringBuilder()
            for ((k, l) in para.withIndex()) {
                sb.append(l)
                if (k < para.size - 1) sb.append(if (lines[first + k].endsWith("  ") || lines[first + k].endsWith("\\")) "  \n" else "\n")
            }
            return sb.toString()
        }

        private fun quoteBase(quote: Int) = if (quote > 0) InlineBase(colorHex = QUOTE_COLOR) else InlineBase()

        private fun leftOf(level: Int, quote: Int) = (if (level > 0) level * INDENT_PT else 0f) + quote * QUOTE_PT

        /** True when [line] would start a block of its own, so a paragraph above it ends. */
        private fun startsBlock(line: String): Boolean {
            if (line.isBlank()) return true
            val indent = indentOf(line)
            if (indent >= 4) return false
            if (FENCE.matches(line) || ATX.matches(line) || RULE.matches(line) || QUOTE.matches(line)) return true
            if (BULLET.matches(line) && !RULE.matches(line)) return true
            val ordered = ORDERED.matchEntire(line)
            return ordered != null && ordered.groupValues[2] == "1" && ordered.groupValues[5].isNotBlank()
        }

        private fun paragraph(runs: List<DocxRun>, level: Int, quote: Int, after: Float = 8f) = DocxParagraph(
            runs,
            layout = ParagraphLayout(spaceBeforePt = 0f, spaceAfterPt = after, line = LineSpacing.Multiple(1.2f), leftPt = leftOf(level, quote), sizeHalfPoints = 22),
        )

        private fun heading(text: String, level: Int, first: Boolean): DocxParagraph {
            val size = HEADING_SIZES[level.coerceIn(1, 6)]
            return DocxParagraph(
                inline(text.trim(), InlineBase(bold = true)),
                headingLevel = level.coerceIn(1, 6),
                layout = ParagraphLayout(
                    spaceBeforePt = if (first) 0f else if (level <= 2) 18f else 12f, spaceAfterPt = 6f, line = LineSpacing.Multiple(1.15f),
                    sizeHalfPoints = size, bottomBorder = if (level <= 2) HEADING_LINE else null,
                ),
            )
        }

        // ---- lists ----

        private class Marker(val indent: Int, val ordered: Boolean, val number: Int, val contentIndent: Int, val text: String)

        private fun marker(line: String): Marker? {
            if (RULE.matches(line)) return null
            BULLET.matchEntire(line)?.let { m ->
                val spaces = m.groupValues[3].length
                val content = m.groupValues[4]
                val gap = if (spaces in 1..4 && content.isNotEmpty()) spaces else 1
                return Marker(m.groupValues[1].length, false, 0, m.groupValues[1].length + 1 + gap, content)
            }
            ORDERED.matchEntire(line)?.let { m ->
                val spaces = m.groupValues[4].length
                val content = m.groupValues[5]
                val gap = if (spaces in 1..4 && content.isNotEmpty()) spaces else 1
                val digits = m.groupValues[2]
                return Marker(m.groupValues[1].length, true, digits.toIntOrNull() ?: 1, m.groupValues[1].length + digits.length + 1 + gap, content)
            }
            return null
        }

        /** Reads the list starting at [from] into [out]; returns the index of the first line after it. */
        private fun list(lines: List<String>, from: Int, level: Int, quote: Int, depth: Int, out: MutableList<DocxBlock>): Int {
            val first = marker(lines[from]) ?: return from + 1
            var i = from
            var number = first.number
            while (i < lines.size) {
                val m = marker(lines[i]) ?: break
                if (m.ordered != first.ordered || m.indent > first.indent + 3 || m.indent < first.indent - 3) break
                val item = ArrayList<String>()
                item += m.text
                var j = i + 1
                while (j < lines.size) {
                    val l = lines[j]
                    if (l.isBlank()) {
                        var k = j
                        while (k < lines.size && lines[k].isBlank()) k++
                        if (k < lines.size && indentOf(lines[k]) >= m.contentIndent) {
                            for (b in j until k) item += ""
                            j = k
                            continue
                        }
                        break
                    }
                    if (indentOf(l) >= m.contentIndent) {
                        item += l.drop(m.contentIndent)
                        j++
                        continue
                    }
                    val sibling = marker(l)
                    if (sibling != null || startsBlock(l)) break
                    if (item.last().isNotBlank()) {
                        item += l.trim() // lazy continuation
                        j++
                        continue
                    }
                    break
                }
                val label = if (first.ordered) "$number." else null
                var task: String? = null
                var firstLine = item[0]
                Regex("^\\[([ xX])] +(.*)$").matchEntire(firstLine)?.let {
                    task = if (it.groupValues[1] == " ") "☐" else "☑"
                    firstLine = it.groupValues[2]
                }
                item[0] = firstLine
                val content = blocks(item, level + 1, quote, depth + 1)
                val mark = DocxListMark(level.coerceAtMost(8), task ?: label ?: bullet(level))
                var placed = false
                for (block in content) {
                    if (!placed && block is DocxParagraph && block.headingLevel == 0 && block.list == null) {
                        out += block.copy(list = mark, layout = block.layout?.copy(spaceAfterPt = 3f, leftPt = 0f))
                        placed = true
                    } else if (!placed && block is DocxParagraph && block.headingLevel > 0) {
                        out += DocxParagraph(listOf(DocxRun("")), list = mark, layout = ParagraphLayout(spaceAfterPt = 0f, sizeHalfPoints = 22))
                        out += block
                        placed = true
                    } else {
                        if (!placed) {
                            out += DocxParagraph(listOf(DocxRun("")), list = mark, layout = ParagraphLayout(spaceAfterPt = 0f, sizeHalfPoints = 22))
                            placed = true
                        }
                        out += block
                    }
                }
                if (!placed) out += DocxParagraph(listOf(DocxRun("")), list = mark, layout = ParagraphLayout(spaceAfterPt = 3f, sizeHalfPoints = 22))
                number++
                i = j
                // Blank lines between items don't end the list.
                var k = i
                while (k < lines.size && lines[k].isBlank()) k++
                if (k < lines.size && k > i) {
                    val next = marker(lines[k])
                    if (next != null && next.ordered == first.ordered && next.indent <= first.indent + 3 && next.indent >= first.indent - 3 && indentOf(lines[k]) < m.contentIndent) i = k
                }
            }
            return i
        }

        private fun bullet(level: Int) = when (level % 3) {
            0 -> "•"
            1 -> "◦"
            else -> "▪"
        }

        // ---- tables ----

        /** Splits a table row on its unescaped pipes, ignoring pipes inside `code`. */
        fun splitRow(line: String): List<String> {
            var s = line.trim()
            if (s.startsWith("|")) s = s.substring(1)
            if (s.endsWith("|") && !s.endsWith("\\|")) s = s.dropLast(1)
            val cells = ArrayList<String>()
            val cell = StringBuilder()
            var inCode = false
            var i = 0
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '\\' && i + 1 < s.length && s[i + 1] == '|' -> {
                        cell.append('|')
                        i++
                    }
                    c == '`' -> {
                        inCode = !inCode
                        cell.append(c)
                    }
                    c == '|' && !inCode -> {
                        cells += cell.toString().trim()
                        cell.setLength(0)
                    }
                    else -> cell.append(c)
                }
                i++
            }
            cells += cell.toString().trim()
            return cells
        }

        private fun table(head: List<String>, sep: List<String>, rows: List<List<String>>): DocxTable {
            val columns = head.size
            val aligns = sep.map { s ->
                when {
                    s.startsWith(":") && s.endsWith(":") && s.length > 1 -> DocxAlign.Center
                    s.endsWith(":") -> DocxAlign.End
                    else -> DocxAlign.Start
                }
            }
            val all = listOf(head) + rows.map { r -> List(columns) { r.getOrNull(it) ?: "" } }
            val weights = FloatArray(columns) { c -> all.maxOf { it.getOrNull(c)?.length ?: 0 }.coerceIn(6, 40).toFloat() }
            val total = weights.sum()
            val widths = weights.map { it / total * CONTENT_WIDTH_PT }
            val line = BorderLine(0.5f, "BFC3CA")
            val cellRows = all.mapIndexed { r, cells ->
                List(columns) { c ->
                    val base = InlineBase(bold = r == 0)
                    listOf<DocxBlock>(
                        DocxParagraph(
                            inline(cells.getOrNull(c) ?: "", base), align = aligns[c],
                            layout = ParagraphLayout(spaceAfterPt = 0f, line = LineSpacing.Multiple(1.15f), sizeHalfPoints = 21),
                        ),
                    )
                }
            }
            val props = all.indices.map { r ->
                List(columns) { c ->
                    CellProps(c, 1, shadingHex = if (r == 0) "F1F3F4" else null, borders = CellBorders(line, line, line, line), vAlign = CellAlign.Top)
                }
            }
            return DocxTable(cellRows, TableLayout(widths, props, List(all.size) { null }, 5f, 5f, 3f, 3f))
        }
    }
}
