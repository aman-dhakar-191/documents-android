package io.github.amandhakar.documents.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val NST = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""

private fun docxWith(body: String, styles: String? = null): DocxDocument {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        z.putNextEntry(ZipEntry("word/document.xml")); z.write("<?xml version=\"1.0\"?><w:document $NST><w:body>$body</w:body></w:document>".toByteArray()); z.closeEntry()
        if (styles != null) { z.putNextEntry(ZipEntry("word/styles.xml")); z.write("<?xml version=\"1.0\"?><w:styles $NST>$styles</w:styles>".toByteArray()); z.closeEntry() }
    }
    return DocxParser.parse(ByteArrayInputStream(out.toByteArray()))
}

private fun cell(text: String, tcPr: String = "") = "<w:tc>" + (if (tcPr.isNotEmpty()) "<w:tcPr>$tcPr</w:tcPr>" else "") + "<w:p><w:r><w:t>$text</w:t></w:r></w:p></w:tc>"
private fun row(vararg cells: String, trPr: String = "") = "<w:tr>" + (if (trPr.isNotEmpty()) "<w:trPr>$trPr</w:trPr>" else "") + cells.joinToString("") + "</w:tr>"
private fun table(rows: String, tblPr: String = "", grid: List<Int>? = null) =
    "<w:tbl>" + (if (tblPr.isNotEmpty()) "<w:tblPr>$tblPr</w:tblPr>" else "") +
        (if (grid != null) "<w:tblGrid>" + grid.joinToString("") { "<w:gridCol w:w=\"$it\"/>" } + "</w:tblGrid>" else "") + rows + "</w:tbl>"
private fun tableOf(doc: DocxDocument) = doc.blocks.filterIsInstance<DocxTable>().first()

private const val NORMAL = "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"

class TableParsingTest {
    @Test fun columnWidthsComeFromTheGrid() {
        val t = tableOf(docxWith(table(row(cell("a"), cell("b")), grid = listOf(2880, 5760))))
        assertEquals(listOf(144f, 288f), t.layout!!.columnWidthsPt)
        assertEquals(listOf(0, 1), t.layout!!.cells[0].map { it.gridStart })
    }

    @Test fun withoutAGridCellWidthsAreUsed() {
        val t = tableOf(docxWith(table(row(cell("a", "<w:tcW w:w=\"1440\" w:type=\"dxa\"/>"), cell("b", "<w:tcW w:w=\"2880\" w:type=\"dxa\"/>")))))
        assertEquals(listOf(72f, 144f), t.layout!!.columnWidthsPt)
    }

    @Test fun withoutAnyWidthsTheColumnsAreLeftForTheLayoutToShare() {
        val t = tableOf(docxWith(table(row(cell("a"), cell("b")))))
        assertEquals(listOf(0f, 0f), t.layout!!.columnWidthsPt)
        assertEquals(listOf(150f, 150f), PageLayout.tableColumnWidths(t.layout!!, 300f))
    }

    @Test fun gridSpanCoversSeveralColumns() {
        val t = tableOf(docxWith(table(row(cell("wide", "<w:gridSpan w:val=\"2\"/>"), cell("c")) + row(cell("a"), cell("b"), cell("c")), grid = listOf(1000, 1000, 1000))))
        val first = t.layout!!.cells[0]
        assertEquals(2, first[0].colSpan)
        assertEquals(2, first[1].gridStart)
    }

    @Test fun verticalMergesAreRecognisedAndHaveNoLineBetween() {
        val borders = "<w:tblBorders><w:top w:val=\"single\" w:sz=\"4\"/><w:bottom w:val=\"single\" w:sz=\"4\"/><w:insideH w:val=\"single\" w:sz=\"4\"/></w:tblBorders>"
        val t = tableOf(docxWith(table(
            row(cell("m", "<w:vMerge w:val=\"restart\"/>"), cell("x")) + row(cell("", "<w:vMerge/>"), cell("y")) + row(cell("z"), cell("w")),
            tblPr = borders, grid = listOf(1000, 1000),
        )))
        val cells = t.layout!!.cells
        assertEquals(VMerge.Restart, cells[0][0].vMerge)
        assertEquals(VMerge.Continue, cells[1][0].vMerge)
        assertNull(cells[0][0].borders.bottom)
        assertNull(cells[1][0].borders.top)
        assertNotNull(cells[1][0].borders.bottom)
        assertNotNull(cells[0][1].borders.bottom) // the neighbour keeps its line
    }

