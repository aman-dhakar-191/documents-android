package io.github.amandhakar.documents.document

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A PDF opened with Android's [PdfRenderer]. The renderer allows only one page open at a time
 * and is not thread-safe, so every access goes through [lock].
 *
 * Throws [java.io.IOException] if the file isn't a valid PDF and [SecurityException] if it is
 * password protected.
 */
internal class PdfDocument(
    private val fd: ParcelFileDescriptor,
    private val onClose: () -> Unit = {},
) : Document {
    private val lock = Any()
    private var closed = false
    private val renderer: PdfRenderer = try {
        PdfRenderer(fd)
    } catch (e: Throwable) {
        fd.close()
        onClose()
        throw e
    }

    override val pageCount: Int = renderer.pageCount

    override suspend fun renderPage(index: Int, widthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        synchronized(lock) {
            check(!closed) { "document is closed" }
            val page = renderer.openPage(index)
            try {
                val size = PageSizing.bitmapSize(page.width, page.height, widthPx)
                val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            renderer.close()
            fd.close()
        }
        onClose()
    }
}
