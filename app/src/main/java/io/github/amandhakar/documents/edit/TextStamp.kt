package io.github.amandhakar.documents.edit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/** A picture of some text, used to show a new text box while it is positioned and once it is placed. */
/** [widthPerPt] is how wide the text is at a font size of 1 point (in points). */
class TextStamp(val bitmap: Bitmap, val aspect: Float, val baselineRatio: Float, val widthPerPt: Float) {
    companion object {
        private const val TEXT_PX = 96f
        private const val PAD_PX = 4

        /** Draws [text] in [font]'s closest built-in typeface (black, see-through background). Blank text has no picture: returns null. */
        fun render(text: String, font: FontStyleChoice = FontStyleChoice()): TextStamp? {
            if (text.isBlank()) return null
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = TEXT_PX
                typeface = Typeface.create(
                    when (font.family) {
                        FontFamilyChoice.Sans -> Typeface.SANS_SERIF
                        FontFamilyChoice.Serif -> Typeface.SERIF
                        FontFamilyChoice.Mono -> Typeface.MONOSPACE
                    },
                    when {
                        font.bold && font.italic -> Typeface.BOLD_ITALIC
                        font.bold -> Typeface.BOLD
                        font.italic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    },
                )
            }
            val metrics = paint.fontMetrics
            val textHeight = metrics.descent - metrics.ascent
            val width = (paint.measureText(text) + 2 * PAD_PX).toInt().coerceAtLeast(1)
            val height = (textHeight + 2 * PAD_PX).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val baseline = PAD_PX - metrics.ascent
            Canvas(bitmap).drawText(text, PAD_PX.toFloat(), baseline, paint)
            return TextStamp(bitmap, width.toFloat() / height, baseline / height, paint.measureText(text) / TEXT_PX)
        }
    }
}