    @Test fun shadingAndVerticalAlignmentAreRead() {
        val t = tableOf(docxWith(table(row(cell("a", "<w:shd w:val=\"clear\" w:fill=\"d9e2f3\"/><w:vAlign w:val=\"center\"/>"), cell("b", "<w:shd w:fill=\"auto\"/>")))))
        assertEquals("D9E2F3", t.layout!!.cells[0][0].shadingHex)
        assertEquals(CellAlign.Center, t.layout!!.cells[0][0].vAlign)
        assertNull(t.layout!!.cells[0][1].shadingHex)
    }

    @Test fun tableBordersApplyOutsideAndInside() {
        val b = "<w:tblBorders><w:top w:val=\"single\" w:sz=\"8\" w:color=\"FF0000\"/><w:left w:val=\"single\" w:sz=\"4\"/><w:bottom w:val=\"single\" w:sz=\"4\"/>" +
            "<w:right w:val=\"single\" w:sz=\"4\"/><w:insideH w:val=\"dashed\" w:sz=\"2\"/><w:insideV w:val=\"nil\"/></w:tblBorders>"
        val t = tableOf(docxWith(table(row(cell("a"), cell("b")) + row(cell("c"), cell("d")), tblPr = b, grid = listOf(1000, 1000))))
        val a = t.layout!!.cells[0][0].borders
        assertEquals(BorderLine(1f, "FF0000"), a.top)
        assertEquals(BorderLine(0.5f), a.left)
        assertNull(a.right) // inside vertical: none
        assertEquals(BorderLine(0.25f), a.bottom) // inside horizontal
        val d = t.layout!!.cells[1][1].borders
        assertEquals(BorderLine(0.5f), d.right)
        assertEquals(BorderLine(0.5f), d.bottom)
    }

    @Test fun aCellsOwnBordersWinAndNoneRemovesALine() {
        val b = "<w:tblBorders><w:top w:val=\"single\" w:sz=\"4\"/><w:left w:val=\"single\" w:sz=\"4\"/></w:tblBorders>"
        val own = "<w:tcBorders><w:top w:val=\"single\" w:sz=\"16\" w:color=\"00FF00\"/><w:left w:val=\"nil\"/></w:tcBorders>"
        val c = tableOf(docxWith(table(row(cell("a", own)), tblPr = b, grid = listOf(1000)))).layout!!.cells[0][0].borders
        assertEquals(BorderLine(2f, "00FF00"), c.top)
        assertNull(c.left)
    }

    @Test fun aTableStyleGivesBordersAndNoParagraphSpacing() {
        val styles = "<w:docDefaults><w:pPrDefault><w:pPr><w:spacing w:after=\"200\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>$NORMAL" +
            "<w:style w:type=\"table\" w:default=\"1\" w:styleId=\"TableNormal\"><w:name w:val=\"Normal Table\"/><w:tblPr><w:tblCellMar><w:left w:w=\"108\"/><w:right w:w=\"108\"/></w:tblCellMar></w:tblPr></w:style>" +
            "<w:style w:type=\"table\" w:styleId=\"TableGrid\"><w:name w:val=\"Table Grid\"/><w:basedOn w:val=\"TableNormal\"/><w:pPr><w:spacing w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/></w:pPr>" +
            "<w:tblPr><w:tblBorders><w:top w:val=\"single\" w:sz=\"4\"/><w:left w:val=\"single\" w:sz=\"4\"/><w:bottom w:val=\"single\" w:sz=\"4\"/><w:right w:val=\"single\" w:sz=\"4\"/><w:insideH w:val=\"single\" w:sz=\"4\"/><w:insideV w:val=\"single\" w:sz=\"4\"/></w:tblBorders></w:tblPr></w:style>"
        val doc = docxWith(table(row(cell("a"), cell("b")), tblPr = "<w:tblStyle w:val=\"TableGrid\"/>", grid = listOf(1000, 1000)) + "<w:p><w:r><w:t>after</w:t></w:r></w:p>", styles)
        val t = tableOf(doc)
        val inside = (t.rows[0][0][0] as DocxParagraph).layout!!
        assertEquals(0f, inside.spaceAfterPt, 0f)
        assertEquals(LineSpacing.Multiple(1f), inside.line)
        assertEquals(BorderLine(0.5f), t.layout!!.cells[0][0].borders.left)
        assertEquals(BorderLine(0.5f), t.layout!!.cells[0][1].borders.right)
        // Outside the table the document's own spacing still applies.
        assertEquals(10f, (doc.blocks.last() as DocxParagraph).layout!!.spaceAfterPt, 0f)
    }

