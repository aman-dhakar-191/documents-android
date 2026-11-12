package io.github.amandhakar.documents.convert

import io.github.amandhakar.documents.edit.FontStyleChoice
import io.github.amandhakar.documents.edit.PageLines
import io.github.amandhakar.documents.edit.TextLine
import io.github.amandhakar.documents.slides.PptxText
import io.github.amandhakar.documents.word.DocxAlign
import io.github.amandhakar.documents.word.DocxImage
import io.github.amandhakar.documents.word.DocxParagraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun line(text: String, left: Float, baseline: Float, size: Float = 11f, bold: Boolean = false, italic: Boolean = false, width: Float = 400f) =
    TextLine(text, left, baseline - size, left + width, baseline + size * 0.25f, baseline, size, FontStyleChoice(bold = bold, italic = italic))

private fun page(vararg lines: TextLine, width: Float = 595f, height: Float = 842f) = PageLines(width, height, lines.toList())

class PdfParagraphsTest {
    @Test fun wrappedLinesBecomeOneParagraph() {
        val p = PdfParagraphs.fromPage(page(line("The quick brown fox", 72f, 100f), line("jumps over the lazy dog.", 72f, 113f)))
        assertEquals(1, p.size)
        assertEquals("The quick brown fox jumps over the lazy dog.", p[0].text)
    }

    @Test fun aBigGapStartsANewParagraph() {
        val p = PdfParagraphs.fromPage(page(line("First paragraph.", 72f, 100f, width = 90f), line("Second paragraph.", 72f, 135f, width = 90f)))
        assertEquals(listOf("First paragraph.", "Second paragraph."), p.map { it.text })
    }

    @Test fun readingOrderFollowsTheTopOfThePageNotTheOrderGiven() {
        val p = PdfParagraphs.fromPage(page(line("Lower.", 72f, 300f, width = 40f), line("Upper.", 72f, 100f, width = 40f)))
        assertEquals(listOf("Upper.", "Lower."), p.map { it.text })
    }

    @Test fun biggerTextBecomesAHeadingAndKeepsItsSize() {
        val p = PdfParagraphs.fromPage(
            page(
                line("Chapter One", 72f, 100f, size = 24f, bold = true, width = 150f),
                line("Body text follows here in the usual size and carries on for a while so it is the body.", 72f, 140f),
                line("More of the body text on a second line to give it plenty of weight.", 72f, 153f),
            ),
        )
        assertEquals(1, p[0].heading)
        assertEquals(24f, p[0].sizePt, 0f)
        assertEquals(0, p[1].heading)
    }

    @Test fun headingLevelsGoByRatio() {
        assertEquals(1, PdfParagraphs.headingLevel(22f, 11f, false, 1, "x"))
        assertEquals(2, PdfParagraphs.headingLevel(16f, 11f, false, 1, "x"))
        assertEquals(3, PdfParagraphs.headingLevel(13f, 11f, false, 1, "x"))
        assertEquals(0, PdfParagraphs.headingLevel(11f, 11f, false, 1, "x"))
        assertEquals(0, PdfParagraphs.headingLevel(11f, 0f, true, 1, "x"))
    }

    @Test fun aShortBoldLineTheSizeOfTheBodyIsASmallHeading() {
        assertEquals(3, PdfParagraphs.headingLevel(11f, 11f, true, 1, "Introduction"))
        assertEquals(0, PdfParagraphs.headingLevel(11f, 11f, true, 1, "A bold sentence that ends here."))
        assertEquals(0, PdfParagraphs.headingLevel(11f, 11f, true, 2, "Two bold lines"))
    }

    @Test fun bulletsAreFlaggedAndTheirMarkIsRemoved() {
        val p = PdfParagraphs.fromPage(page(line("• First item", 72f, 100f, width = 80f), line("• Second item", 72f, 113f, width = 80f), line("- Third item", 72f, 126f, width = 80f)))
        assertEquals(listOf("First item", "Second item", "Third item"), p.map { it.text })
        assertTrue(p.all { it.bullet })
    }

    @Test fun aBulletFromASymbolFontIsRecognised() {
        val p = PdfParagraphs.fromPage(page(line("\uF0B7 Symbol bullet", 72f, 100f, width = 90f), line("\uF0B7Tight bullet", 72f, 113f, width = 90f)))
        assertEquals(listOf("Symbol bullet", "Tight bullet"), p.map { it.text })
        assertTrue(p.all { it.bullet })
    }

