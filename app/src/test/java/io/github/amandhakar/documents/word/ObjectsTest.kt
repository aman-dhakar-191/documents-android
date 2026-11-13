package io.github.amandhakar.documents.word

import io.github.amandhakar.documents.officeedit.DocxEditModel
import io.github.amandhakar.documents.officeedit.DocxEdits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val NSO = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
    "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
    "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
    "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\" " +
    "xmlns:wps=\"http://schemas.microsoft.com/office/word/2010/wordprocessingShape\" " +
    "xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\" " +
    "xmlns:v=\"urn:schemas-microsoft-com:vml\""

private val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10, 0, 0, 0, 13, 'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
    0, 0, 0, 100, 0, 0, 0, 50, 8, 2, 0, 0, 0)

private fun docOf(body: String, withImage: Boolean = true): DocxDocument {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        z.putNextEntry(ZipEntry("word/document.xml")); z.write("<?xml version=\"1.0\"?><w:document $NSO><w:body>$body</w:body></w:document>".toByteArray()); z.closeEntry()
        z.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
        z.write("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rIdImg\" Type=\"x/image\" Target=\"media/image1.png\"/></Relationships>".toByteArray())
        z.closeEntry()
        if (withImage) { z.putNextEntry(ZipEntry("word/media/image1.png")); z.write(PNG); z.closeEntry() }
    }
    return DocxParser.parse(ByteArrayInputStream(out.toByteArray()))
}

private fun blip() = "<a:graphic><a:graphicData><pic:pic><pic:blipFill><a:blip r:embed=\"rIdImg\"/></pic:blipFill></pic:pic></a:graphicData></a:graphic>"
private fun inline(cx: Long?, cy: Long?) =
    "<w:drawing><wp:inline>" + (if (cx != null) "<wp:extent cx=\"$cx\" cy=\"$cy\"/>" else "") + blip() + "</wp:inline></w:drawing>"
private fun anchor(attrs: String, pos: String, wrap: String, cx: Long = 1270000, cy: Long = 635000) =
    "<w:drawing><wp:anchor $attrs>$pos<wp:extent cx=\"$cx\" cy=\"$cy\"/>$wrap${blip()}</wp:anchor></w:drawing>"
private fun run(inner: String) = "<w:r>$inner</w:r>"
private fun para(inner: String, pPr: String = "") = "<w:p>" + (if (pPr.isNotEmpty()) "<w:pPr>$pPr</w:pPr>" else "") + inner + "</w:p>"
private fun txt(text: String) = para(run("<w:t>$text</w:t>"))

private fun textBox(inner: String, cx: Long = 2540000, spPr: String = "") =
    "<w:drawing><wp:anchor><wp:extent cx=\"$cx\" cy=\"635000\"/><a:graphic><a:graphicData><wps:wsp>$spPr<wps:txbx><w:txbxContent>$inner</w:txbxContent></wps:txbx></wps:wsp></a:graphicData></a:graphic></wp:anchor></w:drawing>"

class PictureSizeTest {
    @Test fun aPicturesDrawnSizeComesFromItsExtent() {
        val img = docOf(para(run(inline(1905000, 952500)))).blocks.filterIsInstance<DocxImage>().single()
        assertEquals(150f, img.widthPt!!, 0.01f)
        assertEquals(75f, img.heightPt!!, 0.01f)
    }

    @Test fun withoutAnExtentThePixelsAreUsed() {
        val img = docOf(para(run(inline(null, null)))).blocks.filterIsInstance<DocxImage>().single()
        assertNull(img.widthPt)
        val (w, h) = PageLayout.imageBoxPt(img, 500f, 500f)
        assertEquals(75f, w, 0.01f) // 100 px at 96 per inch
        assertEquals(37.5f, h, 0.01f)
    }

    @Test fun theDeclaredSizeBeatsThePixelsButStillFitsThePage() {
        val img = DocxImage("x.png", PNG, widthPt = 800f, heightPt = 400f)
        val (w, h) = PageLayout.imageBoxPt(img, 400f, 500f)
        assertEquals(400f, w, 0.01f)
        assertEquals(200f, h, 0.01f)
        val tall = DocxImage("x.png", PNG, widthPt = 100f, heightPt = 900f)
        val (w2, h2) = PageLayout.imageBoxPt(tall, 400f, 450f)
        assertEquals(450f, h2, 0.01f)
        assertEquals(50f, w2, 0.01f)
    }

    @Test fun absurdExtentsAreCapped() {
        val img = docOf(para(run(inline(2_000_000_000L, 2_000_000_000L)))).blocks.filterIsInstance<DocxImage>().single()
        assertTrue(img.widthPt!! <= 5000f)
        assertTrue(docOf(para(run(inline(-5, 0)))).blocks.filterIsInstance<DocxImage>().single().widthPt == null)
    }

