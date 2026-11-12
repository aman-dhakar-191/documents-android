package io.github.amandhakar.documents.edit

/** A point on the signature pad. */
data class InkPoint(val x: Float, val y: Float)

/** One stroke: from finger down to finger up. */
data class Stroke(val points: List<InkPoint>)

/** Where the drawn ink goes in the signature picture. */
data class InkLayout(val width: Int, val height: Int, val scale: Float, val offsetX: Float, val offsetY: Float)

object SignatureInk {
    const val MAX_SIDE_PX = 1000
    const val PADDING_PX = 12

    /** True when there is nothing worth saving: no strokes, or only taps with no movement. */
    fun isBlank(strokes: List<Stroke>): Boolean {
        val points = strokes.flatMap { it.points }
        if (points.size < 2) return true
        return points.all { it == points.first() }
    }

    /**
     * How to draw [strokes] into a picture no longer than [MAX_SIDE_PX] on its longest side, with
     * [PADDING_PX] (plus half the pen width) of space around the ink. Null when blank.
     */
    fun layout(strokes: List<Stroke>, strokeWidthPx: Float = 6f): InkLayout? {
        if (isBlank(strokes)) return null
        val points = strokes.flatMap { it.points }
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val pad = PADDING_PX + strokeWidthPx / 2f
        val inkW = (maxX - minX).coerceAtLeast(1f)
        val inkH = (maxY - minY).coerceAtLeast(1f)
        val scale = minOf(1f, (MAX_SIDE_PX - 2 * pad) / maxOf(inkW, inkH))
        val width = (inkW * scale + 2 * pad).toInt().coerceAtLeast(1)
        val height = (inkH * scale + 2 * pad).toInt().coerceAtLeast(1)
        return InkLayout(width, height, scale, offsetX = pad - minX * scale, offsetY = pad - minY * scale)
    }
}
