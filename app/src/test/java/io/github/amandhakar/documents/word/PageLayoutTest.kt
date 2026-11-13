package io.github.amandhakar.documents.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun para(text: String, size: Int? = null, heading: Int = 0, list: DocxListMark? = null, before: Boolean = false, after: Boolean = false) =
    DocxParagraph(listOf(DocxRun(text, sizeHalfPoints = size)), heading, list, pageBreakBefore = before, pageBreakAfter = after)

private fun doc(vararg blocks: DocxBlock, page: PageSetup = PageSetup()) = DocxDocument(blocks.toList(), page = page)

class PageSetupTest {
    @Test fun defaultsToA4WithOneInchMargins() {
        val p = PageSetup()
        assertEquals(595.3f, p.widthPt, 0.01f)
        assertEquals(841.9f, p.heightPt, 0.01f)
        assertEquals(72f, p.marginLeftPt, 0f)
        assertEquals(595.3f - 144f, p.contentWidthPt, 0.01f)
        assertEquals(841.9f - 144f, p.contentHeightPt, 0.01f)
    }

    @Test fun readsWordsNumbersInTwips() {
        val p = PageSetup.fromTwips(12240, 15840, 1440, 1080, 1440, 1080) // US Letter
        assertEquals(612f, p.widthPt, 0f)
        assertEquals(792f, p.heightPt, 0f)
        assertEquals(54f, p.marginLeftPt, 0f)
        assertEquals(72f, p.marginTopPt, 0f)
    }

    @Test fun landscapeKeepsItsOwnNumbers() {
        val p = PageSetup.fromTwips(16838, 11906, 1440, 1440, 1440, 1440)
        assertTrue(p.widthPt > p.heightPt)
    }

    @Test fun missingSizeFallsBackToA4() {
        val p = PageSetup.fromTwips(null, null, 1440, 1440, 1440, 1440)
        assertEquals(PageSetup.A4_WIDTH_PT, p.widthPt, 0f)
    }

    @Test fun absurdSizesFallBackToA4() {
        assertEquals(PageSetup.A4_WIDTH_PT, PageSetup.fromTwips(10, 10, 0, 0, 0, 0).widthPt, 0f)
        assertEquals(PageSetup.A4_HEIGHT_PT, PageSetup.fromTwips(12240, 9_999_999, 0, 0, 0, 0).heightPt, 0f)
        assertEquals(PageSetup.A4_WIDTH_PT, PageSetup.fromTwips(-5, 15840, 0, 0, 0, 0).widthPt, 0f)
    }

    @Test fun missingOrNegativeMarginsFallBackToOneInch() {
        assertEquals(72f, PageSetup.fromTwips(12240, 15840, null, 1440, 1440, 1440).marginTopPt, 0f)
        assertEquals(72f, PageSetup.fromTwips(12240, 15840, -1, 1440, 1440, 1440).marginLeftPt, 0f)
    }

    @Test fun marginsThatLeaveNoRoomFallBackToOneInch() {
        val p = PageSetup.fromTwips(12240, 15840, 1440, 6000, 1440, 6000)
        assertEquals(72f, p.marginLeftPt, 0f)
        assertTrue(p.contentWidthPt > 100f)
    }

    @Test fun zeroMarginsAreAllowed() {
        val p = PageSetup.fromTwips(12240, 15840, 0, 0, 0, 0)
        assertEquals(612f, p.contentWidthPt, 0f)
    }
}

class ImageSizeTest {
    private fun png(w: Int, h: Int): ByteArray {
        val b = ByteArray(32)
        byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10).copyInto(b)
        for (i in 0..3) {
            b[16 + i] = (w shr (24 - 8 * i)).toByte()
            b[20 + i] = (h shr (24 - 8 * i)).toByte()
        }
        return b
    }

    @Test fun pngSize() = assertEquals(640 to 260, ImageSize.of(png(640, 260)))

    @Test fun gifSize() {
        val b = ByteArray(16)
        "GIF89a".toByteArray().copyInto(b)
        b[6] = 100; b[7] = 0; b[8] = 50; b[9] = 1 // 100 x 306
        assertEquals(100 to 306, ImageSize.of(b))
    }

    @Test fun bmpSize() {
        val b = ByteArray(32)
        b[0] = 'B'.code.toByte(); b[1] = 'M'.code.toByte()
        b[18] = 200.toByte(); b[22] = 0xF0.toByte(); b[23] = 0xFF.toByte(); b[24] = 0xFF.toByte(); b[25] = 0xFF.toByte() // 200 x -16 (top-down)
        assertEquals(200 to 16, ImageSize.of(b))
    }

    @Test fun jpegSizeSkipsOtherSegments() {
        // SOI, an APP0 segment of length 4, then SOF0 with height 300 and width 500.
        val b = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(), 0, 4, 1, 2,
            0xFF.toByte(), 0xC0.toByte(), 0, 11, 8, 1, 0x2C, 1, 0xF4.toByte(), 3, 0, 0, 0,
        )
        assertEquals(500 to 300, ImageSize.of(b))
    }

    @Test fun unknownOrTruncatedDataIsNull() {
        assertNull(ImageSize.of(ByteArray(0)))
        assertNull(ImageSize.of(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)))
        assertNull(ImageSize.of(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertNull(ImageSize.of(png(0, 10)))
        assertNull(ImageSize.of(png(10, 0)))
    }

    @Test fun absurdPngSizesAreNull() = assertNull(ImageSize.of(png(500_000, 10)))
}

