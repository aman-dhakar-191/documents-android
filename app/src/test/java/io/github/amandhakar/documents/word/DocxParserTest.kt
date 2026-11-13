package io.github.amandhakar.documents.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
    "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
    "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\""

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

private fun body(inner: String) = "<?xml version=\"1.0\"?><w:document $NS><w:body>$inner</w:body></w:document>"

private fun parse(vararg parts: Pair<String, Any>, limits: DocxLimits = DocxLimits()) =
    DocxParser.parse(ByteArrayInputStream(zip(*parts)), limits)

private fun p(text: String, props: String = "") = "<w:p>$props<w:r><w:t>$text</w:t></w:r></w:p>"

class DocxParserTest {
    @Test fun readsPlainParagraphs() {
        val doc = parse("word/document.xml" to body(p("Hello") + p("World")))
        assertEquals(listOf("Hello", "World"), doc.blocks.map { (it as DocxParagraph).text })
        assertFalse(doc.truncated)
    }

    @Test fun plainTextJoinsParagraphsWithNewlines() {
        val doc = parse("word/document.xml" to body(p("One") + p("Two")))
        assertEquals("One\nTwo", doc.plainText())
    }

    @Test fun readsBoldItalicUnderlineSizeAndColour() {
        val run = "<w:r><w:rPr><w:b/><w:i/><w:u w:val=\"single\"/><w:strike/><w:sz w:val=\"32\"/><w:color w:val=\"ff0000\"/></w:rPr><w:t>X</w:t></w:r>"
        val doc = parse("word/document.xml" to body("<w:p>$run</w:p>"))
        val r = (doc.blocks[0] as DocxParagraph).runs.single()
        assertTrue(r.bold && r.italic && r.underline && r.strike)
        assertEquals(32, r.sizeHalfPoints)
        assertEquals("FF0000", r.colorHex)
    }

    @Test fun aToggleSetToFalseIsOff() {
        val run = "<w:r><w:rPr><w:b w:val=\"false\"/><w:i w:val=\"0\"/><w:u w:val=\"none\"/></w:rPr><w:t>X</w:t></w:r>"
        val r = (parse("word/document.xml" to body("<w:p>$run</w:p>")).blocks[0] as DocxParagraph).runs.single()
        assertFalse(r.bold)
        assertFalse(r.italic)
        assertFalse(r.underline)
    }

    @Test fun autoColourAndBadColoursAreDropped() {
        fun colour(v: String): String? {
            val run = "<w:r><w:rPr><w:color w:val=\"$v\"/></w:rPr><w:t>X</w:t></w:r>"
            return (parse("word/document.xml" to body("<w:p>$run</w:p>")).blocks[0] as DocxParagraph).runs.single().colorHex
        }
        assertNull(colour("auto"))
        assertNull(colour("red"))
        assertNull(colour("12345"))
        assertNull(colour("GG0000"))
    }

    @Test fun mergesNeighbouringRunsWithTheSameLook() {
        val xml = "<w:p><w:r><w:t>Hel</w:t></w:r><w:r><w:t>lo</w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>!</w:t></w:r></w:p>"
        val runs = (parse("word/document.xml" to body(xml)).blocks[0] as DocxParagraph).runs
        assertEquals(listOf("Hello", "!"), runs.map { it.text })
    }

    @Test fun tabsAndLineBreaksBecomeCharacters_pageBreaksDoNot() {
        val xml = "<w:p><w:r><w:t>a</w:t><w:tab/><w:t>b</w:t><w:br/><w:t>c</w:t><w:br w:type=\"page\"/><w:t>d</w:t></w:r></w:p>"
        assertEquals("a\tb\ncd", (parse("word/document.xml" to body(xml)).blocks[0] as DocxParagraph).text)
    }

    @Test fun deletedTextIsNotShownButInsertedAndLinkedTextIs() {
        val xml = "<w:p><w:del><w:r><w:delText>gone</w:delText></w:r></w:del><w:ins><w:r><w:t>new</w:t></w:r></w:ins>" +
            "<w:hyperlink><w:r><w:t> link</w:t></w:r></w:hyperlink></w:p>"
        assertEquals("new link", (parse("word/document.xml" to body(xml)).blocks[0] as DocxParagraph).text)
    }

