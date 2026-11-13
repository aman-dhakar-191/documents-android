package io.github.amandhakar.documents.convert

import io.github.amandhakar.documents.edit.PageLines
import io.github.amandhakar.documents.edit.TextLine
import io.github.amandhakar.documents.slides.EmuBox
import io.github.amandhakar.documents.slides.PptxAlign
import io.github.amandhakar.documents.slides.PptxAnchor
import io.github.amandhakar.documents.slides.PptxDeck
import io.github.amandhakar.documents.slides.PptxGeometry
import io.github.amandhakar.documents.slides.PptxParagraph
import io.github.amandhakar.documents.slides.PptxPicture
import io.github.amandhakar.documents.slides.PptxRun
import io.github.amandhakar.documents.slides.PptxShape
import io.github.amandhakar.documents.slides.PptxSlide
import io.github.amandhakar.documents.slides.PptxText
import io.github.amandhakar.documents.word.DocxAlign
import io.github.amandhakar.documents.word.DocxBlock
import io.github.amandhakar.documents.word.DocxColumns
import io.github.amandhakar.documents.word.DocxDocument
import io.github.amandhakar.documents.word.DocxImage
import io.github.amandhakar.documents.word.DocxListMark
import io.github.amandhakar.documents.word.DocxParagraph
import io.github.amandhakar.documents.word.DocxRun
import io.github.amandhakar.documents.word.PageSetup
import kotlin.math.abs
import kotlin.math.roundToInt

/** A paragraph put back together from the lines of a PDF page. Positions are in points from the top-left of the page. */
data class ConvertedParagraph(
    val text: String,
    val sizePt: Float,
    val bold: Boolean,
    val italic: Boolean,
    /** 0 for normal text, 1 to 3 for headings (bigger text). */
    val heading: Int,
    /** True when the text started with a bullet mark (which is not part of [text]). */
    val bullet: Boolean,
    val centered: Boolean,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Which stretch of the page this came from (a full-width title is one, the columns under it another), top to bottom. */
    val segment: Int = 0,
    /** Which column of its stretch, left to right, and how many columns the stretch has. */
    val column: Int = 0,
    val columns: Int = 1,
    /** The space between the columns in points (0 for one column). */
    val gapPt: Float = 0f,
)

/**
 * Rebuilds paragraphs, headings and bullets from the text lines of a PDF page, and turns them into Word and PowerPoint
 * content. A PDF only knows where each line sits, so this is a best guess from sizes and gaps: lines are joined into a
 * paragraph, bigger text becomes a heading, and a page that is clearly two columns is read column by column. Free of
 * Android calls so it can be unit tested.
 */
object PdfParagraphs {
    // A bullet mark then text. Marks drawn from a symbol font arrive as a private-use character (e.g. U+F0B7), with or without a space.
    private val BULLET = Regex("^(?:[\u2022\u25CF\u25AA\u25A0\u25E6\u00B7\u2219\u2013\u2014*-]\\s+|[\uE000-\uF8FF]\\s*)(.+)$")

    /** The usual text size on a page: the size with the most characters. */
    fun bodySize(lines: List<TextLine>): Float {
        if (lines.isEmpty()) return 11f
        val weight = HashMap<Int, Int>()
        for (l in lines) {
            val key = (l.fontSize * 2).roundToInt()
            weight[key] = (weight[key] ?: 0) + l.text.length
        }
        return (weight.maxByOrNull { it.value }?.key ?: 22) / 2f
    }

    /** 1 to 3 for text clearly bigger than the body, else 0. A short bold line the size of the body counts as a small heading. */
    fun headingLevel(size: Float, body: Float, bold: Boolean, lineCount: Int, text: String): Int = when {
        body <= 0f -> 0
        size >= body * 1.7f -> 1
        size >= body * 1.35f -> 2
        size >= body * 1.18f -> 3
        bold && lineCount == 1 && size >= body * 0.98f && text.length in 2..70 && !text.trimEnd().endsWith(".") && !text.trimEnd().endsWith(",") -> 3
        else -> 0
    }

    /**
     * Splits lines into columns when the page is plainly two columns (a clear empty strip down the middle that no line
     * crosses, with at least three lines on each side); otherwise returns them all as one column, top to bottom.
     */
    fun splitColumns(lines: List<TextLine>, pageWidth: Float): List<List<TextLine>> =
        if (pageWidth <= 0f) listOf(lines) else columnsOf(lines, pageWidth * 0.3f, pageWidth * 0.7f, 0).first

    /** Where a page's lines split into two columns, and how wide the empty strip between them is. */
    private class Split(val x: Float, val gap: Float)

