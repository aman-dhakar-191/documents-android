package io.github.amandhakar.documents.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class XlsxNumbersTest {
    private fun f(raw: String, code: String) = XlsxNumbers.format(raw, NumberFormat(200, code))
    private fun builtIn(raw: String, id: Int) = XlsxNumbers.format(raw, NumberFormat(id))

    @Test fun generalHidesFloatingPointNoise() {
        assertEquals("0.3", XlsxNumbers.general(BigDecimal("0.30000000000000004")))
        assertEquals("42", XlsxNumbers.general(BigDecimal("42.0")))
        assertEquals("-1.5", XlsxNumbers.general(BigDecimal("-1.5")))
        assertEquals("0", XlsxNumbers.general(BigDecimal("0")))
        assertEquals("1.23457E+15", XlsxNumbers.general(BigDecimal("1234567890123456")))
        assertEquals("1E-10", XlsxNumbers.general(BigDecimal("0.0000000001")))
        assertEquals("12345678901", XlsxNumbers.general(BigDecimal("12345678901")))
    }

    @Test fun builtInFormats() {
        assertEquals("3", builtIn("3.4", 1))
        assertEquals("3.40", builtIn("3.4", 2))
        assertEquals("1,234", builtIn("1234.4", 3))
        assertEquals("1,234.50", builtIn("1234.5", 4))
        assertEquals("50%", builtIn("0.5", 9))
        assertEquals("12.35%", builtIn("0.12345", 10))
        assertEquals("1.23E+04", builtIn("12345", 11))
        assertEquals("(1,234)", builtIn("-1234", 37))
        assertEquals("abc", builtIn("abc", 0)) // not a number: shown as stored
    }

    @Test fun customNumberFormats() {
        assertEquals("$1,234.50", f("1234.5", "\"$\"#,##0.00"))
        assertEquals("₹ 99.00", f("99", "[\$₹-4009] 0.00"))
        assertEquals("€5", f("5", "[\$€-407]0"))
        assertEquals("12 kg", f("12", "0 \"kg\""))
        assertEquals("1.5", f("1.5", "0.##"))
        assertEquals("2", f("2", "0.##"))
        assertEquals("007", f("7", "000"))
        assertEquals("1,235K", f("1234567", "#,##0,\"K\""))
        assertEquals("-5.00", f("-5", "0.00"))
        assertEquals("5.00", f("-5", "0.00;[Red]0.00"))
        assertEquals("zero", f("0", "0;-0;\"zero\""))
        assertEquals(".5", f("0.5", "#.0"))
    }

    @Test fun formatsTheAppDoesntUnderstandFallBackToGeneral() {
        assertEquals("0.25", f("0.25", "# ?/?"))
    }

    @Test fun datesAndTimes() {
        assertEquals("2026-11-27", builtIn("46353", 14))
        assertEquals("1900-01-01", builtIn("1", 14))
        assertEquals("1900-02-28", builtIn("59", 14))
        assertEquals("1900-03-01", builtIn("61", 14))
        assertEquals("14:30", builtIn("0.604166666666667", 20))
        assertEquals("2:30 PM", builtIn("0.604166666666667", 18))
        assertEquals("2026-11-27 14:30", f("46353.6041666667", "dd/mm/yyyy hh:mm"))
        assertEquals("26:00:00", builtIn("1.0833333333", 46))
        assertEquals("1904-01-02", XlsxNumbers.format("1", NumberFormat(14), date1904 = true))
        assertTrue(XlsxNumbers.isDate(NumberFormat(14)))
        assertTrue(XlsxNumbers.isDate(NumberFormat(165, "d-mmm")))
        assertTrue(XlsxNumbers.isDate(NumberFormat(165, "[h]:mm")))
        assertFalse(XlsxNumbers.isDate(NumberFormat(165, "0.00")))
        assertFalse(XlsxNumbers.isDate(NumberFormat(165, "\"day\" 0")))
        assertFalse(XlsxNumbers.isDate(NumberFormat(0)))
    }

    @Test fun whatTypingStores() {
        val general = NumberFormat.GENERAL
        val date = NumberFormat(14)
        assertEquals(CellInput.Empty, XlsxNumbers.interpret("", general))
        assertEquals(CellInput.Number("12.5"), XlsxNumbers.interpret("12.5", general))
        assertEquals(CellInput.Number("-3"), XlsxNumbers.interpret("-3", general))
        assertEquals(CellInput.Number("1000"), XlsxNumbers.interpret("1e3", general))
        assertEquals(CellInput.Number("5"), XlsxNumbers.interpret("+5.00", general))
        assertEquals(CellInput.Number("0.12"), XlsxNumbers.interpret("12%", general))
        assertEquals(CellInput.Bool(true), XlsxNumbers.interpret("true", general))
        assertEquals(CellInput.Text("hello"), XlsxNumbers.interpret("hello", general))
        assertEquals(CellInput.Text("=SUM(A1:A3)"), XlsxNumbers.interpret("=SUM(A1:A3)", general))
        assertEquals(CellInput.Text(" 5"), XlsxNumbers.interpret(" 5", general))
        assertEquals(CellInput.Text("1,234"), XlsxNumbers.interpret("1,234", general))
        assertEquals(CellInput.Text("2026-11-27"), XlsxNumbers.interpret("2026-11-27", general)) // not a date cell
        assertEquals(CellInput.Number("46353"), XlsxNumbers.interpret("2026-11-27", date))
        assertEquals(CellInput.Number("46353.5"), XlsxNumbers.interpret("2026-11-27 12:00", date))
        assertEquals(CellInput.Number("0.5"), XlsxNumbers.interpret("12:00", NumberFormat(20)))
        assertEquals(CellInput.Text("2026-02-30"), XlsxNumbers.interpret("2026-02-30", date))
        assertEquals(CellInput.Number("1"), XlsxNumbers.interpret("1900-01-01", date))
        assertEquals(CellInput.Number("61"), XlsxNumbers.interpret("1900-03-01", date))
    }

    @Test fun aTypedDateShowsAsTheSameDate() {
        val date = NumberFormat(14)
        val stored = XlsxNumbers.interpret("2031-05-09", date) as CellInput.Number
        assertEquals("2031-05-09", XlsxNumbers.format(stored.value, date))
    }
}
