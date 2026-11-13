package io.github.amandhakar.documents.word

import kotlin.math.floor
import kotlin.math.min

/** Picture sizes read straight from the file header, so pages can be planned without decoding the picture. */
object ImageSize {
    /** Width and height in pixels of a png, jpeg, gif or bmp, or null when the header isn't recognised. */
    fun of(b: ByteArray): Pair<Int, Int>? = try {
        when {
            b.size >= 24 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte() ->
                be32(b, 16) to be32(b, 20)
            b.size >= 10 && b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() ->
                le16(b, 6) to le16(b, 8)
            b.size >= 26 && b[0] == 'B'.code.toByte() && b[1] == 'M'.code.toByte() ->
                le32(b, 18) to Math.abs(le32(b, 22))
            b.size >= 4 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() -> jpeg(b)
            else -> null
        }?.takeIf { it.first > 0 && it.second > 0 && it.first < 100_000 && it.second < 100_000 }
    } catch (e: IndexOutOfBoundsException) {
        null
    }

    private fun be32(b: ByteArray, o: Int) = ((b[o].toInt() and 255) shl 24) or ((b[o + 1].toInt() and 255) shl 16) or ((b[o + 2].toInt() and 255) shl 8) or (b[o + 3].toInt() and 255)
    private fun le32(b: ByteArray, o: Int) = ((b[o + 3].toInt() and 255) shl 24) or ((b[o + 2].toInt() and 255) shl 16) or ((b[o + 1].toInt() and 255) shl 8) or (b[o].toInt() and 255)
    private fun le16(b: ByteArray, o: Int) = ((b[o + 1].toInt() and 255) shl 8) or (b[o].toInt() and 255)
    private fun be16(b: ByteArray, o: Int) = ((b[o].toInt() and 255) shl 8) or (b[o + 1].toInt() and 255)

    private fun jpeg(b: ByteArray): Pair<Int, Int>? {
        var i = 2
        while (i + 9 < b.size) {
            if (b[i] != 0xFF.toByte()) {
                i++
                continue
            }
            val marker = b[i + 1].toInt() and 255
            if (marker == 0xFF) {
                i++
                continue
            }
            // Start-of-frame markers (not the restart, define-huffman or arithmetic ones) carry the size.
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                return be16(b, i + 7) to be16(b, i + 5)
            }
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) {
                i += 2
                continue
            }
            val length = be16(b, i + 2)
            if (length < 2) return null
            i += 2 + length
        }
        return null
    }
}

/**
 * Plans a Word document onto pages: which blocks go on which page. It can't match Word exactly (no real fonts or
 * layout engine here), so heights are estimated from the text's length and size, and the pages are an honest
 * approximation. Free of Android calls so it can be unit tested.
 */
object PageLayout {
    const val BODY_PT = 11f
    private const val LINE_FACTOR = 1.2f
    private const val CHAR_WIDTH_EM = 0.43f
    private const val AFTER_PT = 8f
    private const val LIST_AFTER_PT = 3f
    const val LIST_INDENT = 18f
    private const val CELL_PADDING_PT = 4f

    /** Heading text sizes in points. */
    fun headingPt(level: Int): Float = when (level) {
        1 -> 18f
        2 -> 14f
        3 -> 12f
        else -> BODY_PT
    }

    /**
     * The largest size in the paragraph. When the file gave the paragraph its layout: its biggest run's size, else the
     * style's size. Otherwise (a made-up document): a heading's size, else its biggest run's, else normal text.
     */
    fun fontPt(p: DocxParagraph): Float {
        val runSize = p.runs.mapNotNull { it.sizeHalfPoints }.maxOrNull()?.let { it / 2f }
        val layout = p.layout
        if (layout != null) return runSize ?: layout.sizeHalfPoints?.let { it / 2f } ?: if (p.headingLevel > 0) headingPt(p.headingLevel) else BODY_PT
        return if (p.headingLevel > 0) headingPt(p.headingLevel) else runSize ?: BODY_PT
    }

