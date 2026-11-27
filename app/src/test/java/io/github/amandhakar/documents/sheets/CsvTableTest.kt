package io.github.amandhakar.documents.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTableTest {
    private fun sheet(text: String, delimiter: Char = ',', limits: SheetLimits = SheetLimits()) = CsvTable.parse(text, delimiter, limits = limits).sheets.single()

    @Test fun plainRows() {
        val s = sheet("name,age\nAman,30\n")
        assertEquals(2, s.rowCount)
        assertEquals(2, s.columnCount)
        assertEquals("Aman", s.cell(1, 0)!!.text)
        assertEquals(CellKind.Number, s.cell(1, 1)!!.kind)
        assertEquals(CellKind.Text, s.cell(0, 1)!!.kind)
    }

    @Test fun quotedCellsHoldCommasQuotesAndLineBreaks() {
        val s = sheet("a,\"b, c\",\"say \"\"hi\"\"\"\r\n\"two\nlines\",x,\"7\"\r\n")
        assertEquals("b, c", s.cell(0, 1)!!.text)
        assertEquals("say \"hi\"", s.cell(0, 2)!!.text)
        assertEquals("two\nlines", s.cell(1, 0)!!.text)
        assertEquals(CellKind.Text, s.cell(1, 2)!!.kind) // quoted: kept as text
        assertEquals(2, s.rowCount)
    }

    @Test fun tabsAndEmptyCells() {
        val s = sheet("a\t\tc\n\tb", '\t')
        assertEquals("c", s.cell(0, 2)!!.text)
        assertNull(s.cell(0, 1))
        assertEquals("b", s.cell(1, 1)!!.text)
    }

    @Test fun noFinalNewlineAndByteOrderMark() {
        val s = sheet("\uFEFFx,y")
        assertEquals("x", s.cell(0, 0)!!.text)
        assertEquals("y", s.cell(0, 1)!!.text)
    }

    @Test fun limits() {
        val s = sheet((1..100).joinToString("\n") { "$it" }, limits = SheetLimits(maxCells = 10))
        assertTrue(s.truncated)
        assertEquals(10, s.cells.size)
        val wide = sheet((1..10).joinToString(","), limits = SheetLimits(maxColumns = 3))
        assertEquals(3, wide.columnCount)
        assertTrue(wide.truncated)
    }

    @Test fun csvCantBeEditedAsATable() {
        assertTrue(sheet("a").editBlock(0, 0)!!.contains("Text"))
        assertEquals(0, sheet("").rowCount)
    }
}
