package io.github.amandhakar.documents.export

import java.io.FileNotFoundException
import java.io.IOException

/** Names and messages for saving a document as a PDF. Free of Android calls so it can be unit tested. */
object ExportNames {
    private val ENDINGS = listOf(".docx", ".pptx", ".html", ".htm", ".xhtml", ".pdf")

    /** "Report.docx" gives "Report.pdf"; a missing, blank or odd name still gives a usable file name. */
    fun pdfName(original: String?): String {
        var base = original.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .filter { !it.isISOControl() }
            .trim()
        val ending = ENDINGS.firstOrNull { base.endsWith(it, ignoreCase = true) }
        if (ending != null) base = base.dropLast(ending.length)
        base = base.trim()
        return (if (base.isEmpty()) "document" else base) + ".pdf"
    }

    /** "Report.pdf" gives "Report.docx" (pass ".docx" or ".pptx"); a missing, blank or odd name still gives a usable file name. */
    fun nameWithExtension(original: String?, extension: String): String = pdfName(original).removeSuffix(".pdf") + extension

    /** What to tell the person when saving as a PDF failed. */
    fun errorMessage(error: Throwable): String = when (error) {
        is OutOfMemoryError -> "This document is too big to turn into a PDF on this phone."
        is SecurityException, is FileNotFoundException -> "Couldn't save there. Pick another place."
        is IOException -> "Couldn't write the PDF. Nothing was changed."
        else -> "Couldn't turn this document into a PDF."
    }
}
