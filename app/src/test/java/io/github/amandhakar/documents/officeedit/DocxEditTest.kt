package io.github.amandhakar.documents.officeedit

import io.github.amandhakar.documents.word.DocxDocument
import io.github.amandhakar.documents.word.DocxListMark
import io.github.amandhakar.documents.word.DocxParagraph
import io.github.amandhakar.documents.word.DocxParser
import io.github.amandhakar.documents.word.DocxRun
import io.github.amandhakar.documents.word.DocxTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
    "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
    "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
    "xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\" mc:Ignorable=\"w14\" " +
    "xmlns:w14=\"http://schemas.microsoft.com/office/word/2010/wordml\""

private fun zip(vararg parts: Pair<String, Any>): ByteArray {
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

private fun docx(body: String, vararg extra: Pair<String, Any>): ByteArray =
    zip("word/document.xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document $NS><w:body>$body</w:body></w:document>", *extra)

private fun run(text: String, props: String = "") = "<w:r>${if (props.isEmpty()) "" else "<w:rPr>$props</w:rPr>"}<w:t xml:space=\"preserve\">$text</w:t></w:r>"
private fun p(vararg runs: String, ppr: String = "") = "<w:p>${if (ppr.isEmpty()) "" else "<w:pPr>$ppr</w:pPr>"}${runs.joinToString("")}</w:p>"

private fun read(bytes: ByteArray): DocxDocument = DocxParser.parse(ByteArrayInputStream(bytes))
private fun texts(bytes: ByteArray) = read(bytes).blocks.filterIsInstance<DocxParagraph>().map { it.text }

private fun entries(bytes: ByteArray): Map<String, ByteArray> {
    val m = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
        while (true) {
            val e = z.nextEntry ?: break
            m[e.name] = z.readBytes()
        }
    }
    return m
}

class DocxEditsTest {
    @Test fun noEditsMeansNoChanges() {
        val e = DocxEdits()
        assertTrue(e.isEmpty)
        assertEquals(0, e.count)
    }

    @Test fun replacingTheSameParagraphTwiceCountsOnceAndKeepsTheLastText() {
        val e = DocxEdits().replace(3, "one").replace(3, "two")
        assertEquals(1, e.count)
        assertEquals("two", e.textOf(3))
    }

    @Test fun deletingDropsEarlierReplacements() {
        val e = DocxEdits().replace(2, "x").delete(2)
        assertEquals(setOf(2), e.resolved().deleted)
        assertTrue(e.resolved().replaced.isEmpty())
    }

    @Test fun anInsertedParagraphGetsAMadeUpIdAndCanBeEdited() {
        val e = DocxEdits().insertAfter(5, "new")
        val id = e.lastInsertId!!
        assertTrue(id >= DocxEdits.TEMP_BASE)
        val e2 = e.replace(id, "newer")
        assertEquals("newer", e2.resolved().inserts.single().text)
        assertEquals(1, e2.count)
    }

    @Test fun insertedParagraphsGetDifferentIds() {
        val e = DocxEdits().insertAfter(1, "a").insertAfter(1, "b")
        assertEquals(2, e.resolved().inserts.map { it.id }.distinct().size)
    }

    @Test fun deletingAnInsertedParagraphAlsoRemovesWhatWasAddedAfterIt() {
        val a = DocxEdits().insertAfter(1, "a")
        val idA = a.lastInsertId!!
        val b = a.insertAfter(idA, "b")
        val c = b.delete(idA)
        assertTrue(c.resolved().inserts.isEmpty())
        assertEquals(0, c.count)
    }

    @Test fun undoTakesBackTheLatestChangeOnly() {
        val e = DocxEdits().replace(1, "a").replace(2, "b").undo()
        assertEquals(setOf(1), e.resolved().replaced.keys)
        assertTrue(DocxEdits().undo().isEmpty)
    }

    @Test fun undoingAnInsertFreesItsId() {
        val e = DocxEdits().insertAfter(1, "a").undo().insertAfter(1, "b")
        assertEquals(DocxEdits.TEMP_BASE, e.lastInsertId)
    }