    @Test fun headingsComeFromTheStyleName() {
        val styles = "<w:styles $NS><w:style w:styleId=\"Kop1\"><w:name w:val=\"heading 1\"/></w:style>" +
            "<w:style w:styleId=\"Big\"><w:name w:val=\"Title\"/></w:style>" +
            "<w:style w:styleId=\"Plain\"><w:name w:val=\"Normal\"/></w:style></w:styles>"
        val doc = parse(
            "word/document.xml" to body(
                p("A", "<w:pPr><w:pStyle w:val=\"Kop1\"/></w:pPr>") + p("B", "<w:pPr><w:pStyle w:val=\"Big\"/></w:pPr>") +
                    p("C", "<w:pPr><w:pStyle w:val=\"Plain\"/></w:pPr>"),
            ),
            "word/styles.xml" to styles,
        )
        assertEquals(listOf(1, 1, 0), doc.blocks.map { (it as DocxParagraph).headingLevel })
    }

    @Test fun headingStyleIdWorksWithoutAStylesPart() {
        val doc = parse("word/document.xml" to body(p("A", "<w:pPr><w:pStyle w:val=\"Heading2\"/></w:pPr>")))
        assertEquals(2, (doc.blocks[0] as DocxParagraph).headingLevel)
    }

    @Test fun alignmentIsRead() {
        val doc = parse(
            "word/document.xml" to body(
                p("a", "<w:pPr><w:jc w:val=\"center\"/></w:pPr>") + p("b", "<w:pPr><w:jc w:val=\"right\"/></w:pPr>") +
                    p("c", "<w:pPr><w:jc w:val=\"both\"/></w:pPr>") + p("d"),
            ),
        )
        assertEquals(
            listOf(DocxAlign.Center, DocxAlign.End, DocxAlign.Justify, DocxAlign.Start),
            doc.blocks.map { (it as DocxParagraph).align },
        )
    }

    private val numbering = "<w:numbering $NS>" +
        "<w:abstractNum w:abstractNumId=\"0\"><w:lvl w:ilvl=\"0\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/></w:lvl>" +
        "<w:lvl w:ilvl=\"1\"><w:numFmt w:val=\"lowerLetter\"/></w:lvl></w:abstractNum>" +
        "<w:abstractNum w:abstractNumId=\"1\"><w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"bullet\"/></w:lvl></w:abstractNum>" +
        "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num><w:num w:numId=\"2\"><w:abstractNumId w:val=\"1\"/></w:num></w:numbering>"

    private fun item(text: String, numId: Int, level: Int = 0) =
        p(text, "<w:pPr><w:numPr><w:ilvl w:val=\"$level\"/><w:numId w:val=\"$numId\"/></w:numPr></w:pPr>")

    @Test fun numberedListsCountAndRestartLowerLevels() {
        val doc = parse(
            "word/document.xml" to body(item("a", 1) + item("b", 1, 1) + item("c", 1, 1) + item("d", 1) + item("e", 1, 1)),
            "word/numbering.xml" to numbering,
        )
        assertEquals(listOf("1.", "a.", "b.", "2.", "a."), doc.blocks.map { (it as DocxParagraph).list!!.label })
        assertEquals(listOf(0, 1, 1, 0, 1), doc.blocks.map { (it as DocxParagraph).list!!.level })
    }

    @Test fun bulletsUseASymbol() {
        val doc = parse("word/document.xml" to body(item("x", 2)), "word/numbering.xml" to numbering)
        assertEquals("•", (doc.blocks[0] as DocxParagraph).list!!.label)
    }

    @Test fun anUnknownListIsShownAsABullet() {
        val doc = parse("word/document.xml" to body(item("x", 9)))
        assertEquals("•", (doc.blocks[0] as DocxParagraph).list!!.label)
    }

    @Test fun numIdZeroMeansNoList() {
        val doc = parse("word/document.xml" to body(item("x", 0)), "word/numbering.xml" to numbering)
        assertNull((doc.blocks[0] as DocxParagraph).list)
    }

    private val listStyles = "<w:styles $NS>" +
        "<w:style w:styleId=\"ListBullet\"><w:name w:val=\"List Bullet\"/><w:pPr><w:numPr><w:numId w:val=\"2\"/></w:numPr></w:pPr></w:style>" +
        "<w:style w:styleId=\"MyBullet\"><w:name w:val=\"My bullet\"/><w:basedOn w:val=\"ListBullet\"/></w:style>" +
        "<w:style w:styleId=\"ListNumber\"><w:name w:val=\"List Number\"/><w:pPr><w:numPr><w:numId w:val=\"1\"/></w:numPr></w:pPr></w:style>" +
        "<w:style w:styleId=\"ListNumber2\"><w:name w:val=\"List Number 2\"/><w:pPr><w:numPr><w:numId w:val=\"1\"/></w:numPr></w:pPr></w:style>" +
        "<w:style w:styleId=\"LoopA\"><w:name w:val=\"a\"/><w:basedOn w:val=\"LoopB\"/></w:style>" +
        "<w:style w:styleId=\"LoopB\"><w:name w:val=\"b\"/><w:basedOn w:val=\"LoopA\"/></w:style></w:styles>"

