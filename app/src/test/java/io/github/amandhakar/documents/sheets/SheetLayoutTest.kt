package io.github.amandhakar.documents.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetLayoutTest {
    private fun sheet(
        widths: Map<Int, Float> = emptyMap(),
        path: String? = "xl/worksheets/sheet1.xml",
        readOnly: String? = null,
        rows: Int = 3,
        cols: Int = 2,
        truncated: Boolean = false,
    ) = SheetData("S", path, emptyMap(), rows, cols, widths, truncated = truncated, readOnly = readOnly)

    @Test fun widthsFollowExcelAndStayReadable() {
        val s = sheet(mapOf(0 to 0f, 1 to 20f, 2 to 1f, 3 to 500f))
        assertEquals(0f, SheetLayout.columnWidthDp(s, 0), 0f)
        assertEquals(150f, SheetLayout.columnWidthDp(s, 1), 0.01f)
        assertEquals(40f, SheetLayout.columnWidthDp(s, 2), 0f)
        assertEquals(320f, SheetLayout.columnWidthDp(s, 3), 0f)
        assertEquals(SheetLayout.DEFAULT_WIDTH * 7f + 10f, SheetLayout.columnWidthDp(s, 9), 0.01f)
    }

    @Test fun editingAddsRoomOnlyWhereEditingWorks() {
        val s = sheet()
        assertEquals(3, SheetLayout.shownRows(s, editing = false))
        assertEquals(3 + SheetLayout.EDIT_EXTRA_ROWS, SheetLayout.shownRows(s, editing = true))
        assertEquals(2 + SheetLayout.EDIT_EXTRA_COLUMNS, SheetLayout.shownColumns(s, editing = true))
        assertEquals(3, SheetLayout.shownRows(sheet(path = null), editing = true))
        assertEquals(2, SheetLayout.shownColumns(sheet(readOnly = "protected"), editing = true))
        assertEquals(1 + SheetLayout.EDIT_EXTRA_COLUMNS, SheetLayout.shownColumns(sheet(rows = 0, cols = 0), editing = true))
    }

    @Test fun aFormulaWithNoSavedValueShowsTheFormula() {
        assertEquals("=SUM(A1:A2)", SheetLayout.shownText(SheetCell("", CellKind.Empty, formula = "SUM(A1:A2)")))
        assertEquals("3", SheetLayout.shownText(SheetCell("3", CellKind.Number, "3", formula = "SUM(A1:A2)")))
        assertEquals("", SheetLayout.shownText(SheetCell("", CellKind.Empty)))
    }

    @Test fun notices() {
        val wb = Workbook(listOf(sheet()), skippedSheets = 2)
        assertEquals(listOf("2 charts or other sheets aren't shown."), SheetLayout.notices(wb, wb.sheets[0], editing = false))
        val cut = sheet(truncated = true, readOnly = "big")
        assertTrue(SheetLayout.notices(Workbook(listOf(cut)), cut, editing = true).containsAll(listOf("This sheet is bigger than the app reads. Only the first part is shown.", "big")))
        val empty = sheet(rows = 0, cols = 0)
        assertEquals(listOf("This sheet is empty."), SheetLayout.notices(Workbook(listOf(empty)), empty, editing = false))
        assertEquals(emptyList<String>(), SheetLayout.notices(Workbook(listOf(empty)), empty, editing = true))
        assertEquals(listOf("This workbook has no sheets."), SheetLayout.notices(Workbook(emptyList()), null, editing = false))
    }
}
