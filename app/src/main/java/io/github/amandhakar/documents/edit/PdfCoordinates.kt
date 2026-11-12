package io.github.amandhakar.documents.edit

/** A PDF page's visible box in PDF units: its lower-left corner and size, before any /Rotate is applied. */
data class PageBox(val left: Float, val bottom: Float, val width: Float, val height: Float) {
    /** Width / height as the page is shown, after [rotation] (degrees clockwise). */
    fun shownAspect(rotation: Int): Float =
        if (PdfCoordinates.normalizeRotation(rotation) % 180 == 0) width / height else height / width
}

/** The six numbers of a PDF transformation matrix: x' = a*x + c*y + e, y' = b*x + d*y + f. */
data class PdfMatrix(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float)

/** Turns a spot on the page as shown on screen into PDF page coordinates, including the page's /Rotate. */
object PdfCoordinates {
    /** 0, 90, 180 or 270. Anything that is not a multiple of 90 is treated as 0, as PDF viewers do. */
    fun normalizeRotation(degrees: Int): Int {
        val d = ((degrees % 360) + 360) % 360
        return if (d % 90 == 0) d else 0
    }

    /**
     * The matrix that draws an image's unit square (which PDF places upright, x to the right and y up)
     * onto [placed] as the person sees the page, whatever the page's [rotation].
     * [heightFraction] is the signature's height as a fraction of the shown page height.
     */
    fun imageMatrix(box: PageBox, rotation: Int, placed: PlacedSignature, heightFraction: Float): PdfMatrix {
        val rot = normalizeRotation(rotation)
        fun point(u: Float, v: Float): Pair<Float, Float> {
            val (px, py) = when (rot) {
                90 -> v * box.width to u * box.height
                180 -> (1f - u) * box.width to v * box.height
                270 -> (1f - v) * box.width to (1f - u) * box.height
                else -> u * box.width to (1f - v) * box.height
            }
            return (box.left + px) to (box.bottom + py)
        }
        val u0 = placed.left
        val v0 = placed.top
        val bottomLeft = point(u0, v0 + heightFraction)
        val bottomRight = point(u0 + placed.width, v0 + heightFraction)
        val topLeft = point(u0, v0)
        return PdfMatrix(
            a = bottomRight.first - bottomLeft.first,
            b = bottomRight.second - bottomLeft.second,
            c = topLeft.first - bottomLeft.first,
            d = topLeft.second - bottomLeft.second,
            e = bottomLeft.first,
            f = bottomLeft.second,
        )
    }
}
