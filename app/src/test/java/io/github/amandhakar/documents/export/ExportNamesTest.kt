package io.github.amandhakar.documents.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class ExportNamesTest {
    @Test fun replacesTheOfficeEnding() {
        assertEquals("Report.pdf", ExportNames.pdfName("Report.docx"))
        assertEquals("Deck.pdf", ExportNames.pdfName("Deck.PPTX"))
        assertEquals("page.pdf", ExportNames.pdfName("page.html"))
    }

    @Test fun keepsOtherDotsInTheName() = assertEquals("a.b.c.pdf", ExportNames.pdfName("a.b.c.docx"))

    @Test fun anAlreadyPdfNameIsNotDoubled() = assertEquals("x.pdf", ExportNames.pdfName("x.pdf"))

    @Test fun namesWithoutAKnownEndingKeepTheirText() = assertEquals("notes.txt.pdf", ExportNames.pdfName("notes.txt"))

    @Test fun missingOrBlankNamesGetADefault() {
        assertEquals("document.pdf", ExportNames.pdfName(null))
        assertEquals("document.pdf", ExportNames.pdfName(""))
        assertEquals("document.pdf", ExportNames.pdfName("   "))
        assertEquals("document.pdf", ExportNames.pdfName(".docx"))
    }

    @Test fun pathsAndControlCharactersAreRemoved() {
        assertEquals("Report.pdf", ExportNames.pdfName("/storage/emulated/0/Download/Report.docx"))
        assertEquals("Report.pdf", ExportNames.pdfName("C:\\Users\\me\\Report.docx"))
        assertEquals("ab.pdf", ExportNames.pdfName("a\u0000b.docx"))
    }

    @Test fun errorsHaveHelpfulMessages() {
        assertTrue(ExportNames.errorMessage(OutOfMemoryError()).contains("too big"))
        assertTrue(ExportNames.errorMessage(SecurityException()).contains("Pick another place"))
        assertTrue(ExportNames.errorMessage(FileNotFoundException()).contains("Pick another place"))
        assertTrue(ExportNames.errorMessage(IOException("x")).contains("Nothing was changed"))
        assertTrue(ExportNames.errorMessage(IllegalStateException()).contains("Couldn't turn"))
    }
}
