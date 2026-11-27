package io.github.amandhakar.documents.sheets

/** How a sheet is laid out on screen: sizes, the text a cell shows and the notes above the grid. Free of Android calls so it can be unit tested. */
object SheetLayout {
    /** Excel's default column width, in characters. */
    const val DEFAULT_WIDTH = 8.43f

    /** While editing, a few empty rows and columns past the used ones, so cells can be added. */
    const val EDIT_EXTRA_ROWS = 20
    const val EDIT_EXTRA_COLUMNS = 5

    /** A column's width on screen in dp: Excel's width in characters, about 7 dp each, kept between 40 and 320; 0 when hidden. */
    fun columnWidthDp(sheet: SheetData, col: Int): Float {
        val chars = sheet.columnWidths[col] ?: DEFAULT_WIDTH
        if (chars <= 0f) return 0f
        return (chars * 7f + 10f).coerceIn(40f, 320f)
    }

    fun shownRows(sheet: SheetData, editing: Boolean): Int =
        if (editing && sheet.path != null && sheet.readOnly == null) minOf(sheet.rowCount + EDIT_EXTRA_ROWS, CellRefs.MAX_ROWS) else sheet.rowCount

    fun shownColumns(sheet: SheetData, editing: Boolean): Int =
        if (editing && sheet.path != null && sheet.readOnly == null) minOf(maxOf(sheet.columnCount, 1) + EDIT_EXTRA_COLUMNS, CellRefs.MAX_COLUMNS) else sheet.columnCount

    /** What a cell shows: its value, or its formula when the file saved no value for it. */
    fun shownText(cell: SheetCell): String = cell.text.ifEmpty { cell.formula?.let { "=$it" } ?: "" }

    /** Notes for the sheet on screen: left-out parts, why it can't be edited, and that it is empty. */
    fun notices(workbook: Workbook, sheet: SheetData?, editing: Boolean): List<String> {
        val out = ArrayList<String>()
        if (sheet == null) {
            out += if (workbook.skippedSheets > 0) "This workbook has only charts or other sheets the app can't show." else "This workbook has no sheets."
            return out
        }
        if (sheet.truncated) out += "This sheet is bigger than the app reads. Only the first part is shown."
        if (workbook.skippedSheets > 0) {
            out += if (workbook.skippedSheets == 1) "1 chart or other sheet isn't shown." else "${workbook.skippedSheets} charts or other sheets aren't shown."
        }
        if (editing && sheet.path != null) sheet.readOnly?.let { out += it }
        if (sheet.rowCount == 0 && !(editing && sheet.path != null && sheet.readOnly == null)) out += "This sheet is empty."
        return out
    }
}