    @Test fun aDashThatIsNotFollowedByASpaceIsNotABullet() {
        val p = PdfParagraphs.fromPage(page(line("-5 degrees tonight", 72f, 100f, width = 90f)))
        assertFalse(p.single().bullet)
        assertEquals("-5 degrees tonight", p.single().text)
    }

    @Test fun aNumberedLineIsKeptAsText() {
        val p = PdfParagraphs.fromPage(page(line("1. Numbered start", 72f, 100f, width = 90f)))
        assertEquals("1. Numbered start", p[0].text)
        assertFalse(p[0].bullet)
    }

    @Test fun aWordSplitWithAHyphenIsJoined() {
        val p = PdfParagraphs.fromPage(page(line("This is a hyphen-", 72f, 100f), line("ated word.", 72f, 113f)))
        assertEquals("This is a hyphenated word.", p[0].text)
    }

    @Test fun aRealHyphenBeforeACapitalIsKept() {
        val p = PdfParagraphs.fromPage(page(line("Rock-", 72f, 100f), line("Paper and scissors", 72f, 113f)))
        assertEquals("Rock- Paper and scissors", p[0].text)
    }

    @Test fun piecesOnTheSameBaselineAreJoinedLeftToRightWithATabWhenFarApart() {
        val p = PdfParagraphs.fromPage(page(line("right", 300f, 100f, width = 40f), line("left", 72f, 100.5f, width = 40f)))
        assertEquals("left\tright", p.single().text)
    }

    @Test fun twoColumnsAreReadOneAfterTheOther() {
        val left = (0 until 5).map { line("L${it + 1}", 50f, 100f + it * 14f, width = 200f) }
        val right = (0 until 5).map { line("R${it + 1}", 320f, 100f + it * 14f, width = 200f) }
        val p = PdfParagraphs.fromPage(page(*(left + right).toTypedArray()))
        assertEquals("L1 L2 L3 L4 L5", p[0].text)
        assertEquals("R1 R2 R3 R4 R5", p[1].text)
    }

    @Test fun aLineAcrossTheMiddleKeepsOneColumn() {
        val body = (0 until 4).map { line("L${it + 1}", 50f, 140f + it * 14f, width = 200f) } + (0 until 4).map { line("R${it + 1}", 320f, 140f + it * 14f, width = 200f) }
        val wide = line("A title across the whole page", 50f, 100f, width = 480f)
        val columns = PdfParagraphs.splitColumns(body + wide, 595f)
        assertEquals(1, columns.size)
    }

    @Test fun fewLinesAreNeverSplitIntoColumns() {
        assertEquals(1, PdfParagraphs.splitColumns(listOf(line("a", 50f, 100f, width = 100f), line("b", 400f, 100f, width = 100f)), 595f).size)
    }

    @Test fun aNarrowLineInTheMiddleIsCentred() {
        val p = PdfParagraphs.fromPage(
            page(
                line("Centred title", 247f, 100f, size = 20f, width = 100f),
                line("Body text runs the full width of the column here and keeps going.", 72f, 160f, width = 451f),
            ),
        )
        assertTrue(p[0].centered)
        assertFalse(p[1].centered)
    }

    @Test fun aFirstLineIndentAfterASentenceStartsAParagraph() {
        val p = PdfParagraphs.fromPage(page(line("The first paragraph ends here.", 72f, 100f, width = 440f), line("Second starts indented.", 90f, 113f, width = 400f)))
        assertEquals(2, p.size)
    }

    @Test fun emptyAndBlankPagesGiveNothing() {
        assertTrue(PdfParagraphs.fromPage(page()).isEmpty())
        assertTrue(PdfParagraphs.fromPage(page(line("   ", 72f, 100f))).isEmpty())
    }

    @Test fun bodySizeIsTheSizeWithTheMostText() {
        val lines = listOf(line("Title", 72f, 50f, size = 30f), line("A long line of ordinary body text here.", 72f, 100f, size = 11f))
        assertEquals(11f, PdfParagraphs.bodySize(lines), 0f)
        assertEquals(11f, PdfParagraphs.bodySize(emptyList()), 0f)
    }

    @Test fun boldAndItalicAreKept() {
        val p = PdfParagraphs.fromPage(page(line("Emphasis here", 72f, 100f, bold = true, italic = true, width = 90f)))
        assertTrue(p[0].bold && p[0].italic)
    }