    private fun styled(text: String, style: String) = p(text, "<w:pPr><w:pStyle w:val=\"$style\"/></w:pPr>")

    @Test fun aListStyleMakesItsParagraphsListItems() {
        val doc = parse(
            "word/document.xml" to body(styled("a", "ListBullet") + styled("b", "ListNumber") + styled("c", "ListNumber")),
            "word/styles.xml" to listStyles, "word/numbering.xml" to numbering,
        )
        assertEquals(listOf("\u2022", "1.", "2."), doc.blocks.map { (it as DocxParagraph).list!!.label })
    }

    @Test fun aStyleBasedOnAListStyleIsAListToo() {
        val doc = parse("word/document.xml" to body(styled("a", "MyBullet")), "word/styles.xml" to listStyles, "word/numbering.xml" to numbering)
        assertEquals("\u2022", (doc.blocks.single() as DocxParagraph).list!!.label)
    }

    @Test fun aParagraphWithNumIdZeroOverridesItsListStyle() {
        val xml = p("a", "<w:pPr><w:pStyle w:val=\"ListBullet\"/><w:numPr><w:numId w:val=\"0\"/></w:numPr></w:pPr>")
        val doc = parse("word/document.xml" to body(xml), "word/styles.xml" to listStyles, "word/numbering.xml" to numbering)
        assertNull((doc.blocks.single() as DocxParagraph).list)
    }

    @Test fun aStyleLoopDoesNotHangOrMakeAList() {
        val doc = parse("word/document.xml" to body(styled("a", "LoopA")), "word/styles.xml" to listStyles)
        assertNull((doc.blocks.single() as DocxParagraph).list)
    }

    @Test fun theLevelCanComeFromTheStyleLinkInTheNumberingPart() {
        val numbering2 = "<w:numbering $NS><w:abstractNum w:abstractNumId=\"0\">" +
            "<w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"decimal\"/><w:pStyle w:val=\"ListNumber\"/></w:lvl>" +
            "<w:lvl w:ilvl=\"1\"><w:numFmt w:val=\"lowerLetter\"/><w:pStyle w:val=\"ListNumber2\"/></w:lvl></w:abstractNum>" +
            "<w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num></w:numbering>"
        val doc = parse(
            "word/document.xml" to body(styled("a", "ListNumber") + styled("b", "ListNumber2")),
            "word/styles.xml" to listStyles, "word/numbering.xml" to numbering2,
        )
        assertEquals(listOf(0, 1), doc.blocks.map { (it as DocxParagraph).list!!.level })
        assertEquals(listOf("1.", "a."), doc.blocks.map { (it as DocxParagraph).list!!.label })
    }

    @Test fun aHeadingStyleBasedOnAHeadingIsAHeading() {
        val styles = "<w:styles $NS><w:style w:styleId=\"Big\"><w:name w:val=\"My big\"/><w:basedOn w:val=\"Heading2\"/></w:style>" +
            "<w:style w:styleId=\"Heading2\"><w:name w:val=\"heading 2\"/></w:style></w:styles>"
        val doc = parse("word/document.xml" to body(styled("a", "Big")), "word/styles.xml" to styles)
        assertEquals(2, (doc.blocks.single() as DocxParagraph).headingLevel)
    }

    @Test fun readsTablesWithRowsAndCells() {
        val xml = "<w:tbl><w:tr><w:tc>${p("a")}</w:tc><w:tc>${p("b")}</w:tc></w:tr><w:tr><w:tc>${p("c")}</w:tc><w:tc>${p("d")}</w:tc></w:tr></w:tbl>"
        val table = parse("word/document.xml" to body(xml)).blocks.single() as DocxTable
        assertEquals(2, table.rows.size)
        assertEquals(listOf("a", "b"), table.rows[0].map { (it.single() as DocxParagraph).text })
        assertEquals("a\tb\nc\td", DocxDocument(listOf(table)).plainText())
    }

    @Test fun contentControlsShowTheirContent() {
        val xml = "<w:sdt><w:sdtContent>${p("inside")}</w:sdtContent></w:sdt>"
        assertEquals("inside", (parse("word/document.xml" to body(xml)).blocks.single() as DocxParagraph).text)
    }

