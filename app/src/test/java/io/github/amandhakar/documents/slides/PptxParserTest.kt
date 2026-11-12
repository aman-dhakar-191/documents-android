package io.github.amandhakar.documents.slides

import io.github.amandhakar.documents.ooxml.OoxmlException
import io.github.amandhakar.documents.ooxml.OoxmlLimits
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

private const val NS = "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
    "xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" " +
    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""

private const val REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
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

private fun rels(vararg entries: Triple<String, String, String>) =
    "<Relationships xmlns=\"$REL_NS\">" + entries.joinToString("") { "<Relationship Id=\"${it.first}\" Type=\"$RT/${it.second}\" Target=\"${it.third}\"/>" } + "</Relationships>"

private fun shapeTree(inner: String) = "<p:cSld><p:spTree><p:nvGrpSpPr/><p:grpSpPr/>$inner</p:spTree></p:cSld>"

/** A tiny but complete deck. Pass bodies for the slide, the layout and the master; the rest is filled in. */
private class Deck(
    val slide: String,
    val layout: String = "",
    val masterShapes: String = "",
    val txStyles: String = "",
    val theme: String = "<a:theme $NS><a:themeElements><a:clrScheme name=\"t\">" +
        "<a:dk1><a:sysClr val=\"windowText\" lastClr=\"000000\"/></a:dk1><a:lt1><a:sysClr val=\"window\" lastClr=\"FFFFFF\"/></a:lt1>" +
        "<a:dk2><a:srgbClr val=\"1F497D\"/></a:dk2><a:lt2><a:srgbClr val=\"EEECE1\"/></a:lt2>" +
        "<a:accent1><a:srgbClr val=\"4F81BD\"/></a:accent1><a:accent2><a:srgbClr val=\"C0504D\"/></a:accent2>" +
        "</a:clrScheme></a:themeElements></a:theme>",
    val masterBg: String = "",
    val slideBg: String = "",
    val extraSlideRels: List<Triple<String, String, String>> = emptyList(),
    val extra: Map<String, Any> = emptyMap(),
    val defaultTextStyle: String = "",
    val slides: Int = 1,
) {
    fun bytes(): ByteArray {
        val parts = LinkedHashMap<String, Any>()
        parts["ppt/presentation.xml"] = "<p:presentation $NS><p:sldIdLst>" +
            (1..slides).joinToString("") { "<p:sldId id=\"${255 + it}\" r:id=\"rId$it\"/>" } +
            "</p:sldIdLst><p:sldSz cx=\"9144000\" cy=\"6858000\"/>$defaultTextStyle</p:presentation>"
        parts["ppt/_rels/presentation.xml.rels"] = rels(*(1..slides).map { Triple("rId$it", "slide", "slides/slide$it.xml") }.toTypedArray())
        for (i in 1..slides) {
            parts["ppt/slides/slide$i.xml"] = "<p:sld $NS>${slideBg}${shapeTree(slide)}</p:sld>".let {
                if (slideBg.isEmpty()) it else "<p:sld $NS><p:cSld>$slideBg<p:spTree><p:nvGrpSpPr/><p:grpSpPr/>$slide</p:spTree></p:cSld></p:sld>"
            }
            parts["ppt/slides/_rels/slide$i.xml.rels"] =
                rels(Triple("rId1", "slideLayout", "../slideLayouts/slideLayout1.xml"), *extraSlideRels.toTypedArray())
        }
        parts["ppt/slideLayouts/slideLayout1.xml"] = "<p:sldLayout $NS>${shapeTree(layout)}</p:sldLayout>"
        parts["ppt/slideLayouts/_rels/slideLayout1.xml.rels"] = rels(Triple("rId1", "slideMaster", "../slideMasters/slideMaster1.xml"))
        parts["ppt/slideMasters/slideMaster1.xml"] = "<p:sldMaster $NS>" +
            (if (masterBg.isEmpty()) shapeTree(masterShapes) else "<p:cSld>$masterBg<p:spTree><p:nvGrpSpPr/><p:grpSpPr/>$masterShapes</p:spTree></p:cSld>") +
            "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\"/>$txStyles</p:sldMaster>"
        parts["ppt/slideMasters/_rels/slideMaster1.xml.rels"] = rels(Triple("rId1", "theme", "../theme/theme1.xml"))
        parts["ppt/theme/theme1.xml"] = theme
        parts.putAll(extra)
        return zipOf(parts)
    }

    fun parse(limits: PptxLimits = PptxLimits()) = PptxParser.parse(ByteArrayInputStream(bytes()), limits)
}