    @Test fun theParagraphBoxCoversItsLines() {
        val p = PdfParagraphs.fromPage(page(line("One", 72f, 100f, width = 100f), line("two", 80f, 113f, width = 150f)))
        assertEquals(72f, p[0].left, 0f)
        assertEquals(230f, p[0].right, 0.01f)
        assertTrue(p[0].bottom > p[0].top)
    }
}

class PdfLayoutHeuristicsTest {
    @Test fun shortLinesAtTheSameIndentAreSeparateListItems() {
        val p = PdfParagraphs.fromPage(
            page(
                line("A full-width line of ordinary text that runs across the whole column here.", 72f, 100f, width = 450f),
                line("First item", 90f, 120f, width = 60f), line("Second item", 90f, 134f, width = 70f), line("Third item", 90f, 148f, width = 65f),
            ),
        )
        assertEquals(listOf("A full-width line of ordinary text that runs across the whole column here.", "First item", "Second item", "Third item"), p.map { it.text })
    }

    @Test fun aWrappedParagraphWithShortLastLineStaysOneParagraph() {
        val p = PdfParagraphs.fromPage(page(line("A long first line of text that fills the whole width nicely.", 72f, 100f, width = 450f), line("and a shorter end.", 72f, 113f, width = 90f), line("Next paragraph starts after a gap.", 72f, 150f, width = 200f)))
        assertEquals(2, p.size)
        assertEquals("A long first line of text that fills the whole width nicely. and a shorter end.", p[0].text)
    }

    @Test fun tableRowsKeepTheirCellsApartWithTabsAndStaySeparateRows() {
        val p = PdfParagraphs.fromPage(
            page(
                line("Item", 72f, 100f, width = 40f), line("Qty", 200f, 100f, width = 25f), line("Price", 300f, 100f, width = 35f),
                line("Pens", 72f, 114f, width = 30f), line("10", 200f, 114f, width = 15f), line("4.50", 300f, 114f, width = 25f),
            ),
        )
        assertEquals(listOf("Item\tQty\tPrice", "Pens\t10\t4.50"), p.map { it.text })
    }

    @Test fun closePiecesOnOneLineAreJoinedWithASpace() {
        val p = PdfParagraphs.fromPage(page(line("Hello", 72f, 100f, width = 30f), line("world", 106f, 100f, width = 30f)))
        assertEquals("Hello world", p.single().text)
    }

    private fun headed(n: Int, body: String, header: String = "Company report", footer: String? = null) = page(
        line(header, 72f, 40f, width = 120f),
        line(body, 72f, 300f, width = 300f),
        *(listOfNotNull(footer?.let { line(it, 280f, 800f, width = 60f) }).toTypedArray()),
    )

    @Test fun repeatedHeadersAndFootersAreLeftOut() {
        val pages = listOf(headed(1, "Page one body", footer = "Page 1"), headed(2, "Page two body", footer = "Page 2"), headed(3, "Page three body", footer = "Page 3"))
        val texts = PdfParagraphs.withoutRunningLines(pages).map { p -> PdfParagraphs.fromPage(p).map { it.text } }
        assertEquals(listOf(listOf("Page one body"), listOf("Page two body"), listOf("Page three body")), texts)
    }

    @Test fun aLineThatAppearsOnOnlyOnePageIsKept() {
        val pages = listOf(headed(1, "One", header = "Only on page one"), headed(2, "Two", header = "Different header"))
        val texts = PdfParagraphs.withoutRunningLines(pages).map { p -> PdfParagraphs.fromPage(p).map { it.text } }
        assertEquals(listOf("Only on page one", "One"), texts[0])
    }

    @Test fun repeatedTextInTheMiddleOfThePageIsKept() {
        val pages = listOf(page(line("Same line", 72f, 400f, width = 80f)), page(line("Same line", 72f, 400f, width = 80f)))
        assertEquals(2, PdfParagraphs.withoutRunningLines(pages).count { it.lines.isNotEmpty() })
    }

    @Test fun aSinglePageKeepsItsHeaderAndFooter() {
        val pages = listOf(headed(1, "Only page", footer = "Page 1"))
        assertEquals(3, PdfParagraphs.withoutRunningLines(pages).single().lines.size)
    }

