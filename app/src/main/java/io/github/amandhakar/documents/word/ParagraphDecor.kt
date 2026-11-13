package io.github.amandhakar.documents.word

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** A paragraph's background colour and the line under it, when its layout says so (code blocks, rules, headings with a line). */
internal fun Modifier.paragraphDecor(layout: ParagraphLayout?, lineWidth: (BorderLine) -> Dp): Modifier {
    if (layout == null) return this
    var m = this
    layout.shadingHex?.let { m = m.background(Color(("FF$it").toLong(16))) }
    val line = layout.bottomBorder
    if (line != null) {
        val color = line.colorHex?.let { Color(("FF$it").toLong(16)) } ?: Color(0xFF1B1D22)
        val width = lineWidth(line)
        m = m.drawBehind {
            val w = maxOf(1f, width.toPx())
            drawLine(color, Offset(0f, size.height - w / 2f), Offset(size.width, size.height - w / 2f), strokeWidth = w)
        }
    }
    return m
}