class PageLayoutTest {
    private val a4 = PageSetup()

    @Test fun biggerTextAndLongerTextAreTaller() {
        val small = PageLayout.heightOf(para("word ".repeat(10)), a4)
        val long = PageLayout.heightOf(para("word ".repeat(400)), a4)
        val big = PageLayout.heightOf(para("word ".repeat(10), size = 48), a4)
        assertTrue(long > small)
        assertTrue(big > small)
    }

    @Test fun headingsUseTheirOwnSize() {
        assertTrue(PageLayout.fontPt(para("x", heading = 1)) > PageLayout.fontPt(para("x", heading = 2)))
        assertEquals(PageLayout.BODY_PT, PageLayout.fontPt(para("x")), 0f)
        assertEquals(12f, PageLayout.fontPt(para("x", size = 24)), 0f)
    }

    @Test fun anEmptyParagraphStillTakesALine() {
        assertEquals(1, PageLayout.lineCount(para(""), a4.contentWidthPt))
        assertTrue(PageLayout.heightOf(para(""), a4) > 0f)
    }

    @Test fun lineBreaksAddLines() {
        assertEquals(3, PageLayout.lineCount(para("a\nb\nc"), a4.contentWidthPt))
    }

    @Test fun listItemsAreIndentedSoWrapEarlier() {
        val width = a4.contentWidthPt
        val plain = PageLayout.charsPerLine(para("x"), width)
        val nested = PageLayout.charsPerLine(para("x", list = DocxListMark(2, "•")), width)
        assertTrue(nested < plain)
    }

    @Test fun anEmptyDocumentHasOneEmptyPage() {
        val pages = PageLayout.paginate(DocxDocument(emptyList()))
        assertEquals(1, pages.size)
        assertTrue(pages[0].isEmpty())
    }

    @Test fun fewParagraphsShareOnePage() {
        assertEquals(1, PageLayout.paginate(doc(para("a"), para("b"), para("c"))).size)
    }

    @Test fun manyParagraphsMakeSeveralPagesInOrderWithNothingLost() {
        val blocks = (1..200).map { para("Paragraph number $it") }
        val pages = PageLayout.paginate(doc(*blocks.toTypedArray()))
        assertTrue(pages.size > 1)
        assertEquals(blocks.map { it.text }, pages.flatten().map { (it as DocxParagraph).text })
    }

    @Test fun noPageGoesOverItsContentHeight() {
        val blocks = (1..150).map { para("Line $it " + "text ".repeat(it % 30)) }
        val d = doc(*blocks.toTypedArray())
        for (page in PageLayout.paginate(d)) {
            val h = page.sumOf { PageLayout.heightOf(it, d.page).toDouble() }
            assertTrue("page of $h", h <= d.page.contentHeightPt + 0.5)
        }
    }

    @Test fun aBiggerPageHoldsMoreOnEachPage() {
        val blocks = (1..120).map { para("Paragraph number $it") }
        val a4Pages = PageLayout.paginate(doc(*blocks.toTypedArray())).size
        val tall = PageSetup(widthPt = 595f, heightPt = 2000f)
        assertTrue(PageLayout.paginate(doc(*blocks.toTypedArray(), page = tall)).size < a4Pages)
    }

    @Test fun pageBreakBeforeStartsANewPage() {
        val pages = PageLayout.paginate(doc(para("a"), para("b", before = true), para("c")))
        assertEquals(listOf("a"), pages[0].map { (it as DocxParagraph).text })
        assertEquals(listOf("b", "c"), pages[1].map { (it as DocxParagraph).text })
    }

    @Test fun pageBreakBeforeOnTheFirstBlockMakesNoEmptyPage() {
        assertEquals(1, PageLayout.paginate(doc(para("a", before = true), para("b"))).size)
    }

