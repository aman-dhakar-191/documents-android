package io.github.amandhakar.documents.convert

import io.github.amandhakar.documents.slides.EmuBox
import io.github.amandhakar.documents.slides.PptxAlign
import io.github.amandhakar.documents.slides.PptxAnchor
import io.github.amandhakar.documents.slides.PptxDeck
import io.github.amandhakar.documents.slides.PptxGeometry
import io.github.amandhakar.documents.slides.PptxParagraph
import io.github.amandhakar.documents.slides.PptxParser
import io.github.amandhakar.documents.slides.PptxPicture
import io.github.amandhakar.documents.slides.PptxRun
import io.github.amandhakar.documents.slides.PptxSlide
import io.github.amandhakar.documents.slides.PptxText
import io.github.amandhakar.documents.word.DocxAlign
import io.github.amandhakar.documents.word.DocxDocument
import io.github.amandhakar.documents.word.DocxImage
import io.github.amandhakar.documents.word.DocxListMark
import io.github.amandhakar.documents.word.DocxParagraph
import io.github.amandhakar.documents.word.DocxParser
import io.github.amandhakar.documents.word.DocxRun
import io.github.amandhakar.documents.word.PageSetup
import io.github.amandhakar.documents.ooxml.XmlText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

private val PNG = ByteArray(40).also {
    byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10).copyInto(it)
    it[19] = 100; it[23] = 50 // 100 x 50
}

private fun roundTrip(doc: DocxDocument): DocxDocument {
    val out = ByteArrayOutputStream()
    DocxWriter.write(doc, out)
    return DocxParser.parse(ByteArrayInputStream(out.toByteArray()))
}

private fun roundTrip(deck: PptxDeck): PptxDeck {
    val out = ByteArrayOutputStream()
    PptxWriter.write(deck, out)
    return PptxParser.parse(ByteArrayInputStream(out.toByteArray()))
}

class XmlTextTest {
    @Test fun escapesTheFiveCharacters() = assertEquals("a&amp;b&lt;c&gt;d&quot;e&apos;f", XmlText.escape("a&b<c>d\"e'f"))
    @Test fun dropsCharactersXmlForbids() = assertEquals("ab", XmlText.escape("a\u0000\u0001b￾"))
    @Test fun keepsTabsNewlinesAndOrdinaryText() = assertEquals("a\tb\nc é 日本", XmlText.escape("a\tb\nc é 日本"))
    @Test fun keepsEmojiPairs() = assertEquals("😀", XmlText.escape("😀"))
}

class DocxWriterTest {
    @Test fun textAndFormattingSurviveAWriteAndRead() {
        val doc = DocxDocument(
            listOf(
                DocxParagraph(listOf(DocxRun("Title & <more>")), headingLevel = 1),
                DocxParagraph(listOf(DocxRun("plain "), DocxRun("bold", bold = true), DocxRun(" it", italic = true, underline = true, strike = true, colorHex = "b3261e", sizeHalfPoints = 28))),
            ),
        )
        val back = roundTrip(doc)
        val title = back.blocks[0] as DocxParagraph
        assertEquals("Title & <more>", title.text)
        assertEquals(1, title.headingLevel)
        val runs = (back.blocks[1] as DocxParagraph).runs
        assertEquals(listOf("plain ", "bold", " it"), runs.map { it.text })
        assertTrue(runs[1].bold)
        assertTrue(runs[2].italic && runs[2].underline && runs[2].strike)
        assertEquals("B3261E", runs[2].colorHex)
        assertEquals(28, runs[2].sizeHalfPoints)
    }