    @Test fun cellMarginsAlignmentAndIndentAreRead() {
        val pr = "<w:jc w:val=\"center\"/><w:tblInd w:w=\"720\" w:type=\"dxa\"/><w:tblCellMar><w:top w:w=\"40\"/><w:left w:w=\"200\"/><w:bottom w:w=\"60\"/><w:right w:w=\"300\"/></w:tblCellMar>"
        val l = tableOf(docxWith(table(row(cell("a")), tblPr = pr, grid = listOf(1000)))).layout!!
        assertEquals(DocxAlign.Center, l.align)
        assertEquals(36f, l.indentPt, 0f)
        assertEquals(2f, l.marginTopPt, 0f)
        assertEquals(10f, l.marginLeftPt, 0f)
        assertEquals(3f, l.marginBottomPt, 0f)
        assertEquals(15f, l.marginRightPt, 0f)
    }

    @Test fun withoutAnyMarginsWordsDefaultApplies() {
        val l = tableOf(docxWith(table(row(cell("a")), grid = listOf(1000)))).layout!!
        assertEquals(5.4f, l.marginLeftPt, 0f)
        assertEquals(0f, l.marginTopPt, 0f)
    }

    @Test fun rowHeightsAreRead() {
        val t = tableOf(docxWith(table(row(cell("a"), trPr = "<w:trHeight w:val=\"720\" w:hRule=\"atLeast\"/>") + row(cell("b")), grid = listOf(1000))))
        assertEquals(listOf(36f, null), t.layout!!.rowMinHeightsPt)
    }

    @Test fun plainTextAndEditingSeeTheSameCells() {
        val doc = docxWith(table(row(cell("a"), cell("b")) + row(cell("c"), cell("d")), grid = listOf(1000, 1000)))
        assertEquals("a\tb\nc\td", doc.plainText())
        assertEquals(4, doc.blocks.filterIsInstance<DocxTable>().single().rows.flatten().size)
    }

    // ---- hostile or odd files ----

    @Test fun aHugeGridSpanAndManyColumnsAreKeptInRange() {
        val t = tableOf(docxWith(table(row(cell("a", "<w:gridSpan w:val=\"99999\"/>")) + row(cell("b")), grid = List(500) { 100 })))
        assertTrue(t.layout!!.columnWidthsPt.size <= 64)
        assertTrue(t.layout!!.cells[0][0].colSpan <= 64)
        assertTrue(PageLayout.tableRowHeights(t, PageSetup(), 400f).all { it > 0f })
    }

    @Test fun absurdWidthsDoNotMakeAnAbsurdTable() {
        val t = tableOf(docxWith(table(row(cell("a"), cell("b")), grid = listOf(999999999, 999999999))))
        val widths = PageLayout.tableColumnWidths(t.layout!!, 400f)
        assertEquals(400f, widths.sum(), 0.5f)
    }