private fun xfrm(x: Int, y: Int, w: Int, h: Int) = "<a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$w\" cy=\"$h\"/></a:xfrm>"

private fun textBox(
    text: String,
    rPrAttrs: String = "",
    rPrInner: String = "",
    xf: String = xfrm(100, 200, 3000, 400),
    bodyPr: String = "<a:bodyPr/>",
) = "<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"t\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>$xf</p:spPr>" +
    "<p:txBody>$bodyPr<a:p><a:r>${if (rPrAttrs.isEmpty() && rPrInner.isEmpty()) "" else "<a:rPr $rPrAttrs>$rPrInner</a:rPr>"}<a:t>$text</a:t></a:r></a:p></p:txBody></p:sp>"

private fun placeholder(type: String?, idx: Int?, text: String, xf: String = "", bodyPr: String = "<a:bodyPr/>") =
    "<p:sp><p:nvSpPr><p:cNvPr id=\"3\" name=\"ph\"/><p:cNvSpPr/><p:nvPr><p:ph${if (type != null) " type=\"$type\"" else ""}${if (idx != null) " idx=\"$idx\"" else ""}/></p:nvPr></p:nvSpPr>" +
        "<p:spPr>$xf</p:spPr><p:txBody>$bodyPr<a:p><a:r><a:t>$text</a:t></a:r></a:p></p:txBody></p:sp>"

private fun texts(deck: PptxDeck, slide: Int = 0) = deck.slides[slide].shapes.filterIsInstance<PptxText>()

class PptxParserTest {
    @Test fun readsTheSlideSizeAndSlides() {
        val deck = Deck(textBox("Hi"), slides = 3).parse()
        assertEquals(9144000L, deck.widthEmu)
        assertEquals(6858000L, deck.heightEmu)
        assertEquals(3, deck.slides.size)
        assertFalse(deck.truncated)
    }

