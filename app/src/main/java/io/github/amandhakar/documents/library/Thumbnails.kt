package io.github.amandhakar.documents.library

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import io.github.amandhakar.documents.document.DocumentOpener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** First-page previews for the cards, cached in memory. */
object Thumbnails {
    private const val WIDTH_PX = 360
    private const val CACHE_BYTES = 24 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // A few at a time, so scrolling a long list doesn't open dozens of files at once.
    private val gate = Semaphore(2)

    /** Returns null if the file can't be rendered (damaged, password-protected, not a PDF). */
    suspend fun load(context: Context, file: PdfFile): Bitmap? {
        val key = "${file.id}:${file.modifiedSeconds}"
        cache.get(key)?.let { return it }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val document = DocumentOpener.open(context.applicationContext, file.uri)
                    try {
                        document.renderPage(0, WIDTH_PX).also { cache.put(key, it) }
                    } finally {
                        document.close()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
