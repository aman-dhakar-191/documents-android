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

private const val NSX = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""

private fun parseWith(body: String, styles: String?): DocxDocument {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        z.putNextEntry(ZipEntry("word/document.xml"))
        z.write("<?xml version=\"1.0\"?><w:document $NSX><w:body>$body</w:body></w:document>".toByteArray())
        z.closeEntry()
        if (styles != null) {
            z.putNextEntry(ZipEntry("word/styles.xml"))
            z.write("<?xml version=\"1.0\"?><w:styles $NSX>$styles</w:styles>".toByteArray())
            z.closeEntry()
        }
    }
    return DocxParser.parse(ByteArrayInputStream(out.toByteArray()))
}

private fun p(text: String, pPr: String = "") = "<w:p>" + (if (pPr.isNotEmpty()) "<w:pPr>$pPr</w:pPr>" else "") + "<w:r><w:t>$text</w:t></w:r></w:p>"
private fun first(doc: DocxDocument) = doc.blocks.filterIsInstance<DocxParagraph>().first()
private fun style(id: String, name: String, props: String, based: String? = null, default: Boolean = false, type: String = "paragraph") =
    "<w:style w:type=\"$type\"" + (if (default) " w:default=\"1\"" else "") + " w:styleId=\"$id\"><w:name w:val=\"$name\"/>" +
        (if (based != null) "<w:basedOn w:val=\"$based\"/>" else "") + props + "</w:style>"

private const val DEFAULTS =
    "<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val=\"22\"/></w:rPr></w:rPrDefault>" +
        "<w:pPrDefault><w:pPr><w:spacing w:after=\"160\" w:line=\"259\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>"

class ParagraphLayoutParsingTest {
    @Test fun noStylesPartMeansNoLayout() {
        assertNull(first(parseWith(p("a"), null)).layout)
    }

    @Test fun docDefaultsAndTheDefaultStyleApplyToPlainParagraphs() {
        val doc = parseWith(p("a"), DEFAULTS + style("Normal", "Normal", "", default = true))
        val l = first(doc).layout!!
        assertEquals(8f, l.spaceAfterPt, 0f)
        assertEquals(0f, l.spaceBeforePt, 0f)
        assertEquals(259 / 240f, (l.line as LineSpacing.Multiple).factor, 0.001f)
        assertEquals(22, l.sizeHalfPoints)
    }

    @Test fun stylesWithoutSpacingMeanNoSpace() {
        val l = first(parseWith(p("a"), style("Normal", "Normal", "", default = true))).layout!!
        assertEquals(0f, l.spaceAfterPt, 0f)
        assertEquals(LineSpacing.Multiple(1f), l.line)
        assertNull(l.sizeHalfPoints)
    }

    @Test fun aStyleChainIsReadFromTheRootDown() {
        val styles = DEFAULTS + style("Normal", "Normal", "", default = true) +
            style("Base", "Base", "<w:pPr><w:spacing w:before=\"240\" w:after=\"60\"/><w:ind w:left=\"720\"/></w:pPr><w:rPr><w:sz w:val=\"28\"/></w:rPr>", based = "Normal") +
            style("Child", "Child", "<w:pPr><w:spacing w:after=\"0\"/></w:pPr>", based = "Base")
        val l = first(parseWith(p("a", "<w:pStyle w:val=\"Child\"/>"), styles)).layout!!
        assertEquals(12f, l.spaceBeforePt, 0f) // from Base
        assertEquals(0f, l.spaceAfterPt, 0f) // Child overrides
        assertEquals(36f, l.leftPt, 0f)
        assertEquals(28, l.sizeHalfPoints)
        assertEquals("Child", l.styleId)
    }

    @Test fun theParagraphsOwnSettingsWinPerAttribute() {
        val styles = DEFAULTS + style("Normal", "Normal", "<w:pPr><w:spacing w:before=\"200\" w:after=\"200\"/></w:pPr>", default = true)
        val l = first(parseWith(p("a", "<w:spacing w:after=\"400\"/>"), styles)).layout!!
        assertEquals(10f, l.spaceBeforePt, 0f)
        assertEquals(20f, l.spaceAfterPt, 0f)
    }