    @Test fun insertsAfterAreKeptInTheOrderTheyWereMade() {
        val r = DocxEdits().insertAfter(4, "first").insertAfter(4, "second").resolved()
        assertEquals(listOf("first", "second"), r.insertsAfter(4).map { it.text })
    }
}

class DocxEditModelTest {
    private fun doc() = DocxDocument(
        listOf(
            DocxParagraph(listOf(DocxRun("Title", bold = true, sizeHalfPoints = 40)), headingLevel = 1, id = 0),
            DocxParagraph(listOf(DocxRun("plain "), DocxRun("bold", bold = true)), id = 1),
            DocxParagraph(listOf(DocxRun("item")), list = DocxListMark(0, "•"), id = 2),
            DocxTable(listOf(listOf(listOf(DocxParagraph(listOf(DocxRun("cell")), id = 3))))),
        ),
    )

    private fun paragraphs(d: DocxDocument): List<DocxParagraph> =
        d.blocks.flatMap { b -> if (b is DocxTable) b.rows.flatten().flatten().filterIsInstance<DocxParagraph>() else listOfNotNull(b as? DocxParagraph) }

    @Test fun noEditsGiveTheSameDocument() {
        val d = doc()
        assertTrue(DocxEditModel.apply(d, DocxEdits()) === d)
    }

    @Test fun replacedTextTakesTheLookOfTheFirstRun() {
        val p = paragraphs(DocxEditModel.apply(doc(), DocxEdits().replace(0, "New title")))[0]
        assertEquals("New title", p.text)
        assertTrue(p.runs.single().bold)
        assertEquals(40, p.runs.single().sizeHalfPoints)
        assertEquals(1, p.headingLevel)
    }

    @Test fun aMultiRunParagraphBecomesOneRun() {
        val p = paragraphs(DocxEditModel.apply(doc(), DocxEdits().replace(1, "all new")))[1]
        assertEquals(1, p.runs.size)
        assertFalse(p.runs[0].bold)
    }

    @Test fun anEmptyReplacementClearsTheParagraph() {
        val p = paragraphs(DocxEditModel.apply(doc(), DocxEdits().replace(1, "")))[1]
        assertTrue(p.runs.isEmpty())
    }

    @Test fun deletedParagraphsDisappear() {
        assertEquals(listOf("Title", "item", "cell"), paragraphs(DocxEditModel.apply(doc(), DocxEdits().delete(1))).map { it.text })
    }

    @Test fun anAddedParagraphKeepsTheListAndLookOfItsNeighbour() {
        val result = paragraphs(DocxEditModel.apply(doc(), DocxEdits().insertAfter(2, "another")))
        val added = result[3]
        assertEquals("another", added.text)
        assertEquals("•", added.list!!.label)
        assertTrue(added.id >= DocxEdits.TEMP_BASE)
    }

    @Test fun addedParagraphsFollowEachOtherInOrder() {
        val first = DocxEdits().insertAfter(1, "a")
        val second = first.insertAfter(first.lastInsertId!!, "b").insertAfter(1, "c")
        assertEquals(listOf("Title", "plain bold", "a", "b", "c", "item", "cell"), paragraphs(DocxEditModel.apply(doc(), second)).map { it.text })
    }

    @Test fun editsReachParagraphsInTableCells() {
        val d = DocxEditModel.apply(doc(), DocxEdits().replace(3, "changed cell"))
        assertEquals("changed cell", ((d.blocks[3] as DocxTable).rows[0][0][0] as DocxParagraph).text)
    }

    @Test fun aParagraphAddedAfterADeletedOneStillAppears() {
        val e = DocxEdits().insertAfter(1, "stays").delete(1)
        assertEquals(listOf("Title", "stays", "item", "cell"), paragraphs(DocxEditModel.apply(doc(), e)).map { it.text })
    }
}

class DocxEditorTest {
    private val styles = "<w:styles $NS/>"
    private val media = byteArrayOf(1, 2, 3, 4, 5, 9, 8, 7)
    private fun file(body: String) = docx(body, "word/styles.xml" to styles, "word/media/image1.png" to media, "customXml/item1.xml" to "<x/>")

