package io.github.amandhakar.documents.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentFormatTest {
    @Test fun pdfMimeType() = assertEquals(DocumentFormat.Pdf, DocumentFormat.fromMimeType("application/pdf"))

    @Test fun mimeTypeIgnoresCaseAndParameters() {
        assertEquals(DocumentFormat.Pdf, DocumentFormat.fromMimeType("Application/PDF"))
        assertEquals(DocumentFormat.Pdf, DocumentFormat.fromMimeType(" application/pdf; charset=binary"))
    }

    @Test fun unsupportedOrMissingMimeTypeIsNull() {
        assertNull(DocumentFormat.fromMimeType(null))
        assertNull(DocumentFormat.fromMimeType(""))
        assertNull(DocumentFormat.fromMimeType("application/octet-stream"))
        assertNull(DocumentFormat.fromMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertNull(DocumentFormat.fromMimeType("application/pdfx"))
    }

    @Test fun pdfFileName() {
        assertEquals(DocumentFormat.Pdf, DocumentFormat.fromFileName("report.pdf"))
        assertEquals(DocumentFormat.Pdf, DocumentFormat.fromFileName("REPORT.PDF"))
        assertEquals(DocumentFormat.Pdf, DocumentFormat.fromFileName("a.b.pdf"))
    }

    @Test fun unsupportedOrMissingFileNameIsNull() {
        assertNull(DocumentFormat.fromFileName(null))
        assertNull(DocumentFormat.fromFileName(""))
        assertNull(DocumentFormat.fromFileName("pdf"))
        assertNull(DocumentFormat.fromFileName("notes.docx"))
        assertNull(DocumentFormat.fromFileName("report.pdf.exe"))
    }

    @Test fun webPageMimeTypes() {
        assertEquals(DocumentFormat.Html, DocumentFormat.fromMimeType("text/html"))
        assertEquals(DocumentFormat.Html, DocumentFormat.fromMimeType("text/html; charset=utf-8"))
        assertEquals(DocumentFormat.Html, DocumentFormat.fromMimeType("application/xhtml+xml"))
        assertNull(DocumentFormat.fromMimeType("text/plain"))
    }

    @Test fun webPageFileNames() {
        assertEquals(DocumentFormat.Html, DocumentFormat.fromFileName("index.html"))
        assertEquals(DocumentFormat.Html, DocumentFormat.fromFileName("PAGE.HTM"))
        assertEquals(DocumentFormat.Html, DocumentFormat.fromFileName("a.b.xhtml"))
        assertNull(DocumentFormat.fromFileName("script.js"))
        assertNull(DocumentFormat.fromFileName("page.html.exe"))
    }
}
