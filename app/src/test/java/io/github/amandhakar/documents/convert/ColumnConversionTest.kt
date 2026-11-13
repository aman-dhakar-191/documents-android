package io.github.amandhakar.documents.convert

import io.github.amandhakar.documents.edit.FontStyleChoice
import io.github.amandhakar.documents.edit.PageLines
import io.github.amandhakar.documents.edit.TextLine
import io.github.amandhakar.documents.word.DocxColumns
import io.github.amandhakar.documents.word.DocxDocument
import io.github.amandhakar.documents.word.DocxParagraph
import io.github.amandhakar.documents.word.DocxParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

private fun ln(text: String, left: Float, baseline: Float, size: Float = 11f, bold: Boolean = false, width: Float = 200f) =
    TextLine(text, left, baseline - size, left + width, baseline + size * 0.25f, baseline, size, FontStyleChoice(bold = bold))

/** A column of [count] wrapped lines (one paragraph per [per] lines) starting at [top]. */
private fun column(tag: String, left: Float, top: Float, count: Int, per: Int = 4, width: Float = 200f): List<TextLine> =
    (0 until count).map { i ->
        val paragraph = i / per
        val row = i % per
        ln("$tag${paragraph + 1} line ${row + 1} of the text goes here", left, top + paragraph * (per * 13f + 14f) + row * 13f, width = width)
    }

private fun pageOf(lines: List<TextLine>) = PageLines(595f, 842f, lines)

private fun write(doc: DocxDocument): DocxDocument {
    val out = ByteArrayOutputStream()
    DocxWriter.write(doc, out)
    return DocxParser.parse(ByteArrayInputStream(out.toByteArray()))
}

class ColumnConversionTest {
    private val twoColumns = column("L", 72f, 150f, 12) + column("R", 323f, 150f, 12)

    @Test fun aTitleAcrossThePageSitsAboveTwoColumns() {
        val title = ln("A Wide Title Across Both Columns Of The Whole Page Here", 72f, 100f, size = 22f, bold = true, width = 451f)
        val paragraphs = PdfParagraphs.fromPage(pageOf(listOf(title) + twoColumns))
        assertEquals("A Wide Title Across Both Columns Of The Whole Page Here", paragraphs[0].text)
        assertEquals(1, paragraphs[0].columns)
        val body = paragraphs.drop(1)
        assertTrue(body.all { it.columns == 2 && it.segment == 1 })
        // Left column first, then the right one.
        val order = body.map { it.column }
        assertEquals(order.sorted(), order)
        assertTrue(body.first().text.startsWith("L1"))
        assertTrue(body.last().text.startsWith("R"))
    }

    @Test fun theGapIsTheSpaceBetweenTheColumns() {
        val paragraphs = PdfParagraphs.fromPage(pageOf(twoColumns))
        assertTrue(paragraphs.all { it.columns == 2 })
        assertEquals(51f, paragraphs.first().gapPt, 3f)
    }

    @Test fun threeColumnsAreReadOneAfterAnother() {
        val three = column("A", 60f, 120f, 12, width = 150f) + column("B", 223f, 120f, 12, width = 150f) + column("C", 386f, 120f, 12, width = 150f)
        val paragraphs = PdfParagraphs.fromPage(pageOf(three))
        assertTrue(paragraphs.all { it.columns == 3 })
        val tags = paragraphs.map { it.text.first() }
        assertEquals(tags.sorted(), tags)
        assertEquals(listOf('A', 'B', 'C'), tags.distinct())
    }

    @Test fun aSingleColumnPageStaysOneColumn() {
        val single = column("S", 72f, 100f, 16, width = 451f)
        assertTrue(PdfParagraphs.fromPage(pageOf(single)).all { it.columns == 1 })
    }

    @Test fun aSingleColumnPageWithShortLinesIsNotMistakenForColumns() {
        val lines = column("S", 72f, 100f, 16, width = 451f) + listOf(ln("short end", 72f, 400f, width = 60f), ln("another short", 72f, 413f, width = 80f), ln("third short", 72f, 426f, width = 70f))
        assertTrue(PdfParagraphs.fromPage(pageOf(lines)).all { it.columns == 1 })
    }

    @Test fun aWideLineInTheMiddleSplitsTheColumnsIntoTwoStretches() {
        val upper = column("U", 72f, 100f, 8) + column("V", 323f, 100f, 8)
        val wide = ln("A figure caption that runs across the whole width of the page here", 72f, 260f, width = 451f)
        val lower = column("X", 72f, 290f, 8) + column("Y", 323f, 290f, 8)
        val paragraphs = PdfParagraphs.fromPage(pageOf(upper + wide + lower))
        val segments = paragraphs.map { it.segment }.distinct()
        assertEquals(3, segments.size)
        val order = paragraphs.map { it.text.first() }.joinToString("").replace(Regex("(.)\\1+"), "$1")
        assertEquals("UVAXY", order)
    }

    @Test fun docxGetsAColumnSectionAndColumnBreaks() {
        val doc = PdfParagraphs.toDocx(listOf(pageOf(twoColumns)))
        val marker = doc.blocks.first() as DocxColumns
        assertEquals(2, marker.count)
        val paragraphs = doc.blocks.filterIsInstance<DocxParagraph>()
        assertEquals(1, paragraphs.count { it.columnBreakAfter })
        // The break is after the last paragraph of the left column.
        val broken = paragraphs.indexOfFirst { it.columnBreakAfter }
        assertTrue(paragraphs[broken].text.startsWith("L"))
        assertTrue(paragraphs[broken + 1].text.startsWith("R"))
    }

