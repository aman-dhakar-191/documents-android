package io.github.amandhakar.documents.library

import io.github.amandhakar.documents.document.DocumentFormat

/** The chips above the library: which kind of file to show. Add one when a new format is added. */
enum class LibraryFilter(val label: String) {
    All("All"),
    Pdf("PDF"),
    Web("Web pages");

    fun matches(format: DocumentFormat): Boolean = when (this) {
        All -> true
        Pdf -> format == DocumentFormat.Pdf
        Web -> format == DocumentFormat.Html
    }
}

/** Filtering and counting the library list. Free of Android calls so it can be unit tested. */
object LibraryFilters {
    fun apply(files: List<PdfFile>, filter: LibraryFilter): List<PdfFile> = files.filter { filter.matches(it.format) }

    /** How many files each chip would show. */
    fun counts(files: List<PdfFile>): Map<LibraryFilter, Int> =
        LibraryFilter.entries.associateWith { filter -> files.count { filter.matches(it.format) } }
}
