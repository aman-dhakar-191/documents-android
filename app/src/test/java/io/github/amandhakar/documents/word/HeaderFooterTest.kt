package io.github.amandhakar.documents.word

import io.github.amandhakar.documents.officeedit.DocxEditModel
import io.github.amandhakar.documents.officeedit.DocxEdits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val NSH = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""

private fun zipOf(parts: Map<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z -> for ((n, c) in parts) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
    return out.toByteArray()
}

private fun hp(vararg runs: String) = "<w:p>${runs.joinToString("")}</w:p>"
private fun t(text: String) = "<w:r><w:t xml:space=\"preserve\">$text</w:t></w:r>"
private fun field(instr: String, result: String = "7") =
    "<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText xml:space=\"preserve\"> $instr </w:instrText></w:r>" +
        "<w:r><w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:t>$result</w:t></w:r><w:r><w:fldChar w:fldCharType=\"end\"/></w:r>"

private fun docWith(
    body: String,
    sect: String,
    headers: Map<String, String> = emptyMap(),
    extra: Map<String, String> = emptyMap(),
): DocxDocument {
    val rels = headers.keys.withIndex().joinToString("") { (i, name) ->
        val type = if (name.contains("header")) "header" else "footer"
        "<Relationship Id=\"rId${i + 10}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/$type\" Target=\"$name\"/>"
    }
    val parts = LinkedHashMap<String, String>()
    parts["word/document.xml"] = "<?xml version=\"1.0\"?><w:document $NSH><w:body>$body<w:sectPr>$sect</w:sectPr></w:body></w:document>"
    parts["word/_rels/document.xml.rels"] = "<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">$rels</Relationships>"
    for ((name, xml) in headers) parts["word/$name"] = xml
    parts.putAll(extra)
    return DocxParser.parse(ByteArrayInputStream(zipOf(parts)))
}

private fun hdr(inner: String) = "<?xml version=\"1.0\"?><w:hdr $NSH>$inner</w:hdr>"
private fun ftr(inner: String) = "<?xml version=\"1.0\"?><w:ftr $NSH>$inner</w:ftr>"
private fun ref(kind: String, id: Int, type: String = "default") = "<w:${kind}Reference w:type=\"$type\" r:id=\"rId$id\"/>"
private fun text(blocks: List<DocxBlock>) = blocks.joinToString("|") { (it as DocxParagraph).text }
private val body1 = "<w:p><w:r><w:t>body</w:t></w:r></w:p>"

class HeaderFooterParsingTest {
    @Test fun aDefaultHeaderAndFooterAreRead() {
        val doc = docWith(body1, ref("header", 10) + ref("footer", 11), mapOf("header1.xml" to hdr(hp(t("Quarterly report"))), "footer1.xml" to ftr(hp(t("Confidential")))))
        assertEquals("Quarterly report", text(doc.headerFooter.header))
        assertEquals("Confidential", text(doc.headerFooter.footer))
        assertFalse(doc.headerFooter.differentFirstPage)
    }

    @Test fun theFirstPageCanHaveItsOwn() {
        val doc = docWith(
            body1, ref("header", 10) + ref("header", 11, "first") + "<w:titlePg/>",
            mapOf("header1.xml" to hdr(hp(t("Running"))), "header2.xml" to hdr(hp(t("Cover")))),
        )
        assertTrue(doc.headerFooter.differentFirstPage)
        assertEquals("Cover", text(doc.headerFooter.headerFor(0)))
        assertEquals("Running", text(doc.headerFooter.headerFor(1)))
    }

    @Test fun withoutTitlePgTheFirstPageHeaderIsNotUsed() {
        val doc = docWith(body1, ref("header", 10) + ref("header", 11, "first"), mapOf("header1.xml" to hdr(hp(t("Running"))), "header2.xml" to hdr(hp(t("Cover")))))
        assertEquals("Running", text(doc.headerFooter.headerFor(0)))
    }

    @Test fun distancesAreRead() {
        val doc = docWith(body1, "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"360\" w:footer=\"540\"/>")
        assertEquals(18f, doc.headerFooter.headerDistancePt, 0f)
        assertEquals(27f, doc.headerFooter.footerDistancePt, 0f)
        assertEquals(36f, docWith(body1, "").headerFooter.headerDistancePt, 0f)
    }

    @Test fun noHeadersMeansNone() {
        val doc = docWith(body1, "")
        assertTrue(doc.headerFooter.header.isEmpty() && doc.headerFooter.footer.isEmpty())
    }

