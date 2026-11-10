package io.github.amandhakar.documents.document

/** Zoom levels for the page list: 1.0 fits the page to the screen width. */
object Zoom {
    const val MIN = 1f
    const val MAX = 4f
    const val STEP = 0.5f

    fun zoomIn(current: Float): Float = (current + STEP).coerceIn(MIN, MAX)

    fun zoomOut(current: Float): Float = (current - STEP).coerceIn(MIN, MAX)

    /** [current] zoom multiplied by [factor], kept within [MIN]..[MAX]. A bad factor (NaN, zero, negative) changes nothing. */
    fun scaled(current: Float, factor: Float): Float {
        val base = current.coerceIn(MIN, MAX)
        if (!factor.isFinite() || factor <= 0f) return base
        return (base * factor).coerceIn(MIN, MAX)
    }

    /**
     * The visual scale to show while two fingers are down. [accumulated] is the scale so far in
     * this gesture and [factor] is this move's change. The result never takes [current] outside
     * [MIN]..[MAX], so spreading past the limit and pinching back doesn't feel stuck.
     */
    fun pinchScale(current: Float, accumulated: Float, factor: Float): Float {
        val base = current.coerceIn(MIN, MAX)
        val a = if (accumulated.isFinite() && accumulated > 0f) accumulated else 1f
        val f = if (factor.isFinite() && factor > 0f) factor else 1f
        return (base * a * f).coerceIn(MIN, MAX) / base
    }
}
