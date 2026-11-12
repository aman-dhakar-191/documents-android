package io.github.amandhakar.documents.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import io.github.amandhakar.documents.slides.EmuBox
import io.github.amandhakar.documents.slides.PptxAlign
import io.github.amandhakar.documents.slides.PptxAnchor
import io.github.amandhakar.documents.slides.PptxDeck
import io.github.amandhakar.documents.slides.PptxGeometry
import io.github.amandhakar.documents.slides.PptxNotShown
import io.github.amandhakar.documents.slides.PptxParagraph
import io.github.amandhakar.documents.slides.PptxPicture
import io.github.amandhakar.documents.slides.PptxShape
import io.github.amandhakar.documents.slides.PptxSlide
import io.github.amandhakar.documents.slides.PptxTable
import io.github.amandhakar.documents.slides.PptxText
import io.github.amandhakar.documents.word.DocxImages
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Turns a PowerPoint deck into a PDF with Android's own PdfDocument (no library): one page per slide at the slide's own
 * size, with real selectable text, shapes, pictures and tables placed where the slides have them. Charts, SmartArt and
 * objects are the same grey labelled boxes as on screen. Speaker notes are not included. Blocking: call from a
 * background thread.
 */
object SlidesPdfExporter {
    private const val EMU_PER_PT = 12700f

    fun export(deck: PptxDeck, out: OutputStream) {
        val pdf = PdfDocument()
        try {
            val w = (deck.widthEmu / EMU_PER_PT).roundToInt().coerceAtLeast(1)
            val h = (deck.heightEmu / EMU_PER_PT).roundToInt().coerceAtLeast(1)
            deck.slides.forEachIndexed { index, slide ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(w, h, index + 1).create())
                try {
                    drawSlide(page.canvas, slide, w.toFloat(), h.toFloat())
                } finally {
                    pdf.finishPage(page)
                }
            }
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private fun hexColor(hex: String): Int = Color.parseColor("#$hex")

    private fun rect(box: EmuBox) = RectF(box.x / EMU_PER_PT, box.y / EMU_PER_PT, (box.x + box.w) / EMU_PER_PT, (box.y + box.h) / EMU_PER_PT)

    private fun drawSlide(c: Canvas, slide: PptxSlide, width: Float, height: Float) {
        c.drawRect(0f, 0f, width, height, Paint().apply { this.color = hexColor(slide.backgroundHex) })
        for (shape in slide.shapes) drawShape(c, shape)
    }

    private fun drawShape(c: Canvas, shape: PptxShape) {
        when (shape) {
            is PptxText -> drawText(c, shape)
            is PptxPicture -> {
                val bitmap = DocxImages.decode(shape.bytes) ?: return
                try {
                    c.drawBitmap(bitmap, null, rect(shape.box), Paint(Paint.FILTER_BITMAP_FLAG))
                } finally {
                    bitmap.recycle()
                }
            }
            is PptxTable -> drawTable(c, shape)
            is PptxNotShown -> {
                val r = rect(shape.box)
                c.drawRect(r, Paint().apply { color = Color.rgb(0xE4, 0xE5, 0xE8) })
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 14f
                    color = Color.rgb(0x5A, 0x60, 0x6C)
                    textAlign = Paint.Align.CENTER
                }
                c.drawText(shape.label, r.centerX(), r.centerY() + 5f, paint)
            }
        }
    }

    private fun drawText(c: Canvas, shape: PptxText) {
        val r = rect(shape.box)
        if (shape.fillHex != null) drawGeometry(c, r, shape.geometry, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hexColor(shape.fillHex) })
        if (shape.lineHex != null) {
            drawGeometry(
                c, r, shape.geometry,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                    color = hexColor(shape.lineHex)
                },
            )
        }
        val insetX = 7.2f
        val insetY = 3.6f
        val textWidth = (r.width() - 2 * insetX).coerceAtLeast(12f)
        val layouts = shape.paragraphs.map { it to layoutOf(it, textWidth - it.level * 27f) }
        val total = layouts.sumOf { it.second.height.toDouble() }.toFloat()
        var y = when (shape.anchor) {
            PptxAnchor.Top -> r.top + insetY
            PptxAnchor.Middle -> r.top + (r.height() - total) / 2
            PptxAnchor.Bottom -> r.bottom - insetY - total
        }
        for ((p, layout) in layouts) {
            val x = r.left + insetX + p.level * 27f
            c.save()
            c.translate(x, y)
            layout.draw(c)
            c.restore()
            if (p.bullet != null && p.runs.isNotEmpty()) {
                val first = p.runs.first()
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = first.sizePt
                    color = hexColor(first.colorHex)
                    typeface = Typeface.SANS_SERIF
                }
                c.drawText(p.bullet, x - first.sizePt * 1.1f, y + layout.getLineBaseline(0), paint)
            }
            y += layout.height
        }
    }

    private fun drawGeometry(c: Canvas, r: RectF, geometry: PptxGeometry, paint: Paint) {
        when (geometry) {
            PptxGeometry.Ellipse -> c.drawOval(r, paint)
            PptxGeometry.RoundRect -> c.drawRoundRect(r, r.height() / 8, r.height() / 8, paint)
            PptxGeometry.Rect -> c.drawRect(r, paint)
        }
    }

    private fun drawTable(c: Canvas, table: PptxTable) {
        val origin = rect(table.box)
        val border = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
            color = Color.rgb(0x9A, 0xA0, 0xA6)
        }
        var y = origin.top
        for (row in table.rows) {
            var x = origin.left
            val rowHeight = row.heightEmu / EMU_PER_PT
            row.cells.forEachIndexed { i, cell ->
                val w = (table.columnWidthsEmu.getOrNull(i) ?: (table.box.w / maxOf(1, row.cells.size))) / EMU_PER_PT
                val cellRect = RectF(x, y, x + w, y + rowHeight)
                if (cell.fillHex != null) c.drawRect(cellRect, Paint().apply { color = hexColor(cell.fillHex) })
                c.drawRect(cellRect, border)
                var offset = 3.6f
                for (p in cell.paragraphs) {
                    val layout = layoutOf(p, (w - 14.4f).coerceAtLeast(12f))
                    c.save()
                    c.translate(x + 7.2f, y + offset)
                    layout.draw(c)
                    c.restore()
                    offset += layout.height
                }
                x += w
            }
            y += rowHeight
        }
    }

    private fun layoutOf(p: PptxParagraph, widthPt: Float): StaticLayout {
        val base = p.runs.firstOrNull()?.sizePt ?: 18f
        val text = SpannableStringBuilder()
        for (run in p.runs) {
            val start = text.length
            text.append(run.text)
            val end = text.length
            if (end == start) continue
            if (run.sizePt != base) text.setSpan(RelativeSizeSpan(run.sizePt / base), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val style = when {
                run.bold && run.italic -> Typeface.BOLD_ITALIC
                run.bold -> Typeface.BOLD
                run.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) text.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (run.underline) text.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(ForegroundColorSpan(hexColor(run.colorHex)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = base
            color = Color.BLACK
            typeface = Typeface.SANS_SERIF
        }
        val alignment = when (p.align) {
            PptxAlign.Center -> Layout.Alignment.ALIGN_CENTER
            PptxAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPt.toInt().coerceAtLeast(1)).setAlignment(alignment)
        if (p.align == PptxAlign.Justify) builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        return builder.build()
    }
}