    /**
     * Splits lines into columns, left to right: two, or three or four when each side of a split can be split again
     * (looking for a gap between [from] and [to]). The second value is the gap between the columns in points.
     */
    private fun columnsOf(lines: List<TextLine>, from: Float, to: Float, depth: Int): Pair<List<List<TextLine>>, Float> {
        val split = findSplit(lines, from, to) ?: return listOf(lines) to 0f
        val left = lines.filter { it.right <= split.x }
        val right = lines.filter { it.left >= split.x }
        if (depth >= 1) return listOf(left, right) to split.gap
        fun again(side: List<TextLine>): List<List<TextLine>> {
            val lo = side.minOf { it.left }
            val hi = side.maxOf { it.right }
            return columnsOf(side, lo + (hi - lo) * 0.3f, lo + (hi - lo) * 0.7f, depth + 1).first
        }
        return (again(left) + again(right)) to split.gap
    }

    private fun findSplit(lines: List<TextLine>, from: Float, to: Float): Split? {
        if (lines.size < 8 || to <= from) return null
        var bestStart = -1f
        var bestEnd = -1f
        var runStart = -1f
        var x = from
        val stop = to
        while (x <= stop) {
            val crossed = lines.any { it.left < x && it.right > x }
            if (!crossed) {
                if (runStart < 0f) runStart = x
                if (x - runStart > bestEnd - bestStart) {
                    bestStart = runStart
                    bestEnd = x
                }
            } else {
                runStart = -1f
            }
            x += 1f
        }
        if (bestStart < 0f || bestEnd - bestStart < 8f) return null
        val split = (bestStart + bestEnd) / 2
        val leftSide = lines.filter { it.right <= split }
        val rightSide = lines.filter { it.left >= split }
        if (leftSide.size < 3 || rightSide.size < 3 || leftSide.size + rightSide.size != lines.size) return null
        return Split(split, bestEnd - bestStart)
    }

    /** A stretch of a page: its lines in one column, or in several side by side. */
    private class Segment(val columns: List<List<TextLine>>, val gapPt: Float)

