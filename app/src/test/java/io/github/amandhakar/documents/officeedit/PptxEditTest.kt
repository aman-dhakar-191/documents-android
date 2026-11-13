package io.github.amandhakar.documents.officeedit

import io.github.amandhakar.documents.slides.PptxParser
import io.github.amandhakar.documents.slides.PptxShape
import io.github.amandhakar.documents.slides.PptxTable
import io.github.amandhakar.documents.slides.PptxText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val PNS = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
    "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" " +
    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""
private const val RELS = "http://schemas.openxmlformats.org/package/2006/relationships"
private const val RT = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

private fun zipOf(parts: Map<String, Any>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        for ((name, content) in parts) {
            z.putNextEntry(ZipEntry(name))
            z.write(if (content is ByteArray) content else (content as String).toByteArray())
            z.closeEntry()
        }
    }
    return out.toByteArray()
}

private fun xfrm() = "<a:xfrm><a:off x=\"100\" y=\"100\"/><a:ext cx=\"3000\" cy=\"900\"/></a:xfrm>"
private fun box(vararg paragraphs: String) =
    "<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"t\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr><p:spPr>${xfrm()}</p:spPr><p:txBody><a:bodyPr/>${paragraphs.joinToString("")}</p:txBody></p:sp>"
private fun para(text: String, rPr: String = "<a:rPr lang=\"en-US\" sz=\"2400\" b=\"1\"/>", ppr: String = "") =
    "<a:p>$ppr<a:r>$rPr<a:t>$text</a:t></a:r></a:p>"

private fun deck(vararg slideBodies: String, extra: Map<String, Any> = emptyMap()): ByteArray {
    val parts = LinkedHashMap<String, Any>()
    parts["ppt/presentation.xml"] = "<p:presentation $PNS><p:sldIdLst>" + slideBodies.indices.joinToString("") { "<p:sldId id=\"${256 + it}\" r:id=\"rId${it + 1}\"/>" } + "</p:sldIdLst><p:sldSz cx=\"9144000\" cy=\"6858000\"/></p:presentation>"
    parts["ppt/_rels/presentation.xml.rels"] = "<Relationships xmlns=\"$RELS\">" + slideBodies.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"$RT/slide\" Target=\"slides/slide${it + 1}.xml\"/>" } + "</Relationships>"
    slideBodies.forEachIndexed { i, body ->
        parts["ppt/slides/slide${i + 1}.xml"] = "<p:sld $PNS><p:cSld><p:spTree><p:nvGrpSpPr/><p:grpSpPr/>$body</p:spTree></p:cSld></p:sld>"
        parts["ppt/slides/_rels/slide${i + 1}.xml.rels"] = "<Relationships xmlns=\"$RELS\"/>"
    }
    parts["ppt/media/image1.png"] = byteArrayOf(9, 8, 7, 6)
    parts["docProps/app.xml"] = "<Properties/>"
    parts.putAll(extra)
    return zipOf(parts)
}

private fun read(bytes: ByteArray) = PptxParser.parse(ByteArrayInputStream(bytes))
private fun entries(bytes: ByteArray): Map<String, ByteArray> {
    val m = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; m[e.name] = z.readBytes() } }
    return m
}
private fun slideTexts(bytes: ByteArray, slide: Int) = read(bytes).slides[slide].shapes.filterIsInstance<PptxText>().flatMap { s -> s.paragraphs.map { it.text } }

class PptxEditsTest {
    @Test fun noEditsMeansNoChanges() {
        assertTrue(PptxEdits().isEmpty)
        assertEquals(0, PptxEdits().count)
    }

    @Test fun theLatestTextForAParagraphWins() {
        val e = PptxEdits().replace("s1", 0, "a").replace("s1", 0, "b")
        assertEquals(1, e.count)
        assertEquals("b", e.textOf("s1", 0))
    }

    @Test fun sameNumberOnAnotherSlideIsAnotherParagraph() {
        assertEquals(2, PptxEdits().replace("s1", 0, "a").replace("s2", 0, "b").count)
    }

    @Test fun undoTakesBackTheLatestChange() {
        val e = PptxEdits().replace("s1", 0, "a").replace("s1", 1, "b").undo()
        assertEquals(1, e.count)
        assertTrue(PptxEdits().undo().isEmpty)
    }
}

class PptxEditorTest {
    private fun twoBoxes() = deck(box(para("Title")) + box(para("Body one", ppr = "<a:pPr algn=\"ctr\"/>"), para("Body two")), box(para("Second slide")))