    /** The size of one run: its own, else the paragraph's (see [fontPt]). */
    fun runPt(p: DocxParagraph, run: DocxRun): Float {
        val layout = p.layout
        if (layout != null) return run.sizeHalfPoints?.let { it / 2f } ?: layout.sizeHalfPoints?.let { it / 2f } ?: if (p.headingLevel > 0) headingPt(p.headingLevel) else BODY_PT
        return if (p.headingLevel > 0) headingPt(p.headingLevel) else run.sizeHalfPoints?.let { it / 2f } ?: BODY_PT
    }

    internal fun spaceBefore(p: DocxParagraph) = p.layout?.spaceBeforePt ?: if (p.headingLevel > 0) 10f else 0f
    internal fun spaceAfter(p: DocxParagraph) = p.layout?.spaceAfterPt ?: if (p.list != null) LIST_AFTER_PT else if (p.headingLevel > 0) 4f else AFTER_PT

    /** How far in the paragraph's text starts from the left of the text area (a list item's own scheme wins over the file's). */
    internal fun indent(p: DocxParagraph) = if (p.list != null) LIST_INDENT * (p.list.level + 1) else p.layout?.leftPt ?: 0f
    internal fun rightIndent(p: DocxParagraph) = p.layout?.rightPt ?: 0f

    /** How far the first line is moved from the rest (negative: hanging). Not for list items, which have their own mark. */
    internal fun firstLineIndent(p: DocxParagraph) = if (p.list != null) 0f else p.layout?.firstLinePt ?: 0f

    /** How many characters of this paragraph's text fit on a line. */
    fun charsPerLine(p: DocxParagraph, widthPt: Float): Int =
        maxOf(1, ((widthPt - indent(p) - rightIndent(p)) / (fontPt(p) * CHAR_WIDTH_EM)).toInt())

    /** The bundled font standing in for the paragraph's text: the one the file gives its largest run, else the paragraph's own font. */
    fun faceOf(p: DocxParagraph): WordFace {
        val biggest = p.runs.filter { it.text.isNotEmpty() }.maxByOrNull { runPt(p, it) }
        return WordFonts.faceFor(biggest?.fontName ?: p.layout?.fontName)
    }

    private fun wrapRuns(p: DocxParagraph): List<WrapRun> = p.runs.filter { it.text.isNotEmpty() }.map { run ->
        WrapRun(run.text, WordFonts.faceFor(run.fontName ?: p.layout?.fontName), run.bold || p.headingLevel > 0, run.italic, runPt(p, run))
    }

    /** The end of each line (a character offset into the paragraph's text), from the real letter widths of the bundled fonts. */
    fun lineEnds(p: DocxParagraph, widthPt: Float): List<Int> {
        val room = widthPt - indent(p) - rightIndent(p)
        return TextWrap.lineEnds(wrapRuns(p), room - firstLineIndent(p), room)
    }

    fun lineCount(p: DocxParagraph, widthPt: Float): Int = lineEnds(p, widthPt).size

    /** The height of one line: the text's natural height (1.2 times its size) times the line spacing, or the file's exact / minimum height. */
    fun lineHeightPt(p: DocxParagraph): Float {
        val natural = fontPt(p) * if (p.layout != null) WordFonts.lineHeightEm(faceOf(p)) else LINE_FACTOR
        return when (val spacing = p.layout?.line) {
            null -> natural
            is LineSpacing.Multiple -> natural * spacing.factor
            is LineSpacing.AtLeast -> maxOf(natural, spacing.pt)
            is LineSpacing.Exactly -> spacing.pt
        }
    }

