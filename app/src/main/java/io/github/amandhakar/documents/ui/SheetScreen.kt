package io.github.amandhakar.documents.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amandhakar.documents.sheets.CellKind
import io.github.amandhakar.documents.sheets.CellRefs
import io.github.amandhakar.documents.sheets.SheetCell
import io.github.amandhakar.documents.sheets.SheetData
import io.github.amandhakar.documents.sheets.SheetLayout
import io.github.amandhakar.documents.sheets.Workbook
import kotlin.math.roundToInt

private val ROW_HEIGHT = 32.dp
private val ROW_HEADER = 48.dp

/**
 * A spreadsheet (Excel sheet or CSV table): the column letters and row numbers stay in place while the cells scroll,
 * tabs switch between sheets, and tapping a cell shows all of its text (and its formula) at the bottom. While [onEdit] is
 * set, tapping a cell also asks to change it. [selected] is the tapped cell (row, column) of sheet [sheetIndex].
 */
@Composable
fun SheetScreen(
    workbook: Workbook,
    sheetIndex: Int,
    onSheet: (Int) -> Unit,
    selected: Pair<Int, Int>?,
    onSelect: (Pair<Int, Int>?) -> Unit,
    onEdit: ((SheetData, Int, Int) -> Unit)?,
    notice: String? = null,
) {
    val index = sheetIndex.coerceIn(0, (workbook.sheets.size - 1).coerceAtLeast(0))
    val sheet = workbook.sheets.getOrNull(index)
    Column(Modifier.fillMaxSize()) {
        if (workbook.sheets.size > 1) {
            ScrollableTabRow(selectedTabIndex = index, edgePadding = 8.dp) {
                workbook.sheets.forEachIndexed { i, s ->
                    Tab(
                        selected = i == index,
                        onClick = {
                            onSelect(null)
                            onSheet(i)
                        },
                        text = { Text(if (s.hidden) "${s.name} (hidden)" else s.name, maxLines = 1) },
                    )
                }
            }
        }
        val notes = SheetLayout.notices(workbook, sheet, editing = onEdit != null) + listOfNotNull(notice)
        for (note in notes) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (sheet == null) return@Column
        Box(Modifier.weight(1f)) {
            Grid(sheet, selected, onTap = { row, col ->
                onSelect(row to col)
                onEdit?.invoke(sheet, row, col)
            }, editing = onEdit != null)
        }
        selected?.let { (row, col) -> CellDetail(sheet, row, col) }
    }
}

@Composable
private fun Grid(sheet: SheetData, selected: Pair<Int, Int>?, onTap: (Int, Int) -> Unit, editing: Boolean) {
    val density = LocalDensity.current
    val rows = SheetLayout.shownRows(sheet, editing)
    val cols = SheetLayout.shownColumns(sheet, editing)
    // Where each column starts, in pixels; hidden columns are 0 wide.
    val starts = remember(sheet, cols, density) {
        val px = FloatArray(cols + 1)
        for (c in 0 until cols) px[c + 1] = px[c] + with(density) { SheetLayout.columnWidthDp(sheet, c).dp.toPx() }
        px
    }
    var offset by remember(sheet) { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = with(density) { (maxWidth - ROW_HEADER).toPx() }.coerceAtLeast(1f)
        val maxOffset = (starts[cols] - viewport).coerceAtLeast(0f)
        if (offset > maxOffset) offset = maxOffset
        val hState = rememberScrollableState { delta ->
            val old = offset
            offset = (offset - delta).coerceIn(0f, maxOffset)
            old - offset
        }
        // Only the columns on screen are drawn, so a wide sheet stays quick.
        var first = 0
        while (first < cols && starts[first + 1] <= offset) first++
        var last = first
        while (last < cols && starts[last] < offset + viewport) last++
        val visible = first until last
        val listState = rememberLazyListState()
        Column(Modifier.fillMaxSize().scrollable(hState, Orientation.Horizontal)) {
            // Column letters.
            Row(Modifier.height(ROW_HEIGHT).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.width(ROW_HEADER).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                    for (c in visible) {
                        val w = starts[c + 1] - starts[c]
                        if (w <= 0f) continue
                        HeaderText(
                            CellRefs.columnName(c),
                            selected?.second == c,
                            Modifier.offset { IntOffset((starts[c] - offset).roundToInt(), 0) }.width(with(density) { w.toDp() }).fillMaxHeight(),
                        )
                    }
                }
            }
            HorizontalDivider()
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                items(rows, key = { it }) { row ->
                    Row(Modifier.height(ROW_HEIGHT)) {
                        HeaderText(
                            (row + 1).toString(),
                            selected?.first == row,
                            Modifier.width(ROW_HEADER).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                            for (c in visible) {
                                val w = starts[c + 1] - starts[c]
                                if (w <= 0f) continue
                                val cell = sheet.cell(row, c)
                                CellBox(
                                    cell,
                                    selected == (row to c),
                                    Modifier
                                        .offset { IntOffset((starts[c] - offset).roundToInt(), 0) }
                                        .width(with(density) { w.toDp() })
                                        .fillMaxHeight()
                                        .clickable { onTap(row, c) },
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun HeaderText(text: String, highlighted: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun CellBox(cell: SheetCell?, selected: Boolean, modifier: Modifier) {
    val shown = cell?.let { SheetLayout.shownText(it) }.orEmpty()
    val align = when (cell?.kind) {
        CellKind.Number, CellKind.Date -> TextAlign.End
        CellKind.Bool, CellKind.Error -> TextAlign.Center
        else -> TextAlign.Start
    }
    Box(
        modifier
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            shown,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = align,
            color = if (cell?.kind == CellKind.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The tapped cell's name, its whole text and its formula, under the grid. */
@Composable
private fun CellDetail(sheet: SheetData, row: Int, col: Int) {
    val cell = sheet.cell(row, col)
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(CellRefs.name(row, col), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        SelectionContainer {
            Column {
                Text(cell?.text?.ifEmpty { null } ?: if (cell?.formula == null) "(empty)" else "(no value saved)", style = MaterialTheme.typography.bodyMedium, maxLines = 6, overflow = TextOverflow.Ellipsis)
                cell?.formula?.let { Text("Formula: =$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