    @Test fun replacingTextChangesOnlyThatParagraph() {
        val out = PptxEditor.applyToBytes(twoBoxes(), PptxEdits().replace("ppt/slides/slide1.xml", 0, "New title"))
        assertEquals(listOf("New title", "Body one", "Body two"), slideTexts(out, 0))
        assertEquals(listOf("Second slide"), slideTexts(out, 1))
    }

    @Test fun everyOtherPartIsCopiedThroughUnchanged() {
        val original = twoBoxes()
        val out = PptxEditor.applyToBytes(original, PptxEdits().replace("ppt/slides/slide1.xml", 1, "x"))
        val before = entries(original)
        val after = entries(out)
        assertEquals(before.keys.toList(), after.keys.toList())
        for (name in before.keys - "ppt/slides/slide1.xml") assertEquals(name, before.getValue(name).toList(), after.getValue(name).toList())
    }

    @Test fun theLookAndPositionOfTheTextAreKept() {
        val out = PptxEditor.applyToBytes(twoBoxes(), PptxEdits().replace("ppt/slides/slide1.xml", 1, "Changed"))
        val shape = read(out).slides[0].shapes.filterIsInstance<PptxText>()[1]
        val p = shape.paragraphs[0]
        assertEquals("Changed", p.text)
        assertEquals(24f, p.runs.single().sizePt, 0f)
        assertTrue(p.runs.single().bold)
        assertEquals(io.github.amandhakar.documents.slides.PptxAlign.Center, p.align)
        assertEquals(100L, shape.box.x)
    }

    @Test fun severalRunsBecomeOneWithTheFirstOnesLook() {
        val multi = "<a:p><a:r><a:rPr sz=\"3000\" i=\"1\"/><a:t>one </a:t></a:r><a:r><a:rPr sz=\"1000\"/><a:t>two</a:t></a:r></a:p>"
        val out = PptxEditor.applyToBytes(deck(box(multi)), PptxEdits().replace("ppt/slides/slide1.xml", 0, "merged"))
        val p = (read(out).slides[0].shapes.single() as PptxText).paragraphs.single()
        assertEquals(1, p.runs.size)
        assertEquals("merged", p.text)
        assertEquals(30f, p.runs[0].sizePt, 0f)
        assertTrue(p.runs[0].italic)
    }

    @Test fun lineBreaksComeBackAsLineBreaks() {
        val out = PptxEditor.applyToBytes(deck(box(para("x"))), PptxEdits().replace("ppt/slides/slide1.xml", 0, "line one\nline two"))
        assertEquals("line one\nline two", slideTexts(out, 0).single())
    }

    @Test fun anEmptyParagraphGetsTheLookOfItsEndMark() {
        val empty = "<a:p><a:pPr algn=\"r\"/><a:endParaRPr lang=\"en-US\" sz=\"2000\" b=\"1\"/></a:p>"
        val out = PptxEditor.applyToBytes(deck(box(empty)), PptxEdits().replace("ppt/slides/slide1.xml", 0, "filled"))
        val p = (read(out).slides[0].shapes.single() as PptxText).paragraphs.single()
        assertEquals("filled", p.text)
        assertEquals(20f, p.runs.single().sizePt, 0f)
        assertTrue(p.runs.single().bold)
    }

    @Test fun clearingEmptiesTheParagraphButKeepsIt() {
        val out = PptxEditor.applyToBytes(deck(box(para("gone"), para("kept"))), PptxEdits().replace("ppt/slides/slide1.xml", 0, ""))
        val shape = read(out).slides[0].shapes.filterIsInstance<PptxText>().single()
        assertEquals(listOf("", "kept"), shape.paragraphs.map { it.text })
    }

    @Test fun textInATableCellCanBeEdited() {
        val tbl = "<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"7\" name=\"f\"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr><p:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"700\" cy=\"300\"/></p:xfrm>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/table\"><a:tbl><a:tblGrid><a:gridCol w=\"400\"/></a:tblGrid><a:tr h=\"150\"><a:tc><a:txBody><a:bodyPr/>${para("cell")}</a:txBody></a:tc></a:tr></a:tbl></a:graphicData></a:graphic></p:graphicFrame>"
        val out = PptxEditor.applyToBytes(deck(box(para("before")) + tbl), PptxEdits().replace("ppt/slides/slide1.xml", 1, "new cell"))
        val table = read(out).slides[0].shapes.filterIsInstance<PptxTable>().single()
        assertEquals("new cell", table.rows[0].cells[0].paragraphs.single().text)
    }

