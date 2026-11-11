package io.github.amandhakar.documents.document

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
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

    // Android's PDF renderer gained text selection in Android 15 (API 35).
    override val supportsTextSelection: Boolean = Build.VERSION.SDK_INT >= 35

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

    override suspend fun selectText(pageIndex: Int, start: TextEdge, stop: TextEdge): PageText? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= 35) selectTextApi35(pageIndex, start, stop) else null
        }

    @RequiresApi(35)
    private fun selectTextApi35(pageIndex: Int, start: TextEdge, stop: TextEdge): PageText? =
        synchronized(lock) {
            check(!closed) { "document is closed" }
            val page = renderer.openPage(pageIndex)
            try {
                val width = page.width
                val height = page.height
                fun boundary(edge: TextEdge): SelectionBoundary = when (edge) {
                    is TextEdge.At -> SelectionBoundary(
                        Point(
                            PageGeometry.toPoints(edge.point.x, width),
                            PageGeometry.toPoints(edge.point.y, height),
                        ),
                    )
                    // An end from an earlier selection, passed back exactly as Android gave it.
                    is TextEdge.Kept -> edge.token as SelectionBoundary
                }
                val selection = page.selectContent(boundary(start), boundary(stop))
                    ?: return@synchronized null
                val contents = selection.selectedTextContents
                val text = PageGeometry.joinText(contents.map { it.text })
                if (text.isEmpty()) return@synchronized null
                val bounds = contents.flatMap { it.bounds }.map {
                    PageGeometry.normalize(it.left, it.top, it.right, it.bottom, width, height)
                }
                // Keep the ends in reading order, so the first handle is always the earlier one.
                val first = selection.start
                val last = selection.stop
                val (earlier, later) = if (first.index <= last.index) first to last else last to first
                PageText(text, bounds, TextEdge.Kept(earlier), TextEdge.Kept(later))
            } finally {
                page.close()
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
