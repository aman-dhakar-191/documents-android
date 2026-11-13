package io.github.amandhakar.documents.word

import kotlin.math.ceil
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
    private const val CHAR_WIDTH_EM = 0.5f
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

    /** The largest size in the paragraph: a heading's, else its biggest run's, else normal text. */
    fun fontPt(p: DocxParagraph): Float =
        if (p.headingLevel > 0) headingPt(p.headingLevel) else p.runs.mapNotNull { it.sizeHalfPoints }.maxOrNull()?.let { it / 2f } ?: BODY_PT

    internal fun spaceBefore(p: DocxParagraph) = if (p.headingLevel > 0) 10f else 0f
    internal fun spaceAfter(p: DocxParagraph) = if (p.list != null) LIST_AFTER_PT else if (p.headingLevel > 0) 4f else AFTER_PT
    internal fun indent(p: DocxParagraph) = if (p.list != null) LIST_INDENT * (p.list.level + 1) else 0f

    /** How many characters of this paragraph's text fit on a line. */
    fun charsPerLine(p: DocxParagraph, widthPt: Float): Int =
        maxOf(1, ((widthPt - indent(p)) / (fontPt(p) * CHAR_WIDTH_EM)).toInt())

    fun lineCount(p: DocxParagraph, widthPt: Float): Int {
        val per = charsPerLine(p, widthPt)
        val text = p.text
        if (text.isEmpty()) return 1
        return text.split('\n').sumOf { line: String -> maxOf(1, ceil(line.length / per.toDouble()).toInt()) }
    }

    fun lineHeightPt(p: DocxParagraph) = fontPt(p) * LINE_FACTOR

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
        is DocxTable -> {
            var total = 8f
            for (row in block.rows) {
                val cellWidth = widthPt / maxOf(1, row.size)
                var tallest = 0f
                for (cell in row) {
                    var cellHeight = 0f
                    for (inner in cell) cellHeight += heightOf(inner, page, cellWidth - 2 * CELL_PADDING_PT)
                    tallest = maxOf(tallest, cellHeight)
                }
                total += tallest + 2 * CELL_PADDING_PT
            }
            total
        }
    }

    /** Cuts a paragraph after [chars] characters (preferably at a space), keeping each run's look. The rest has no bullet but keeps its indent. */
    fun split(p: DocxParagraph, chars: Int): Pair<DocxParagraph, DocxParagraph> {
        val text = p.text
        var cut = chars.coerceIn(1, maxOf(1, text.length - 1))
        val space = text.lastIndexOf(' ', cut)
        if (space > cut / 2) cut = space + 1
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

    /** The pages: each is the list of blocks on it (always at least one page). A tall table or picture that can't fit even alone is placed anyway. */
    fun paginate(doc: DocxDocument): List<List<DocxBlock>> {
        val page = doc.page
        val width = page.contentWidthPt
        val limit = page.contentHeightPt
        val pages = ArrayList<MutableList<DocxBlock>>()
        var current = ArrayList<DocxBlock>()
        var used = 0f
        fun newPage() {
            pages += current
            current = ArrayList()
            used = 0f
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
                            val (a, b) = split(block, take * charsPerLine(block, width))
                            current += a
                            newPage()
                            block = b
                            continue
                        }
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
}