    /** A picture's size on the page in points: its pixels at 96 per inch, no wider than the page's text and no taller than it. */
    fun imageBoxPt(image: DocxImage, widthPt: Float, heightPt: Float): Pair<Float, Float> {
        val (pw, ph) = ImageSize.of(image.bytes) ?: (4 to 3)
        var w = min(pw * 0.75f, widthPt)
        var h = w * ph / pw
        if (h > heightPt) {
            h = heightPt
            w = h * pw / ph
        }
        return w to h
    }

    fun heightOf(block: DocxBlock, page: PageSetup, widthPt: Float = page.contentWidthPt): Float = when (block) {
        is DocxParagraph -> spaceBefore(block) + lineCount(block, widthPt) * lineHeightPt(block) + spaceAfter(block)
        is DocxImage -> imageBoxPt(block, widthPt, page.contentHeightPt).second + 6f
        is DocxColumns -> 0f
        is DocxTable -> TABLE_OUTER_PT + tableRowHeights(block, page, widthPt).sum()
    }

    private const val TABLE_OUTER_PT = 8f
    private const val MIN_COLUMN_WIDTH_PT = 24f

    /**
     * The widths of a table's columns in points when laid out [availablePt] wide: the grid the file gives, columns it
     * gave no width sharing what is left, and everything scaled down when wider than the text area.
     */
    fun tableColumnWidths(layout: TableLayout, availablePt: Float): List<Float> {
        val widths = layout.columnWidthsPt.toMutableList()
        if (widths.isEmpty()) return widths
        val zeros = widths.count { it <= 0f }
        if (zeros > 0) {
            val rest = maxOf(zeros * MIN_COLUMN_WIDTH_PT, availablePt - widths.filter { it > 0f }.sum())
            for (i in widths.indices) if (widths[i] <= 0f) widths[i] = rest / zeros
        }
        val total = widths.sum()
        if (availablePt > 0f && total > availablePt) for (i in widths.indices) widths[i] = widths[i] * availablePt / total
        return widths
    }

    /** The height of each row of a table laid out [widthPt] wide: its tallest cell (merged cells count on their first row) or the row's minimum height. */
    fun tableRowHeights(table: DocxTable, page: PageSetup, widthPt: Float): List<Float> {
        val layout = table.layout
        val minLine = BODY_PT * LINE_FACTOR
        if (layout == null) {
            return table.rows.map { row ->
                val cellWidth = widthPt / maxOf(1, row.size)
                var tallest = 0f
                for (cell in row) {
                    var cellHeight = 0f
                    for (inner in cell) cellHeight += heightOf(inner, page, cellWidth - 2 * CELL_PADDING_PT)
                    tallest = maxOf(tallest, cellHeight)
                }
                tallest + 2 * CELL_PADDING_PT
            }
        }
        val widths = tableColumnWidths(layout, widthPt)
        return table.rows.mapIndexed { r, row ->
            var tallest = minLine
            row.forEachIndexed { c, cell ->
                val props = layout.cells.getOrNull(r)?.getOrNull(c) ?: return@forEachIndexed
                if (props.vMerge == VMerge.Continue) return@forEachIndexed
                val from = props.gridStart.coerceIn(0, widths.size)
                val to = (props.gridStart + props.colSpan).coerceIn(from, widths.size)
                val cellWidth = maxOf(MIN_COLUMN_WIDTH_PT, widths.subList(from, to).sum() - layout.marginLeftPt - layout.marginRightPt)
                var content = 0f
                for (inner in cell) content += heightOf(inner, page, cellWidth)
                tallest = maxOf(tallest, content + layout.marginTopPt + layout.marginBottomPt)
            }
            maxOf(tallest, layout.rowMinHeightsPt.getOrNull(r) ?: 0f)
        }
    }

