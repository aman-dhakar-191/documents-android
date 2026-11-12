package io.github.amandhakar.documents.edit

import android.content.Context
import android.graphics.Bitmap
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.IOException

/**
 * Puts signature pictures into a PDF with PdfBox-Android. The page's own content is kept as it is
 * (text stays text); the picture is added on top. Blocking: call from a background thread.
 */
object PdfSigner {
    /**
     * Reads [input], draws [signature] at every one of [placements] and writes the result to [output].
     * Throws [IOException] if the PDF can't be read (a password-protected one included) and
     * [IllegalArgumentException] if a placement names a page the PDF doesn't have.
     */
    fun sign(context: Context, input: File, signature: Bitmap, placements: List<PlacedSignature>, output: File) {
        require(placements.isNotEmpty()) { "nothing to sign" }
        PDFBoxResourceLoader.init(context.applicationContext)
        // Scratch files, not memory, for the parts of big documents.
        PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            require(placements.all { it.page in 0 until doc.numberOfPages }) { "no such page" }
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