    @Test fun aReferenceToAMissingPartIsIgnored() {
        val doc = docWith(body1, ref("header", 10), emptyMap(), mapOf("word/_rels/document.xml.rels" to "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId10\" Target=\"header9.xml\"/></Relationships>"))
        assertTrue(doc.headerFooter.header.isEmpty())
        assertEquals("body", doc.plainText())
    }

    @Test fun aDamagedHeaderDoesNotStopTheDocument() {
        val doc = docWith(body1, ref("header", 10), mapOf("header1.xml" to "<w:hdr this is not xml"))
        assertTrue(doc.headerFooter.header.isEmpty())
        assertEquals("body", doc.plainText())
    }

    @Test fun aHeaderWithADoctypeIsRefusedLikeAnyOtherPart() {
        val bad = "<?xml version=\"1.0\"?><!DOCTYPE w [<!ENTITY x \"y\">]><w:hdr $NSH>${hp(t("&x;"))}</w:hdr>"
        val doc = docWith(body1, ref("header", 10), mapOf("header1.xml" to bad))
        assertTrue(doc.headerFooter.header.isEmpty())
    }

    @Test fun aHeaderIsNotPartOfTheBodyText() {
        val doc = docWith(body1, ref("header", 10), mapOf("header1.xml" to hdr(hp(t("Running")))))
        assertEquals("body", doc.plainText())
        assertEquals(listOf(0), doc.blocks.filterIsInstance<DocxParagraph>().map { it.id })
    }

    @Test fun headerParagraphsCantBeEditedByTapping() {
        val doc = docWith(body1, ref("header", 10), mapOf("header1.xml" to hdr(hp(t("Running")))))
        assertEquals(-1, (doc.headerFooter.header[0] as DocxParagraph).id)
    }

    @Test fun aHugeHeaderIsCapped() {
        val many = (1..1000).joinToString("") { hp(t("line $it")) }
        val doc = docWith(body1, ref("header", 10), mapOf("header1.xml" to hdr(many)))
        assertTrue(doc.headerFooter.header.size <= 200)
    }

    @Test fun editingKeepsTheHeader() {
        val doc = docWith(body1, ref("header", 10), mapOf("header1.xml" to hdr(hp(t("Running")))))
        val edited = DocxEditModel.apply(doc, DocxEdits().replace(0, "changed"))
        assertEquals("Running", text(edited.headerFooter.header))
        assertEquals("changed", edited.plainText())
    }
}

class PageNumberFieldTest {
    private fun footerRuns(vararg runs: String): List<DocxRun> {
        val doc = docWith(body1, ref("footer", 10), mapOf("footer1.xml" to ftr(hp(*runs))))
        return (doc.headerFooter.footer[0] as DocxParagraph).runs
    }

    @Test fun aPageFieldBecomesAMarkedRun() {
        val runs = footerRuns(t("Page "), field("PAGE"))
        assertEquals(2, runs.size)
        assertEquals(PageField.Page, runs[1].field)
        assertEquals("1", runs[1].text) // the stored result "7" is replaced by a stand-in
    }

    @Test fun pageXOfYReadsInOrder() {
        val runs = footerRuns(t("Page "), field("PAGE"), t(" of "), field("NUMPAGES", "12"))
        assertEquals(listOf(null, PageField.Page, null, PageField.Pages), runs.map { it.field })
        assertEquals(listOf("Page ", "1", " of ", "1"), runs.map { it.text })
    }

    @Test fun aSimpleFieldIsRecognisedToo() {
        val runs = footerRuns("<w:fldSimple w:instr=\" PAGE \\* MERGEFORMAT \"><w:r><w:rPr><w:b/></w:rPr><w:t>3</w:t></w:r></w:fldSimple>")
        assertEquals(PageField.Page, runs.single().field)
        assertTrue(runs.single().bold)
    }

    @Test fun otherFieldsKeepTheirStoredResult() {
        val runs = footerRuns(t("Printed "), field("DATE \\@ \"d MMMM yyyy\"", "5 May 2025"))
        assertEquals("Printed 5 May 2025", runs.joinToString("") { it.text })
        assertTrue(runs.all { it.field == null })
    }

    @Test fun theFieldRunKeepsItsLook() {
        val styled = "<w:r><w:rPr><w:b/><w:sz w:val=\"28\"/></w:rPr><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText>PAGE</w:instrText></w:r>" +
            "<w:r><w:rPr><w:b/><w:sz w:val=\"28\"/></w:rPr><w:fldChar w:fldCharType=\"separate\"/></w:r><w:r><w:rPr><w:b/><w:sz w:val=\"28\"/></w:rPr><w:t>2</w:t></w:r><w:r><w:fldChar w:fldCharType=\"end\"/></w:r>"
        val run = footerRuns(styled).single()
        assertTrue(run.bold)
        assertEquals(28, run.sizeHalfPoints)
    }