    /**
     * The stretches of a page, top to bottom. A page that is plainly columns all the way down is one stretch. Otherwise
     * lines that span the columns (lines running most of the page's width, or a heading that sits across the gap, like
     * a centred title) are set aside: the columns are found from the other lines, and each run of column lines between
     * spanning ones is its own stretch.
     */
    private fun segments(lines: List<TextLine>, pageWidth: Float): List<Segment> {
        val (plain, plainGap) = if (pageWidth > 0f) columnsOf(lines, pageWidth * 0.3f, pageWidth * 0.7f, 0) else listOf(lines) to 0f
        if (plain.size > 1) return listOf(Segment(plain, plainGap.coerceIn(MIN_GAP_PT, MAX_GAP_PT)))
        val single = listOf(Segment(listOf(lines), 0f))
        if (pageWidth <= 0f || lines.isEmpty()) return single
        val extent = lines.maxOf { it.right } - lines.minOf { it.left }
        val body = bodySize(lines)
        val wide = lines.filter { it.right - it.left > extent * 0.65f }
        val candidates = java.util.IdentityHashMap<TextLine, Boolean>().apply {
            wide.forEach { put(it, true) }
            lines.filter { it.fontSize >= body * 1.18f }.forEach { put(it, true) }
        }
        if (candidates.isEmpty() || candidates.size == lines.size) return single
        val (cols, gap) = columnsOf(lines.filter { candidates[it] == null }, pageWidth * 0.3f, pageWidth * 0.7f, 0)
        if (cols.size < 2) return single
        // The middle of each gap between neighbouring columns.
        val boundaries = (0 until cols.size - 1).map { i -> (cols[i].maxOf { it.right } + cols[i + 1].minOf { it.left }) / 2 }
        fun crosses(l: TextLine) = boundaries.any { l.left < it && l.right > it }
        // A heading that sits inside one column stays with that column; wide lines and headings across a gap span them.
        val spanning = java.util.IdentityHashMap<TextLine, Boolean>()
        for (l in lines) if (candidates[l] != null && crosses(l)) spanning[l] = true
        val out = ArrayList<Segment>()
        val run = ArrayList<TextLine>()
        var runSpans = false
        fun add(segment: Segment) {
            // Two single-column stretches in a row are one (a title that wrapped, then the abstract).
            val last = out.lastOrNull()
            if (last != null && last.columns.size == 1 && segment.columns.size == 1) {
                out[out.size - 1] = Segment(listOf(last.columns[0] + segment.columns[0]), 0f)
            } else {
                out += segment
            }
        }
        var lastSpanning: TextLine? = null

        /** True when [l] looks like the next wrapped line of the same paragraph as [ref] (same look, close below, same left edge). */
        fun continues(ref: TextLine, l: TextLine) =
            l.font == ref.font && abs(l.fontSize - ref.fontSize) <= ref.fontSize * 0.15f &&
                l.baseline - ref.baseline > 0f && l.baseline - ref.baseline <= l.fontSize * 1.7f && abs(l.left - ref.left) <= l.fontSize * 0.5f

        /**
         * The last line(s) of a wrapped spanning paragraph (a caption, an abstract) can fall in the first column's strip.
         * They go back to the spanning paragraph when they continue it and start well above the other columns' text.
         */
        fun takeTails(): List<TextLine> {
            val ref0 = lastSpanning ?: return emptyList()
            val last = out.lastOrNull() ?: return emptyList()
            if (last.columns.size != 1) return emptyList()
            val inFirst = run.filter { l -> boundaries.count { l.left >= it } == 0 }.sortedBy { it.baseline }
            val othersTop = run.filter { l -> boundaries.count { l.left >= it } > 0 }.minOfOrNull { it.baseline } ?: return emptyList()
            val tail = ArrayList<TextLine>()
            var ref = ref0
            for (l in inFirst) {
                if (continues(ref, l) && l.baseline < othersTop - l.fontSize * 0.5f) {
                    tail += l
                    ref = l
                } else {
                    break
                }
            }
            return tail
        }

        fun flush() {
            if (run.isEmpty()) return
            if (runSpans) {
                add(Segment(listOf(ArrayList(run)), 0f))
                lastSpanning = run.maxByOrNull { it.baseline }
            } else {
                val tail = takeTails()
                if (tail.isNotEmpty()) {
                    val last = out.last()
                    out[out.size - 1] = Segment(listOf(last.columns[0] + tail), 0f)
                    lastSpanning = tail.last()
                    run.removeAll { l -> tail.any { it === l } }
                }
                if (run.isEmpty()) return
                val side = (0..boundaries.size).map { ci -> run.filter { l -> boundaries.count { l.left >= it } == ci } }.filter { it.isNotEmpty() }
                add(if (side.size >= 2) Segment(side, gap.coerceIn(MIN_GAP_PT, MAX_GAP_PT)) else Segment(listOf(ArrayList(run)), 0f))
            }
            run.clear()
        }
        for (line in lines.sortedWith(compareBy({ it.baseline }, { it.left }))) {
            val isSpanning = spanning[line] != null
            if (run.isNotEmpty() && isSpanning != runSpans) flush()
            runSpans = isSpanning
            run += line
        }
        flush()
        return out
    }

    private const val MIN_GAP_PT = 12f
    private const val MAX_GAP_PT = 72f

    /** A visual line: the merged [line], and whether it was made of pieces far apart (a table row or tab-separated line). */
    private class Row(val line: TextLine, val tabular: Boolean)

    /** Lines on (nearly) the same baseline become one line, left to right; pieces far apart are joined with a tab. */
    private fun mergeRows(sorted: List<TextLine>): List<Row> {
        val rows = ArrayList<Row>()
        for (line in sorted) {
            val last = rows.lastOrNull()
            if (last != null && abs(line.baseline - last.line.baseline) <= maxOf(2f, last.line.fontSize * 0.25f)) {
                val (a, b) = if (line.left < last.line.left) line to last.line else last.line to line
                val far = b.left - a.right > a.fontSize * 1.5f
                rows[rows.size - 1] = Row(
                    a.copy(
                        text = a.text + (if (far) "\t" else " ") + b.text,
                        left = minOf(a.left, b.left), right = maxOf(a.right, b.right),
                        top = minOf(a.top, b.top), bottom = maxOf(a.bottom, b.bottom),
                        baseline = (a.baseline + b.baseline) / 2,
                    ),
                    last.tabular || far,
                )
            } else {
                rows += Row(line, false)
            }
        }
        return rows
    }

    private fun endsSentence(s: String) = s.trimEnd().let { it.endsWith(".") || it.endsWith("!") || it.endsWith("?") || it.endsWith(":") }

    /** Digits count as one thing, so "Page 3" and "Page 4" are the same line. */
    private fun normalised(text: String) = text.trim().replace(Regex("\\d+"), "#")