    @Test fun specialCharactersAreEscapedAndControlCharactersDropped() {
        val out = PptxEditor.applyToBytes(deck(box(para("x"))), PptxEdits().replace("ppt/slides/slide1.xml", 0, "R&D <b> \"q\" \u0001ok"))
        assertEquals("R&D <b> \"q\" ok", slideTexts(out, 0).single())
    }

    @Test fun aSlideOrParagraphThatIsNotThereIsRefused() {
        for (edits in listOf(PptxEdits().replace("ppt/slides/slide9.xml", 0, "x"), PptxEdits().replace("ppt/slides/slide1.xml", 99, "x"))) {
            try {
                PptxEditor.applyToBytes(twoBoxes(), edits)
                fail("expected an exception")
            } catch (e: OfficeEditException) {
                assertTrue(e.message!!.contains("can't be found"))
            }
        }
    }

    @Test fun aFileThatIsNotAZipIsRefused() {
        try {
            PptxEditor.applyToBytes("nope".toByteArray(), PptxEdits().replace("a", 0, "b"))
            fail("expected an exception")
        } catch (e: OfficeEditException) {
            assertFalse(e.message.isNullOrEmpty())
        }
    }

    @Test fun aSlideWithADoctypeIsRefused() {
        val bytes = deck(box(para("x")), extra = mapOf("ppt/slides/slide1.xml" to "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><p:sld $PNS/>"))
        try {
            PptxEditor.applyToBytes(bytes, PptxEdits().replace("ppt/slides/slide1.xml", 0, "x"))
            fail("expected an exception")
        } catch (e: OfficeEditException) {
            assertTrue(e.message!!.contains("unsafe"))
        }
    }

    @Test fun theResultMatchesThePreviewOfTheSameEdits() {
        val original = twoBoxes()
        val edits = PptxEdits().replace("ppt/slides/slide1.xml", 0, "T").replace("ppt/slides/slide1.xml", 2, "B2").replace("ppt/slides/slide2.xml", 0, "S2")
        val result = PptxEditor.applyToBytes(original, edits)
        assertTrue(PptxEditor.matches(ByteArrayInputStream(result), PptxEditModel.apply(read(original), edits)))
    }

    @Test fun aResultThatDoesNotMatchThePreviewIsCaught() {
        val original = twoBoxes()
        val result = PptxEditor.applyToBytes(original, PptxEdits().replace("ppt/slides/slide1.xml", 0, "changed"))
        assertFalse(PptxEditor.matches(ByteArrayInputStream(result), read(original)))
        assertFalse(PptxEditor.matches(ByteArrayInputStream("garbage".toByteArray()), read(original)))
    }

    @Test fun paragraphIdsAreTheirOrderInTheSlideFile() {
        val ids = read(twoBoxes()).slides[0].shapes.filterIsInstance<PptxText>().flatMap { it.paragraphs.map { p -> p.id } }
        assertEquals(listOf(0, 1, 2), ids)
        assertEquals("ppt/slides/slide1.xml", read(twoBoxes()).slides[0].path)
    }

    @Test fun paragraphsWithFieldsAreNotEditable() {
        val field = "<a:p><a:fld id=\"{1}\" type=\"slidenum\"><a:rPr/><a:t>3</a:t></a:fld></a:p>"
        val shape: PptxShape = read(deck(box(field + para("normal")))).slides[0].shapes.filterIsInstance<PptxText>().single()
        val ps = (shape as PptxText).paragraphs
        assertFalse(ps[0].editable)
        assertTrue(ps[1].editable)
    }

    @Test fun editingTwiceInARowKeepsWorking() {
        val once = PptxEditor.applyToBytes(twoBoxes(), PptxEdits().replace("ppt/slides/slide1.xml", 0, "A"))
        val twice = PptxEditor.applyToBytes(once, PptxEdits().replace("ppt/slides/slide2.xml", 0, "B"))
        assertEquals("A", slideTexts(twice, 0)[0])
        assertEquals(listOf("B"), slideTexts(twice, 1))
    }

    @Test fun previewShowsEditsInTextBoxesAndKeepsTheRest() {
        val d = read(twoBoxes())
        val preview = PptxEditModel.apply(d, PptxEdits().replace("ppt/slides/slide1.xml", 1, "preview"))
        assertEquals(listOf("Title", "preview", "Body two"), preview.slides[0].shapes.filterIsInstance<PptxText>().flatMap { s -> s.paragraphs.map { it.text } })
        assertTrue(PptxEditModel.apply(d, PptxEdits()) === d)
    }
}