    private val png = byteArrayOf(1, 2, 3, 4)
    private val drawing = "<w:p><w:r><w:drawing><wp:inline><a:graphic><a:graphicData><a:blip r:embed=\"rId5\"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
    private val rels = "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId5\" Type=\"image\" Target=\"media/pic.png\"/>" +
        "<Relationship Id=\"rId6\" Type=\"hyperlink\" Target=\"https://example.com\" TargetMode=\"External\"/></Relationships>"

    @Test fun picturesAreFoundThroughTheirRelationship() {
        val doc = parse("word/document.xml" to body(drawing), "word/_rels/document.xml.rels" to rels, "word/media/pic.png" to png)
        val image = doc.blocks.filterIsInstance<DocxImage>().single()
        assertEquals("word/media/pic.png", image.name)
        assertEquals(png.toList(), image.bytes.toList())
    }

    @Test fun aParagraphThatOnlyHoldsAPictureLeavesNoEmptyParagraphBehind() {
        val doc = parse("word/document.xml" to body(drawing + drawing), "word/_rels/document.xml.rels" to rels, "word/media/pic.png" to png)
        assertEquals(2, doc.blocks.size)
        assertTrue(doc.blocks.all { it is DocxImage })
    }

    @Test fun aPictureWithTextInTheSameParagraphKeepsTheText() {
        val xml = "<w:p><w:r><w:t>caption</w:t></w:r><w:r><w:drawing><wp:inline><a:graphic><a:graphicData><a:blip r:embed=\"rId5\"/></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
        val doc = parse("word/document.xml" to body(xml), "word/_rels/document.xml.rels" to rels, "word/media/pic.png" to png)
        assertEquals("caption", (doc.blocks[0] as DocxParagraph).text)
        assertTrue(doc.blocks[1] is DocxImage)
    }

    @Test fun aPageBreakBeforeOnAPicturesParagraphMovesToThePicture() {
        val xml = drawing.replace("<w:p>", "<w:p><w:pPr><w:pageBreakBefore/></w:pPr>")
        val doc = parse("word/document.xml" to body(xml), "word/_rels/document.xml.rels" to rels, "word/media/pic.png" to png)
        assertEquals(1, doc.blocks.size)
        assertTrue((doc.blocks[0] as DocxImage).pageBreakBefore)
    }

    @Test fun aPageBreakAfterAPictureKeepsABreakParagraphAfterIt() {
        val xml = drawing.replace("</w:drawing></w:r>", "</w:drawing><w:br w:type=\"page\"/></w:r>")
        val doc = parse("word/document.xml" to body(xml), "word/_rels/document.xml.rels" to rels, "word/media/pic.png" to png)
        assertTrue(doc.blocks[0] is DocxImage)
        assertTrue((doc.blocks[1] as DocxParagraph).pageBreakAfter)
    }

    @Test fun aMissingPictureIsSkipped() {
        val doc = parse("word/document.xml" to body(drawing), "word/_rels/document.xml.rels" to rels)
        assertTrue(doc.blocks.none { it is DocxImage })
    }

    @Test fun pictureTypesWeCantShowAreSkipped() {
        val emf = rels.replace("media/pic.png", "media/pic.emf")
        val doc = parse("word/document.xml" to body(drawing), "word/_rels/document.xml.rels" to emf, "word/media/pic.emf" to png)
        assertTrue(doc.blocks.none { it is DocxImage })
    }

    @Test fun oversizedPicturesAreSkippedNotFatal() {
        val doc = parse(
            "word/document.xml" to body(p("text") + drawing), "word/_rels/document.xml.rels" to rels,
            "word/media/pic.png" to ByteArray(100),
            limits = DocxLimits(maxImageBytes = 50),
        )
        assertTrue(doc.blocks.none { it is DocxImage })
        assertEquals("text", (doc.blocks[0] as DocxParagraph).text)
    }

    @Test fun relationshipTargetsAreNormalised() {
        assertEquals("word/media/a.png", DocxParser.resolve("media/a.png"))
        assertEquals("word/media/a.png", DocxParser.resolve("/word/media/a.png"))
        assertEquals("media/a.png", DocxParser.resolve("../media/a.png"))
        assertEquals("a.png", DocxParser.resolve("../../../a.png"))
    }

    private fun failure(block: () -> Unit): String {
        try {
            block()
        } catch (e: DocxException) {
            return e.message.orEmpty()
        }
        fail("expected a DocxException")
        return ""
    }

    @Test fun aFileThatIsNotAZipIsRefused() {
        val message = failure { DocxParser.parse(ByteArrayInputStream("hello, not a zip".toByteArray())) }
        assertTrue(message.contains("valid .docx"))
    }

