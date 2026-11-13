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
    fun splitColumns(lines: List<TextLine>, pageWidth: Float): List<List<TextLine>> {
        if (lines.size < 8 || pageWidth <= 0f) return listOf(lines)
        var bestStart = -1f
        var bestEnd = -1f
        var runStart = -1f
        var x = pageWidth * 0.3f
        val stop = pageWidth * 0.7f
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
        if (bestStart < 0f || bestEnd - bestStart < 8f) return listOf(lines)
        val split = (bestStart + bestEnd) / 2
        val leftSide = lines.filter { it.right <= split }
        val rightSide = lines.filter { it.left >= split }
        if (leftSide.size < 3 || rightSide.size < 3 || leftSide.size + rightSide.size != lines.size) return listOf(lines)
        return listOf(leftSide, rightSide)
    }

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
        for (column in splitColumns(lines, page.widthPt)) {
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
                    text = text, sizePt = first.fontSize, bold = first.font.bold, italic = first.font.italic,
                    heading = heading, bullet = bullet, centered = centered && !bullet,
                    left = left, top = group.minOf { it.top }, right = right, bottom = group.maxOf { it.bottom },
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
        pages.forEachIndexed { index, page ->
            val paragraphs = fromPage(page)
            if (paragraphs.isEmpty()) {
                // An empty page (a picture or scan) keeps its place so page numbers still line up.
                if (index > 0) blocks += DocxParagraph(emptyList(), pageBreakBefore = true)
                return@forEachIndexed
            }
            paragraphs.forEachIndexed { i, p ->
                blocks += DocxParagraph(
                    runs = listOf(
                        DocxRun(p.text, bold = p.bold && p.heading == 0, italic = p.italic, sizeHalfPoints = if (p.heading == 0) (p.sizePt * 2).roundToInt().coerceIn(2, 400) else null),
                    ),
                    headingLevel = p.heading,
                    list = if (p.bullet) DocxListMark(0, "•") else null,
                    align = if (p.centered) DocxAlign.Center else DocxAlign.Start,
                    pageBreakBefore = i == 0 && index > 0,
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
