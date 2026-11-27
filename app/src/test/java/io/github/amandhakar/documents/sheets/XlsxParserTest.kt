package io.github.amandhakar.documents.sheets

import io.github.amandhakar.documents.ooxml.OoxmlException
import io.github.amandhakar.documents.ooxml.OoxmlLimits
import io.github.amandhakar.documents.sheets.XlsxTestFiles.sheet
import io.github.amandhakar.documents.sheets.XlsxTestFiles.workbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class XlsxParserTest {
    private fun read(bytes: ByteArray, sheetLimits: SheetLimits = SheetLimits()) = XlsxParser.parse(ByteArrayInputStream(bytes), OoxmlLimits(), sheetLimits)

    private fun refused(bytes: ByteArray): String = try {
        read(bytes)
        fail("expected the file to be refused")
        ""
    } catch (e: OoxmlException) {
        e.message ?: ""
    }

    @Test fun readsValuesOfEveryKind() {
        val wb = read(workbook(listOf("Data" to sheet(
            "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\"><v>42</v></c><c r=\"C1\" t=\"b\"><v>1</v></c>" +
                "<c r=\"D1\" t=\"inlineStr\"><is><t>inline</t></is></c><c r=\"E1\" t=\"e\"><v>#DIV/0!</v></c>" +
                "<c r=\"F1\" t=\"str\"><f>A1&amp;\"!\"</f><v>Name!</v></c></row>",
        )), strings = listOf("Name")))
        val s = wb.sheets.single()
        assertEquals("Data", s.name)
        assertEquals("xl/worksheets/sheet1.xml", s.path)
        assertEquals("Name", s.cell(0, 0)!!.text)
        assertEquals(CellKind.Number, s.cell(0, 1)!!.kind)
        assertEquals("42", s.cell(0, 1)!!.text)
        assertEquals("TRUE", s.cell(0, 2)!!.text)
        assertEquals("inline", s.cell(0, 3)!!.text)
        assertEquals("#DIV/0!", s.cell(0, 4)!!.text)
        assertEquals("Name!", s.cell(0, 5)!!.text)
        assertEquals("A1&\"!\"", s.cell(0, 5)!!.formula)
        assertEquals(1, s.rowCount)
        assertEquals(6, s.columnCount)
    }

    @Test fun numberFormatsAndDatesFollowTheStyle() {
        val wb = read(workbook(listOf("S" to sheet(
            "<row r=\"1\"><c r=\"A1\" s=\"1\"><v>3.14159</v></c><c r=\"B1\" s=\"2\"><v>46353</v></c>" +
                "<c r=\"C1\" s=\"3\"><v>0.256</v></c><c r=\"D1\" s=\"4\"><v>1234567.891</v></c><c r=\"E1\" s=\"5\"><v>46353.5</v></c></row>",
        ))))
        val s = wb.sheets.single()
        assertEquals("3.14", s.cell(0, 0)!!.text)
        assertEquals("2026-11-27", s.cell(0, 1)!!.text)
        assertEquals(CellKind.Date, s.cell(0, 1)!!.kind)
        assertEquals("26%", s.cell(0, 2)!!.text)
        assertEquals("1,234,567.89", s.cell(0, 3)!!.text)
        assertEquals("2026-11-27 12:00", s.cell(0, 4)!!.text)
    }

    @Test fun rowsAndCellsWithoutAddressesAreCountedInOrder() {
        val wb = read(workbook(listOf("S" to sheet("<row><c><v>1</v></c><c><v>2</v></c></row><row><c r=\"C2\"><v>3</v></c><c><v>4</v></c></row>"))))
        val s = wb.sheets.single()
        assertEquals("1", s.cell(0, 0)!!.text)
        assertEquals("2", s.cell(0, 1)!!.text)
        assertEquals("3", s.cell(1, 2)!!.text)
        assertEquals("4", s.cell(1, 3)!!.text)
    }

    @Test fun sheetsInOrderWithHiddenOnesMarked() {
        val wb = read(workbook(listOf("One" to sheet(""), "Two" to sheet("<row r=\"1\"><c r=\"A1\"><v>2</v></c></row>")), hidden = setOf(1)))
        assertEquals(listOf("One", "Two"), wb.sheets.map { it.name })
        assertFalse(wb.sheets[0].hidden)
        assertTrue(wb.sheets[1].hidden)
        assertEquals(0, wb.sheets[0].rowCount)
    }

    @Test fun mergesWidthsAndProtection() {
        val wb = read(workbook(listOf("S" to sheet(
            "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row>",
            extra = "<sheetProtection sheet=\"1\"/><mergeCells count=\"1\"><mergeCell ref=\"A1:C2\"/></mergeCells>",
            before = "<cols><col min=\"1\" max=\"2\" width=\"20\" customWidth=\"1\"/><col min=\"3\" max=\"3\" width=\"9\" hidden=\"1\"/></cols>",
        )), strings = listOf("Title")))
        val s = wb.sheets.single()
        assertEquals(listOf(CellRange(0, 0, 1, 2)), s.merges)
        assertEquals(20f, s.columnWidths[0])
        assertEquals(20f, s.columnWidths[1])
        assertEquals(0f, s.columnWidths[2])
        assertNotNull(s.readOnly)
        assertNotNull(s.editBlock(5, 5))
    }

    @Test fun tableHeadersAndPivotTablesAreKnown() {
        val table = "<table xmlns=\"${XlsxTestFiles.NS}\" id=\"1\" name=\"T\" ref=\"B2:D9\"/>"
        val wb = read(workbook(
            listOf("T" to sheet("<row r=\"2\"><c r=\"B2\" t=\"inlineStr\"><is><t>Col</t></is></c></row>"), "P" to sheet("")),
            extraParts = mapOf("xl/tables/table1.xml" to table, "xl/pivotTables/p1.xml" to "<pivotTableDefinition/>"),
            sheetRels = mapOf(0 to XlsxTestFiles.tableRels(), 1 to XlsxTestFiles.tableRels("../pivotTables/p1.xml", "pivotTable")),
        ))
        assertEquals(listOf(CellRange(1, 1, 1, 3)), wb.sheets[0].tableHeaders)
        assertNotNull(wb.sheets[0].editBlock(1, 2))
        assertNull(wb.sheets[0].editBlock(2, 2))
        assertNotNull(wb.sheets[1].readOnly)
    }

    @Test fun sharedAndArrayFormulaMastersAreMarked() {
        val wb = read(workbook(listOf("S" to sheet(
            "<row r=\"1\"><c r=\"A1\"><f t=\"shared\" ref=\"A1:A3\" si=\"0\">B1*2</f><v>2</v></c><c r=\"B1\"><f t=\"array\" ref=\"B1\">SUM(C1:C2)</f><v>0</v></c></row>" +
                "<row r=\"2\"><c r=\"A2\"><f t=\"shared\" si=\"0\"/><v>4</v></c></row>",
        ))))
        val s = wb.sheets.single()
        assertTrue(s.cell(0, 0)!!.sharedFormulaMaster)
        assertTrue(s.cell(0, 1)!!.arrayFormula)
        assertFalse(s.cell(1, 0)!!.sharedFormulaMaster)
        assertNotNull(s.editBlock(0, 0))
        assertNotNull(s.editBlock(0, 1))
        assertNull(s.editBlock(1, 0))
    }

    @Test fun date1904Workbooks() {
        val wb = read(workbook(listOf("S" to sheet("<row r=\"1\"><c r=\"A1\" s=\"2\"><v>0</v></c></row>")), workbookPr = "<workbookPr date1904=\"1\"/>"))
        assertTrue(wb.date1904)
        assertEquals("1904-01-01", wb.sheets.single().cell(0, 0)!!.text)
    }

    @Test fun richTextAndPhoneticGuidesInSharedStrings() {
        val bytes = workbook(listOf("S" to sheet("<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c></row>")),
            extraParts = mapOf("xl/sharedStrings.xml" to "<sst xmlns=\"${XlsxTestFiles.NS}\"><si><r><t>Bold</t></r><r><t xml:space=\"preserve\"> plain</t></r><rPh><t>ignored</t></rPh></si></sst>"))
        assertEquals("Bold plain", read(bytes).sheets.single().cell(0, 0)!!.text)
    }

    @Test fun cellLimitStopsReadingAndSaysSo() {
        val rows = (1..50).joinToString("") { "<row r=\"$it\"><c r=\"A$it\"><v>$it</v></c></row>" }
        val s = read(workbook(listOf("S" to sheet(rows))), SheetLimits(maxCells = 10)).sheets.single()
        assertTrue(s.truncated)
        assertEquals(10, s.cells.size)
        // A sheet that wasn't read whole can't be edited: saving would have to rewrite the parts never read.
        assertNotNull(s.editBlock(0, 0))
    }

    @Test fun aFarAwayCellDoesntMakeRowsThousandsWide() {
        val s = read(workbook(listOf("S" to sheet("<row r=\"1\"><c r=\"A1\"><v>1</v></c><c r=\"XFD1\"><v>2</v></c></row>")))).sheets.single()
        assertEquals(256, s.columnCount)
        assertTrue(s.truncated)
    }

    @Test fun refusedFiles() {
        assertTrue(refused(XlsxTestFiles.zip(mapOf("a.txt" to "x"))).contains("Excel"))
        assertTrue(refused("not a zip at all".toByteArray()).contains("valid"))
        val doctype = workbook(listOf("S" to "<!DOCTYPE x [<!ENTITY a \"b\">]>" + sheet("")))
        assertTrue(refused(doctype).contains("unsafe"))
        val broken = workbook(listOf("S" to "<worksheet><sheetData><row>"))
        assertTrue(refused(broken).contains("damaged"))
    }

    @Test fun cellNames() {
        assertEquals("A", CellRefs.columnName(0))
        assertEquals("Z", CellRefs.columnName(25))
        assertEquals("AA", CellRefs.columnName(26))
        assertEquals("XFD", CellRefs.columnName(16383))
        assertEquals(11 to 1, CellRefs.parse("B12"))
        assertEquals(0 to 0, CellRefs.parse("\$A\$1"))
        assertNull(CellRefs.parse("12"))
        assertNull(CellRefs.parse("A0"))
        assertNull(CellRefs.parse("XFE1"))
        assertNull(CellRefs.parse("A1048577"))
        assertEquals(CellRange(0, 0, 2, 1), CellRefs.parseRange("B3:A1"))
        assertEquals("A1:C3", CellRefs.rangeName(CellRange(0, 0, 2, 2)))
    }
}