    @Test fun anEmptyFileIsRefused() {
        failure { DocxParser.parse(ByteArrayInputStream(ByteArray(0))) }
    }

    @Test fun aZipWithoutADocumentIsRefused() {
        val message = failure { parse("readme.txt" to "hi") }
        assertTrue(message.contains("document.xml"))
    }

    @Test fun aDocumentWithoutABodyIsRefused() {
        failure { parse("word/document.xml" to "<w:document $NS></w:document>") }
    }

    @Test fun brokenXmlIsRefused() {
        failure { parse("word/document.xml" to "<w:document $NS><w:body><w:p>") }
    }

    @Test fun aDoctypeIsRefusedSoEntityTricksCannotRun() {
        val evil = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" + body(p("&x;")).substringAfter("?>")
        val message = failure { parse("word/document.xml" to evil) }
        assertTrue(message.contains("unsafe"))
    }

    @Test fun anEntityDeclarationAloneIsRefused() {
        failure { parse("word/document.xml" to body(p("x")).replace("<w:body>", "<!ENTITY a \"b\"><w:body>")) }
    }

    @Test fun anOversizedDocumentPartIsRefused() {
        val message = failure { parse("word/document.xml" to body(p("x".repeat(500))), limits = DocxLimits(maxXmlPartBytes = 100)) }
        assertTrue(message.contains("too big"))
    }

    @Test fun aZipBombIsStopped() {
        // About 2 MB of zeros squeezes to a few KB; the limit is far below that when unpacked.
        val message = failure {
            parse("word/document.xml" to body(p("x")), "junk.bin" to ByteArray(2 * 1024 * 1024), limits = DocxLimits(maxInflatedBytes = 1024 * 1024))
        }
        assertTrue(message.contains("unpacks"))
    }

    @Test fun tooManyPartsIsRefused() {
        val parts = (1..20).map { "x$it.txt" to "a" }.toTypedArray()
        failure { parse("word/document.xml" to body(p("x")), *parts, limits = DocxLimits(maxEntries = 10)) }
    }

    @Test fun tooManyBlocksIsTruncatedNotFatal() {
        val doc = parse("word/document.xml" to body((1..10).joinToString("") { p("p$it") }), limits = DocxLimits(maxBlocks = 3))
        assertEquals(3, doc.blocks.size)
        assertTrue(doc.truncated)
    }
}

class NumberLabelsTest {
    @Test fun decimalAndDefault() {
        assertEquals("3.", NumberLabels.label("decimal", 3, 0))
        assertEquals("3.", NumberLabels.label("somethingElse", 3, 0))
    }

    @Test fun letters() {
        assertEquals("a", NumberLabels.letters(1, false))
        assertEquals("z", NumberLabels.letters(26, false))
        assertEquals("aa", NumberLabels.letters(27, false))
        assertEquals("AB", NumberLabels.letters(28, true))
        assertEquals("", NumberLabels.letters(0, false))
    }

    @Test fun romanNumerals() {
        assertEquals("IV", NumberLabels.roman(4))
        assertEquals("MCMXCIV", NumberLabels.roman(1994))
        assertEquals("0", NumberLabels.roman(0))
        assertEquals("4000", NumberLabels.roman(4000))
        assertEquals("iii.", NumberLabels.label("lowerRoman", 3, 0))
    }

    @Test fun bulletsDifferByLevel() {
        assertEquals("•", NumberLabels.label("bullet", 1, 0))
        assertEquals("◦", NumberLabels.label("bullet", 1, 1))
        assertEquals("▪", NumberLabels.label("bullet", 1, 5))
    }

    @Test fun noneGivesNothing() = assertEquals("", NumberLabels.label("none", 1, 0))
}

class ListCounterTest {
    @Test fun countsUpPerListAndLevel() {
        val c = ListCounter()
        assertEquals(1, c.next("1", 0))
        assertEquals(2, c.next("1", 0))
        assertEquals(1, c.next("2", 0))
        assertEquals(1, c.next("1", 1))
        assertEquals(2, c.next("1", 1))
    }

    @Test fun aHigherLevelRestartsTheLowerOnes() {
        val c = ListCounter()
        c.next("1", 0)
        c.next("1", 1)
        c.next("1", 1)
        c.next("1", 0)
        assertEquals(1, c.next("1", 1))
    }

    @Test fun startValueIsHonoured() {
        val c = ListCounter()
        assertEquals(5, c.next("1", 0, start = 5))
        assertEquals(6, c.next("1", 0, start = 5))
    }
}