    @Test fun raggedRowsAndEmptyTablesAreFine() {
        val t = tableOf(docxWith(table(row(cell("a"), cell("b"), cell("c")) + row(cell("d")) + "<w:tr/>", grid = listOf(1000, 1000))))
        assertEquals(3, t.rows.size)
        assertEquals(3, t.layout!!.cells.size)
        assertEquals(3, PageLayout.tableRowHeights(t, PageSetup(), 400f).size)
        val empty = tableOf(docxWith("<w:tbl/>"))
        assertEquals(0, empty.rows.size)
        assertEquals(0.0f, PageLayout.tableRowHeights(empty, PageSetup(), 400f).sum(), 0f)
    }

    @Test fun aTableInsideACellIsRead() {
        val inner = table(row(cell("inner")), grid = listOf(1000))
        val t = tableOf(docxWith("<w:tbl><w:tblGrid><w:gridCol w:w=\"3000\"/></w:tblGrid><w:tr><w:tc>$inner<w:p/></w:tc></w:tr></w:tbl>"))
        assertTrue(t.rows[0][0].any { it is DocxTable })
    }

    @Test fun aMissingStyleAndABrokenColourDontStopTheFile() {
        val t = tableOf(docxWith(table(row(cell("a", "<w:shd w:fill=\"zzzzzz\"/><w:tcBorders><w:top w:val=\"single\" w:sz=\"x\" w:color=\"??\"/></w:tcBorders>")), tblPr = "<w:tblStyle w:val=\"Nope\"/>", grid = listOf(1000)), NORMAL))
        assertNull(t.layout!!.cells[0][0].shadingHex)
        assertEquals(BorderLine(0.5f), t.layout!!.cells[0][0].borders.top)
    }
}

class TableLayoutMathTest {
    private val page = PageSetup(500f, 700f, 50f, 50f, 50f, 50f)

    private fun layoutOf(widths: List<Float>, rows: Int, cols: Int, spans: Map<Pair<Int, Int>, Int> = emptyMap()): TableLayout =
        TableLayout(
            columnWidthsPt = widths,
            cells = List(rows) { r ->
                var start = 0
                List(cols) { c ->
                    val span = spans[r to c] ?: 1
                    CellProps(start, span).also { start += span }
                }
            },
            rowMinHeightsPt = List(rows) { null },
        )

    private fun p(text: String) = DocxParagraph(listOf(DocxRun(text)))

    @Test fun unknownColumnsShareWhatsLeft() {
        val l = layoutOf(listOf(100f, 0f, 0f), 1, 3)
        assertEquals(listOf(100f, 100f, 100f), PageLayout.tableColumnWidths(l, 300f))
    }

    @Test fun aTableWiderThanTheTextIsScaledDown() {
        val l = layoutOf(listOf(300f, 300f), 1, 2)
        val w = PageLayout.tableColumnWidths(l, 300f)
        assertEquals(listOf(150f, 150f), w)
    }

    @Test fun aNarrowerTableKeepsItsWidth() {
        val l = layoutOf(listOf(100f, 100f), 1, 2)
        assertEquals(listOf(100f, 100f), PageLayout.tableColumnWidths(l, 300f))
    }

    @Test fun aRowIsAsTallAsItsTallestCell() {
        val l = layoutOf(listOf(200f, 40f), 1, 2)
        val short = p("hi")
        val long = p("word ".repeat(40)) // wraps a lot in a 40 pt column
        val h = PageLayout.tableRowHeights(DocxTable(listOf(listOf(listOf(short), listOf(long))), l), page, 400f)[0]
        val shortOnly = PageLayout.tableRowHeights(DocxTable(listOf(listOf(listOf(short), listOf(short))), l), page, 400f)[0]
        assertTrue(h > shortOnly * 3)
    }

    @Test fun aRowsMinimumHeightIsKept() {
        val l = layoutOf(listOf(200f), 1, 1).copy(rowMinHeightsPt = listOf(80f))
        assertEquals(80f, PageLayout.tableRowHeights(DocxTable(listOf(listOf(listOf(p("x")))), l), page, 400f)[0], 0.01f)
    }

