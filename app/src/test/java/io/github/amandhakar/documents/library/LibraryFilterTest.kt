package io.github.amandhakar.documents.library

import io.github.amandhakar.documents.document.DocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
    private fun file(id: Long, format: DocumentFormat) =
        PdfFile(id = id, name = "f$id", folder = "", sizeBytes = 1, modifiedSeconds = 0, format = format)

    private val files = listOf(
        file(1, DocumentFormat.Pdf),
        file(2, DocumentFormat.Html),
        file(3, DocumentFormat.Pdf),
    )

    @Test fun allShowsEverythingInTheSameOrder() = assertEquals(listOf(1L, 2L, 3L), LibraryFilters.apply(files, LibraryFilter.All).map { it.id })

    @Test fun pdfShowsOnlyPdfs() = assertEquals(listOf(1L, 3L), LibraryFilters.apply(files, LibraryFilter.Pdf).map { it.id })

    @Test fun webShowsOnlyWebPages() = assertEquals(listOf(2L), LibraryFilters.apply(files, LibraryFilter.Web).map { it.id })

    @Test fun anEmptyListStaysEmpty() = assertEquals(emptyList<PdfFile>(), LibraryFilters.apply(emptyList(), LibraryFilter.Pdf))

    @Test fun countsPerChip() {
        val counts = LibraryFilters.counts(files)
        assertEquals(3, counts[LibraryFilter.All])
        assertEquals(2, counts[LibraryFilter.Pdf])
        assertEquals(1, counts[LibraryFilter.Web])
    }

    @Test fun countsOfAnEmptyListAreZero() = assertEquals(0, LibraryFilters.counts(emptyList()).values.sum())

    @Test fun aFileWithoutTheFormatArgumentIsAPdf() =
        assertEquals(DocumentFormat.Pdf, PdfFile(id = 1, name = "a.pdf", folder = "", sizeBytes = 1, modifiedSeconds = 0).format)
}
