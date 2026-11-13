package io.github.amandhakar.documents.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val NSW = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""

private fun docxOf(body: String): DocxDocument {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        z.putNextEntry(ZipEntry("word/document.xml"))
        z.write("<?xml version=\"1.0\"?><w:document $NSW><w:body>$body</w:body></w:document>".toByteArray())
        z.closeEntry()
    }
    return DocxParser.parse(ByteArrayInputStream(out.toByteArray()))
}

private fun para(text: String, props: String = "") = "<w:p>$props<w:r><w:t>$text</w:t></w:r></w:p>"
private fun cols(num: Int, space: Int? = 720, type: String? = null) =
    "<w:sectPr>" + (if (type != null) "<w:type w:val=\"$type\"/>" else "") + "<w:cols w:num=\"$num\"" + (if (space != null) " w:space=\"$space\"" else "") + "/></w:sectPr>"

class DocxColumnsParsingTest {
    @Test fun aSingleColumnDocumentHasNoMarkers() {
        val doc = docxOf(para("a") + para("b") + "<w:sectPr/>")
        assertTrue(doc.blocks.none { it is DocxColumns })
    }

    @Test fun aTwoColumnBodyStartsWithAMarker() {
        val doc = docxOf(para("a") + para("b") + cols(2, 360))
        assertEquals(DocxColumns(2, 18f), doc.blocks[0])
        assertEquals(3, doc.blocks.size)
    }

    @Test fun aTitleThenTwoColumnsGetsAContinuousMarkerBetween() {
        val titleWithSection = "<w:p><w:pPr><w:sectPr/></w:pPr><w:r><w:t>Title</w:t></w:r></w:p>"
        val doc = docxOf(titleWithSection + para("left") + para("right") + cols(2, 720, "continuous"))
        assertEquals("Title", (doc.blocks[0] as DocxParagraph).text)
        assertEquals(DocxColumns(2, 36f, newPage = false), doc.blocks[1])
        assertEquals(4, doc.blocks.size)
    }

    @Test fun aSectionThatStartsOnANewPageSaysSo() {
        val first = "<w:p><w:pPr><w:sectPr/></w:pPr><w:r><w:t>One</w:t></w:r></w:p>"
        val doc = docxOf(first + para("two") + cols(2, 720, "nextPage"))
        assertTrue((doc.blocks[1] as DocxColumns).newPage)
    }

    @Test fun backToOneColumnAddsAMarker() {
        val twoCol = "<w:p><w:pPr>${cols(2)}</w:pPr><w:r><w:t>cols</w:t></w:r></w:p>"
        val doc = docxOf(twoCol + para("full") + "<w:sectPr/>")
        assertEquals(DocxColumns(2, 36f), doc.blocks[0])
        assertEquals(DocxColumns(1, 36f, newPage = true), doc.blocks[2])
    }

    @Test fun theColumnCountAndGapAreKeptInRange() {
        val doc = docxOf(para("a") + cols(9, 99999))
        val marker = doc.blocks[0] as DocxColumns
        assertEquals(4, marker.count)
        assertEquals(72f, marker.gapPt, 0f)
    }

    @Test fun aMissingGapIsHalfAnInch() {
        val doc = docxOf(para("a") + cols(2, null))
        assertEquals(36f, (doc.blocks[0] as DocxColumns).gapPt, 0f)
    }

    @Test fun aColumnBreakIsRemembered() {
        val doc = docxOf("<w:p><w:r><w:t>end of column</w:t></w:r><w:r><w:br w:type=\"column\"/></w:r></w:p>" + para("next") + cols(2))
        val first = doc.blocks[1] as DocxParagraph
        assertTrue(first.columnBreakAfter)
        assertEquals("end of column", first.text)
        assertFalse((doc.blocks[2] as DocxParagraph).columnBreakAfter)
    }

    @Test fun markersAreNotText() {
        val doc = docxOf(para("a") + para("b") + cols(2))
        assertEquals("a\nb", doc.plainText())
    }

    @Test fun paragraphIdsIgnoreTheMarkers() {
        val doc = docxOf(para("a") + para("b") + cols(2))
        assertEquals(listOf(0, 1), doc.blocks.filterIsInstance<DocxParagraph>().map { it.id })
    }
}

class PaginateColumnsTest {
    // Content 300 x 200 pt; a one-line paragraph is about 21 pt tall, so 9 fit a column.
    private val page = PageSetup(400f, 300f, 50f, 50f, 50f, 50f)
    private fun p(n: Int) = DocxParagraph(listOf(DocxRun("P$n")))
    private fun doc(vararg blocks: DocxBlock) = DocxDocument(blocks.toList(), page = page)
    private fun texts(column: List<DocxBlock>) = column.map { (it as DocxParagraph).text }

    @Test fun withoutColumnsItIsTheOldPagination() {
        val d = doc(*Array(30) { p(it) })
        val old = PageLayout.paginate(d)
        val new = PageLayout.paginateColumns(d)
        assertEquals(old.size, new.size)
        for (i in old.indices) {
            assertEquals(1, new[i].bands.size)
            assertEquals(texts(old[i]), texts(new[i].bands[0].columns[0]))
        }
    }