    @Test fun cellMarginsAddToTheHeightAndNarrowTheText() {
        val base = layoutOf(listOf(120f), 1, 1)
        val padded = base.copy(marginTopPt = 5f, marginBottomPt = 5f, marginLeftPt = 20f, marginRightPt = 20f)
        val text = p("word ".repeat(10))
        val a = PageLayout.tableRowHeights(DocxTable(listOf(listOf(listOf(text))), base.copy(marginLeftPt = 0f, marginRightPt = 0f)), page, 400f)[0]
        val b = PageLayout.tableRowHeights(DocxTable(listOf(listOf(listOf(text))), padded), page, 400f)[0]
        assertTrue(b >= a + 10f)
    }

    @Test fun aMergedCellsContinuationAddsNoHeight() {
        val base = layoutOf(listOf(100f, 100f), 2, 2)
        val cells = base.cells.mapIndexed { r, row -> row.mapIndexed { c, cp -> if (r == 1 && c == 0) cp.copy(vMerge = VMerge.Continue) else cp } }
        val l = base.copy(cells = cells)
        val longText = p("word ".repeat(200))
        val rows = listOf(listOf(listOf(p("a")), listOf(p("b"))), listOf(listOf(longText), listOf(p("d"))))
        val h = PageLayout.tableRowHeights(DocxTable(rows, l), page, 400f)
        assertTrue("continuation content must be ignored, row was ${h[1]}", h[1] < 40f)
    }

    @Test fun aSpannedCellUsesTheWidthOfAllItsColumns() {
        val spans = mapOf((0 to 0) to 2)
        val l = layoutOf(listOf(60f, 60f, 60f), 1, 2, spans)
        val text = p("word ".repeat(30))
        val wide = PageLayout.tableRowHeights(DocxTable(listOf(listOf(listOf(text), listOf(p("x")))), l), page, 400f)[0]
        val narrow = PageLayout.tableRowHeights(DocxTable(listOf(listOf(listOf(text), listOf(p("x")))), layoutOf(listOf(60f, 60f, 60f), 1, 2)), page, 400f)[0]
        assertTrue(wide < narrow)
    }

    @Test fun aLongTableSplitsBetweenRowsAndKeepsItsLayoutInSync() {
        val l = layoutOf(listOf(200f), 40, 1)
        val rows = (0 until 40).map { listOf(listOf<DocxBlock>(p("row $it"))) }
        val t = DocxTable(rows, l)
        val cut = PageLayout.splitTable(t, page, 400f, 100f)!!
        assertEquals(40, cut.first.rows.size + cut.second.rows.size)
        assertEquals(cut.first.rows.size, cut.first.layout!!.cells.size)
        assertEquals(cut.second.rows.size, cut.second.layout!!.rowMinHeightsPt.size)
        assertTrue(cut.first.rows.size >= 1)
    }

    @Test fun aTableThatCantBeCutIsNotCut() {
        val l = layoutOf(listOf(200f), 1, 1)
        assertNull(PageLayout.splitTable(DocxTable(listOf(listOf(listOf(p("x")))), l), page, 400f, 1f))
        val two = layoutOf(listOf(200f), 2, 1)
        assertNull(PageLayout.splitTable(DocxTable(List(2) { listOf(listOf<DocxBlock>(p("x"))) }, two), page, 400f, 1f)) // not even one row fits
    }

    @Test fun everyRowOfAPaginatedTableAppearsOnceInOrderInColumnsToo() {
        val l = layoutOf(listOf(100f), 60, 1)
        val rows = (0 until 60).map { listOf(listOf<DocxBlock>(p("r$it"))) }
        val doc = DocxDocument(listOf(DocxColumns(2, 20f), DocxTable(rows, l)), page = page)
        val seen = PageLayout.paginateColumns(doc).flatMap { pg -> pg.bands.flatMap { b -> b.columns.flatMap { c -> c.filterIsInstance<DocxTable>().flatMap { t -> t.rows.map { (it[0][0] as DocxParagraph).text } } } } }
        assertEquals((0 until 60).map { "r$it" }, seen)
    }
}
