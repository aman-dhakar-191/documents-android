package io.github.amandhakar.documents.document

/** Zoom levels for the page list: 1.0 fits the page to the screen width. */
object Zoom {
    const val MIN = 1f
    const val MAX = 4f
    const val STEP = 0.5f

    fun zoomIn(current: Float): Float = (current + STEP).coerceIn(MIN, MAX)

    fun zoomOut(current: Float): Float = (current - STEP).coerceIn(MIN, MAX)
}