    @Test fun aCentredPicturesParagraphAlignmentIsKept() {
        val img = docOf(para(run(inline(1270000, 635000)), "<w:jc w:val=\"center\"/>")).blocks.filterIsInstance<DocxImage>().single()
        assertEquals(DocxAlign.Center, img.align)
    }

    @Test fun aPictureWhoseFileIsMissingIsLeftOut() {
        val doc = docOf(para(run(inline(1270000, 635000))) + txt("after"), withImage = false)
        assertTrue(doc.blocks.none { it is DocxImage })
        assertEquals("after", doc.plainText().trim())
    }
}

class FloatingPictureTest {
    private val rel = "<wp:positionH relativeFrom=\"page\"><wp:posOffset>635000</wp:posOffset></wp:positionH><wp:positionV relativeFrom=\"margin\"><wp:posOffset>1270000</wp:posOffset></wp:positionV>"

    @Test fun aPictureBehindTheTextBecomesAFloat() {
        val doc = docOf(para(run(anchor("behindDoc=\"1\"", rel, "<wp:wrapNone/>"))) + txt("text"))
        val f = doc.blocks.filterIsInstance<DocxFloat>().single()
        assertTrue(f.behind)
        assertEquals(50f, f.xPt, 0.01f)
        assertEquals(100f, f.yPt, 0.01f)
        assertEquals(FloatFrom.Page, f.xFrom)
        assertEquals(FloatFrom.Margin, f.yFrom)
        assertEquals(100f, f.image.widthPt!!, 0.01f)
    }

    @Test fun aPictureInFrontWithNoWrapIsAFloatToo() {
        val f = docOf(para(run(anchor("behindDoc=\"0\"", rel, "<wp:wrapNone/>")))).blocks.filterIsInstance<DocxFloat>().single()
        assertEquals(false, f.behind)
    }

    @Test fun aFloatCanBeAlignedInsteadOfOffset() {
        val pos = "<wp:positionH relativeFrom=\"margin\"><wp:align>center</wp:align></wp:positionH><wp:positionV relativeFrom=\"page\"><wp:align>bottom</wp:align></wp:positionV>"
        val f = docOf(para(run(anchor("behindDoc=\"1\"", pos, "<wp:wrapNone/>")))).blocks.filterIsInstance<DocxFloat>().single()
        assertEquals(DocxAlign.Center, f.alignH)
        assertEquals(CellAlign.Bottom, f.alignV)
    }

    @Test fun aPictureTextWrapsAroundSitsInTheFlow() {
        val pos = "<wp:positionH relativeFrom=\"margin\"><wp:align>right</wp:align></wp:positionH><wp:positionV relativeFrom=\"paragraph\"><wp:posOffset>0</wp:posOffset></wp:positionV>"
        val doc = docOf(para(run(anchor("behindDoc=\"0\"", pos, "<wp:wrapSquare wrapText=\"bothSides\"/>"))) + txt("text"))
        assertTrue(doc.blocks.none { it is DocxFloat })
        assertEquals(DocxAlign.End, doc.blocks.filterIsInstance<DocxImage>().single().align)
    }

    @Test fun aFloatTakesNoRoomAndIsNoText() {
        val f = DocxFloat(DocxImage("x", PNG), 0f, 0f)
        assertEquals(0f, PageLayout.heightOf(f, PageSetup()), 0f)
        assertEquals("a", DocxDocument(listOf(DocxParagraph(listOf(DocxRun("a"))), f)).plainText())
    }

    @Test fun absurdOffsetsAreKeptInRange() {
        val pos = "<wp:positionH relativeFrom=\"page\"><wp:posOffset>99999999999</wp:posOffset></wp:positionH><wp:positionV relativeFrom=\"page\"><wp:posOffset>-99999999999</wp:posOffset></wp:positionV>"
        val f = docOf(para(run(anchor("behindDoc=\"1\"", pos, "<wp:wrapNone/>")))).blocks.filterIsInstance<DocxFloat>().single()
        assertEquals(1574.8f, f.xPt, 0.5f) // clamped, not taken at face value
        assertEquals(-1574.8f, f.yPt, 0.5f)
    }

    @Test fun everyPaginatedPageKeepsItsFloats() {
        val page = PageSetup(400f, 300f, 50f, 50f, 50f, 50f)
        val blocks = listOf<DocxBlock>(DocxFloat(DocxImage("x", PNG), 0f, 0f)) + List(60) { DocxParagraph(listOf(DocxRun("P$it"))) }
        val pages = PageLayout.paginateColumns(DocxDocument(blocks, page = page))
        assertEquals(1, pages.flatMap { pg -> pg.bands.flatMap { b -> b.columns.flatten() } }.count { it is DocxFloat })
    }
}