    @Test fun everyOtherPartOfTheFileIsCopiedThroughUnchanged() {
        val original = file(p(run("Hello")) + p(run("World")))
        val edited = DocxEditor.applyToBytes(original, DocxEdits().replace(0, "Changed"))
        val before = entries(original)
        val after = entries(edited)
        assertEquals(before.keys.toList(), after.keys.toList())
        for (name in before.keys - "word/document.xml") assertEquals(name, before.getValue(name).toList(), after.getValue(name).toList())
    }

    @Test fun replacingTextChangesOnlyThatParagraph() {
        val edited = DocxEditor.applyToBytes(file(p(run("Hello")) + p(run("World"))), DocxEdits().replace(0, "Hi there"))
        assertEquals(listOf("Hi there", "World"), texts(edited))
    }

    @Test fun theFirstRunsFormattingIsKeptAndOthersAreMerged() {
        val body = p(run("Bold ", "<w:b/>"), run("then plain "), run("end", "<w:i/>"))
        val doc = read(DocxEditor.applyToBytes(file(body), DocxEdits().replace(0, "Rewritten")))
        val runs = (doc.blocks.single() as DocxParagraph).runs
        assertEquals(1, runs.size)
        assertEquals("Rewritten", runs[0].text)
        assertTrue(runs[0].bold)
        assertFalse(runs[0].italic)
    }

    @Test fun paragraphPropertiesSurvive() {
        val body = p(run("Heading text"), ppr = "<w:pStyle w:val=\"Heading1\"/><w:jc w:val=\"center\"/>")
        val doc = read(DocxEditor.applyToBytes(file(body), DocxEdits().replace(0, "New heading")))
        val para = doc.blocks.single() as DocxParagraph
        assertEquals(1, para.headingLevel)
        assertEquals(io.github.amandhakar.documents.word.DocxAlign.Center, para.align)
    }

    @Test fun textInsideALinkCanBeReplaced() {
        val body = "<w:p>${run("See ")}<w:hyperlink w:anchor=\"x\">${run("this link")}</w:hyperlink></w:p>"
        assertEquals(listOf("Replaced"), texts(DocxEditor.applyToBytes(file(body), DocxEdits().replace(0, "Replaced"))))
    }

    @Test fun tabsAndLineBreaksComeBack() {
        val edited = DocxEditor.applyToBytes(file(p(run("x"))), DocxEdits().replace(0, "a\tb\nc"))
        assertEquals(listOf("a\tb\nc"), texts(edited))
    }

    @Test fun anEmptyParagraphCanGetText() {
        val edited = DocxEditor.applyToBytes(file("<w:p><w:pPr><w:jc w:val=\"right\"/></w:pPr></w:p>"), DocxEdits().replace(0, "now text"))
        val para = read(edited).blocks.single() as DocxParagraph
        assertEquals("now text", para.text)
        assertEquals(io.github.amandhakar.documents.word.DocxAlign.End, para.align)
    }

    @Test fun clearingKeepsTheParagraphButEmptiesIt() {
        val edited = DocxEditor.applyToBytes(file(p(run("gone")) + p(run("kept"))), DocxEdits().replace(0, ""))
        assertEquals(listOf("", "kept"), texts(edited))
    }

    @Test fun deletingRemovesTheParagraph() {
        assertEquals(listOf("a", "c"), texts(DocxEditor.applyToBytes(file(p(run("a")) + p(run("b")) + p(run("c"))), DocxEdits().delete(1))))
    }

    @Test fun deletingTheOnlyParagraphInATableCellJustEmptiesIt() {
        val body = "<w:tbl><w:tr><w:tc>${p(run("cell"))}</w:tc></w:tr></w:tbl>"
        val table = read(DocxEditor.applyToBytes(file(body), DocxEdits().delete(0))).blocks.single() as DocxTable
        assertEquals("", (table.rows[0][0][0] as DocxParagraph).text)
    }

    @Test fun deletingAParagraphThatHoldsAPictureKeepsThePicture() {
        val drawing = "<w:r><w:drawing><wp:inline><a:graphic><a:graphicData><a:blip r:embed=\"rId5\"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"
        val out = DocxEditor.applyToBytes(file("<w:p>${run("caption")}$drawing</w:p>"), DocxEdits().delete(0))
        val xml = String(entries(out).getValue("word/document.xml"))
        assertTrue(xml.contains("<w:drawing>"))
        assertFalse(xml.contains("caption"))
    }