    @Test fun alignmentTabsLineBreaksAndPageBreaksSurvive() {
        val doc = DocxDocument(
            listOf(
                DocxParagraph(listOf(DocxRun("a\tb\nc")), align = DocxAlign.Center),
                DocxParagraph(listOf(DocxRun("new page")), pageBreakBefore = true, align = DocxAlign.Justify),
                DocxParagraph(listOf(DocxRun("ends page")), pageBreakAfter = true, align = DocxAlign.End),
            ),
        )
        val back = roundTrip(doc).blocks.map { it as DocxParagraph }
        assertEquals("a\tb\nc", back[0].text)
        assertEquals(DocxAlign.Center, back[0].align)
        assertTrue(back[1].pageBreakBefore)
        assertEquals(DocxAlign.Justify, back[1].align)
        assertTrue(back[2].pageBreakAfter)
        assertEquals(DocxAlign.End, back[2].align)
    }

    @Test fun listMarksAreWrittenAsTextInFrontOfTheParagraph() {
        val back = roundTrip(DocxDocument(listOf(DocxParagraph(listOf(DocxRun("item")), list = DocxListMark(0, "•")))))
        assertEquals("•\titem", (back.blocks.single() as DocxParagraph).text)
    }

    @Test fun pageSizeAndMarginsSurvive() {
        val back = roundTrip(DocxDocument(emptyList(), page = PageSetup(612f, 792f, 36f, 54f, 36f, 54f)))
        assertEquals(612f, back.page.widthPt, 0f)
        assertEquals(792f, back.page.heightPt, 0f)
        assertEquals(54f, back.page.marginLeftPt, 0f)
        assertEquals(36f, back.page.marginTopPt, 0f)
    }

    @Test fun picturesSurviveWithTheirBytes() {
        val back = roundTrip(DocxDocument(listOf(DocxImage("x", PNG), DocxParagraph(listOf(DocxRun("after"))))))
        val image = back.blocks.filterIsInstance<DocxImage>().single()
        assertEquals(PNG.toList(), image.bytes.toList())
    }

    @Test fun twoPicturesKeepTheirOwnBytes() {
        val other = PNG.copyOf().also { it[23] = 60 }
        val images = roundTrip(DocxDocument(listOf(DocxImage("a", PNG), DocxImage("b", other)))).blocks.filterIsInstance<DocxImage>()
        assertEquals(listOf(PNG.toList(), other.toList()), images.map { it.bytes.toList() })
    }

    @Test fun anUnknownPictureTypeIsLeftOut() {
        val back = roundTrip(DocxDocument(listOf(DocxImage("x", byteArrayOf(1, 2, 3, 4, 5)), DocxParagraph(listOf(DocxRun("t"))))))
        assertTrue(back.blocks.none { it is DocxImage })
    }

    @Test fun controlCharactersAreRemovedSoTheFileStaysValid() {
        val back = roundTrip(DocxDocument(listOf(DocxParagraph(listOf(DocxRun("a\u0001b\u0000c"))))))
        assertEquals("abc", (back.blocks.single() as DocxParagraph).text)
    }

    @Test fun badColoursAndSizesAreNotWritten() {
        val run = (roundTrip(DocxDocument(listOf(DocxParagraph(listOf(DocxRun("x", colorHex = "red", sizeHalfPoints = 9999)))))).blocks.single() as DocxParagraph).runs.single()
        assertNull(run.colorHex)
        assertNull(run.sizeHalfPoints)
    }

    @Test fun anEmptyDocumentIsStillAValidFile() {
        val back = roundTrip(DocxDocument(emptyList()))
        assertTrue(back.blocks.isEmpty())
    }
}

class PptxWriterTest {
    private fun run(t: String, pt: Float = 18f, b: Boolean = false, c: String = "000000") = PptxRun(t, b, false, false, pt, c)

