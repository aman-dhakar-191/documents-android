package io.github.amandhakar.documents.word

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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

/** The number of the page being drawn and of all pages, which page number fields in the header, footer and text show. */
private val LocalPageInfo = staticCompositionLocalOf { 1 to 1 }

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
                            .padding(start = scale.dp(setup.marginLeftPt), end = scale.dp(setup.marginRightPt)),
                    ) {
                      CompositionLocalProvider(LocalPageInfo provides (index + 1 to pages.size)) {
                        Column(Modifier.fillMaxWidth().padding(top = scale.dp(PageLayout.bodyTopPt(document, index)), bottom = scale.dp(PageLayout.bodyBottomPt(document, index)))) {
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
                        val header = document.headerFooter.headerFor(index)
                        if (header.isNotEmpty()) {
                            Column(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(top = scale.dp(document.headerFooter.headerDistancePt))) {
                                for (block in header) PageBlock(block, setup, scale)
                            }
                        }
                        val footer = document.headerFooter.footerFor(index)
                        if (footer.isNotEmpty()) {
                            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = scale.dp(document.headerFooter.footerDistancePt))) {
                                for (block in footer) PageBlock(block, setup, scale)
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
    val pageInfo = LocalPageInfo.current
    val text = remember(p, scale.dpPerPt, pageInfo) { styled(p, scale, pageInfo) }
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
            Text(
                p.list.label, color = PageInk, fontSize = scale.sp(font), lineHeight = scale.sp(PageLayout.lineHeightPt(p)),
                fontFamily = WordFontFamilies.of(PageLayout.faceOf(p)), modifier = Modifier.width(scale.dp(PageLayout.LIST_INDENT)),
            )
        }
        val body: @Composable () -> Unit = {
            Text(
                text,
                color = PageInk,
                style = TextStyle(
                    fontSize = scale.sp(font), lineHeight = scale.sp(PageLayout.lineHeightPt(p)), textAlign = align,
                    fontFamily = WordFontFamilies.of(PageLayout.faceOf(p)),
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

private fun styled(p: DocxParagraph, scale: PageScale, pageInfo: Pair<Int, Int>): AnnotatedString = buildAnnotatedString {
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
                fontFamily = WordFontFamilies.forName(run.fontName ?: p.layout?.fontName),
            ),
        ) {
            append(
                when (run.field) {
                    PageField.Page -> pageInfo.first.toString()
                    PageField.Pages -> pageInfo.second.toString()
                    null -> run.text
                },
            )
        }
    }
}

@Composable
private fun PageTable(table: DocxTable, setup: PageSetup, scale: PageScale, widthPt: Float) {
    val layout = table.layout
    if (layout != null) {
        LaidOutTable(table, layout, setup, scale, widthPt)
        return
    }
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

private fun borderColor(line: BorderLine): Color = line.colorHex?.let { Color(("FF$it").toLong(16)) } ?: PageInk

/**
 * A table drawn from what the file says: its column widths, cells that span columns, shading, lines, margins, vertical
 * alignment, row heights, and where it sits (left, centred, right). A merged cell's text stays in its top row.
 */
@Composable
private fun LaidOutTable(table: DocxTable, layout: TableLayout, setup: PageSetup, scale: PageScale, widthPt: Float) {
    val widths = remember(layout, widthPt) { PageLayout.tableColumnWidths(layout, widthPt) }
    val tableWidth = widths.sum()
    val startPt = when (layout.align) {
        DocxAlign.Center -> ((widthPt - tableWidth) / 2f).coerceAtLeast(0f)
        DocxAlign.End -> (widthPt - tableWidth).coerceAtLeast(0f)
        else -> layout.indentPt.coerceIn(0f, maxOf(0f, widthPt - 24f))
    }
    Column(Modifier.padding(start = scale.dp(startPt), top = scale.dp(4f), bottom = scale.dp(4f))) {
        table.rows.forEachIndexed { r, row ->
            val minHeight = layout.rowMinHeightsPt.getOrNull(r) ?: 0f
            Row(Modifier.height(IntrinsicSize.Min).heightIn(min = scale.dp(minHeight))) {
                val firstStart = layout.cells.getOrNull(r)?.firstOrNull()?.gridStart ?: 0
                if (firstStart > 0) Spacer(Modifier.width(scale.dp(widths.take(firstStart).sum())))
                row.forEachIndexed { c, cell ->
                    val props = layout.cells.getOrNull(r)?.getOrNull(c) ?: CellProps(c)
                    val first = props.gridStart.coerceIn(0, widths.size)
                    val last = (props.gridStart + props.colSpan).coerceIn(first, widths.size)
                    val cellWidth = widths.subList(first, last).sum()
                    val textWidth = (cellWidth - layout.marginLeftPt - layout.marginRightPt).coerceAtLeast(12f)
                    val borders = props.borders
                    Box(
                        Modifier.width(scale.dp(cellWidth)).fillMaxHeight()
                            .background(props.shadingHex?.let { Color(("FF$it").toLong(16)) } ?: Color.Transparent)
                            .drawBehind {
                                fun edge(line: BorderLine?, a: Offset, b: Offset) {
                                    if (line != null) drawLine(borderColor(line), a, b, strokeWidth = maxOf(1f, scale.dp(line.widthPt).toPx()))
                                }
                                edge(borders.top, Offset(0f, 0f), Offset(size.width, 0f))
                                edge(borders.bottom, Offset(0f, size.height), Offset(size.width, size.height))
                                edge(borders.left, Offset(0f, 0f), Offset(0f, size.height))
                                edge(borders.right, Offset(size.width, 0f), Offset(size.width, size.height))
                            }
                            .padding(
                                start = scale.dp(layout.marginLeftPt), end = scale.dp(layout.marginRightPt),
                                top = scale.dp(layout.marginTopPt), bottom = scale.dp(layout.marginBottomPt),
                            ),
                        contentAlignment = when (props.vAlign) {
                            CellAlign.Top -> Alignment.TopStart
                            CellAlign.Center -> Alignment.CenterStart
                            CellAlign.Bottom -> Alignment.BottomStart
                        },
                    ) {
                        Column { for (block in cell) PageBlock(block, setup, scale, textWidth) }
                    }
                }
            }
        }
    }
}