    @Test fun textFillsTheFirstColumnThenTheSecondThenTheNextPage() {
        val d = doc(DocxColumns(2, 20f), *Array(40) { p(it) })
        val pages = PageLayout.paginateColumns(d)
        val band = pages[0].bands.single()
        assertEquals(2, band.columns.size)
        assertEquals("P0", texts(band.columns[0]).first())
        // The second column continues exactly where the first stopped.
        assertEquals("P${band.columns[0].size}", texts(band.columns[1]).first())
        // More text than fits a page goes to a second page.
        assertTrue(pages.size >= 2)
        assertEquals("P${band.columns[0].size + band.columns[1].size}", texts(pages[1].bands.single().columns[0]).first())
    }

    @Test fun everyBlockAppearsOnceInOrder() {
        val d = doc(DocxColumns(2, 20f), *Array(55) { p(it) })
        val all = PageLayout.paginateColumns(d).flatMap { pg -> pg.bands.flatMap { b -> b.columns.flatMap { texts(it) } } }
        assertEquals((0 until 55).map { "P$it" }, all)
    }

    @Test fun aColumnBreakStartsTheNextColumnEarly() {
        val d = doc(DocxColumns(2, 20f), p(0).copy(columnBreakAfter = true), p(1), p(2))
        val band = PageLayout.paginateColumns(d).single().bands.single()
        assertEquals(listOf("P0"), texts(band.columns[0]))
        assertEquals(listOf("P1", "P2"), texts(band.columns[1]))
    }

    @Test fun aColumnBreakInTheLastColumnStartsANewPage() {
        val d = doc(DocxColumns(2, 20f), p(0).copy(columnBreakAfter = true), p(1).copy(columnBreakAfter = true), p(2))
        val pages = PageLayout.paginateColumns(d)
        assertEquals(2, pages.size)
        assertEquals(listOf("P2"), texts(pages[1].bands.single().columns[0]))
    }

    @Test fun aFullWidthTitleSitsAboveTheColumns() {
        val d = doc(p(0), DocxColumns(2, 20f), p(1), p(2).copy(columnBreakAfter = true), p(3))
        val page0 = PageLayout.paginateColumns(d).single()
        assertEquals(2, page0.bands.size)
        assertEquals(1, page0.bands[0].columns.size)
        assertEquals(listOf("P0"), texts(page0.bands[0].columns[0]))
        assertEquals(2, page0.bands[1].columns.size)
        assertEquals(listOf("P1", "P2"), texts(page0.bands[1].columns[0]))
        assertEquals(listOf("P3"), texts(page0.bands[1].columns[1]))
    }

    @Test fun columnsUnderATitleHaveLessRoom() {
        val lone = PageLayout.paginateColumns(doc(DocxColumns(2, 20f), *Array(12) { p(it) })).single().bands.single().columns[0].size
        val d = doc(p(100), DocxColumns(2, 20f), *Array(12) { p(it) })
        val underTitle = PageLayout.paginateColumns(d).single().bands[1].columns[0].size
        assertTrue(underTitle < lone)
    }

    @Test fun backToOneColumnContinuesBelowTheColumns() {
        val d = doc(DocxColumns(2, 20f), p(0), p(1), DocxColumns(1, 0f), p(2))
        val bands = PageLayout.paginateColumns(d).single().bands
        assertEquals(2, bands.size)
        assertEquals(2, bands[0].columns.size)
        assertEquals(listOf("P2"), texts(bands[1].columns[0]))
    }

    @Test fun aSectionOnANewPageStartsOne() {
        val d = doc(p(0), DocxColumns(2, 20f, newPage = true), p(1))
        val pages = PageLayout.paginateColumns(d)
        assertEquals(2, pages.size)
        assertEquals(listOf("P1"), texts(pages[1].bands.single().columns[0]))
    }

    @Test fun pageBreakBeforeStillStartsANewPage() {
        val d = doc(DocxColumns(2, 20f), p(0), p(1).copy(pageBreakBefore = true))
        assertEquals(2, PageLayout.paginateColumns(d).size)
    }

    @Test fun moreColumnsThanFitAreDropped() {
        val narrow = PageSetup(120f, 300f, 50f, 40f, 50f, 40f) // 40 pt of text
        assertEquals(1, PageLayout.effectiveColumns(3, 20f, narrow))
        assertEquals(2, PageLayout.effectiveColumns(2, 4f, PageSetup(200f, 300f, 50f, 40f, 50f, 40f)))
        assertEquals(4, PageLayout.effectiveColumns(9, 10f, PageSetup()))
    }

    @Test fun columnWidthsShareTheTextWidth() {
        assertEquals(300f, PageLayout.columnWidthPt(1, 20f, page), 0f)
        assertEquals(140f, PageLayout.columnWidthPt(2, 20f, page), 0.01f)
    }

    @Test fun aHugeBlockIsStillPlacedAndNeverLoopsForever() {
        val tall = DocxParagraph(listOf(DocxRun("x ".repeat(5000))))
        val pages = PageLayout.paginateColumns(doc(DocxColumns(3, 10f), tall, p(1)))
        assertTrue(pages.isNotEmpty())
        val all = pages.flatMap { pg -> pg.bands.flatMap { b -> b.columns.flatMap { c -> c } } }.joinToString("") { (it as DocxParagraph).text }
        assertTrue(all.endsWith("P1"))
    }

    @Test fun anEmptyDocumentIsOneEmptyPage() {
        assertEquals(1, PageLayout.paginateColumns(doc()).size)
        assertEquals(1, PageLayout.paginateColumns(doc(DocxColumns(2, 20f))).size)
    }
}