    @Test fun deletingASectionBreakParagraphKeepsTheBreak() {
        val body = p(run("end of section"), ppr = "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/></w:sectPr>") + p(run("next"))
        val xml = String(entries(DocxEditor.applyToBytes(file(body), DocxEdits().delete(0))).getValue("word/document.xml"))
        assertTrue(xml.contains("<w:sectPr>"))
        assertFalse(xml.contains("end of section"))
    }

    @Test fun anAddedParagraphGoesAfterItsAnchorWithTheSameLook() {
        val body = p(run("first", "<w:b/>"), ppr = "<w:pStyle w:val=\"Heading2\"/>") + p(run("last"))
        val edited = DocxEditor.applyToBytes(file(body), DocxEdits().insertAfter(0, "inserted"))
        val doc = read(edited)
        val paragraphs = doc.blocks.filterIsInstance<DocxParagraph>()
        assertEquals(listOf("first", "inserted", "last"), paragraphs.map { it.text })
        assertEquals(2, paragraphs[1].headingLevel)
        assertTrue(paragraphs[1].runs.single().bold)
    }

    @Test fun addedParagraphsKeepTheirOrderAndChains() {
        val first = DocxEdits().insertAfter(0, "a")
        val edits = first.insertAfter(first.lastInsertId!!, "b").insertAfter(0, "c")
        val edited = DocxEditor.applyToBytes(file(p(run("start")) + p(run("end"))), edits)
        assertEquals(listOf("start", "a", "b", "c", "end"), texts(edited))
    }

    @Test fun aCopyDoesNotRepeatASectionBreakOrAPageBreakBefore() {
        val body = p(run("x"), ppr = "<w:pageBreakBefore/><w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/></w:sectPr>")
        val xml = String(entries(DocxEditor.applyToBytes(file(body), DocxEdits().insertAfter(0, "y"))).getValue("word/document.xml"))
        assertEquals(1, Regex("<w:sectPr>").findAll(xml).count())
        assertEquals(1, Regex("<w:pageBreakBefore/>").findAll(xml).count())
    }

    @Test fun addedParagraphsCanFollowADeletedOne() {
        val edits = DocxEdits().insertAfter(1, "kept").delete(1)
        assertEquals(listOf("a", "kept", "c"), texts(DocxEditor.applyToBytes(file(p(run("a")) + p(run("b")) + p(run("c"))), edits)))
    }

    @Test fun specialCharactersAreEscapedAndControlCharactersDropped() {
        val edited = DocxEditor.applyToBytes(file(p(run("x"))), DocxEdits().replace(0, "R&D <b> \"q\" \u0001ok"))
        assertEquals(listOf("R&D <b> \"q\" ok"), texts(edited))
    }

    @Test fun theDocumentsNamespacesAndIgnorableMarkupSurvive() {
        val xml = String(entries(DocxEditor.applyToBytes(file(p(run("x"))), DocxEdits().replace(0, "y"))).getValue("word/document.xml"))
        assertTrue(xml.contains("mc:Ignorable=\"w14\""))
        assertTrue(xml.contains("xmlns:w14="))
        assertTrue(xml.contains("standalone=\"yes\""))
    }

    @Test fun aParagraphNumberThatDoesNotExistIsRefused() {
        try {
            DocxEditor.applyToBytes(file(p(run("only"))), DocxEdits().replace(7, "x"))
            fail("expected an exception")
        } catch (e: OfficeEditException) {
            assertTrue(e.message!!.contains("can't be found"))
        }
    }

    @Test fun aFileThatIsNotAZipIsRefused() {
        try {
            DocxEditor.applyToBytes("not a zip".toByteArray(), DocxEdits())
            fail("expected an exception")
        } catch (e: OfficeEditException) {
            assertNotNull(e.message)
        }
    }

    @Test fun aZipWithoutADocumentIsRefused() {
        try {
            DocxEditor.applyToBytes(zip("readme.txt" to "hi"), DocxEdits())
            fail("expected an exception")
        } catch (e: OfficeEditException) {
            assertTrue(e.message!!.contains("document.xml"))
        }
    }