    @Test fun lineRulesAreRead() {
        val styles = style("Normal", "Normal", "", default = true)
        assertEquals(LineSpacing.Exactly(15f), first(parseWith(p("a", "<w:spacing w:line=\"300\" w:lineRule=\"exact\"/>"), styles)).layout!!.line)
        assertEquals(LineSpacing.AtLeast(18f), first(parseWith(p("a", "<w:spacing w:line=\"360\" w:lineRule=\"atLeast\"/>"), styles)).layout!!.line)
        assertEquals(LineSpacing.Multiple(1.5f), first(parseWith(p("a", "<w:spacing w:line=\"360\" w:lineRule=\"auto\"/>"), styles)).layout!!.line)
    }

    @Test fun indentsAndHangingAreRead() {
        val styles = style("Normal", "Normal", "", default = true)
        val l = first(parseWith(p("a", "<w:ind w:left=\"1440\" w:right=\"360\" w:hanging=\"360\"/>"), styles)).layout!!
        assertEquals(72f, l.leftPt, 0f)
        assertEquals(18f, l.rightPt, 0f)
        assertEquals(-18f, l.firstLinePt, 0f)
        val f = first(parseWith(p("a", "<w:ind w:firstLine=\"720\"/>"), styles)).layout!!
        assertEquals(36f, f.firstLinePt, 0f)
    }

    @Test fun autoSpacingIsFourteenPoints() {
        val l = first(parseWith(p("a", "<w:spacing w:beforeAutospacing=\"1\" w:afterAutospacing=\"1\"/>"), style("Normal", "Normal", "", default = true))).layout!!
        assertEquals(14f, l.spaceBeforePt, 0f)
        assertEquals(14f, l.spaceAfterPt, 0f)
    }

    @Test fun absurdNumbersAreKeptInRange() {
        val styles = style("Normal", "Normal", "", default = true)
        val l = first(parseWith(p("a", "<w:spacing w:before=\"99999999\" w:line=\"99999\" w:lineRule=\"auto\"/>"), styles)).layout!!
        assertEquals(1584f, l.spaceBeforePt, 0f)
        assertEquals(5f, (l.line as LineSpacing.Multiple).factor, 0f)
        val bad = first(parseWith(p("a", "<w:spacing w:before=\"-500\" w:after=\"x\" w:line=\"-5\"/>"), styles)).layout!!
        assertEquals(0f, bad.spaceBeforePt, 0f)
        assertEquals(LineSpacing.Multiple(1f), bad.line)
    }

    @Test fun contextualSpacingDropsTheGapBetweenNeighboursOfTheSameStyle() {
        val styles = DEFAULTS + style("Normal", "Normal", "", default = true) +
            style("ListP", "List Paragraph", "<w:pPr><w:spacing w:before=\"100\" w:after=\"100\"/><w:contextualSpacing/></w:pPr>", based = "Normal")
        val body = p("a", "<w:pStyle w:val=\"ListP\"/>") + p("b", "<w:pStyle w:val=\"ListP\"/>") + p("c", "<w:pStyle w:val=\"ListP\"/>") + p("plain")
        val ps = parseWith(body, styles).blocks.filterIsInstance<DocxParagraph>().map { it.layout!! }
        assertEquals(5f, ps[0].spaceBeforePt, 0f) // the first keeps its space above
        assertEquals(0f, ps[0].spaceAfterPt, 0f)
        assertEquals(0f, ps[1].spaceBeforePt, 0f)
        assertEquals(0f, ps[1].spaceAfterPt, 0f)
        assertEquals(0f, ps[2].spaceBeforePt, 0f)
        assertEquals(5f, ps[2].spaceAfterPt, 0f) // the last keeps its space below
    }

    @Test fun contextualSpacingDoesNotApplyBetweenDifferentStyles() {
        val styles = style("Normal", "Normal", "", default = true) +
            style("A", "A", "<w:pPr><w:spacing w:after=\"200\"/><w:contextualSpacing/></w:pPr>", based = "Normal") +
            style("B", "B", "<w:pPr><w:spacing w:before=\"200\"/><w:contextualSpacing/></w:pPr>", based = "Normal")
        val ps = parseWith(p("a", "<w:pStyle w:val=\"A\"/>") + p("b", "<w:pStyle w:val=\"B\"/>"), styles).blocks.filterIsInstance<DocxParagraph>().map { it.layout!! }
        assertEquals(10f, ps[0].spaceAfterPt, 0f)
        assertEquals(10f, ps[1].spaceBeforePt, 0f)
    }

    @Test fun aStyleThatBasesItselfOnItselfDoesNotHangTheParser() {
        val styles = style("Loop", "Loop", "<w:pPr><w:spacing w:after=\"100\"/></w:pPr>", based = "Loop")
        assertEquals(5f, first(parseWith(p("a", "<w:pStyle w:val=\"Loop\"/>"), styles)).layout!!.spaceAfterPt, 0f)
    }