    @Test fun aBodyParagraphWithAPageFieldIsNotEditable() {
        val doc = docWith("<w:p>${field("PAGE")}</w:p>", "")
        assertFalse((doc.blocks[0] as DocxParagraph).editable)
        assertEquals(PageField.Page, (doc.blocks[0] as DocxParagraph).runs.single().field)
    }

    @Test fun aBrokenFieldDoesNotSwallowTheRestOfTheText() {
        // begin and instruction but no separate or end: the text after it must survive.
        val runs = footerRuns("<w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText>PAGE</w:instrText></w:r>", t("still here"))
        assertEquals("still here", runs.joinToString("") { it.text })
    }
}

class HeaderFooterLayoutTest {
    private val page = PageSetup(400f, 400f, 50f, 40f, 50f, 40f)
    private fun p(text: String) = DocxParagraph(listOf(DocxRun(text)))
    private fun doc(hf: HeaderFooter, n: Int = 60) = DocxDocument(List(n) { p("P$it") }, page = page, headerFooter = hf)

    @Test fun withoutAHeaderTheMarginsApply() {
        val d = doc(HeaderFooter())
        assertEquals(50f, PageLayout.bodyTopPt(d, 0), 0f)
        assertEquals(50f, PageLayout.bodyBottomPt(d, 0), 0f)
        assertEquals(300f, PageLayout.contentLimit(d, 0), 0f)
    }

    @Test fun aShortHeaderInsideTheMarginChangesNothing() {
        val d = doc(HeaderFooter(header = listOf(p("H")), headerDistancePt = 10f))
        assertEquals(50f, PageLayout.bodyTopPt(d, 0), 0f) // 10 + about 21 is less than 50
    }

    @Test fun aTallHeaderPushesTheBodyDown() {
        val tall = HeaderFooter(header = List(5) { p("line") }, headerDistancePt = 20f)
        val d = doc(tall)
        assertTrue(PageLayout.bodyTopPt(d, 0) > 50f)
        assertTrue(PageLayout.contentLimit(d, 0) < 300f)
    }

    @Test fun aTallFooterPushesTheBodyUp() {
        val d = doc(HeaderFooter(footer = List(5) { p("line") }, footerDistancePt = 20f))
        assertTrue(PageLayout.bodyBottomPt(d, 0) > 50f)
    }

    @Test fun theFirstPageUsesItsOwnHeaderWhenAsked() {
        val hf = HeaderFooter(header = List(6) { p("x") }, firstHeader = emptyList(), differentFirstPage = true, headerDistancePt = 20f)
        val d = doc(hf)
        assertEquals(50f, PageLayout.bodyTopPt(d, 0), 0f)
        assertTrue(PageLayout.bodyTopPt(d, 1) > 50f)
    }

    @Test fun lessRoomMeansFewerLinesPerPageAndMorePages() {
        val plain = PageLayout.paginate(doc(HeaderFooter()))
        val withFooter = PageLayout.paginate(doc(HeaderFooter(footer = List(6) { p("f") }, footerDistancePt = 20f)))
        assertTrue(withFooter.size > plain.size)
        assertEquals(plain.flatten().size, withFooter.flatten().size)
    }

    @Test fun theFirstPageCanHoldMoreWhenItsHeaderIsEmpty() {
        val hf = HeaderFooter(header = List(8) { p("x") }, differentFirstPage = true, headerDistancePt = 20f)
        val pages = PageLayout.paginate(doc(hf))
        assertTrue(pages[0].size > pages[1].size)
    }

    @Test fun columnsFollowTheSameLimits() {
        val d = DocxDocument(listOf<DocxBlock>(DocxColumns(2, 20f)) + List(80) { p("P$it") }, page = page, headerFooter = HeaderFooter(footer = List(6) { p("f") }, footerDistancePt = 20f))
        val without = DocxDocument(d.blocks, page = page)
        assertTrue(PageLayout.paginateColumns(d).size >= PageLayout.paginateColumns(without).size)
    }

    @Test fun anAbsurdHeaderCantEatTheWholePage() {
        val d = doc(HeaderFooter(header = List(500) { p("x") }, footer = List(500) { p("y") }))
        assertTrue(PageLayout.contentLimit(d, 0) >= 36f)
        assertTrue(PageLayout.paginate(d).isNotEmpty())
    }
}