    /**
     * Running headers, footers and page numbers: a line in the top or bottom tenth of the page whose text (digits aside)
     * appears on at least two pages is left out of the conversion, since Word and PowerPoint make their own. A PDF of a
     * single page keeps everything.
     */
    fun withoutRunningLines(pages: List<PageLines>): List<PageLines> {
        if (pages.size < 2) return pages
        fun inMargin(page: PageLines, line: TextLine) = page.heightPt > 0 && (line.bottom < page.heightPt * 0.1f || line.top > page.heightPt * 0.9f)
        val seen = HashMap<String, MutableSet<Int>>()
        pages.forEachIndexed { i, page ->
            for (l in page.lines) if (inMargin(page, l) && l.text.isNotBlank()) seen.getOrPut(normalised(l.text)) { HashSet() } += i
        }
        val running = seen.filterValues { it.size >= 2 }.keys
        if (running.isEmpty()) return pages
        return pages.map { page -> page.copy(lines = page.lines.filterNot { inMargin(page, it) && normalised(it.text) in running }) }
    }

    /** The paragraphs of one page in reading order. */
    fun fromPage(page: PageLines): List<ConvertedParagraph> {
        val lines = page.lines.filter { it.text.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val body = bodySize(lines)
        val out = ArrayList<ConvertedParagraph>()
        for ((segmentIndex, segment) in segments(lines, page.widthPt).withIndex()) for ((columnIndex, column) in segment.columns.withIndex()) {
            val rows = mergeRows(column.sortedWith(compareBy({ it.baseline }, { it.left })))
            if (rows.isEmpty()) continue
            val colLeft = rows.minOf { it.line.left }
            val colRight = rows.maxOf { it.line.right }
            var previousTabular = false
            val group = ArrayList<TextLine>()
            val texts = ArrayList<String>()

            fun flush() {
                if (group.isEmpty()) return
                val first = group.first()
                val joined = StringBuilder()
                for (t in texts) {
                    if (joined.isEmpty()) {
                        joined.append(t)
                    } else if (joined.endsWith("-") && joined.length > 1 && t.firstOrNull()?.isLowerCase() == true) {
                        joined.setLength(joined.length - 1) // a word split over two lines
                        joined.append(t)
                    } else {
                        joined.append(' ').append(t)
                    }
                }
                var text = joined.toString().trim()
                var bullet = false
                BULLET.find(text)?.let {
                    bullet = true
                    text = it.groupValues[1]
                }
                val heading = headingLevel(first.fontSize, body, first.font.bold, group.size, text)
                val left = group.minOf { it.left }
                val right = group.maxOf { it.right }
                val centre = (left + right) / 2
                val centered = group.size == 1 && (right - left) < (colRight - colLeft) * 0.8f &&
                    abs(centre - (colLeft + colRight) / 2) < (colRight - colLeft) * 0.03f
                out += ConvertedParagraph(
                    text = text, sizePt = first.fontSize, bold = group.all { it.font.bold }, italic = group.all { it.font.italic },
                    heading = heading, bullet = bullet, centered = centered && !bullet,
                    left = left, top = group.minOf { it.top }, right = right, bottom = group.maxOf { it.bottom },
                    segment = segmentIndex, column = columnIndex, columns = segment.columns.size, gapPt = segment.gapPt,
                )
                group.clear()
                texts.clear()
            }

            for (item in rows) {
                val row = item.line
                val prev = group.lastOrNull()
                val rowText = row.text.trim()
                if (prev != null) {
                    val spacing = row.baseline - prev.baseline
                    // A short line followed by another starting at the same place is usually a list item, not a wrapped line.
                    val shortAbove = (prev.right - prev.left) < (colRight - colLeft) * 0.6f && abs(row.left - prev.left) < row.fontSize * 0.5f
                    val newParagraph =
                        item.tabular || previousTabular ||
                            BULLET.containsMatchIn(rowText) ||
                            spacing > prev.fontSize * 1.2f * 1.5f ||
                            abs(row.fontSize - prev.fontSize) > prev.fontSize * 0.15f ||
                            shortAbove ||
                            (row.left - group.first().left > row.fontSize && endsSentence(prev.text)) ||
                            (endsSentence(prev.text) && prev.right < colRight - row.fontSize * 8f)
                    if (newParagraph) flush()
                }
                group += row
                texts += rowText
                previousTabular = item.tabular
            }
            flush()
        }
        return out
    }

    // ---- Word ----

    /** Word content from the text of PDF pages. The page size is the PDF's; each page's first paragraph starts a new page. */
    fun toDocx(allPages: List<PageLines>): DocxDocument {
        val pages = withoutRunningLines(allPages)
        val first = pages.firstOrNull { it.widthPt > 0 && it.heightPt > 0 }
        val setup = if (first != null) PageSetup(first.widthPt, first.heightPt, 54f, 54f, 54f, 54f) else PageSetup()
        val blocks = ArrayList<DocxBlock>()
        var columnCount = 1
        var columnGap = 0f
        pages.forEachIndexed { index, page ->
            val paragraphs = fromPage(page)
            if (paragraphs.isEmpty()) {
                // An empty page (a picture or scan) keeps its place so page numbers still line up.
                if (index > 0) blocks += DocxParagraph(emptyList(), pageBreakBefore = true)
                return@forEachIndexed
            }
            paragraphs.forEachIndexed { i, p ->
                // A new column layout starts a new section (Word keeps columns per section).
                if (p.columns != columnCount || (p.columns > 1 && abs(p.gapPt - columnGap) > 0.5f)) {
                    blocks += DocxColumns(p.columns, p.gapPt)
                    columnCount = p.columns
                    columnGap = p.gapPt
                }
                // The last paragraph of a column ends it, so the next column starts at the top whatever the first one's length.
                val next = paragraphs.getOrNull(i + 1)
                val endsColumn = next != null && next.segment == p.segment && next.column != p.column
                blocks += DocxParagraph(
                    runs = listOf(
                        DocxRun(p.text, bold = p.bold && p.heading == 0, italic = p.italic, sizeHalfPoints = if (p.heading == 0) (p.sizePt * 2).roundToInt().coerceIn(2, 400) else null),
                    ),
                    headingLevel = p.heading,
                    list = if (p.bullet) DocxListMark(0, "•") else null,
                    align = if (p.centered) DocxAlign.Center else DocxAlign.Start,
                    pageBreakBefore = i == 0 && index > 0,
                    columnBreakAfter = endsColumn,
                )
            }
        }
        return DocxDocument(blocks, page = setup)
    }

    /** Word content with each page as a picture. [pictures] are png or jpeg bytes, one per page. */
    fun picturesToDocx(pictures: List<ByteArray>, pageWidthPt: Float, pageHeightPt: Float): DocxDocument =
        DocxDocument(
            pictures.mapIndexed { i, b -> DocxImage("page${i + 1}", b, pageBreakBefore = i > 0) },
            page = PageSetup(pageWidthPt, pageHeightPt, 24f, 24f, 24f, 24f),
        )

    // ---- PowerPoint ----

    private const val EMU_PER_PT = 12700L

    /** A deck from the text of PDF pages: one slide per page at the page's size, with each paragraph as a text box where it was. */
    fun toPptx(allPages: List<PageLines>): PptxDeck {
        val pages = withoutRunningLines(allPages)
        val first = pages.firstOrNull { it.widthPt > 0 && it.heightPt > 0 }
        val widthPt = first?.widthPt ?: 720f
        val heightPt = first?.heightPt ?: 540f
        val slides = pages.map { page ->
            val shapes = ArrayList<PptxShape>()
            for (p in fromPage(page)) {
                // The text box has inner margins of 7.2 and 3.6 pt, so it starts a little up and left and a little wider.
                val x = ((p.left - 7.2f) * EMU_PER_PT).toLong().coerceAtLeast(0)
                val y = ((p.top - 3.6f) * EMU_PER_PT).toLong().coerceAtLeast(0)
                val w = ((p.right - p.left) * 1.06f + 14.4f).times(EMU_PER_PT).toLong()
                val h = ((p.bottom - p.top) * 1.1f + 7.2f).times(EMU_PER_PT).toLong()
                shapes += PptxText(
                    EmuBox(x, y, w, h),
                    listOf(
                        PptxParagraph(
                            listOf(PptxRun(p.text, p.bold, p.italic, false, p.sizePt.coerceIn(1f, 200f), "000000")),
                            if (p.centered) PptxAlign.Center else PptxAlign.Start,
                            0,
                            if (p.bullet) "•" else null,
                        ),
                    ),
                    null, null, PptxGeometry.Rect, PptxAnchor.Top,
                )
            }
            PptxSlide(shapes, "FFFFFF", "")
        }
        return PptxDeck((widthPt * EMU_PER_PT).toLong(), (heightPt * EMU_PER_PT).toLong(), slides)
    }

    /** A deck with each page as one full-slide picture. */
    fun picturesToPptx(pictures: List<ByteArray>, pageWidthPt: Float, pageHeightPt: Float): PptxDeck {
        val w = (pageWidthPt * EMU_PER_PT).toLong()
        val h = (pageHeightPt * EMU_PER_PT).toLong()
        return PptxDeck(w, h, pictures.mapIndexed { i, b -> PptxSlide(listOf(PptxPicture(EmuBox(0, 0, w, h), "page${i + 1}", b)), "FFFFFF", "") })
    }
}