    @Test fun pageBreakAfterEndsThePage() {
        val pages = PageLayout.paginate(doc(para("a", after = true), para("b")))
        assertEquals(2, pages.size)
        assertEquals("b", (pages[1].single() as DocxParagraph).text)
    }

    @Test fun aLongParagraphIsSplitAcrossPagesWithAllItsText() {
        val text = "word ".repeat(3000).trim()
        val pages = PageLayout.paginate(doc(para(text)))
        assertTrue(pages.size > 1)
        val joined = pages.flatten().joinToString("") { (it as DocxParagraph).text }
        assertEquals(text.replace(" ", ""), joined.replace(" ", ""))
    }

    @Test fun everyPieceOfASplitParagraphMakesProgress() {
        val pages = PageLayout.paginate(doc(para("x".repeat(100_000))))
        assertTrue(pages.size > 1)
        assertTrue(pages.all { it.isNotEmpty() })
    }

    @Test fun aTableThatDoesNotFitMovesToTheNextPage() {
        val filler = (1..40).map { para("Filler paragraph $it") }
        val table = DocxTable(listOf(listOf(listOf(para("cell")))))
        val pages = PageLayout.paginate(doc(*filler.toTypedArray(), table))
        assertTrue(pages.flatten().last() is DocxTable)
        assertEquals(1, pages.flatten().count { it is DocxTable })
    }

    @Test fun aTableTallerThanAPageContinuesOnTheNextPagesRowByRow() {
        val rows = (1..500).map { listOf(listOf<DocxBlock>(para("row $it"))) }
        val pages = PageLayout.paginate(doc(DocxTable(rows)))
        assertTrue(pages.size > 1)
        val seen = pages.flatten().filterIsInstance<DocxTable>().flatMap { t -> t.rows.map { ((it[0][0]) as DocxParagraph).text } }
        assertEquals((1..500).map { "row $it" }, seen)
    }

    @Test fun aSingleRowTallerThanAPageIsStillPlacedOnce() {
        val tall = DocxParagraph(listOf(DocxRun("word ".repeat(20000))))
        val pages = PageLayout.paginate(doc(DocxTable(listOf(listOf(listOf<DocxBlock>(tall))))))
        assertEquals(1, pages.flatten().count { it is DocxTable })
    }

    @Test fun picturesAreScaledToThePageAndKeepTheirShape() {
        val png = ByteArray(32).also {
            byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10).copyInto(it)
            it[18] = 0x0F; it[19] = 0xA0.toByte() // 4000 wide
            it[22] = 0x07; it[23] = 0xD0.toByte() // 2000 tall
        }
        val (w, h) = PageLayout.imageBoxPt(DocxImage("x.png", png), a4.contentWidthPt, a4.contentHeightPt)
        assertEquals(a4.contentWidthPt, w, 0.01f)
        assertEquals(w / 2, h, 0.5f)
    }

    @Test fun aVeryTallPictureIsCappedToThePageHeight() {
        val png = ByteArray(32).also {
            byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10).copyInto(it)
            it[19] = 100 // 100 wide
            it[22] = 0x27; it[23] = 0x10 // 10000 tall
        }
        val (w, h) = PageLayout.imageBoxPt(DocxImage("x.png", png), a4.contentWidthPt, a4.contentHeightPt)
        assertEquals(a4.contentHeightPt, h, 0.01f)
        assertTrue(w < a4.contentWidthPt)
    }

    private fun pagePicture(pageBreak: Boolean) = DocxImage("p", ByteArray(32).also {
        byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10).copyInto(it)
        it[18] = 0x04; it[19] = 0xD8.toByte() // 1240 wide
        it[22] = 0x06; it[23] = 0xDA.toByte() // 1754 tall
    }, pageBreak)

    @Test fun fullPagePicturesGetOnePageEachAndNoBlankPages() {
        val setup = PageSetup(595f, 842f, 24f, 24f, 24f, 24f)
        val pages = PageLayout.paginate(DocxDocument((1..3).map { pagePicture(it > 1) }, page = setup))
        assertEquals(3, pages.size)
        assertTrue(pages.all { page -> page.size == 1 && page[0] is DocxImage })
    }

    @Test fun fullPagePicturesStillFitOnePerPageWithoutPageBreakFlags() {
        val setup = PageSetup(595f, 842f, 24f, 24f, 24f, 24f)
        val pages = PageLayout.paginate(DocxDocument((1..3).map { pagePicture(false) }, page = setup))
        assertEquals(3, pages.size)
    }

    @Test fun aPictureWithAPageBreakBeforeStartsANewPage() {
        val pages = PageLayout.paginate(doc(para("a"), pagePicture(true)))
        assertEquals(2, pages.size)
    }

    @Test fun aPictureWithAPageBreakBeforeOnTheFirstPageMakesNoBlankPage() {
        assertEquals(1, PageLayout.paginate(doc(pagePicture(true), para("b"))).size.coerceAtMost(1))
    }

    @Test fun aPictureWithAnUnknownHeaderGetsADefaultShape() {
        val (w, h) = PageLayout.imageBoxPt(DocxImage("x.png", ByteArray(4)), 400f, 600f)
        assertTrue(w > 0 && h > 0)
    }

    @Test fun splittingKeepsRunLooksAndDropsTheBulletOfThePartAfter() {
        val p = DocxParagraph(
            listOf(DocxRun("hello ", bold = true), DocxRun("big world", italic = true)),
            list = DocxListMark(0, "1."), pageBreakAfter = true,
        )
        val (a, b) = PageLayout.split(p, 8)
        assertEquals(p.text, a.text + b.text)
        assertTrue(a.runs.first().bold)
        assertTrue(b.runs.all { it.italic })
        assertEquals("1.", a.list!!.label)
        assertEquals("", b.list!!.label)
        assertFalse(a.pageBreakAfter)
        assertTrue(b.pageBreakAfter)
    }

    @Test fun splitPrefersASpace() {
        val (a, _) = PageLayout.split(para("alpha beta gamma"), 8)
        assertEquals("alpha ", a.text)
    }

    @Test fun splitNeverGivesAnEmptyHalf() {
        val (a, b) = PageLayout.split(para("ab"), 0)
        assertTrue(a.text.isNotEmpty() && b.text.isNotEmpty())
        val (c, d) = PageLayout.split(para("ab"), 99)
        assertTrue(c.text.isNotEmpty() && d.text.isNotEmpty())
    }
}