class TextBoxTest {
    private val two = txt("First line").replace("<w:p>", "<w:p>") + txt("Second line")

    @Test fun aTextBoxIsReadWithItsParagraphs() {
        val doc = docOf(para(run(textBox(two))))
        val box = doc.blocks.filterIsInstance<DocxTextBox>().single()
        assertEquals(2, box.blocks.size)
        assertEquals(200f, box.widthPt!!, 0.01f)
        assertTrue(doc.plainText().contains("First line\nSecond line"))
    }

    @Test fun aTextBoxWrittenTwiceByWordIsReadOnce() {
        val alt = "<mc:AlternateContent><mc:Choice Requires=\"wps\">${textBox(two)}</mc:Choice>" +
            "<mc:Fallback><w:pict><v:shape><v:textbox><w:txbxContent>$two</w:txbxContent></v:textbox></v:shape></w:pict></mc:Fallback></mc:AlternateContent>"
        val doc = docOf(para(run(alt)))
        assertEquals(1, doc.blocks.filterIsInstance<DocxTextBox>().size)
        assertEquals(1, doc.plainText().split("First line").size - 1)
    }

    @Test fun anOldStyleTextBoxIsRead() {
        val doc = docOf(para(run("<w:pict><v:shape><v:textbox><w:txbxContent>$two</w:txbxContent></v:textbox></v:shape></w:pict>")))
        assertEquals(2, doc.blocks.filterIsInstance<DocxTextBox>().single().blocks.size)
    }

    @Test fun lineAndFillAreRead() {
        val sp = "<wps:spPr><a:solidFill><a:srgbClr val=\"FFF2CC\"/></a:solidFill><a:ln w=\"19050\"><a:solidFill><a:srgbClr val=\"FF0000\"/></a:solidFill></a:ln></wps:spPr>"
        val box = docOf(para(run(textBox(two, spPr = sp)))).blocks.filterIsInstance<DocxTextBox>().single()
        assertEquals("FFF2CC", box.fillHex)
        assertEquals("FF0000", box.lineHex)
        assertEquals(1.5f, box.lineWidthPt, 0.01f)
    }

    @Test fun noLineMeansNoBorder() {
        val sp = "<wps:spPr><a:ln><a:noFill/></a:ln></wps:spPr>"
        assertEquals(0f, docOf(para(run(textBox(two, spPr = sp)))).blocks.filterIsInstance<DocxTextBox>().single().lineWidthPt, 0f)
    }

    @Test fun aTextBoxTakesRoomAccordingToItsContent() {
        val box = DocxTextBox(List(10) { DocxParagraph(listOf(DocxRun("line $it"))) }, 200f)
        val single = DocxTextBox(listOf(DocxParagraph(listOf(DocxRun("line")))), 200f)
        assertTrue(PageLayout.heightOf(box, PageSetup()) > PageLayout.heightOf(single, PageSetup()) * 5)
    }

    @Test fun aWiderBoxIsCutToTheTextWidth() {
        val wide = DocxTextBox(listOf(DocxParagraph(listOf(DocxRun("word ".repeat(100))))), 5000f)
        val narrow = DocxTextBox(listOf(DocxParagraph(listOf(DocxRun("word ".repeat(100))))), 200f)
        assertTrue(PageLayout.heightOf(wide, PageSetup()) < PageLayout.heightOf(narrow, PageSetup()))
    }

    @Test fun boxesInsideBoxesDoNotRunAway() {
        var inner = two
        repeat(12) { inner = para(run(textBox(inner))) + txt("level") }
        val doc = docOf(para(run(textBox(inner))))
        assertNotNull(doc.blocks.filterIsInstance<DocxTextBox>().single())
        assertTrue(doc.plainText().length < 100_000)
    }

    @Test fun aTextBoxParagraphCanBeEditedAndTheBoxKeptInThePreview() {
        val doc = docOf(para(run(textBox(txt("Boxed text")))))
        val inside = (doc.blocks.filterIsInstance<DocxTextBox>().single().blocks[0] as DocxParagraph)
        assertTrue(inside.id >= 0)
        val edited = DocxEditModel.apply(doc, DocxEdits().replace(inside.id, "Changed"))
        assertTrue(edited.plainText().contains("Changed"))
        assertEquals(1, edited.blocks.filterIsInstance<DocxTextBox>().size)
    }

    @Test fun anEmptyTextBoxIsLeftOut() {
        assertTrue(docOf(para(run(textBox("")))).blocks.none { it is DocxTextBox })
    }
}