    /**
     * A table cut after as many whole rows as fit in [roomPt], as the first part and the rest, or null when it can't be
     * cut (one row, or not even the first row fits).
     */
    fun splitTable(table: DocxTable, page: PageSetup, widthPt: Float, roomPt: Float): Pair<DocxTable, DocxTable>? {
        if (table.rows.size < 2) return null
        val heights = tableRowHeights(table, page, widthPt)
        var used = TABLE_OUTER_PT
        var k = 0
        while (k < heights.size && used + heights[k] <= roomPt) {
            used += heights[k]
            k++
        }
        if (k == 0 || k >= table.rows.size) return null
        return table.slice(0, k) to table.slice(k, table.rows.size)
    }

    /** Cuts a paragraph after [chars] characters (preferably at a space), keeping each run's look. The rest has no bullet but keeps its indent. */
    fun split(p: DocxParagraph, chars: Int): Pair<DocxParagraph, DocxParagraph> {
        val text = p.text
        var cut = chars.coerceIn(1, maxOf(1, text.length - 1))
        val space = text.lastIndexOf(' ', cut)
        if (space > cut / 2) cut = space + 1
        return splitExactly(p, cut)
    }

    /** Cuts the first [lines] lines off a paragraph laid out [widthPt] wide: the first part is exactly those lines. */
    fun splitAtLine(p: DocxParagraph, widthPt: Float, lines: Int): Pair<DocxParagraph, DocxParagraph> {
        val ends = lineEnds(p, widthPt)
        return splitExactly(p, ends[(lines - 1).coerceIn(0, ends.size - 1)])
    }

    private fun splitExactly(p: DocxParagraph, requested: Int): Pair<DocxParagraph, DocxParagraph> {
        val text = p.text
        val cut = requested.coerceIn(1, maxOf(1, text.length - 1))
        val first = ArrayList<DocxRun>()
        val rest = ArrayList<DocxRun>()
        var seen = 0
        for (run in p.runs) {
            val end = seen + run.text.length
            when {
                end <= cut -> first += run
                seen >= cut -> rest += run
                else -> {
                    first += run.copy(text = run.text.substring(0, cut - seen))
                    rest += run.copy(text = run.text.substring(cut - seen))
                }
            }
            seen = end
        }
        return p.copy(runs = first, pageBreakAfter = false) to
            p.copy(runs = rest, list = p.list?.copy(label = ""), pageBreakBefore = false)
    }

    private fun blocksHeight(blocks: List<DocxBlock>, page: PageSetup): Float = blocks.sumOf { heightOf(it, page).toDouble() }.toFloat()

    /** Where the text of page [pageIndex] (from 0) starts, measured from the top of the page: below the top margin, or below the header if that is taller. */
    fun bodyTopPt(doc: DocxDocument, pageIndex: Int): Float {
        val header = doc.headerFooter.headerFor(pageIndex)
        if (header.isEmpty()) return doc.page.marginTopPt
        return maxOf(doc.page.marginTopPt, doc.headerFooter.headerDistancePt + blocksHeight(header, doc.page)).coerceAtMost(doc.page.heightPt / 2)
    }

    /** How far above the bottom of page [pageIndex] the text must stop: the bottom margin, or the footer if that is taller. */
    fun bodyBottomPt(doc: DocxDocument, pageIndex: Int): Float {
        val footer = doc.headerFooter.footerFor(pageIndex)
        if (footer.isEmpty()) return doc.page.marginBottomPt
        return maxOf(doc.page.marginBottomPt, doc.headerFooter.footerDistancePt + blocksHeight(footer, doc.page)).coerceAtMost(doc.page.heightPt / 2)
    }

    /** The room for text on page [pageIndex]: the page's height less where the header and footer take. */
    fun contentLimit(doc: DocxDocument, pageIndex: Int): Float =
        maxOf(36f, doc.page.heightPt - bodyTopPt(doc, pageIndex) - bodyBottomPt(doc, pageIndex))

