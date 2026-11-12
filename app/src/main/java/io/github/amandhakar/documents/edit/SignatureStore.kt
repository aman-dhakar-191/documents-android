package io.github.amandhakar.documents.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.io.File

/** The person's drawn signature, kept as a picture in the app's private storage so it can be reused. */
class SignatureStore(context: Context) {
    private val file = File(context.filesDir, "signature.png")

    fun load(): Bitmap? = if (file.exists()) BitmapFactory.decodeFile(file.path) else null

    fun save(bitmap: Bitmap): Boolean = try {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } catch (e: java.io.IOException) {
        false
    }

    fun delete() {
        file.delete()
    }

    companion object {
        private val INK = Color.rgb(0x1A, 0x23, 0x7E)

        /**
         * Draws [strokes] as a see-through picture cropped to the ink, with a pen [penWidthPx] wide in the
         * pad's own pixels (so it looks as it did on the pad), or null when there is no ink.
         */
        fun render(strokes: List<Stroke>, penWidthPx: Float): Bitmap? {
            val layout = SignatureInk.layout(strokes, penWidthPx) ?: return null
            val bitmap = Bitmap.createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.translate(layout.offsetX, layout.offsetY)
            canvas.scale(layout.scale, layout.scale)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = INK
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = penWidthPx / layout.scale
            }
            for (stroke in strokes) {
                val points = stroke.points
                if (points.size < 2) continue
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    // Curves through the midpoints, so the line is smooth rather than a polygon.
                    for (i in 1 until points.size - 1) {
                        val midX = (points[i].x + points[i + 1].x) / 2f
                        val midY = (points[i].y + points[i + 1].y) / 2f
                        quadTo(points[i].x, points[i].y, midX, midY)
                    }
                    lineTo(points.last().x, points.last().y)
                }
                canvas.drawPath(path, paint)
            }
            return bitmap
        }
    }
}
