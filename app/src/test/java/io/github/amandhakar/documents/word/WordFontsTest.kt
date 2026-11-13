package io.github.amandhakar.documents.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WordFontsTest {
    @Test fun commonWordFontsMapToTheirMetricTwins() {
        assertEquals(WordFace.Carlito, WordFonts.faceFor("Calibri"))
        assertEquals(WordFace.Carlito, WordFonts.faceFor("  CALIBRI Light "))
        assertEquals(WordFace.Caladea, WordFonts.faceFor("Cambria"))
        assertEquals(WordFace.LiberationSerif, WordFonts.faceFor("Times New Roman"))
        assertEquals(WordFace.LiberationSans, WordFonts.faceFor("Arial"))
        assertEquals(WordFace.LiberationMono, WordFonts.faceFor("Courier New"))
        assertEquals(WordFace.LiberationMono, WordFonts.faceFor("Consolas"))
    }

    @Test fun unknownFontsGoByClueInTheNameElseTheDefault() {
        assertEquals(WordFace.LiberationMono, WordFonts.faceFor("Acme Typewriter"))
        assertEquals(WordFace.LiberationSans, WordFonts.faceFor("Acme Sans"))
        assertEquals(WordFace.LiberationSerif, WordFonts.faceFor("Acme Serif"))
        assertEquals(WordFonts.DEFAULT, WordFonts.faceFor("Zzyzx"))
        assertEquals(WordFonts.DEFAULT, WordFonts.faceFor(null))
        assertEquals(WordFonts.DEFAULT, WordFonts.faceFor(""))
    }

    @Test fun lettersHaveRealWidths() {
        // Calibri: "n" is 525/1000 em, a space 226; Arial: "n" 556, "W" 944; Courier: every character 600.
        assertEquals(0.525f, WordFonts.advanceEm(WordFace.Carlito, false, false, 'n'.code), 0.002f)
        assertEquals(0.226f, WordFonts.advanceEm(WordFace.Carlito, false, false, ' '.code), 0.002f)
        assertEquals(0.556f, WordFonts.advanceEm(WordFace.LiberationSans, false, false, 'n'.code), 0.002f)
        assertEquals(0.944f, WordFonts.advanceEm(WordFace.LiberationSans, false, false, 'W'.code), 0.002f)
        assertEquals(0.6f, WordFonts.advanceEm(WordFace.LiberationMono, false, false, 'i'.code), 0.002f)
        assertEquals(0.6f, WordFonts.advanceEm(WordFace.LiberationMono, true, true, 'W'.code), 0.002f)
    }

    @Test fun boldIsWiderThanRegular() {
        val regular = WordFonts.advanceEm(WordFace.LiberationSans, false, false, 'n'.code)
        val bold = WordFonts.advanceEm(WordFace.LiberationSans, true, false, 'n'.code)
        assertTrue(bold > regular)
    }

    @Test fun charactersOutsideTheTablesCountAsAnN() {
        val unknown = WordFonts.advanceEm(WordFace.Carlito, false, false, 0x4E2D) // a Chinese character
        assertEquals(WordFonts.advanceEm(WordFace.Carlito, false, false, 'n'.code), unknown, 0f)
    }

    @Test fun lineHeightsMatchWordsSingleSpacing() {
        assertEquals(1.2207f, WordFonts.lineHeightEm(WordFace.Carlito), 0.001f) // Calibri
        assertEquals(1.15f, WordFonts.lineHeightEm(WordFace.LiberationSerif), 0.002f) // Times New Roman
        assertEquals(1.15f, WordFonts.lineHeightEm(WordFace.LiberationSans), 0.002f) // Arial
    }

    @Test fun everyTableHasAWidthForEveryCodepoint() {
        assertEquals(FontTables.CODEPOINTS.size, FontTables.CARLITO_REGULAR.size)
        assertEquals(FontTables.CODEPOINTS.size, FontTables.LIBERATIONMONO_BOLD_ITALIC.size)
        assertTrue(FontTables.CARLITO_REGULAR.all { it in 0..2000 })
    }
}

/** Checks the generated width tables against the bundled font files. Skipped where the files can't be found. */
class FontTablesMatchTheFontsTest {
    private fun fontDir(): File? {
        val candidates = listOfNotNull(System.getenv("FONT_DIR"), "src/main/res/font", "app/src/main/res/font")
        return candidates.map { File(it) }.firstOrNull { it.isDirectory }
    }

