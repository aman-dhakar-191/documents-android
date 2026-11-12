package io.github.amandhakar.documents.convert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.github.amandhakar.documents.edit.PdfTextLines
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

/** Why a PDF couldn't be converted, in words the person can act on. */
class ConvertException(message: String) : IOException(message)

/** What was written: how many pages went in out of how many the PDF has. */
class ConvertOutcome(val pagesConverted: Int, val pagesTotal: Int)

/**
 * Turns a PDF into a Word or PowerPoint file with no library. **Text**: the text is read with PdfBox-Android and put
 * back together as paragraphs, headings and bullets (Word) or one text box per paragraph where it was (PowerPoint);
 * it is editable but the layout is a best guess, and a scanned PDF has no text to read. **Pictures**: each page is
 * drawn with Android's PdfRenderer and placed as a picture, which looks exactly like the PDF but can't be edited.
 * Blocking: call from a background thread.
 */
object PdfConverter {
    enum class Target { Word, PowerPoint }

    enum class Mode { Text, Pictures }

    private const val MAX_TEXT_PAGES = 300
    private const val MAX_PICTURE_PAGES = 100
    private const val MAX_PICTURE_BYTES = 60L * 1024 * 1024
    private const val PICTURE_WIDTH_PX = 1240

    fun convert(context: Context, source: Uri, target: Target, mode: Mode, out: OutputStream): ConvertOutcome {
        val file = File.createTempFile("convert", ".pdf", context.cacheDir)
        try {
            (context.contentResolver.openInputStream(source) ?: throw IOException("Could not read $source")).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            return when (mode) {
                Mode.Text -> convertText(context, file, target, out)
                Mode.Pictures -> convertPictures(file, target, out)
            }
        } finally {
            file.delete()
        }
    }

    private fun convertText(context: Context, file: File, target: Target, out: OutputStream): ConvertOutcome {
        val (pages, total) = try {
            PdfTextLines.readAll(context, file, MAX_TEXT_PAGES)
        } catch (e: IOException) {
            if (e.message?.contains("password", ignoreCase = true) == true) {
                throw ConvertException("This PDF is password-protected, which isn't supported yet.")
            }
            throw ConvertException("Couldn't read the text in this PDF. It may be damaged. Try Page pictures.")
        }
        if (pages.none { PdfParagraphs.fromPage(it).isNotEmpty() }) {
            throw ConvertException("This PDF has no text to read (it may be a scan or made of pictures). Use Page pictures instead.")
        }
        when (target) {
            Target.Word -> DocxWriter.write(PdfParagraphs.toDocx(pages), out)
            Target.PowerPoint -> PptxWriter.write(PdfParagraphs.toPptx(pages), out)
        }
        return ConvertOutcome(pages.size, total)
    }

    private fun convertPictures(file: File, target: Target, out: OutputStream): ConvertOutcome {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(fd)
        } catch (e: IOException) {
            fd.close()
            throw ConvertException("Couldn't draw the pages of this PDF. It may be damaged or password-protected.")
        } catch (e: SecurityException) {
            fd.close()
            throw ConvertException("This PDF is password-protected, which isn't supported yet.")
        }
        renderer.use {
            val total = renderer.pageCount
            if (total == 0) throw ConvertException("This PDF has no pages.")
            val pictures = ArrayList<ByteArray>()
            var widthPt = 595f
            var heightPt = 842f
            var bytes = 0L
            for (i in 0 until minOf(total, MAX_PICTURE_PAGES)) {
                renderer.openPage(i).use { page ->
                    if (i == 0) {
                        widthPt = page.width.toFloat()
                        heightPt = page.height.toFloat()
                    }
                    val height = (PICTURE_WIDTH_PX.toLong() * page.height / page.width).toInt().coerceIn(1, 4000)
                    val bitmap = Bitmap.createBitmap(PICTURE_WIDTH_PX, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val jpeg = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, jpeg)
                        val data = jpeg.toByteArray()
                        if (bytes + data.size > MAX_PICTURE_BYTES && pictures.isNotEmpty()) return@use
                        bytes += data.size
                        pictures += data
                    } finally {
                        bitmap.recycle()
                    }
                }
                if (pictures.size <= i) break // the size limit was reached on this page
            }
            when (target) {
                Target.Word -> DocxWriter.write(PdfParagraphs.picturesToDocx(pictures, widthPt, heightPt), out)
                Target.PowerPoint -> PptxWriter.write(PdfParagraphs.picturesToPptx(pictures, widthPt, heightPt), out)
            }
            return ConvertOutcome(pictures.size, total)
        }
    }
}
