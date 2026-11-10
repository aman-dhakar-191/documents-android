package io.github.amandhakar.documents.document

import android.graphics.Bitmap

/** An opened document that can show its pages as bitmaps. Close it when done. */
interface Document : AutoCloseable {
    val pageCount: Int

    /**
     * Renders page [index] (0-based) about [widthPx] pixels wide. Safe to call from any thread.
     * Throws [IllegalStateException] if the document was closed.
     */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap
}
