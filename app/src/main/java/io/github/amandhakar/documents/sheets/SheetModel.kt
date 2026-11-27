package io.github.amandhakar.documents.sheets

/** What a cell holds, as far as showing and editing it goes. */
enum class CellKind { Number, Text, Bool, Error, Date, Empty }

/**
 * One cell as shown. [text] is what the screen shows; [raw] the stored value (a number as written in the file, the text,
 * TRUE / FALSE); [formula] the cell's formula without "=", if any. [style] is the index of its look in the file (kept when
 * the cell is changed). [sharedFormulaMaster] / [arrayFormula] mark formulas that other cells depend on, which are not
 * edited here.
 */
data class SheetCell(
    val text: String,
    val kind: CellKind,
    val raw: String? = null,
    val formula: String? = null,
    val style: Int = 0,
    val sharedFormulaMaster: Boolean = false,
    val arrayFormula: Boolean = false,
)

/** A block of cells from ([top], [left]) to ([bottom], [right]), counted from 0 and inclusive. */
data class CellRange(val top: Int, val left: Int, val bottom: Int, val right: Int) {
    fun contains(row: Int, col: Int) = row in top..bottom && col in left..right
}

/**
 * One sheet. Rows and columns count from 0; [cells] is keyed by [CellRefs.key]. [path] is the sheet's part inside the
 * .xlsx (null for a CSV file). [rowCount] / [columnCount] are how many rows and columns the screen shows; [truncated] is
 * true when the sheet has more than the app reads. [readOnly] says why the sheet can't be edited, if it can't.
 */
class SheetData(
    val name: String,
    val path: String?,
    val cells: Map<Long, SheetCell>,
    val rowCount: Int,
    val columnCount: Int,
    val columnWidths: Map<Int, Float> = emptyMap(),
    val merges: List<CellRange> = emptyList(),
    /** Header rows of tables (Excel "Format as Table"): their text names the table's columns. */
    val tableHeaders: List<CellRange> = emptyList(),
    val hidden: Boolean = false,
    val truncated: Boolean = false,
    val readOnly: String? = null,
) {
    fun cell(row: Int, col: Int): SheetCell? = cells[CellRefs.key(row, col)]

    fun copyWithCells(cells: Map<Long, SheetCell>, rowCount: Int, columnCount: Int) = SheetData(
        name, path, cells, rowCount, columnCount, columnWidths, merges, tableHeaders, hidden, truncated, readOnly,
    )

    /**
     * Why the cell at ([row], [col]) can't be changed here, or null when it can. These cover what would make Excel
     * report a damaged file, or silently break other cells, if only the cell's value were rewritten.
     */
    fun editBlock(row: Int, col: Int): String? {
        readOnly?.let { return it }
        if (path == null) return "This file can't be edited as a table. Use Text to edit it."
        merges.firstOrNull { it.contains(row, col) }?.let {
            if (it.top != row || it.left != col) return "This cell is part of merged cells. Change the first cell of the group (${CellRefs.name(it.top, it.left)})."
        }
        if (tableHeaders.any { it.contains(row, col) }) return "This is a table's header (the column names), which can't be changed here."
        val cell = cell(row, col)
        if (cell?.sharedFormulaMaster == true) return "Other cells copy this cell's formula, so it can't be changed here."
        if (cell?.arrayFormula == true) return "This cell is part of an array formula, which can't be changed here."
        return null
    }
}

/** A whole workbook: its sheets in order, and what is needed to show numbers and dates the way the file says. */
class Workbook(
    val sheets: List<SheetData>,
    /** The number format of each cell style, by style index. */
    val styleFormats: List<NumberFormat> = emptyList(),
    val date1904: Boolean = false,
    /** Where the workbook part and its calculation chain are inside the zip (null for CSV). */
    val workbookPath: String? = null,
    val calcChainPath: String? = null,
    /** Sheets of other kinds (charts, dialogs, macros) that aren't shown. */
    val skippedSheets: Int = 0,
) {
    fun formatOf(style: Int): NumberFormat = styleFormats.getOrNull(style) ?: NumberFormat.GENERAL
}

/** A number format: Excel's built-in [id], or a custom [code] such as "#,##0.00" or "dd/mm/yyyy". */
data class NumberFormat(val id: Int, val code: String? = null) {
    companion object {
        val GENERAL = NumberFormat(0)
    }
}

/** Cell addresses like "B12": names, numbers and keys. Free of Android calls so it can be unit tested. */
object CellRefs {
    const val MAX_ROWS = 1_048_576
    const val MAX_COLUMNS = 16_384

    fun key(row: Int, col: Int): Long = (row.toLong() shl 16) or col.toLong()
    fun rowOf(key: Long): Int = (key shr 16).toInt()
    fun colOf(key: Long): Int = (key and 0xFFFF).toInt()

    /** 0 -> "A", 25 -> "Z", 26 -> "AA". */
    fun columnName(col: Int): String {
        val sb = StringBuilder()
        var n = col + 1
        while (n > 0) {
            val r = (n - 1) % 26
            sb.append('A' + r)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    fun name(row: Int, col: Int): String = columnName(col) + (row + 1)

    /** "B12" -> (11, 1); "$B$12" works too. Null for anything else, or past Excel's last row or column. */
    fun parse(ref: String): Pair<Int, Int>? {
        val s = ref.replace("$", "").trim().uppercase()
        var i = 0
        var col = 0
        while (i < s.length && s[i] in 'A'..'Z') {
            col = col * 26 + (s[i] - 'A' + 1)
            if (col > MAX_COLUMNS) return null
            i++
        }
        if (i == 0 || i == s.length) return null
        val digits = s.substring(i)
        if (!digits.all { it.isDigit() } || digits.length > 7) return null
        val row = digits.toInt()
        if (row < 1 || row > MAX_ROWS) return null
        return (row - 1) to (col - 1)
    }

    /** "A1:C3" -> its range; a single cell "B2" is a one-cell range. */
    fun parseRange(ref: String): CellRange? {
        val parts = ref.split(':')
        val a = parse(parts[0]) ?: return null
        val b = if (parts.size > 1) parse(parts[1]) ?: return null else a
        return CellRange(minOf(a.first, b.first), minOf(a.second, b.second), maxOf(a.first, b.first), maxOf(a.second, b.second))
    }

    fun rangeName(r: CellRange): String = if (r.top == r.bottom && r.left == r.right) name(r.top, r.left) else name(r.top, r.left) + ":" + name(r.bottom, r.right)
}