    /** The pages: each is the list of blocks on it (always at least one page). A tall table or picture that can't fit even alone is placed anyway. */
    fun paginate(doc: DocxDocument): List<List<DocxBlock>> {
        val page = doc.page
        val width = page.contentWidthPt
        var limit = contentLimit(doc, 0)
        val pages = ArrayList<MutableList<DocxBlock>>()
        var current = ArrayList<DocxBlock>()
        var used = 0f
        fun newPage() {
            pages += current
            current = ArrayList()
            used = 0f
            limit = contentLimit(doc, pages.size)
        }
        for (original in doc.blocks) {
            var block: DocxBlock? = original
            if (original is DocxParagraph && original.pageBreakBefore && current.isNotEmpty()) newPage()
            if (original is DocxImage && original.pageBreakBefore && current.isNotEmpty()) newPage()
            var guard = 0
            while (block != null && guard++ < 10_000) {
                val h = heightOf(block, page)
                if (used + h <= limit) {
                    current += block
                    used += h
                    block = null
                    continue
                }
                if (block is DocxParagraph) {
                    val lineH = lineHeightPt(block)
                    val room = floor((limit - used - spaceBefore(block)) / lineH).toInt()
                    val lines = lineCount(block, width)
                    val fitsMore = room >= 2 && lines > room && block.text.length > 1
                    val tallerThanPage = current.isEmpty() && h > limit
                    if (fitsMore || tallerThanPage) {
                        val take = if (fitsMore) room else maxOf(1, floor((limit - spaceBefore(block)) / lineH).toInt())
                        if (lines > take && block.text.length > 1) {
                            val (a, b) = splitAtLine(block, width, take)
                            current += a
                            newPage()
                            block = b
                            continue
                        }
                    }
                }
                if (block is DocxTable) {
                    val cut = splitTable(block, page, width, limit - used)
                    if (cut != null) {
                        current += cut.first
                        newPage()
                        block = cut.second
                        continue
                    }
                }
                if (current.isEmpty()) {
                    // Nothing more can be done: it is taller than a page on its own.
                    current += block
                    used += h
                    block = null
                } else {
                    newPage()
                }
            }
            if (original is DocxParagraph && original.pageBreakAfter) newPage()
        }
        if (current.isNotEmpty() || pages.isEmpty()) pages += current
        return pages
    }

    /** The narrowest a column is allowed to get: more columns than fit are dropped (a damaged file can't make slivers). */
    private const val MIN_COLUMN_PT = 36f

    /** How many columns of this gap really fit across the page's text width. */
    fun effectiveColumns(requested: Int, gapPt: Float, page: PageSetup): Int {
        var n = requested.coerceIn(1, 4)
        while (n > 1 && (page.contentWidthPt - (n - 1) * gapPt) / n < MIN_COLUMN_PT) n--
        return n
    }

    fun columnWidthPt(columns: Int, gapPt: Float, page: PageSetup): Float =
        if (columns <= 1) page.contentWidthPt else (page.contentWidthPt - (columns - 1) * gapPt) / columns

    /** A stretch of a page laid out in [columns].size columns [gapPt] apart; [columns] holds each column's blocks, left to right. */
    class ColumnBand(val columns: List<List<DocxBlock>>, val gapPt: Float)

    /** One page: its bands from top to bottom (a full-width title, then two columns, ...). */
    class ColumnPage(val bands: List<ColumnBand>)

