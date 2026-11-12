package io.github.amandhakar.documents.edit

import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.IOException

/**
 * Changes a PDF with PdfBox-Android: puts signature pictures on it and covers lines of text with new text.
 * The page's own content is kept as it is; everything is added on top. Covered text is NOT removed from the
 * file (it is still there under the box). Blocking: call from a background thread.
 */
/** What erasing old text achieved: how many characters were really removed, and the pages (0-based) where it had to be skipped. */
data class EraseOutcome(val removedCharacters: Int, val failedPages: List<Int>)

object PdfSigner {
    /**
     * Reads [input], applies [textEdits] (each covers a line and writes new text), draws [signature] at every
     * one of [placements] on top, and writes the result to [output]. Throws [IOException] if the PDF can't be
     * read (a password-protected one included) and [IllegalArgumentException] if something names a page the PDF
     * doesn't have, there is nothing to do, or a text change uses characters Helvetica can't draw.
     */
    fun edit(
        context: Context,
        input: File,
        signature: Bitmap?,
        placements: List<PlacedSignature>,
        textEdits: List<TextEdit>,
        newTexts: List<PlacedText>,
        marks: List<PageMark>,
        eraseOld: Boolean,
        output: File,
    ): EraseOutcome {
        require(placements.isNotEmpty() || textEdits.isNotEmpty() || newTexts.isNotEmpty() || marks.isNotEmpty()) {
            "nothing to change"
        }
        require(placements.isEmpty() || signature != null) { "no signature picture" }
        PDFBoxResourceLoader.init(context.applicationContext)
        // Scratch files, not memory, for the parts of big documents.
        PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            require(placements.all { it.page in 0 until doc.numberOfPages }) { "no such page" }
            require(textEdits.all { it.page in 0 until doc.numberOfPages }) { "no such page" }
            require(newTexts.all { it.place.page in 0 until doc.numberOfPages }) { "no such page" }
            val outcome = if (eraseOld) eraseOldText(doc, textEdits) else EraseOutcome(0, emptyList())
            for (edit in textEdits) drawTextEdit(doc, edit)
            for (text in newTexts) drawNewText(doc, text)
            require(marks.all { it.page in 0 until doc.numberOfPages }) { "no such page" }
            for (mark in marks) drawMark(doc, mark)
            if (signature == null) {
                doc.save(output)
                return outcome
            }
            val image = LosslessFactory.createFromImage(doc, signature)
            val signatureAspect = signature.width.toFloat() / signature.height
            for (placed in placements) {
                val page = doc.getPage(placed.page)
                val rotation = page.rotation
                val crop = page.cropBox
                val box = PageBox(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height)
                val height = Placement.height(placed.width, signatureAspect, box.shownAspect(rotation))
                val m = PdfCoordinates.imageMatrix(box, rotation, placed, height)
                // APPEND adds to the page's content; the last "true" wraps the old content so the
                // picture isn't affected by whatever transformations it left behind.
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
                    content.drawImage(image, Matrix(m.a, m.b, m.c, m.d, m.e, m.f))
                }
            }
            doc.save(output)
            return outcome
        }
    }

    /**
     * Really removes the old text under each change (see [TextEraser]). A page where that can't be done safely is left as it
     * is and listed in the result; the change is then only covered.
     */
    private fun eraseOldText(doc: PDDocument, textEdits: List<TextEdit>): EraseOutcome {
        var removed = 0
        val failed = mutableListOf<Int>()
        for ((pageIndex, edits) in textEdits.groupBy { it.page }) {
            val crop = doc.getPage(pageIndex).cropBox
            val boxes = edits.map {
                PageBoxPt(
                    it.left * crop.width,
                    it.top * crop.height,
                    (it.left + it.width) * crop.width,
                    (it.top + it.height) * crop.height,
                )
            }
            try {
                val count = TextEraser.erase(doc, pageIndex, boxes)
                if (count == 0) failed += pageIndex else removed += count
            } catch (e: UnsupportedOperationException) {
                failed += pageIndex
            } catch (e: IOException) {
                failed += pageIndex
            }
        }
        return EraseOutcome(removed, failed)
    }

    /** Covers the old line with a box and writes the new text over it (Helvetica; shrunk a little if it is longer). */
    private fun drawTextEdit(doc: PDDocument, edit: TextEdit) {
        require(PdfText.unsupported(edit.text).isEmpty()) { "unsupported characters" }
        val page = doc.getPage(edit.page)
        require(PdfCoordinates.normalizeRotation(page.rotation) == 0) { "rotated pages can't be edited yet" }
        val crop = page.cropBox
        val w = crop.width
        val h = crop.height
        val x = crop.lowerLeftX + edit.left * w
        val boxWidth = edit.width * w
        val boxHeight = edit.height * h
        val boxBottom = crop.lowerLeftY + (1f - edit.top) * h - boxHeight
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
            content.setNonStrokingColor((edit.cover shr 16) and 0xFF, (edit.cover shr 8) and 0xFF, edit.cover and 0xFF)
            content.addRect(x - COVER_PAD, boxBottom - COVER_PAD, boxWidth + 2 * COVER_PAD, boxHeight + 2 * COVER_PAD)
            content.fill()
            if (edit.text.isNotEmpty()) {
                val font = PDType1Font.HELVETICA
                val size = TextFit.size(edit.fontSizePt, boxWidth, font.getStringWidth(edit.text) / 1000f * edit.fontSizePt)
                content.setNonStrokingColor((edit.ink shr 16) and 0xFF, (edit.ink shr 8) and 0xFF, edit.ink and 0xFF)
                content.beginText()
                content.setFont(font, size)
                content.newLineAtOffset(x, crop.lowerLeftY + (1f - edit.baseline) * h)
                content.showText(edit.text)
                content.endText()
            }
        }
    }

    /** Writes a new piece of text (Helvetica) where the person placed it, sized so it spans the width they chose. */
    private fun drawNewText(doc: PDDocument, newText: PlacedText) {
        require(newText.text.isNotEmpty() && PdfText.unsupported(newText.text).isEmpty()) { "unsupported characters" }
        val page = doc.getPage(newText.place.page)
        require(PdfCoordinates.normalizeRotation(page.rotation) == 0) { "rotated pages can't be edited yet" }
        val crop = page.cropBox
        val w = crop.width
        val h = crop.height
        val font = PDType1Font.HELVETICA
        val size = TextBoxMath.fontSizePt(newText.place.width, w, font.getStringWidth(newText.text) / 1000f)
        val heightFraction = Placement.height(newText.place.width, newText.aspect, w / h)
        val baseline = TextBoxMath.baselineFromTop(newText.place.top, heightFraction, newText.baselineRatio)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
            content.setNonStrokingColor(0, 0, 0)
            content.beginText()
            content.setFont(font, size)
            content.newLineAtOffset(crop.lowerLeftX + newText.place.left * w, crop.lowerLeftY + (1f - baseline) * h)
            content.showText(newText.text)
            content.endText()
        }
    }

    /** Draws a highlight (a see-through yellow box) or a pen stroke, on any page rotation. */
    private fun drawMark(doc: PDDocument, mark: PageMark) {
        val page = doc.getPage(mark.page)
        val rotation = page.rotation
        val crop = page.cropBox
        val box = PageBox(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { content ->
            when (mark) {
                is Highlight -> {
                    val see = PDExtendedGraphicsState()
                    see.nonStrokingAlphaConstant = Marks.HIGHLIGHT_ALPHA
                    content.setGraphicsStateParameters(see)
                    content.setNonStrokingColor((mark.color shr 16) and 0xFF, (mark.color shr 8) and 0xFF, mark.color and 0xFF)
                    val corners = listOf(
                        PdfCoordinates.point(box, rotation, mark.left, mark.top),
                        PdfCoordinates.point(box, rotation, mark.left + mark.width, mark.top),
                        PdfCoordinates.point(box, rotation, mark.left + mark.width, mark.top + mark.height),
                        PdfCoordinates.point(box, rotation, mark.left, mark.top + mark.height),
                    )
                    content.moveTo(corners[0].first, corners[0].second)
                    for (c in corners.drop(1)) content.lineTo(c.first, c.second)
                    content.closePath()
                    content.fill()
                }
                is PenStroke -> {
                    if (mark.points.size < 2) return@use
                    content.setStrokingColor((mark.color shr 16) and 0xFF, (mark.color shr 8) and 0xFF, mark.color and 0xFF)
                    val shownWidth = if (PdfCoordinates.normalizeRotation(rotation) % 180 == 0) box.width else box.height
                    content.setLineWidth(mark.widthFraction * shownWidth)
                    content.setLineCapStyle(1)
                    content.setLineJoinStyle(1)
                    val first = PdfCoordinates.point(box, rotation, mark.points[0].x, mark.points[0].y)
                    content.moveTo(first.first, first.second)
                    for (p in mark.points.drop(1)) {
                        val at = PdfCoordinates.point(box, rotation, p.x, p.y)
                        content.lineTo(at.first, at.second)
                    }
                    content.stroke()
                }
            }
        }
    }

    private const val COVER_PAD = 1f

    /** True when [file] opens as a PDF with at least one page. Used to check a result before it replaces anything. */
    fun looksValid(context: Context, file: File, expectedPages: Int? = null): Boolean = try {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use {
            it.numberOfPages > 0 && (expectedPages == null || it.numberOfPages == expectedPages)
        }
    } catch (e: IOException) {
        false
    }

    /** The page count of [file], or null if it can't be read. */
    fun pageCount(context: Context, file: File): Int? = try {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
    } catch (e: IOException) {
        null
    }
}
