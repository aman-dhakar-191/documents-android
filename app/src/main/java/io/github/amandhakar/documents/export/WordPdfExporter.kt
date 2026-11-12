package io.github.amandhakar.documents.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import io.github.amandhakar.documents.word.DocxAlign
import io.github.amandhakar.documents.word.DocxBlock
import io.github.amandhakar.documents.word.DocxDocument
import io.github.amandhakar.documents.word.DocxImage
import io.github.amandhakar.documents.word.DocxImages
import io.github.amandhakar.documents.word.DocxParagraph
import io.github.amandhakar.documents.word.DocxTable
import io.github.amandhakar.documents.word.PageLayout
import io.github.amandhakar.documents.word.PageSetup
import io.github.amandhakar.documents.word.WordStyle
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Turns a Word document into a PDF with Android's own PdfDocument (no library): pages at the file's page size (A4 when
 * it doesn't say), real selectable text, headings, bold / italic / underline, colours, lists, tables and pictures.
 * Pages are filled using the text's real measured height, so nothing is cut off, but the page breaks still won't
 * match Word's exactly. Blocking: call from a background thread.
 */
object WordPdfExporter {
    fun export(doc: DocxDocument, out: OutputStream) {
        val pdf = PdfDocument()
        try {
            val writer = PageWriter(pdf, doc.page)
            for (block in doc.blocks) writer.block(block)
            writer.finish(atLeastOnePage = true)
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }
}

private val InkColor = Color.rgb(0x1B, 0x1D, 0x22)
private const val LABEL_WIDTH = PageLayout.LIST_INDENT

private class PageWriter(val pdf: PdfDocument, val setup: PageSetup) {
    private var number = 0
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f

    private val left = setup.marginLeftPt
    private val top = setup.marginTopPt
    private val width = setup.contentWidthPt
    private val bottom = setup.heightPt - setup.marginBottomPt
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        color = Color.rgb(0x9A, 0xA0, 0xA6)
    }

    private fun ensurePage(): Canvas {
        canvas?.let { return it }
        number++
        val info = PdfDocument.PageInfo.Builder(setup.widthPt.roundToInt(), setup.heightPt.roundToInt(), number).create()
        val p = pdf.startPage(info)
        page = p
        y = top
        return p.canvas.also { canvas = it }
    }

    private fun newPage() {
        finish(atLeastOnePage = false)
        ensurePage()
    }

    fun finish(atLeastOnePage: Boolean) {
        if (page == null && atLeastOnePage && number == 0) ensurePage()
        page?.let { pdf.finishPage(it) }
        page = null
        canvas = null
    }

    private fun pageHasContent() = canvas != null && y > top + 0.5f

    // A page break after the last paragraph must not leave an empty page, so it waits for the next block.
    private var pendingBreak = false

    fun block(block: DocxBlock) {
        if (pendingBreak) {
            pendingBreak = false
            if (canvas != null) newPage()
        }
        when (block) {
            is DocxParagraph -> paragraph(block)
            is DocxTable -> table(block)
            is DocxImage -> image(block)
        }
    }

    // ---- paragraphs ----

    private fun paragraph(p: DocxParagraph) {
        if (p.pageBreakBefore && pageHasContent()) newPage()
        ensurePage()
        val indent = PageLayout.indent(p)
        val textLeft = left + indent
        val textWidth = (width - indent).coerceAtLeast(36f)
        if (pageHasContent()) y += PageLayout.spaceBefore(p)
        val layout = layoutOf(p, textWidth)
        val label = p.list?.label?.takeIf { it.isNotEmpty() }
        drawLayout(layout, textLeft, label, p)
        y += PageLayout.spaceAfter(p)
        pendingBreak = p.pageBreakAfter
    }

    private fun layoutOf(p: DocxParagraph, widthPt: Float): StaticLayout {
        val base = PageLayout.fontPt(p)
        val text = SpannableStringBuilder()
        for (run in p.runs) {
            val start = text.length
            text.append(run.text)
            val end = text.length
            if (end == start) continue
            val size = if (p.headingLevel > 0) base else run.sizeHalfPoints?.let { it / 2f } ?: PageLayout.BODY_PT
            if (size != base) text.setSpan(RelativeSizeSpan(size / base), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val bold = run.bold || p.headingLevel > 0
            val style = when {
                bold && run.italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                run.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) text.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (run.underline) text.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (run.strike) text.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            WordStyle.readableColor(run.colorHex)?.let {
                text.setSpan(ForegroundColorSpan(Color.parseColor("#$it")), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = base
            color = InkColor
            typeface = Typeface.SANS_SERIF
        }
        val alignment = when (p.align) {
            DocxAlign.Center -> Layout.Alignment.ALIGN_CENTER
            DocxAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPt.toInt().coerceAtLeast(1)).setAlignment(alignment)
        if (p.align == DocxAlign.Justify) builder.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
        return builder.build()
    }

    /** Draws [layout] from the current position, line by line, continuing on new pages when it doesn't fit. */
    private fun drawLayout(layout: StaticLayout, x: Float, label: String?, p: DocxParagraph) {
        var line = 0
        while (line < layout.lineCount) {
            val c = ensurePage()
            val startTop = layout.getLineTop(line)
            val available = bottom - y
            var last = layout.getLineForVertical(minOf((startTop + available).toInt(), layout.height))
            while (last >= line && layout.getLineBottom(last) - startTop > available) last--
            if (last < line) {
                if (pageHasContent()) {
                    newPage()
                    continue
                }
                last = line // an empty page: take one line even if it is taller than the page
            }
            val height = (layout.getLineBottom(last) - startTop).toFloat()
            c.save()
            c.clipRect(x, y, x + layout.width, y + height)
            c.translate(x, y - startTop)
            layout.draw(c)
            c.restore()
            if (line == 0 && label != null) {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = PageLayout.fontPt(p)
                    color = InkColor
                    typeface = Typeface.SANS_SERIF
                }
                c.drawText(label, x - LABEL_WIDTH, y + layout.getLineBaseline(0), paint)
            }
            y += height
            line = last + 1
            if (line < layout.lineCount) newPage()
        }
    }

    // ---- tables ----

    private fun table(t: DocxTable) {
        ensurePage()
        for (row in t.rows) {
            val columns = maxOf(1, row.size)
            val cellWidth = width / columns
            val inner = (cellWidth - 8f).coerceAtLeast(24f)
            val layouts = row.map { cell -> cell.filterIsInstance<DocxParagraph>().map { layoutOf(it, inner) } }
            val rowHeight = (layouts.maxOfOrNull { cellLayouts -> cellLayouts.sumOf { it.height } } ?: 0) + 8f
            if (y + rowHeight > bottom && pageHasContent()) newPage()
            val c = ensurePage()
            var x = left
            for (cellLayouts in layouts) {
                c.drawRect(RectF(x, y, x + cellWidth, y + rowHeight), borderPaint)
                var offset = 4f
                for (layout in cellLayouts) {
                    c.save()
                    c.translate(x + 4f, y + offset)
                    layout.draw(c)
                    c.restore()
                    offset += layout.height
                }
                x += cellWidth
            }
            y += rowHeight
        }
        y += 8f
    }

    // ---- pictures ----

    private fun image(image: DocxImage) {
        val bitmap = DocxImages.decode(image.bytes) ?: return
        try {
            val (w, h) = PageLayout.imageBoxPt(image, width, setup.contentHeightPt)
            if (y + h > bottom && pageHasContent()) newPage()
            val c = ensurePage()
            c.drawBitmap(bitmap, null, RectF(left, y, left + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            y += h + 6f
        } finally {
            bitmap.recycle()
        }
    }
}
