package io.github.amandhakar.documents.sheets

import io.github.amandhakar.documents.officeedit.OfficeEditException
import io.github.amandhakar.documents.sheets.XlsxTestFiles.entries
import io.github.amandhakar.documents.sheets.XlsxTestFiles.sheet
import io.github.amandhakar.documents.sheets.XlsxTestFiles.workbook
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class XlsxEditorTest {
    private val path = "xl/worksheets/sheet1.xml"
    private fun read(bytes: ByteArray) = XlsxParser.parse(ByteArrayInputStream(bytes))

    private fun edit(original: ByteArray, vararg changes: Triple<Int, Int, String>, sheetPath: String = path): Pair<ByteArray, Workbook> {
        val wb = read(original)
        var edits = XlsxEdits()
        for ((r, c, t) in changes) edits = edits.set(sheetPath, r, c, t)
        val out = XlsxEditor.applyToBytes(original, wb, edits)
        val expected = XlsxEditModel.apply(wb, edits)
        assertTrue("the saved file must read back as the preview", XlsxEditor.matches(ByteArrayInputStream(out), expected))
        return out to read(out)
    }

    private fun refused(original: ByteArray, row: Int, col: Int, text: String = "x"): String = try {
        val wb = read(original)
        XlsxEditor.applyToBytes(original, wb, XlsxEdits().set(path, row, col, text))
        fail("expected the edit to be refused")
        ""
    } catch (e: OfficeEditException) {
        e.message ?: ""
    }

    private val basic = workbook(
        listOf(
            "Main" to sheet(
                "<row r=\"1\" spans=\"1:3\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" s=\"4\"><v>10</v></c><c r=\"C1\"><f>B1*2</f><v>20</v></c></row>" +
                    "<row r=\"3\"><c r=\"A3\" s=\"2\"><v>46353</v></c></row>",
                before = "<dimension ref=\"A1:C3\"/>",
            ),
            "Other" to sheet("<row r=\"1\"><c r=\"A1\"><v>7</v></c></row>"),
        ),
        strings = listOf("Item"),
        calcChain = true,
    )

    @Test fun changesTextNumbersAndDatesInPlace() {
        val (_, wb) = edit(basic, Triple(0, 0, "Thing"), Triple(0, 1, "12.5"), Triple(2, 0, "2027-01-15"))
        val s = wb.sheets[0]
        assertEquals("Thing", s.cell(0, 0)!!.text)
        assertEquals("12.50", s.cell(0, 1)!!.text) // keeps the cell's "#,##0.00" look
        assertEquals("2027-01-15", s.cell(2, 0)!!.text)
        assertEquals(CellKind.Date, s.cell(2, 0)!!.kind)
        assertEquals("B1*2", s.cell(0, 2)!!.formula) // the formula next to it is untouched
    }

    @Test fun addsNewCellsAndRowsInOrder() {
        val (bytes, wb) = edit(basic, Triple(1, 3, "new"), Triple(0, 4, "5"), Triple(9, 0, "far"))
        val s = wb.sheets[0]
        assertEquals("new", s.cell(1, 3)!!.text)
        assertEquals("5", s.cell(0, 4)!!.text)
        assertEquals("far", s.cell(9, 0)!!.text)
        val xml = String(entries(bytes).getValue(path))
        assertTrue(xml.indexOf("r=\"2\"") in xml.indexOf("r=\"1\"")..xml.indexOf("r=\"3\""))
        assertTrue(xml.indexOf("r=\"E1\"") > xml.indexOf("r=\"C1\""))
        assertTrue(xml.contains("<dimension ref=\"A1:E10\"/>"))
        assertFalse(xml.contains("spans="))
    }

    @Test fun overwritingAFormulaReplacesItAndEmptyingKeepsTheLook() {
        val (bytes, wb) = edit(basic, Triple(0, 2, "99"), Triple(0, 1, ""))
        val s = wb.sheets[0]
        assertEquals("99", s.cell(0, 2)!!.text)
        assertNull(s.cell(0, 2)!!.formula)
        assertEquals(CellKind.Empty, s.cell(0, 1)!!.kind)
        assertTrue(String(entries(bytes).getValue(path)).contains("<c r=\"B1\" s=\"4\"/>"))
    }

    @Test fun excelIsAskedToRecalculateAndTheCalcChainIsDropped() {
        val (bytes, _) = edit(basic, Triple(0, 1, "3"))
        val e = entries(bytes)
        assertFalse(e.containsKey("xl/calcChain.xml"))
        assertFalse(String(e.getValue("xl/_rels/workbook.xml.rels")).contains("calcChain"))
        assertFalse(String(e.getValue("[Content_Types].xml")).contains("calcChain"))
        assertTrue(String(e.getValue("xl/workbook.xml")).contains("fullCalcOnLoad=\"1\""))
    }

    @Test fun everyOtherPartIsCopiedByteForByte() {
        val (bytes, _) = edit(basic, Triple(0, 1, "3"))
        val before = entries(basic)
        val after = entries(bytes)
        for (name in listOf("xl/worksheets/sheet2.xml", "xl/sharedStrings.xml", "xl/styles.xml", "xl/media/image1.png", "_rels/.rels")) {
            assertArrayEquals(name, before.getValue(name), after.getValue(name))
        }
    }

    @Test fun textIsEscapedAndSpacesKept() {
        val (bytes, wb) = edit(basic, Triple(0, 0, "  <a & b>  "), Triple(1, 0, "two\nlines"), Triple(1, 1, "bad\u0001char"))
        assertEquals("  <a & b>  ", wb.sheets[0].cell(0, 0)!!.text)
        assertEquals("two\nlines", wb.sheets[0].cell(1, 0)!!.text)
        assertEquals("badchar", wb.sheets[0].cell(1, 1)!!.text)
        assertTrue(String(entries(bytes).getValue(path)).contains("xml:space=\"preserve\""))
    }

    @Test fun booleansAndFormulaLookingText() {
        val (_, wb) = edit(basic, Triple(1, 0, "FALSE"), Triple(1, 1, "=A1+1"))
        assertEquals(CellKind.Bool, wb.sheets[0].cell(1, 0)!!.kind)
        assertEquals("=A1+1", wb.sheets[0].cell(1, 1)!!.text)
        assertNull(wb.sheets[0].cell(1, 1)!!.formula)
    }

    @Test fun cellsWithoutAddressesKeepTheirPlaces() {
        val original = workbook(listOf("S" to sheet("<row><c><v>1</v></c><c><v>2</v></c><c><v>3</v></c></row>")))
        val (_, wb) = edit(original, Triple(0, 1, "two"))
        val s = wb.sheets[0]
        assertEquals("1", s.cell(0, 0)!!.text)
        assertEquals("two", s.cell(0, 1)!!.text)
        assertEquals("3", s.cell(0, 2)!!.text)
    }

    @Test fun theLatestChangeToACellWinsAndUndoTakesItBack() {
        var edits = XlsxEdits().set(path, 0, 0, "a").set(path, 0, 0, "b").set(path, 1, 1, "c")
        assertEquals(2, edits.count)
        assertEquals("b", edits.textOf(path, 0, 0))
        edits = edits.undo().undo()
        assertEquals("a", edits.textOf(path, 0, 0))
        assertTrue(edits.undo().isEmpty)
    }

    @Test fun editBoxTextRoundTrips() {
        val wb = read(basic)
        val date = wb.sheets[0].cell(2, 0)
        assertEquals("2026-11-27", XlsxEditModel.editText(date, wb))
        assertEquals("10", XlsxEditModel.editText(wb.sheets[0].cell(0, 1), wb))
        assertEquals("", XlsxEditModel.editText(null, wb))
    }

    @Test fun refusedEdits() {
        val merged = workbook(listOf("S" to sheet("<row r=\"1\"><c r=\"A1\"><v>1</v></c></row>", extra = "<mergeCells><mergeCell ref=\"A1:B2\"/></mergeCells>")))
        assertTrue(refused(merged, 1, 1).contains("merged"))
        val protectedSheet = workbook(listOf("S" to sheet("", extra = "<sheetProtection sheet=\"1\"/>")))
        assertTrue(refused(protectedSheet, 0, 0).contains("protected"))
        val shared = workbook(listOf("S" to sheet("<row r=\"1\"><c r=\"A1\"><f t=\"shared\" ref=\"A1:A2\" si=\"0\">1+1</f><v>2</v></c></row>")))
        assertTrue(refused(shared, 0, 0).contains("formula"))
    }

    @Test fun aSheetThatIsGoneIsReported() {
        val wb = read(basic)
        try {
            XlsxEditor.applyToBytes(basic, wb, XlsxEdits().set("xl/worksheets/nope.xml", 0, 0, "x"))
            fail()
        } catch (e: OfficeEditException) {
            assertTrue(e.message!!.contains("can't be found"))
        }
    }

    @Test fun notAZipIsRefused() {
        val wb = read(basic)
        try {
            XlsxEditor.applyToBytes("plain text".toByteArray(), wb, XlsxEdits().set(path, 0, 0, "x"))
            fail()
        } catch (e: OfficeEditException) {
            assertTrue(e.message!!.isNotEmpty())
        }
    }

    @Test fun aFileThatDoesntReadBackAsThePreviewFailsTheCheck() {
        val wb = read(basic)
        val edits = XlsxEdits().set(path, 0, 0, "changed")
        val expected = XlsxEditModel.apply(wb, edits)
        assertFalse(XlsxEditor.matches(ByteArrayInputStream(basic), expected))
        assertFalse(XlsxEditor.matches(ByteArrayInputStream("junk".toByteArray()), expected))
    }

    @Test fun aWorkbookWithoutCalcPrGetsOneInTheRightPlace() {
        val original = workbook(listOf("S" to sheet("")), extraParts = mapOf())
        val (bytes, _) = edit(original, Triple(0, 0, "1"))
        val xml = String(entries(bytes).getValue("xl/workbook.xml"))
        assertTrue(xml.indexOf("<calcPr") > xml.indexOf("</sheets>"))
    }
}