private const val NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""

private fun docx(body: String): DocxDocument {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        z.putNextEntry(ZipEntry("word/document.xml"))
        z.write("<?xml version=\"1.0\"?><w:document $NS><w:body>$body</w:body></w:document>".toByteArray())
        z.closeEntry()
    }
    return DocxParser.parse(ByteArrayInputStream(out.toByteArray()))
}

class DocxPageParsingTest {
    private val p = "<w:p><w:r><w:t>x</w:t></w:r></w:p>"

    @Test fun noSectionPropertiesMeansA4() {
        val page = docx(p).page
        assertEquals(PageSetup.A4_WIDTH_PT, page.widthPt, 0f)
        assertEquals(72f, page.marginLeftPt, 0f)
    }

    @Test fun readsTheDocumentsOwnPageSizeAndMargins() {
        val sect = "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/><w:pgMar w:top=\"720\" w:right=\"1080\" w:bottom=\"720\" w:left=\"1080\"/></w:sectPr>"
        val page = docx(p + sect).page
        assertEquals(612f, page.widthPt, 0f)
        assertEquals(792f, page.heightPt, 0f)
        assertEquals(36f, page.marginTopPt, 0f)
        assertEquals(54f, page.marginLeftPt, 0f)
    }

    @Test fun badSectionNumbersFallBackToA4() {
        val sect = "<w:sectPr><w:pgSz w:w=\"abc\" w:h=\"-1\"/></w:sectPr>"
        assertEquals(PageSetup.A4_WIDTH_PT, docx(p + sect).page.widthPt, 0f)
    }

    @Test fun pageBreakBeforeIsRead() {
        val xml = "<w:p><w:pPr><w:pageBreakBefore/></w:pPr><w:r><w:t>new page</w:t></w:r></w:p>"
        assertTrue((docx(xml).blocks.single() as DocxParagraph).pageBreakBefore)
        assertFalse((docx(p).blocks.single() as DocxParagraph).pageBreakBefore)
    }

    @Test fun aPageBreakInARunEndsTheParagraphsPage() {
        val xml = "<w:p><w:r><w:t>end</w:t><w:br w:type=\"page\"/></w:r></w:p>"
        val para = docx(xml).blocks.single() as DocxParagraph
        assertTrue(para.pageBreakAfter)
        assertEquals("end", para.text)
    }

    @Test fun aPlainLineBreakIsNotAPageBreak() {
        val xml = "<w:p><w:r><w:t>a</w:t><w:br/><w:t>b</w:t></w:r></w:p>"
        val para = docx(xml).blocks.single() as DocxParagraph
        assertFalse(para.pageBreakAfter)
        assertEquals("a\nb", para.text)
    }

    @Test fun aWholeDocumentWithPageBreaksPaginates() {
        val pages = PageLayout.paginate(docx(p + "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>" + p))
        assertNotNull(pages)
        assertEquals(2, pages.size)
    }
}