    @Test fun conversionsDropRunningLinesToo() {
        val pages = listOf(headed(1, "Body one"), headed(2, "Body two"))
        assertEquals(2, PdfParagraphs.toDocx(pages).blocks.size)
        assertEquals(1, PdfParagraphs.toPptx(pages).slides[0].shapes.size)
    }
}

class PdfToOfficeTest {
    private val pages = listOf(
        page(line("Big title", 72f, 100f, size = 24f, bold = true, width = 150f), line("Body of page one, long enough to be the body text of the page.", 72f, 140f)),
        page(line("• A bullet", 72f, 100f, width = 80f)),
        page(),
        page(line("Back again on page four.", 72f, 100f, width = 150f)),
    )

    @Test fun wordGetsThePdfsPageSizeAndPageBreaks() {
        val doc = PdfParagraphs.toDocx(pages)
        assertEquals(595f, doc.page.widthPt, 0f)
        assertEquals(842f, doc.page.heightPt, 0f)
        val paragraphs = doc.blocks.filterIsInstance<DocxParagraph>()
        assertEquals(1, paragraphs[0].headingLevel)
        assertFalse(paragraphs[0].pageBreakBefore)
        assertTrue(paragraphs[2].pageBreakBefore) // the bullet starts page two
        assertEquals("•", paragraphs[2].list!!.label)
        assertTrue(paragraphs.count { it.pageBreakBefore } == 3) // pages 2, 3 (empty) and 4
    }

    @Test fun anEmptyPageStillTakesAPageInWord() {
        val doc = PdfParagraphs.toDocx(pages)
        assertTrue(doc.blocks.any { it is DocxParagraph && it.text.isEmpty() && it.pageBreakBefore })
    }

    @Test fun centredParagraphsAreCentredInWord() {
        val doc = PdfParagraphs.toDocx(listOf(page(line("Centred title", 247f, 100f, size = 20f, width = 100f), line("Body body body body body body body body.", 72f, 160f, width = 451f))))
        assertEquals(DocxAlign.Center, (doc.blocks[0] as DocxParagraph).align)
    }

    @Test fun noPagesStillGivesAUsableDocument() {
        val doc = PdfParagraphs.toDocx(emptyList())
        assertTrue(doc.blocks.isEmpty())
        assertEquals(595.3f, doc.page.widthPt, 0.01f)
    }

    @Test fun picturesBecomeOnePictureBlockEachInWord() {
        val doc = PdfParagraphs.picturesToDocx(listOf(byteArrayOf(1), byteArrayOf(2)), 595f, 842f)
        assertEquals(2, doc.blocks.filterIsInstance<DocxImage>().size)
        assertEquals(18f, doc.page.marginLeftPt, 0f)
    }

    @Test fun powerPointGetsOneSlidePerPageAtThePageSize() {
        val deck = PdfParagraphs.toPptx(pages)
        assertEquals(4, deck.slides.size)
        assertEquals((595f * 12700).toLong(), deck.widthEmu)
        assertEquals((842f * 12700).toLong(), deck.heightEmu)
        assertTrue(deck.slides[2].shapes.isEmpty())
    }

    @Test fun powerPointTextBoxesSitWhereTheTextWasAndNeverOffThePage() {
        val deck = PdfParagraphs.toPptx(pages)
        for (shape in deck.slides.flatMap { it.shapes }) {
            assertTrue(shape.box.x >= 0 && shape.box.y >= 0 && shape.box.w > 0 && shape.box.h > 0)
        }
        val title = deck.slides[0].shapes.first() as PptxText
        assertEquals(24f, title.paragraphs.single().runs.single().sizePt, 0f)
        assertTrue(title.paragraphs.single().runs.single().bold)
    }

    @Test fun bulletsCarryOverToPowerPoint() {
        val shape = PdfParagraphs.toPptx(pages).slides[1].shapes.single() as PptxText
        assertEquals("•", shape.paragraphs.single().bullet)
        assertEquals("A bullet", shape.paragraphs.single().text)
    }

    @Test fun picturesFillTheSlideInPowerPoint() {
        val deck = PdfParagraphs.picturesToPptx(listOf(byteArrayOf(1)), 842f, 595f)
        assertEquals(1, deck.slides.size)
        val box = deck.slides[0].shapes.single().box
        assertEquals(deck.widthEmu, box.w)
        assertEquals(deck.heightEmu, box.h)
    }
}
