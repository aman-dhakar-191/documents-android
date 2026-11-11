package io.github.amandhakar.documents.document

import android.graphics.Bitmap

/** An opened document that can show its pages as bitmaps. Close it when done. */
interface Document : AutoCloseable {
    val pageCount: Int

    /** True if [selectText] can work on this document on this phone (the PDF text API needs Android 15). */
    val supportsTextSelection: Boolean

    /**
     * Renders page [index] (0-based) about [widthPx] pixels wide. Safe to call from any thread.
     * Throws [IllegalStateException] if the document was closed.
     */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap

    /**
     * The text between [start] and [stop] on page [pageIndex]; the ends may be given in either
     * order. If both are the same spot, the word there. Null when there is no text there, or when
     * [supportsTextSelection] is false. Throws [IllegalStateException] if the document was closed.
     */
    suspend fun selectText(pageIndex: Int, start: TextEdge, stop: TextEdge): PageText?
}
