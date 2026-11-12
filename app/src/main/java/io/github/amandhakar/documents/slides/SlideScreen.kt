package io.github.amandhakar.documents.slides

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.amandhakar.documents.word.DocxImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun hex(h: String): Color = Color(("FF$h").toLong(16))

/** A presentation as a scrolling list of slides drawn to scale, each under a "3 / 8" caption and its speaker notes. */
@Composable
fun SlideScreen(deck: PptxDeck, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(deck.slides) { index, slide ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SlideView(deck, slide)
                Text(
                    "${index + 1} / ${deck.slides.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (slide.notes.isNotBlank()) {
                    Text("Notes: ${slide.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (deck.truncated) {
            item {
                Text(
                    "This presentation has more slides than the app shows. The rest were left out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SlideView(deck: PptxDeck, slide: PptxSlide) {
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .background(hex(slide.backgroundHex)),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val pxPerEmu = widthPx / deck.widthEmu
        val heightDp = with(density) { (deck.heightEmu * pxPerEmu).toDp() }
        val scale = Scale(pxPerEmu, density.density, density.fontScale)
        Box(Modifier.fillMaxWidth().size(maxWidth, heightDp).clip(RectangleShape)) {
            for (shape in slide.shapes) ShapeView(shape, scale)
        }
    }
}

/** Converts slide units: EMU to dp, points to sp. */
private class Scale(val pxPerEmu: Float, val density: Float, val fontScale: Float) {
    fun dp(emu: Long): Dp = Dp(emu * pxPerEmu / density)

    /** A text size in points on the slide as sp. The user's font size setting is not applied: the slide is drawn to scale. */
    fun sp(pt: Float): TextUnit = TextUnit(pt * 12700f * pxPerEmu / density / fontScale, androidx.compose.ui.unit.TextUnitType.Sp)
}

@Composable
private fun ShapeView(shape: PptxShape, scale: Scale) {
    val place = Modifier.offset(scale.dp(shape.box.x), scale.dp(shape.box.y)).size(scale.dp(shape.box.w), scale.dp(shape.box.h))
    when (shape) {
        is PptxText -> TextShape(shape, scale, place)
        is PptxPicture -> {
            val bitmap by produceState<android.graphics.Bitmap?>(null, shape) {
                value = withContext(Dispatchers.Default) { DocxImages.decode(shape.bytes) }
            }
            bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Picture on the slide", contentScale = ContentScale.Fit, modifier = place) }
        }
        is PptxTable -> TableShape(shape, scale, place)
        is PptxNotShown -> Box(place.background(Color(0xFFE4E5E8)), contentAlignment = Alignment.Center) {
            Text(shape.label, color = Color(0xFF5A606C), fontSize = scale.sp(14f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TextShape(shape: PptxText, scale: Scale, place: Modifier) {
    val outline: Shape = when (shape.geometry) {
        PptxGeometry.Ellipse -> CircleShape
        PptxGeometry.RoundRect -> RoundedCornerShape(scale.dp(shape.box.h / 8))
        PptxGeometry.Rect -> RectangleShape
    }
    var m = place
    if (shape.fillHex != null) m = m.background(hex(shape.fillHex), outline)
    if (shape.lineHex != null) m = m.border(1.dp, hex(shape.lineHex), outline)
    val align = when (shape.anchor) {
        PptxAnchor.Top -> Alignment.TopStart
        PptxAnchor.Middle -> Alignment.CenterStart
        PptxAnchor.Bottom -> Alignment.BottomStart
    }
    Box(m.padding(horizontal = scale.dp(91440), vertical = scale.dp(45720)), contentAlignment = align) {
        // The text may be taller than its box, as in PowerPoint when it doesn't fit: it is drawn, not cut.
        Column(Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Top, unbounded = true)) {
            for (p in shape.paragraphs) ParagraphView(p, scale)
        }
    }
}

@Composable
private fun ParagraphView(p: PptxParagraph, scale: Scale) {
    val first = p.runs.firstOrNull()
    val size = first?.sizePt ?: 18f
    val indent = scale.dp(p.level * 342900L)
    val text = remember(p) { styled(p, scale) }
    val align = when (p.align) {
        PptxAlign.Center -> TextAlign.Center
        PptxAlign.End -> TextAlign.End
        PptxAlign.Justify -> TextAlign.Justify
        PptxAlign.Start -> TextAlign.Start
    }
    if (p.runs.isEmpty()) {
        Box(Modifier.size(1.dp, scale.dp((size * 12700L * 1.2f).toLong())))
        return
    }
    Row(Modifier.padding(start = indent)) {
        if (p.bullet != null) {
            Text(p.bullet, color = hex(first?.colorHex ?: "000000"), fontSize = scale.sp(size), modifier = Modifier.width(scale.dp((size * 12700L * 1.1f).toLong())))
        }
        Text(
            text,
            style = TextStyle(fontSize = scale.sp(size), lineHeight = scale.sp(size * 1.2f), textAlign = align),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun styled(p: PptxParagraph, scale: Scale): AnnotatedString = buildAnnotatedString {
    for (run in p.runs) {
        withStyle(
            SpanStyle(
                color = hex(run.colorHex),
                fontSize = scale.sp(run.sizePt),
                fontWeight = if (run.bold) FontWeight.Bold else null,
                fontStyle = if (run.italic) FontStyle.Italic else null,
                textDecoration = if (run.underline) TextDecoration.Underline else null,
            ),
        ) { append(run.text) }
    }
}

@Composable
private fun TableShape(table: PptxTable, scale: Scale, place: Modifier) {
    Column(place) {
        for (row in table.rows) {
            Row(Modifier.fillMaxWidth()) {
                row.cells.forEachIndexed { i, cell ->
                    val w = table.columnWidthsEmu.getOrNull(i) ?: (table.box.w / maxOf(1, row.cells.size))
                    Column(
                        Modifier.width(scale.dp(w))
                            .background(cell.fillHex?.let { hex(it) } ?: Color.Transparent)
                            .border(0.5.dp, Color(0xFF9AA0A6))
                            .padding(scale.dp(45720)),
                    ) {
                        for (p in cell.paragraphs) ParagraphView(p, scale)
                    }
                }
            }
        }
    }
}