    @Test fun aWrittenFileReadsBackWithTheSameColumns() {
        val title = ln("Title Across The Whole Page For This Article Here Now", 72f, 100f, size = 22f, bold = true, width = 451f)
        val original = PdfParagraphs.toDocx(listOf(pageOf(listOf(title) + twoColumns)))
        val back = write(original)
        assertEquals(original.blocks.map { kind(it) }, back.blocks.map { kind(it) })
        val marker = back.blocks.filterIsInstance<DocxColumns>().single()
        assertEquals(2, marker.count)
        assertEquals(original.blocks.filterIsInstance<DocxColumns>().single().gapPt, marker.gapPt, 1f)
        assertEquals(original.plainText(), back.plainText())
    }

    @Test fun pagesThatChangeLayoutGetMarkersWhereItChanges() {
        val one = pageOf(column("S", 72f, 100f, 16, width = 451f))
        val two = pageOf(twoColumns)
        val three = pageOf(column("T", 72f, 100f, 16, width = 451f))
        val back = write(PdfParagraphs.toDocx(listOf(one, two, three)))
        assertEquals(listOf(2, 1), back.blocks.filterIsInstance<DocxColumns>().map { it.count })
        assertEquals(3, back.blocks.count { it is DocxParagraph && it.pageBreakBefore } + 1)
    }

    @Test fun aSinglePageOneColumnDocumentHasNoSections() {
        val doc = PdfParagraphs.toDocx(listOf(pageOf(column("S", 72f, 100f, 16, width = 451f))))
        assertTrue(doc.blocks.none { it is DocxColumns })
        assertFalse(doc.blocks.filterIsInstance<DocxParagraph>().any { it.columnBreakAfter })
        assertNotNull(write(doc))
    }

    private fun kind(b: Any) = when (b) {
        is DocxColumns -> "cols${b.count}"
        is DocxParagraph -> "p:" + b.text + ":" + b.columnBreakAfter
        else -> "other"
    }
}

class ColumnConversionHardCasesTest {
    private val twoColumns = column("L", 72f, 170f, 12) + column("R", 323f, 170f, 12)

    @Test fun aCentredTitleAcrossTheGapDoesNotStopColumnDetection() {
        val title = ln("Newsletter", 232f, 100f, size = 28f, bold = true, width = 130f)
        val paragraphs = PdfParagraphs.fromPage(pageOf(listOf(title) + twoColumns))
        assertEquals("Newsletter", paragraphs[0].text)
        assertEquals(1, paragraphs[0].columns)
        assertTrue(paragraphs.drop(1).all { it.columns == 2 })
    }

    @Test fun aHeadingInsideOneColumnStaysInThatColumn() {
        val heading = ln("Section One", 72f, 160f, size = 15f, bold = true, width = 90f)
        val paragraphs = PdfParagraphs.fromPage(pageOf(listOf(heading) + twoColumns))
        val h = paragraphs.first { it.text == "Section One" }
        assertEquals(2, h.columns)
        assertEquals(0, h.column)
    }

    @Test fun aWrappedCaptionKeepsItsLastLineWithTheCaption() {
        val upper = column("U", 72f, 100f, 8) + column("V", 323f, 100f, 8)
        val italic = FontStyleChoice(italic = true)
        fun cap(text: String, baseline: Float, width: Float) = TextLine(text, 72f, baseline - 9.5f, 72f + width, baseline + 2f, baseline, 9.5f, italic)
        val caption = listOf(cap("Figure 1. Deliveries per month across all suppliers for the full year under review, with the", 260f, 451f), cap("planned figure.", 272f, 80f))
        val lower = column("X", 72f, 310f, 8) + column("Y", 323f, 310f, 8)
        val paragraphs = PdfParagraphs.fromPage(pageOf(upper + caption + lower))
        val figure = paragraphs.first { it.text.startsWith("Figure 1.") }
        assertTrue(figure.text.endsWith("planned figure."))
        assertEquals(1, figure.columns)
        assertTrue(paragraphs.none { it.text.startsWith("planned") })
    }

    @Test fun firstLinesOfBothColumnsOnTheSameLevelAreNotTakenForACaptionTail() {
        val title = ln("A wide title across the whole of the page text here", 72f, 100f, size = 20f, bold = true, width = 451f)
        val paragraphs = PdfParagraphs.fromPage(pageOf(listOf(title) + twoColumns))
        assertEquals("L1 line 1 of the text goes here", paragraphs[1].text.take(31))
        assertEquals(2, paragraphs[1].columns)
    }
}

class RunInLabelTest {
    @Test fun aParagraphThatOnlyStartsWithABoldLabelIsNotAllBold() {
        // A line's look is its first word's, so "Item 1." bold then plain text reads as a bold first line; the other lines are plain.
        val paragraphs = PdfParagraphs.fromPage(
            pageOf(listOf(ln("Item 1. The committee reviewed the proposal in detail", 72f, 100f, bold = true, width = 451f), ln("and agreed that the schedule is realistic if the", 72f, 113f, width = 451f), ln("supplier delivers on time and the budget holds.", 72f, 126f, width = 451f))),
        )
        assertEquals(1, paragraphs.size)
        assertFalse(paragraphs[0].bold)
    }

    @Test fun aParagraphWhoseEveryLineIsBoldStaysBold() {
        val paragraphs = PdfParagraphs.fromPage(
            pageOf(listOf(ln("An all bold paragraph of text that is long enough here", 72f, 100f, bold = true, width = 451f), ln("and continues on a second line that is also bold.", 72f, 113f, bold = true, width = 451f))),
        )
        assertTrue(paragraphs[0].bold)
    }
}
