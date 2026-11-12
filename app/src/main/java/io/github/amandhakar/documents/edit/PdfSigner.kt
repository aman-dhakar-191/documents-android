package io.github.amandhakar.documents.edit

import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.IOException

/**
 * Changes a PDF with PdfBox-Android: puts signature pictures on it and covers lines of text with new text.
 * The page's own content is kept as it is; everything is added on top. Covered text is NOT removed from the
 * file (it is still there under the box). Blocking: call from a background thread.
 */
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
        output: File,
    ) {
        require(placements.isNotEmpty() || textEdits.isNotEmpty() || newTexts.isNotEmpty()) { "nothing to change" }
        require(placements.isEmpty() || signature != null) { "no signature picture" }
        PDFBoxResourceLoader.init(context.applicationContext)
        // Scratch files, not memory, for the parts of big documents.
        PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            require(placements.all { it.page in 0 until doc.numberOfPages }) { "no such page" }
            require(textEdits.all { it.page in 0 until doc.numberOfPages }) { "no such page" }
            require(newTexts.all { it.place.page in 0 until doc.numberOfPages }) { "no such page" }
            for (edit in textEdits) drawTextEdit(doc, edit)
            for (text in newTexts) drawNewText(doc, text)
            if (signature == null) {
                doc.save(output)
                return
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
        }
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
                content.setNonStrokingColor(0, 0, 0)
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