    @Test fun aDocumentWithADoctypeIsRefused() {
        val evil = zip("word/document.xml" to "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><w:document $NS><w:body/></w:document>")
        try {
            DocxEditor.applyToBytes(evil, DocxEdits())
            fail("expected an exception")
        } catch (e: OfficeEditException) {
            assertTrue(e.message!!.contains("unsafe"))
        }
    }

    @Test fun aZipBombIsStopped() {
        val big = file(p(run("x"))).let { docx(p(run("x")), "junk.bin" to ByteArray(2 * 1024 * 1024)) }
        try {
            DocxEditor.apply(ByteArrayInputStream(big), ByteArrayOutputStream(), DocxEdits(), io.github.amandhakar.documents.ooxml.OoxmlLimits(maxInflatedBytes = 1024 * 1024))
            fail("expected an exception")
        } catch (e: OfficeEditException) {
            assertTrue(e.message!!.contains("too big"))
        }
    }

    @Test fun noEditsGiveAFileThatReadsTheSame() {
        val original = file(p(run("Hello", "<w:b/>")) + p(run("World")))
        assertEquals(read(original).plainText(), read(DocxEditor.applyToBytes(original, DocxEdits())).plainText())
    }

    @Test fun theResultMatchesThePreviewOfTheSameEdits() {
        val original = file(p(run("one")) + p(run("two")) + "<w:tbl><w:tr><w:tc>${p(run("cell"))}</w:tc></w:tr></w:tbl>")
        val edits = DocxEdits().replace(0, "ONE").delete(1).insertAfter(0, "extra").replace(2, "new cell")
        val result = DocxEditor.applyToBytes(original, edits)
        val preview = DocxEditModel.apply(read(original), edits)
        assertTrue(DocxEditor.matches(ByteArrayInputStream(result), preview))
    }

    @Test fun aResultThatDoesNotMatchThePreviewIsCaught() {
        val original = file(p(run("one")))
        val result = DocxEditor.applyToBytes(original, DocxEdits().replace(0, "changed"))
        assertFalse(DocxEditor.matches(ByteArrayInputStream(result), read(original)))
        assertFalse(DocxEditor.matches(ByteArrayInputStream("garbage".toByteArray()), read(original)))
    }

    @Test fun paragraphsWithFieldsAreMarkedNotEditable() {
        val field = "<w:p><w:r><w:fldChar w:fldCharType=\"begin\"/></w:r><w:r><w:instrText>PAGE</w:instrText></w:r><w:r><w:fldChar w:fldCharType=\"end\"/></w:r>${run("3")}</w:p>"
        val doc = read(file(field + p(run("normal"))))
        val paragraphs = doc.blocks.filterIsInstance<DocxParagraph>()
        assertFalse(paragraphs[0].editable)
        assertTrue(paragraphs[1].editable)
    }

    @Test fun paragraphIdsAreTheirOrderInTheFileIncludingTables() {
        val doc = read(file(p(run("a")) + "<w:tbl><w:tr><w:tc>${p(run("b"))}</w:tc></w:tr></w:tbl>" + p(run("c"))))
        val ids = doc.blocks.flatMap { b -> if (b is DocxTable) b.rows.flatten().flatten() else listOf(b) }.filterIsInstance<DocxParagraph>().map { it.id }
        assertEquals(listOf(0, 1, 2), ids)
    }

    @Test fun editsLandOnTheRightParagraphWhenTablesComeFirst() {
        val body = "<w:tbl><w:tr><w:tc>${p(run("in table"))}</w:tc></w:tr></w:tbl>" + p(run("after"))
        val edited = DocxEditor.applyToBytes(file(body), DocxEdits().replace(1, "changed after"))
        val doc = read(edited)
        assertEquals("in table", ((doc.blocks[0] as DocxTable).rows[0][0][0] as DocxParagraph).text)
        assertEquals("changed after", (doc.blocks[1] as DocxParagraph).text)
    }

    @Test fun editingTwiceInARowKeepsWorking() {
        val once = DocxEditor.applyToBytes(file(p(run("a")) + p(run("b"))), DocxEdits().replace(0, "A"))
        val twice = DocxEditor.applyToBytes(once, DocxEdits().replace(1, "B"))
        assertEquals(listOf("A", "B"), texts(twice))
    }
}
