package io.github.amandhakar.documents.sheets

/** One typed change: the cell at ([row], [col]) of the sheet at [sheetPath] now says [text]. */
data class CellEdit(val sheetPath: String, val row: Int, val col: Int, val text: String)

/**
 * The cell changes made so far, oldest first. Nothing is written until the person saves. Later changes to the same cell
 * win; [undo] takes back the latest. Free of Android calls so it can be unit tested.
 */
data class XlsxEdits(val list: List<CellEdit> = emptyList()) {
    val isEmpty: Boolean get() = resolved().isEmpty()

    /** How many cells are changed (a cell changed twice counts once). */
    val count: Int get() = resolved().size

    fun set(sheetPath: String, row: Int, col: Int, text: String) = XlsxEdits(list + CellEdit(sheetPath, row, col, text))

    fun undo() = XlsxEdits(list.dropLast(1))

    /** The latest text of each changed cell, by (sheet path, cell key). */
    fun resolved(): Map<Pair<String, Long>, String> {
        val out = LinkedHashMap<Pair<String, Long>, String>()
        for (e in list) out[e.sheetPath to CellRefs.key(e.row, e.col)] = e.text
        return out
    }

    fun textOf(sheetPath: String, row: Int, col: Int): String? = resolved()[sheetPath to CellRefs.key(row, col)]
}

/** The workbook as it will look once the changes are saved: what the screen shows while editing, and what saving must match. */
object XlsxEditModel {
    fun apply(workbook: Workbook, edits: XlsxEdits): Workbook {
        val resolved = edits.resolved()
        if (resolved.isEmpty()) return workbook
        val sheets = workbook.sheets.map { sheet ->
            val mine = resolved.filterKeys { it.first == sheet.path }
            if (mine.isEmpty()) return@map sheet
            val cells = HashMap(sheet.cells)
            var rows = sheet.rowCount
            var cols = sheet.columnCount
            for ((key, text) in mine) {
                val k = key.second
                val old = cells[k]
                val cell = cellFor(text, old?.style ?: 0, workbook)
                if (cell == null) cells.remove(k) else cells[k] = cell
                rows = maxOf(rows, CellRefs.rowOf(k) + 1)
                cols = maxOf(cols, CellRefs.colOf(k) + 1)
            }
            sheet.copyWithCells(cells, rows, cols)
        }
        return Workbook(sheets, workbook.styleFormats, workbook.date1904, workbook.workbookPath, workbook.calcChainPath, workbook.skippedSheets)
    }

    /** The cell that typing [text] gives, keeping the cell's look ([style]); null for an empty unstyled cell. */
    fun cellFor(text: String, style: Int, workbook: Workbook): SheetCell? {
        val format = workbook.formatOf(style)
        return when (val input = XlsxNumbers.interpret(text, format, workbook.date1904)) {
            CellInput.Empty -> if (style != 0) SheetCell("", CellKind.Empty, null, null, style) else null
            is CellInput.Number -> SheetCell(
                XlsxNumbers.format(input.value, format, workbook.date1904),
                if (XlsxNumbers.isDate(format)) CellKind.Date else CellKind.Number,
                input.value, null, style,
            )
            is CellInput.Bool -> (if (input.value) "TRUE" else "FALSE").let { SheetCell(it, CellKind.Bool, it, null, style) }
            is CellInput.Text -> io.github.amandhakar.documents.ooxml.XmlText.clean(input.value).let { SheetCell(it, CellKind.Text, it, null, style) }
        }
    }

    /**
     * What the edit box starts with for a cell: numbers as stored, dates and times as 2026-11-27 14:30 (which typing back
     * stores as the same date), text as written.
     */
    fun editText(cell: SheetCell?, workbook: Workbook): String = when (cell?.kind) {
        null, CellKind.Empty -> ""
        CellKind.Number -> cell.raw ?: cell.text
        CellKind.Date -> {
            val serial = cell.raw?.toBigDecimalOrNull()
            val dt = serial?.let { XlsxNumbers.dateTime(it, workbook.date1904) }
            if (serial == null || dt == null) {
                cell.text
            } else {
                val time = dt.toLocalTime()
                val clock = if (time.second != 0) time.toString().take(8) else time.toString().take(5)
                when {
                    serial < java.math.BigDecimal.ONE -> clock
                    time.toSecondOfDay() == 0 -> dt.toLocalDate().toString()
                    else -> dt.toLocalDate().toString() + " " + clock
                }
            }
        }
        else -> cell.text
    }
}
