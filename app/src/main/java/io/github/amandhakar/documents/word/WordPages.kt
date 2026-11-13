package io.github.amandhakar.documents.word

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PageInk = Color(0xFF1B1D22)
private val PageLine = Color(0xFFBFC3CA)

/** Points to screen units for one zoom level: [dpPerPt] dp per point of paper, and the user's font setting to undo (the page is drawn to scale). */
private class PageScale(val dpPerPt: Float, val fontScale: Float) {
    fun dp(pt: Float): Dp = Dp(pt * dpPerPt)
    fun sp(pt: Float): TextUnit = TextUnit(pt * dpPerPt / fontScale, TextUnitType.Sp)
}

/**
 * A Word document as paper pages (the file's own page size, A4 when it doesn't say), with margins, drawn to scale at
 * [zoom] times the screen's width, with "Page 2 of 5" under each. Where the pages break is an estimate, not Word's own.
 */
@Composable
fun WordPagesScreen(document: DocxDocument, zoom: Float, modifier: Modifier = Modifier) {
    val pages = remember(document) { PageLayout.paginateColumns(document) }
    val setup = document.page
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxSize()) {
        val pageWidth = (maxWidth - 24.dp) * zoom
        val scale = PageScale(pageWidth.value / setup.widthPt, fontScale)
        val pageHeight = scale.dp(setup.heightPt)
        LazyColumn(
            modifier = Modifier.horizontalScroll(rememberScrollState()).width(pageWidth + 24.dp),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(pages) { index, blocks ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier.size(pageWidth, pageHeight)
                            .clip(RectangleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            .background(Color.White)
                            .padding(
                                start = scale.dp(setup.marginLeftPt), end = scale.dp(setup.marginRightPt),
                                top = scale.dp(setup.marginTopPt), bottom = scale.dp(setup.marginBottomPt),
                            ),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            for (band in blocks.bands) {
                                val count = band.columns.size
                                val columnWidth = PageLayout.columnWidthPt(count, band.gapPt, setup)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(scale.dp(band.gapPt))) {
                                    for (column in band.columns) {
                                        Column(Modifier.width(scale.dp(columnWidth))) {
                                            for (block in column) PageBlock(block, setup, scale, columnWidth)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "Page ${index + 1} of ${pages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (document.truncated) {
                item {
                    Text(
                        "This document is longer than the app shows. The rest was left out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageBlock(block: DocxBlock, setup: PageSetup, scale: PageScale, widthPt: Float = setup.contentWidthPt) {
    when (block) {
        is DocxParagraph -> PageParagraph(block, scale)
        is DocxColumns -> Unit
        is DocxTable -> PageTable(block, setup, scale, widthPt)
        is DocxImage -> {
            val bitmap by produceState<android.graphics.Bitmap?>(null, block) {
                value = withContext(Dispatchers.Default) { DocxImages.decode(block.bytes) }
            }
            val (w, h) = PageLayout.imageBoxPt(block, widthPt, setup.contentHeightPt)
            bitmap?.let {
                Image(
                    it.asImageBitmap(), contentDescription = "Picture in the document", contentScale = ContentScale.Fit,
                    modifier = Modifier.size(scale.dp(w), scale.dp(h)).padding(vertical = scale.dp(3f)),
                )
            }
        }
    }
}

@Composable
private fun PageParagraph(p: DocxParagraph, scale: PageScale) {
    val onEdit = LocalWordEdit.current
    val font = PageLayout.fontPt(p)
    val text = remember(p, scale.dpPerPt) { styled(p, scale) }
    val align = when (p.align) {
        DocxAlign.Center -> TextAlign.Center
        DocxAlign.End -> TextAlign.End
        DocxAlign.Justify -> TextAlign.Justify
        DocxAlign.Start -> TextAlign.Start
    }
    Row(
        Modifier.fillMaxWidth().padding(top = scale.dp(PageLayout.spaceBefore(p)), bottom = scale.dp(PageLayout.spaceAfter(p)))
            .padding(
                start = scale.dp(PageLayout.indent(p) - if (p.list != null) PageLayout.LIST_INDENT else 0f),
                end = scale.dp(PageLayout.rightIndent(p)),
            ),
    ) {
        if (p.list != null) {
            Text(p.list.label, color = PageInk, fontSize = scale.sp(font), lineHeight = scale.sp(PageLayout.lineHeightPt(p)), modifier = Modifier.width(scale.dp(PageLayout.LIST_INDENT)))
        }
        val body: @Composable () -> Unit = {
            Text(
                text,
                color = PageInk,
                style = TextStyle(
                    fontSize = scale.sp(font), lineHeight = scale.sp(PageLayout.lineHeightPt(p)), textAlign = align,
                    textIndent = PageLayout.firstLineIndent(p).takeIf { it != 0f }?.let { TextIndent(firstLine = scale.sp(it), restLine = 0.sp) },
                ),
                modifier = Modifier.fillMaxWidth().editable(p, onEdit),
            )
        }
        Box(Modifier.weight(1f)) {
            if (canEdit(p, onEdit)) body() else SelectionContainer { body() }
        }
    }
}

private fun styled(p: DocxParagraph, scale: PageScale): AnnotatedString = buildAnnotatedString {
    for (run in p.runs) {
        val decorations = buildList {
            if (run.underline) add(TextDecoration.Underline)
            if (run.strike) add(TextDecoration.LineThrough)
        }
        val color = WordStyle.readableColor(run.colorHex)?.let { Color(("FF" + it).toLong(16)) } ?: Color.Unspecified
        val size = PageLayout.runPt(p, run)
        withStyle(
            SpanStyle(
                fontWeight = if (run.bold || p.headingLevel > 0) FontWeight.Bold else null,
                fontStyle = if (run.italic) FontStyle.Italic else null,
                textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
                fontSize = scale.sp(size),
                color = color,
            ),
        ) { append(run.text) }
    }
}

@Composable
private fun PageTable(table: DocxTable, setup: PageSetup, scale: PageScale, widthPt: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = scale.dp(4f)).border(1.dp, PageLine)) {
        for (row in table.rows) {
            Row(Modifier.fillMaxWidth()) {
                val cellWidth = widthPt / maxOf(1, row.size)
                for (cell in row) {
                    Column(Modifier.weight(1f).border(0.5.dp, PageLine).padding(scale.dp(4f))) {
                        for (inner in cell) PageBlock(inner, setup, scale, cellWidth - 8f)
                    }
                }
            }
        }
    }
}