    @Test fun layoutSurvivesEditingAndSplitting() {
        val l = first(parseWith(p("alpha beta gamma delta", "<w:spacing w:before=\"200\"/>"), style("Normal", "Normal", "", default = true)))
        val (a, b) = PageLayout.split(l, 8)
        assertNotNull(a.layout)
        assertEquals(l.layout, b.layout)
    }
}

class ParagraphLayoutPaginationTest {
    private fun para(text: String, layout: ParagraphLayout?) = DocxParagraph(listOf(DocxRun(text)), layout = layout)
    private val page = PageSetup(400f, 300f, 50f, 50f, 50f, 50f)

    @Test fun withoutALayoutTheAppsOwnNumbersApply() {
        val q = para("x", null)
        assertEquals(8f, PageLayout.spaceAfter(q), 0f)
        assertEquals(11f * 1.2f, PageLayout.lineHeightPt(q), 0.001f)
    }

    @Test fun theFilesSpacingReplacesTheAppsNumbers() {
        val q = para("x", ParagraphLayout(spaceBeforePt = 3f, spaceAfterPt = 5f))
        assertEquals(3f, PageLayout.spaceBefore(q), 0f)
        assertEquals(5f, PageLayout.spaceAfter(q), 0f)
    }

    @Test fun lineSpacingScalesOrFixesTheLineHeight() {
        val natural = 11f * 1.2f
        assertEquals(natural * 1.5f, PageLayout.lineHeightPt(para("x", ParagraphLayout(line = LineSpacing.Multiple(1.5f)))), 0.001f)
        assertEquals(20f, PageLayout.lineHeightPt(para("x", ParagraphLayout(line = LineSpacing.Exactly(20f)))), 0.001f)
        assertEquals(natural, PageLayout.lineHeightPt(para("x", ParagraphLayout(line = LineSpacing.AtLeast(5f)))), 0.001f)
        assertEquals(30f, PageLayout.lineHeightPt(para("x", ParagraphLayout(line = LineSpacing.AtLeast(30f)))), 0.001f)
    }

    @Test fun theStylesTextSizeIsUsedWhenRunsDontSayAndRunsWinWhenTheyDo() {
        val styled = para("x", ParagraphLayout(sizeHalfPoints = 28))
        assertEquals(14f, PageLayout.fontPt(styled), 0f)
        assertEquals(14f, PageLayout.runPt(styled, styled.runs[0]), 0f)
        val own = DocxParagraph(listOf(DocxRun("x", sizeHalfPoints = 40)), layout = ParagraphLayout(sizeHalfPoints = 28))
        assertEquals(20f, PageLayout.fontPt(own), 0f)
    }

    @Test fun aHeadingWithoutAStyleSizeKeepsTheAppsHeadingSize() {
        val h = DocxParagraph(listOf(DocxRun("x")), headingLevel = 1, layout = ParagraphLayout())
        assertEquals(PageLayout.headingPt(1), PageLayout.fontPt(h), 0f)
    }

    @Test fun indentsNarrowTheLines() {
        val wide = para("x", ParagraphLayout())
        val narrow = para("x", ParagraphLayout(leftPt = 40f, rightPt = 40f))
        assertTrue(PageLayout.charsPerLine(narrow, 300f) < PageLayout.charsPerLine(wide, 300f))
        assertEquals(40f, PageLayout.indent(narrow), 0f)
        assertEquals(40f, PageLayout.rightIndent(narrow), 0f)
    }

    @Test fun listItemsKeepTheirOwnIndentScheme() {
        val item = DocxParagraph(listOf(DocxRun("x")), list = DocxListMark(0, "•"), layout = ParagraphLayout(leftPt = 99f, firstLinePt = -18f))
        assertEquals(PageLayout.LIST_INDENT, PageLayout.indent(item), 0f)
        assertEquals(0f, PageLayout.firstLineIndent(item), 0f)
    }

    @Test fun moreSpacingMeansFewerParagraphsPerPage() {
        fun perPage(layout: ParagraphLayout): Int =
            PageLayout.paginate(DocxDocument(List(40) { para("P$it", layout) }, page = page))[0].size
        assertTrue(perPage(ParagraphLayout(spaceAfterPt = 20f)) < perPage(ParagraphLayout(spaceAfterPt = 0f)))
        assertTrue(perPage(ParagraphLayout(line = LineSpacing.Multiple(2f))) < perPage(ParagraphLayout()))
    }
}
