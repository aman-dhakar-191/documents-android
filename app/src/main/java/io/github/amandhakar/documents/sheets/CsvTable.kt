package io.github.amandhakar.documents.sheets

/**
 * Reads CSV / TSV text into a one-sheet grid for showing (RFC 4180: quoted cells may hold the delimiter, quotes written
 * twice and line breaks). Values are shown as written, numbers lined up on the right. Free of Android calls so it can
 * be unit tested.
 */
object CsvTable {
    private val NUMBER = Regex("[+-]?(\\d[\\d,]*\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?%?")

    fun parse(text: String, delimiter: Char, name: String = "Sheet", limits: SheetLimits = SheetLimits()): Workbook {
        val cells = HashMap<Long, SheetCell>()
        var row = 0
        var col = 0
        var maxRow = -1
        var maxCol = -1
        var truncated = false
        val field = StringBuilder()
        var quoted = false
        var wasQuoted = false
        var i = 0

        fun endField() {
            val value = field.toString()
            if (value.isNotEmpty() && col < limits.maxColumns) {
                val numeric = !wasQuoted && NUMBER.matches(value.trim())
                cells[CellRefs.key(row, col)] = SheetCell(value, if (numeric) CellKind.Number else CellKind.Text, value)
                if (row > maxRow) maxRow = row
                if (col > maxCol) maxCol = col
            } else if (col >= limits.maxColumns && value.isNotEmpty()) {
                truncated = true
            }
            field.clear()
            wasQuoted = false
            col++
        }

        val s = text.removePrefix("﻿")
        while (i < s.length) {
            if (cells.size >= limits.maxCells || row >= CellRefs.MAX_ROWS) {
                truncated = true
                break
            }
            val c = s[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> if (field.isEmpty()) { quoted = true; wasQuoted = true } else field.append(c)
                    delimiter -> endField()
                    '\r' -> Unit
                    '\n' -> {
                        endField()
                        row++
                        col = 0
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (!truncated && (field.isNotEmpty() || col > 0)) endField()
        val sheet = SheetData(
            name = name,
            path = null,
            cells = cells,
            rowCount = maxRow + 1,
            columnCount = maxCol + 1,
            truncated = truncated,
        )
        return Workbook(listOf(sheet))
    }
}