    /**
     * Like [paginate], but follows the document's columns: text fills the first column down the page, then the next,
     * then the next page. A new [DocxColumns] section within a page starts a new band under the one above. Columns of a
     * band that ends mid-page are not balanced (Word balances them at a continuous break), and all pages use the size
     * of the last section.
     */
    fun paginateColumns(doc: DocxDocument): List<ColumnPage> {
        val real = doc.blocks.filter { it !is DocxColumns }
        if (doc.blocks.none { it is DocxColumns && it.count > 1 } && real.none { it is DocxParagraph && it.columnBreakAfter }) {
            return paginate(DocxDocument(real, doc.truncated, doc.page, doc.headerFooter)).map { ColumnPage(listOf(ColumnBand(listOf(it), 0f))) }
        }
        val page = doc.page
        var pageLimit = contentLimit(doc, 0)
        val pages = ArrayList<ColumnPage>()
        var bands = ArrayList<ColumnBand>()
        var n = 1
        var gap = 0f
        var cols = ArrayList<MutableList<DocxBlock>>()
        var heights = ArrayList<Float>()
        var col = 0
        var bandTop = 0f

        fun freshBand() {
            cols = ArrayList()
            heights = ArrayList()
            repeat(n) { cols += ArrayList<DocxBlock>(); heights += 0f }
            col = 0
        }

        fun bandHasContent() = cols.any { it.isNotEmpty() }

        fun closeBand() {
            if (bandHasContent()) {
                bands += ColumnBand(cols.map { it.toList() }, gap)
                bandTop += heights.max()
            }
        }

        fun finishPage() {
            closeBand()
            pages += ColumnPage(bands)
            bands = ArrayList()
            bandTop = 0f
            pageLimit = contentLimit(doc, pages.size)
            freshBand()
        }

        fun pageHasContent() = bands.isNotEmpty() || bandHasContent()

        fun nextSlot() {
            if (col < n - 1) col++ else finishPage()
        }

        freshBand()
        for (original in doc.blocks) {
            if (original is DocxColumns) {
                val count = effectiveColumns(original.count, original.gapPt, page)
                if (original.newPage && pageHasContent()) finishPage()
                if (count != n || original.gapPt != gap) {
                    closeBand()
                    n = count
                    gap = original.gapPt
                    freshBand()
                }
                continue
            }
            if ((original is DocxParagraph && original.pageBreakBefore || original is DocxImage && original.pageBreakBefore) && pageHasContent()) finishPage()
            var block: DocxBlock? = original
            var guard = 0
            while (block != null && guard++ < 10_000) {
                val width = columnWidthPt(n, gap, page)
                val limit = pageLimit - bandTop
                val used = heights[col]
                val h = heightOf(block, page, width)
                if (used + h <= limit) {
                    cols[col] += block
                    heights[col] = used + h
                    block = null
                    continue
                }
                if (block is DocxParagraph) {
                    val lineH = lineHeightPt(block)
                    val room = floor((limit - used - spaceBefore(block)) / lineH).toInt()
                    val lines = lineCount(block, width)
                    val fitsMore = room >= 2 && lines > room && block.text.length > 1
                    val slotEmpty = cols[col].isEmpty() && bandTop == 0f
                    val tallerThanSlot = slotEmpty && h > limit
                    if (fitsMore || tallerThanSlot) {
                        val take = if (fitsMore) room else maxOf(1, floor((limit - spaceBefore(block)) / lineH).toInt())
                        if (lines > take && block.text.length > 1) {
                            val (a, b) = splitAtLine(block, width, take)
                            cols[col] += a
                            heights[col] = used + heightOf(a, page, width)
                            nextSlot()
                            block = b
                            continue
                        }
                    }
                }
                if (block is DocxTable) {
                    val cut = splitTable(block, page, width, limit - used)
                    if (cut != null) {
                        cols[col] += cut.first
                        heights[col] = used + heightOf(cut.first, page, width)
                        nextSlot()
                        block = cut.second
                        continue
                    }
                }
                if (cols[col].isEmpty() && bandTop == 0f) {
                    // Nothing more can be done: it is taller than a column on its own.
                    cols[col] += block
                    heights[col] = used + h
                    block = null
                } else {
                    nextSlot()
                }
            }
            if (original is DocxParagraph && (original.pageBreakAfter)) finishPage()
            else if (original is DocxParagraph && original.columnBreakAfter) nextSlot()
        }
        if (pageHasContent() || pages.isEmpty()) finishPage()
        return pages
    }
}