    @Test fun textBoxesSurviveAWriteAndRead() {
        val box = EmuBox(914400, 457200, 5000000, 800000)
        val deck = PptxDeck(
            12192000L, 6858000L,
            listOf(
                PptxSlide(
                    listOf(
                        PptxText(
                            box,
                            listOf(
                                PptxParagraph(listOf(run("Hello & <world>", 24f, true, "B3261E")), PptxAlign.Center, 0, null),
                                PptxParagraph(listOf(run("bullet", 18f)), PptxAlign.Start, 0, "•"),
                            ),
                            "DDEEFF", "1D5FD6", PptxGeometry.RoundRect, PptxAnchor.Middle,
                        ),
                    ),
                    "FFFFFF", "",
                ),
            ),
        )
        val back = roundTrip(deck)
        assertEquals(12192000L, back.widthEmu)
        val t = back.slides.single().shapes.single() as PptxText
        assertEquals(box, t.box)
        assertEquals("DDEEFF", t.fillHex)
        assertEquals("1D5FD6", t.lineHex)
        assertEquals(PptxGeometry.RoundRect, t.geometry)
        assertEquals(PptxAnchor.Middle, t.anchor)
        val first = t.paragraphs[0]
        assertEquals("Hello & <world>", first.text)
        assertEquals(PptxAlign.Center, first.align)
        assertEquals(24f, first.runs.single().sizePt, 0f)
        assertTrue(first.runs.single().bold)
        assertEquals("B3261E", first.runs.single().colorHex)
        assertEquals("•", t.paragraphs[1].bullet)
        assertNull(first.bullet)
    }

    @Test fun picturesAndBackgroundsSurvive() {
        val deck = PptxDeck(
            9144000L, 6858000L,
            listOf(PptxSlide(listOf(PptxPicture(EmuBox(100, 200, 3000, 2000), "p.png", PNG)), "1D2F57", "")),
        )
        val slide = roundTrip(deck).slides.single()
        assertEquals("1D2F57", slide.backgroundHex)
        val pic = slide.shapes.single() as PptxPicture
        assertEquals(EmuBox(100, 200, 3000, 2000), pic.box)
        assertEquals(PNG.toList(), pic.bytes.toList())
    }

    @Test fun manySlidesKeepTheirOrder() {
        val slides = (1..5).map { i ->
            PptxSlide(listOf(PptxText(EmuBox(0, 0, 1000, 1000), listOf(PptxParagraph(listOf(run("slide $i")), PptxAlign.Start, 0, null)), null, null, PptxGeometry.Rect, PptxAnchor.Top)), "FFFFFF", "")
        }
        assertEquals((1..5).map { "slide $it" }, roundTrip(PptxDeck(9144000L, 6858000L, slides)).slides.map { it.plainText() })
    }

    @Test fun lineBreaksInARunSurvive() {
        val deck = PptxDeck(9144000L, 6858000L, listOf(PptxSlide(listOf(PptxText(EmuBox(0, 0, 1000, 1000), listOf(PptxParagraph(listOf(run("a\nb")), PptxAlign.Start, 0, null)), null, null, PptxGeometry.Rect, PptxAnchor.Top)), "FFFFFF", "")))
        assertEquals("ab", (roundTrip(deck).slides.single().shapes.single() as PptxText).paragraphs.single().text.replace("\n", ""))
    }

    @Test fun anEmptyDeckIsRefusedByTheReaderNotCrashed() {
        // A deck with no slides isn't a presentation anyone can open; the reader says so clearly.
        val out = ByteArrayOutputStream()
        PptxWriter.write(PptxDeck(9144000L, 6858000L, emptyList()), out)
        var failed = false
        try {
            PptxParser.parse(ByteArrayInputStream(out.toByteArray()))
        } catch (e: io.github.amandhakar.documents.ooxml.OoxmlException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test fun controlCharactersAreRemoved() {
        val deck = PptxDeck(9144000L, 6858000L, listOf(PptxSlide(listOf(PptxText(EmuBox(0, 0, 1000, 1000), listOf(PptxParagraph(listOf(run("a\u0001b")), PptxAlign.Start, 0, null)), null, null, PptxGeometry.Rect, PptxAnchor.Top)), "FFFFFF", "")))
        assertEquals("ab", (roundTrip(deck).slides.single().shapes.single() as PptxText).paragraphs.single().text)
        assertFalse(false)
    }
}