    @Test fun widthsMatchWhatTheFontFilesSay() {
        val dir = fontDir() ?: return
        System.setProperty("java.awt.headless", "true")
        val frc = java.awt.font.FontRenderContext(null, true, true)
        val cases = listOf(
            Triple("carlito_regular", FontTables.CARLITO_REGULAR, WordFace.Carlito),
            Triple("carlito_bold", FontTables.CARLITO_BOLD, WordFace.Carlito),
            Triple("caladea_regular", FontTables.CALADEA_REGULAR, WordFace.Caladea),
            Triple("liberation_sans_regular", FontTables.LIBERATIONSANS_REGULAR, WordFace.LiberationSans),
            Triple("liberation_serif_italic", FontTables.LIBERATIONSERIF_ITALIC, WordFace.LiberationSerif),
            Triple("liberation_mono_bold", FontTables.LIBERATIONMONO_BOLD, WordFace.LiberationMono),
        )
        for ((file, table, _) in cases) {
            val font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, File(dir, "$file.ttf")).deriveFont(1000f)
            for ((i, cp) in FontTables.CODEPOINTS.withIndex()) {
                if (cp == 0xA0 || !font.canDisplay(cp)) continue
                val advance = font.getStringBounds(String(Character.toChars(cp)), frc).width
                assertEquals("$file U+${cp.toString(16)}", advance, table[i].toDouble(), 1.5)
            }
        }
    }

    @Test fun everyBundledFontFileHasItsLicenceNotice() {
        val dir = fontDir() ?: return
        val licence = File(dir, "../../assets/licenses/fonts.txt")
        if (!licence.exists()) return
        val text = licence.readText()
        for (name in listOf("Carlito", "Caladea", "Liberation", "SIL OPEN FONT LICENSE Version 1.1")) assertTrue(name, name in text)
        assertTrue(dir.listFiles()!!.all { it.name.endsWith(".ttf") })
    }
}

private const val NSF = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""

private fun parseFonts(body: String, styles: String?, theme: String? = null): DocxDocument {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
        z.putNextEntry(ZipEntry("word/document.xml")); z.write("<?xml version=\"1.0\"?><w:document $NSF><w:body>$body</w:body></w:document>".toByteArray()); z.closeEntry()
        if (styles != null) { z.putNextEntry(ZipEntry("word/styles.xml")); z.write("<?xml version=\"1.0\"?><w:styles $NSF>$styles</w:styles>".toByteArray()); z.closeEntry() }
        if (theme != null) { z.putNextEntry(ZipEntry("word/theme/theme1.xml")); z.write(theme.toByteArray()); z.closeEntry() }
    }
    return DocxParser.parse(ByteArrayInputStream(out.toByteArray()))
}

private const val THEME =
    "<?xml version=\"1.0\"?><a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:themeElements><a:fontScheme name=\"x\">" +
        "<a:majorFont><a:latin typeface=\"Cambria\"/></a:majorFont><a:minorFont><a:latin typeface=\"Calibri\"/></a:minorFont></a:fontScheme></a:themeElements></a:theme>"

class DocxFontParsingTest {
    private fun para(text: String, rPr: String = "") = "<w:p><w:r>" + (if (rPr.isNotEmpty()) "<w:rPr>$rPr</w:rPr>" else "") + "<w:t>$text</w:t></w:r></w:p>"
    private val normal = "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>"

    @Test fun aRunNamesItsOwnFont() {
        val doc = parseFonts(para("a", "<w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\"/>"), normal)
        assertEquals("Arial", (doc.blocks[0] as DocxParagraph).runs[0].fontName)
    }

    @Test fun docDefaultsGiveTheParagraphsFont() {
        val styles = "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Times New Roman\"/></w:rPr></w:rPrDefault></w:docDefaults>$normal"
        val p = parseFonts(para("a"), styles).blocks[0] as DocxParagraph
        assertEquals("Times New Roman", p.layout!!.fontName)
        assertEquals(null, p.runs[0].fontName)
    }

    @Test fun aStyleCanChangeTheFont() {
        val styles = "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\"/></w:rPr></w:rPrDefault></w:docDefaults>$normal" +
            "<w:style w:type=\"paragraph\" w:styleId=\"Code\"><w:name w:val=\"Code\"/><w:basedOn w:val=\"Normal\"/><w:rPr><w:rFonts w:ascii=\"Courier New\"/></w:rPr></w:style>"
        val p = parseFonts("<w:p><w:pPr><w:pStyle w:val=\"Code\"/></w:pPr><w:r><w:t>x</w:t></w:r></w:p>", styles).blocks[0] as DocxParagraph
        assertEquals("Courier New", p.layout!!.fontName)
        assertEquals(WordFace.LiberationMono, PageLayout.faceOf(p))
    }

    @Test fun themeFontsAreResolvedToTheirNames() {
        val styles = "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:asciiTheme=\"minorHAnsi\" w:hAnsiTheme=\"minorHAnsi\"/></w:rPr></w:rPrDefault></w:docDefaults>$normal"
        val doc = parseFonts(para("a", "<w:rFonts w:asciiTheme=\"majorHAnsi\"/>") + para("b"), styles, THEME)
        val first = doc.blocks[0] as DocxParagraph
        assertEquals("Cambria", first.runs[0].fontName)
        assertEquals("Calibri", first.layout!!.fontName)
        assertEquals(WordFace.Caladea, PageLayout.faceOf(first))
    }

    @Test fun aThemeNameWithNoThemePartIsIgnored() {
        val styles = "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:asciiTheme=\"minorHAnsi\"/></w:rPr></w:rPrDefault></w:docDefaults>$normal"
        val p = parseFonts(para("a"), styles).blocks[0] as DocxParagraph
        assertEquals(null, p.layout!!.fontName)
        assertEquals(WordFonts.DEFAULT, PageLayout.faceOf(p))
    }

    @Test fun aBrokenThemeDoesNotStopTheFileOpening() {
        val styles = "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Arial\"/></w:rPr></w:rPrDefault></w:docDefaults>$normal"
        val doc = parseFonts(para("a"), styles, "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"/>")
        assertEquals("Arial", (doc.blocks[0] as DocxParagraph).layout!!.fontName)
    }
}