    @Test fun aDeckWithNoStatedSizeGetsStandardWidescreen() {
        val bytes = Deck(textBox("x")).bytes()
        // Rebuild the same deck with the slide size left out.
        val zip = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes))
        val parts = LinkedHashMap<String, Any>()
        while (true) {
            val e = zip.nextEntry ?: break
            val text = zip.readBytes().toString(Charsets.UTF_8)
            parts[e.name] = if (e.name == "ppt/presentation.xml") text.replace("<p:sldSz cx=\"9144000\" cy=\"6858000\"/>", "") else text
        }
        val deck = PptxParser.parse(ByteArrayInputStream(zipOf(parts)))
        assertEquals(12_192_000L, deck.widthEmu)
        assertEquals(6_858_000L, deck.heightEmu)
    }

    @Test fun aTextBoxKeepsItsPositionAndTextFormatting() {
        val deck = Deck(textBox("Hello", "sz=\"2400\" b=\"1\" i=\"1\" u=\"sng\"", "<a:solidFill><a:srgbClr val=\"B3261E\"/></a:solidFill>")).parse()
        val t = texts(deck).single()
        assertEquals(EmuBox(100, 200, 3000, 400), t.box)
        val run = t.paragraphs.single().runs.single()
        assertEquals("Hello", run.text)
        assertEquals(24f, run.sizePt, 0f)
        assertTrue(run.bold && run.italic && run.underline)
        assertEquals("B3261E", run.colorHex)
    }

    @Test fun textWithNoSizeUsesTheDefaultTextStyleThenEighteenPoints() {
        assertEquals(18f, texts(Deck(textBox("x")).parse()).single().paragraphs.single().runs.single().sizePt, 0f)
        val style = "<p:defaultTextStyle><a:lvl1pPr><a:defRPr sz=\"2000\"/></a:lvl1pPr></p:defaultTextStyle>"
        assertEquals(20f, texts(Deck(textBox("x"), defaultTextStyle = style).parse()).single().paragraphs.single().runs.single().sizePt, 0f)
    }

    @Test fun aPlaceholderTakesItsPositionFromTheLayoutThenTheMaster() {
        val layout = placeholder("title", null, "", xfrm(1, 2, 3, 4))
        val master = placeholder("body", 1, "", xfrm(10, 20, 30, 40))
        val deck = Deck(placeholder("title", null, "T") + placeholder("body", 1, "B"), layout = layout, masterShapes = master).parse()
        val boxes = texts(deck).map { it.box }
        assertEquals(listOf(EmuBox(1, 2, 3, 4), EmuBox(10, 20, 30, 40)), boxes)
    }

    @Test fun aPlaceholderWithItsOwnPositionKeepsIt() {
        val deck = Deck(placeholder("title", null, "T", xfrm(5, 6, 7, 8)), layout = placeholder("title", null, "", xfrm(1, 2, 3, 4))).parse()
        assertEquals(EmuBox(5, 6, 7, 8), texts(deck).single().box)
    }

    @Test fun aPlaceholderWithNoPositionAnywhereIsLeftOut() {
        assertTrue(Deck(placeholder("title", null, "T")).parse().slides[0].shapes.isEmpty())
    }

    private val txStyles = "<p:txStyles><p:titleStyle><a:lvl1pPr algn=\"ctr\"><a:defRPr sz=\"4400\" b=\"1\"/></a:lvl1pPr></p:titleStyle>" +
        "<p:bodyStyle><a:lvl1pPr><a:buChar char=\"•\"/><a:defRPr sz=\"3200\"/></a:lvl1pPr><a:lvl2pPr><a:buChar char=\"-\"/><a:defRPr sz=\"2800\"/></a:lvl2pPr></p:bodyStyle></p:txStyles>"

    @Test fun titleAndBodyTextStylesComeFromTheMaster() {
        val master = placeholder("title", null, "", xfrm(0, 0, 100, 100)) + placeholder("body", 1, "", xfrm(0, 200, 100, 100))
        val deck = Deck(placeholder("title", null, "T") + placeholder("body", 1, "B"), masterShapes = master, txStyles = txStyles).parse()
        val (title, body) = texts(deck)
        val tp = title.paragraphs.single()
        assertEquals(44f, tp.runs.single().sizePt, 0f)
        assertTrue(tp.runs.single().bold)
        assertEquals(PptxAlign.Center, tp.align)
        assertNull(tp.bullet)
        val bp = body.paragraphs.single()
        assertEquals(32f, bp.runs.single().sizePt, 0f)
        assertEquals("•", bp.bullet)
        assertFalse(bp.runs.single().bold)
    }

    @Test fun aSecondLevelUsesTheLevelTwoStyle() {
        val master = placeholder("body", 1, "", xfrm(0, 0, 100, 100))
        val slide = "<p:sp><p:nvSpPr><p:cNvPr id=\"3\" name=\"b\"/><p:cNvSpPr/><p:nvPr><p:ph idx=\"1\"/></p:nvPr></p:nvSpPr><p:spPr/>" +
            "<p:txBody><a:bodyPr/><a:p><a:pPr lvl=\"1\"/><a:r><a:t>sub</a:t></a:r></a:p></p:txBody></p:sp>"
        val p = texts(Deck(slide, masterShapes = master, txStyles = txStyles).parse()).single().paragraphs.single()
        assertEquals(1, p.level)
        assertEquals("-", p.bullet)
        assertEquals(28f, p.runs.single().sizePt, 0f)
    }

    @Test fun aParagraphCanTurnTheBulletOff() {
        val master = placeholder("body", 1, "", xfrm(0, 0, 100, 100))
        val slide = "<p:sp><p:nvSpPr><p:cNvPr id=\"3\" name=\"b\"/><p:cNvSpPr/><p:nvPr><p:ph idx=\"1\"/></p:nvPr></p:nvSpPr><p:spPr/>" +
            "<p:txBody><a:bodyPr/><a:p><a:pPr><a:buNone/></a:pPr><a:r><a:t>plain</a:t></a:r></a:p></p:txBody></p:sp>"
        assertNull(texts(Deck(slide, masterShapes = master, txStyles = txStyles).parse()).single().paragraphs.single().bullet)
    }

    @Test fun numberedBulletsCountAndRestartAfterAPlainParagraph() {
        fun para(num: Boolean, t: String) = "<a:p><a:pPr>${if (num) "<a:buAutoNum type=\"arabicPeriod\"/>" else "<a:buNone/>"}</a:pPr><a:r><a:t>$t</a:t></a:r></a:p>"
        val slide = "<p:sp><p:nvSpPr><p:cNvPr id=\"3\" name=\"b\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>${xfrm(0, 0, 10, 10)}</p:spPr>" +
            "<p:txBody><a:bodyPr/>${para(true, "a")}${para(true, "b")}${para(false, "-")}${para(true, "c")}</p:txBody></p:sp>"
        val labels = texts(Deck(slide).parse()).single().paragraphs.map { it.bullet }
        assertEquals(listOf("1.", "2.", null, "1."), labels)
    }

    @Test fun autofitShrinksTheText() {
        val deck = Deck(textBox("x", "sz=\"2000\"", bodyPr = "<a:bodyPr><a:normAutofit fontScale=\"50000\"/></a:bodyPr>")).parse()
        assertEquals(10f, texts(deck).single().paragraphs.single().runs.single().sizePt, 0f)
    }

    @Test fun themeColoursAreResolvedThroughTheMappingAndLightnessChanges() {
        fun colour(clr: String) = texts(
            Deck(textBox("x", "sz=\"1800\"", "<a:solidFill>$clr</a:solidFill>")).parse(),
        ).single().paragraphs.single().runs.single().colorHex
        assertEquals("4F81BD", colour("<a:schemeClr val=\"accent1\"/>"))
        assertEquals("FFFFFF", colour("<a:schemeClr val=\"bg1\"/>"))
        assertEquals("1F497D", colour("<a:schemeClr val=\"tx2\"/>"))
        assertEquals("000000", colour("<a:schemeClr val=\"nonsense\"/>")) // unknown: falls back to the default text colour
        val lighter = colour("<a:schemeClr val=\"accent1\"><a:lumMod val=\"40000\"/><a:lumOff val=\"60000\"/></a:schemeClr>")
        assertTrue(lighter != "4F81BD" && lighter != "000000")
    }

    @Test fun textDefaultsToTheThemesTextColour() {
        assertEquals("000000", texts(Deck(textBox("x")).parse()).single().paragraphs.single().runs.single().colorHex)
    }

    @Test fun shapesWithAFillOrLineAreKeptEvenWithoutText() {
        val rect = "<p:sp><p:nvSpPr><p:cNvPr id=\"4\" name=\"r\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>${xfrm(0, 0, 50, 50)}" +
            "<a:prstGeom prst=\"ellipse\"/><a:solidFill><a:srgbClr val=\"00FF00\"/></a:solidFill><a:ln><a:solidFill><a:srgbClr val=\"0000FF\"/></a:solidFill></a:ln></p:spPr></p:sp>"
        val empty = "<p:sp><p:nvSpPr><p:cNvPr id=\"5\" name=\"e\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>${xfrm(0, 0, 50, 50)}</p:spPr></p:sp>"
        val shapes = texts(Deck(rect + empty).parse())
        assertEquals(1, shapes.size)
        assertEquals("00FF00", shapes[0].fillHex)
        assertEquals("0000FF", shapes[0].lineHex)
        assertEquals(PptxGeometry.Ellipse, shapes[0].geometry)
    }

    @Test fun aStyleFillRefGivesAnAutoshapeItsThemeColour() {
        val shape = "<p:sp><p:nvSpPr><p:cNvPr id=\"4\" name=\"r\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>${xfrm(0, 0, 50, 50)}</p:spPr>" +
            "<p:style><a:lnRef idx=\"0\"><a:schemeClr val=\"accent2\"/></a:lnRef><a:fillRef idx=\"1\"><a:schemeClr val=\"accent1\"/></a:fillRef></p:style></p:sp>"
        val t = texts(Deck(shape).parse()).single()
        assertEquals("4F81BD", t.fillHex)
        assertNull(t.lineHex)
    }

    @Test fun anAutoshapesTextTakesTheStylesFontColour() {
        fun shape(rPr: String) = "<p:sp><p:nvSpPr><p:cNvPr id=\"4\" name=\"r\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>${xfrm(0, 0, 50, 50)}<a:solidFill><a:srgbClr val=\"1D5FD6\"/></a:solidFill></p:spPr>" +
            "<p:style><a:fontRef idx=\"minor\"><a:schemeClr val=\"lt1\"/></a:fontRef></p:style>" +
            "<p:txBody><a:bodyPr/><a:p><a:r>$rPr<a:t>Hi</a:t></a:r></a:p></p:txBody></p:sp>"
        assertEquals("FFFFFF", texts(Deck(shape("")).parse()).single().paragraphs.single().runs.single().colorHex)
        // The run's own colour still wins.
        assertEquals("112233", texts(Deck(shape("<a:rPr><a:solidFill><a:srgbClr val=\"112233\"/></a:solidFill></a:rPr>")).parse()).single().paragraphs.single().runs.single().colorHex)
    }

    @Test fun anExplicitNoFillWins() {
        val shape = "<p:sp><p:nvSpPr><p:cNvPr id=\"4\" name=\"r\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>${xfrm(0, 0, 50, 50)}<a:noFill/></p:spPr>" +
            "<p:style><a:fillRef idx=\"1\"><a:schemeClr val=\"accent1\"/></a:fillRef></p:style></p:sp>"
        assertTrue(Deck(shape).parse().slides[0].shapes.isEmpty())
    }

    @Test fun dateFooterAndSlideNumberPlaceholdersAreLeftOut() {
        val deck = Deck(
            placeholder("dt", null, "date", xfrm(0, 0, 1, 1)) + placeholder("ftr", null, "foot", xfrm(0, 0, 1, 1)) +
                placeholder("sldNum", null, "1", xfrm(0, 0, 1, 1)) + textBox("kept"),
        ).parse()
        assertEquals(listOf("kept"), texts(deck).map { it.paragraphs.single().text })
    }

    private val png = byteArrayOf(9, 8, 7)
    private fun picture(embed: String) = "<p:pic><p:nvPicPr><p:cNvPr id=\"6\" name=\"p\"/><p:cNvPicPr/><p:nvPr/></p:nvPicPr>" +
        "<p:blipFill><a:blip r:embed=\"$embed\"/></p:blipFill><p:spPr>${xfrm(10, 20, 300, 200)}</p:spPr></p:pic>"

    @Test fun picturesAreFoundThroughTheSlideRelationships() {
        val deck = Deck(
            picture("rId9"),
            extraSlideRels = listOf(Triple("rId9", "image", "../media/pic.png")),
            extra = mapOf("ppt/media/pic.png" to png),
        ).parse()
        val pic = deck.slides[0].shapes.single() as PptxPicture
        assertEquals("ppt/media/pic.png", pic.name)
        assertEquals(png.toList(), pic.bytes.toList())
        assertEquals(EmuBox(10, 20, 300, 200), pic.box)
    }

    @Test fun aMissingPictureIsShownAsALabelledBox() {
        val shape = Deck(picture("rId9"), extraSlideRels = listOf(Triple("rId9", "image", "../media/none.png"))).parse().slides[0].shapes.single()
        assertEquals("Picture not shown", (shape as PptxNotShown).label)
    }

    @Test fun aPictureOfATypeWeCantShowIsALabelledBox() {
        val deck = Deck(
            picture("rId9"),
            extraSlideRels = listOf(Triple("rId9", "image", "../media/pic.emf")),
            extra = mapOf("ppt/media/pic.emf" to png),
        ).parse()
        assertTrue(deck.slides[0].shapes.single() is PptxNotShown)
    }

    private fun frame(dataUri: String, inner: String) =
        "<p:graphicFrame><p:nvGraphicFramePr><p:cNvPr id=\"7\" name=\"f\"/><p:cNvGraphicFramePr/><p:nvPr/></p:nvGraphicFramePr>" +
            "<p:xfrm><a:off x=\"5\" y=\"6\"/><a:ext cx=\"700\" cy=\"300\"/></p:xfrm><a:graphic><a:graphicData uri=\"$dataUri\">$inner</a:graphicData></a:graphic></p:graphicFrame>"

    @Test fun tablesKeepTheirCellsAndColumnWidths() {
        fun cell(t: String, fill: String = "") = "<a:tc><a:txBody><a:bodyPr/><a:p><a:r><a:t>$t</a:t></a:r></a:p></a:txBody><a:tcPr>$fill</a:tcPr></a:tc>"
        val tbl = "<a:tbl><a:tblGrid><a:gridCol w=\"400\"/><a:gridCol w=\"300\"/></a:tblGrid>" +
            "<a:tr h=\"150\">${cell("A", "<a:solidFill><a:srgbClr val=\"FFEECC\"/></a:solidFill>")}${cell("B")}</a:tr><a:tr h=\"150\">${cell("C")}${cell("D")}</a:tr></a:tbl>"
        val table = Deck(frame("http://schemas.openxmlformats.org/drawingml/2006/table", tbl)).parse().slides[0].shapes.single() as PptxTable
        assertEquals(EmuBox(5, 6, 700, 300), table.box)
        assertEquals(listOf(400L, 300L), table.columnWidthsEmu)
        assertEquals(listOf(listOf("A", "B"), listOf("C", "D")), table.rows.map { r -> r.cells.map { it.paragraphs.single().text } })
        assertEquals("FFEECC", table.rows[0].cells[0].fillHex)
        assertNull(table.rows[0].cells[1].fillHex)
    }

    @Test fun chartsSmartArtAndObjectsAreLabelledBoxes() {
        fun label(uri: String) = (Deck(frame(uri, "")).parse().slides[0].shapes.single() as PptxNotShown).label
        assertEquals("Chart not shown", label("http://schemas.openxmlformats.org/drawingml/2006/chart"))
        assertEquals("SmartArt not shown", label("http://schemas.openxmlformats.org/drawingml/2006/diagram"))
        assertEquals("Object not shown", label("http://example.com/ole"))
    }

    @Test fun groupsMoveAndScaleTheirChildren() {
        val group = "<p:grpSp><p:nvGrpSpPr><p:cNvPr id=\"8\" name=\"g\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr>" +
            "<a:xfrm><a:off x=\"1000\" y=\"2000\"/><a:ext cx=\"2000\" cy=\"2000\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"1000\" cy=\"1000\"/></a:xfrm></p:grpSpPr>" +
            textBox("in group", xf = xfrm(100, 200, 300, 400)) + "</p:grpSp>"
        assertEquals(EmuBox(1200, 2400, 600, 800), texts(Deck(group).parse()).single().box)
    }

    @Test fun backgroundsFallBackFromSlideToMasterToWhite() {
        assertEquals("FFFFFF", Deck(textBox("x")).parse().slides[0].backgroundHex)
        val bg = "<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"102030\"/></a:solidFill></p:bgPr></p:bg>"
        assertEquals("102030", Deck(textBox("x"), masterBg = bg).parse().slides[0].backgroundHex)
        val own = "<p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"AABBCC\"/></a:solidFill></p:bgPr></p:bg>"
        assertEquals("AABBCC", Deck(textBox("x"), masterBg = bg, slideBg = own).parse().slides[0].backgroundHex)
    }

    @Test fun aBackgroundCanBeAThemeColour() {
        val bg = "<p:bg><p:bgRef idx=\"1001\"><a:schemeClr val=\"bg2\"/></p:bgRef></p:bg>"
        assertEquals("EEECE1", Deck(textBox("x"), masterBg = bg).parse().slides[0].backgroundHex)
    }

    @Test fun speakerNotesAreRead() {
        val notes = "<p:notes $NS>${shapeTree(placeholder("body", 1, "first") + placeholder("sldImg", null, "") )}</p:notes>"
        val deck = Deck(
            textBox("x"),
            extraSlideRels = listOf(Triple("rId5", "notesSlide", "../notesSlides/notesSlide1.xml")),
            extra = mapOf("ppt/notesSlides/notesSlide1.xml" to notes),
        ).parse()
        assertEquals("first", deck.slides[0].notes)
    }

    @Test fun plainTextAndTitleSummariseASlide() {
        val deck = Deck(textBox("Hello") + textBox("World", xf = xfrm(0, 500, 10, 10))).parse()
        assertEquals("Hello\nWorld", deck.slides[0].plainText())
        assertEquals("Hello", deck.slides[0].title())
    }

    @Test fun mergesNeighbouringRunsWithTheSameLook() {
        val shape = "<p:sp><p:nvSpPr><p:cNvPr id=\"3\" name=\"t\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr><p:spPr>${xfrm(0, 0, 10, 10)}</p:spPr>" +
            "<p:txBody><a:bodyPr/><a:p><a:r><a:t>Hel</a:t></a:r><a:r><a:t>lo</a:t></a:r><a:r><a:rPr b=\"1\"/><a:t>!</a:t></a:r></a:p></p:txBody></p:sp>"
        assertEquals(listOf("Hello", "!"), texts(Deck(shape).parse()).single().paragraphs.single().runs.map { it.text })
    }

    @Test fun tooManySlidesAreCutOffAndSaid() {
        val deck = Deck(textBox("x"), slides = 5).parse(PptxLimits(maxSlides = 2))
        assertEquals(2, deck.slides.size)
        assertTrue(deck.truncated)
    }

    @Test fun tooManyShapesOnASlideAreCutOff() {
        val many = (1..10).joinToString("") { textBox("s$it") }
        assertEquals(3, Deck(many).parse(PptxLimits(maxShapesPerSlide = 3)).slides[0].shapes.size)
    }

    private fun failure(block: () -> Unit): String {
        try {
            block()
        } catch (e: OoxmlException) {
            return e.message.orEmpty()
        }
        fail("expected an OoxmlException")
        return ""
    }

    @Test fun aFileThatIsNotAZipIsRefused() {
        assertTrue(failure { PptxParser.parse(ByteArrayInputStream("not a zip".toByteArray())) }.contains("valid Office"))
    }

    @Test fun aZipWithoutAPresentationIsRefused() {
        assertTrue(failure { PptxParser.parse(ByteArrayInputStream(zipOf(mapOf("readme.txt" to "hi")))) }.contains("presentation.xml"))
    }

    @Test fun aPresentationWithoutSlidesIsRefused() {
        val bytes = zipOf(mapOf("ppt/presentation.xml" to "<p:presentation $NS><p:sldIdLst/></p:presentation>"))
        assertTrue(failure { PptxParser.parse(ByteArrayInputStream(bytes)) }.contains("no slides"))
    }

    @Test fun aDoctypeIsRefused() {
        val evil = "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><p:presentation $NS/>"
        assertTrue(failure { PptxParser.parse(ByteArrayInputStream(zipOf(mapOf("ppt/presentation.xml" to evil)))) }.contains("unsafe"))
    }

    @Test fun anOversizedPartIsRefused() {
        val limits = PptxLimits(zip = OoxmlLimits(maxXmlPartBytes = 200))
        assertTrue(failure { Deck(textBox("x".repeat(500))).parse(limits) }.contains("too big"))
    }

    @Test fun aZipBombIsStopped() {
        val limits = PptxLimits(zip = OoxmlLimits(maxInflatedBytes = 1024 * 1024))
        assertTrue(failure { Deck(textBox("x"), extra = mapOf("ppt/junk.bin" to ByteArray(2 * 1024 * 1024))).parse(limits) }.contains("unpacks"))
    }

    @Test fun brokenSlideXmlIsRefused() {
        failure { Deck(textBox("x"), extra = mapOf("ppt/slides/slide1.xml" to "<p:sld $NS><p:cSld>")).parse() }
    }
}
