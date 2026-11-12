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
        file(4, DocumentFormat.Docx),
    )

    @Test fun allShowsEverythingInTheSameOrder() = assertEquals(listOf(1L, 2L, 3L, 4L), LibraryFilters.apply(files, LibraryFilter.All).map { it.id })

    @Test fun pdfShowsOnlyPdfs() = assertEquals(listOf(1L, 3L), LibraryFilters.apply(files, LibraryFilter.Pdf).map { it.id })

    @Test fun wordShowsOnlyWordFiles() = assertEquals(listOf(4L), LibraryFilters.apply(files, LibraryFilter.Word).map { it.id })

    @Test fun webShowsOnlyWebPages() = assertEquals(listOf(2L), LibraryFilters.apply(files, LibraryFilter.Web).map { it.id })

    @Test fun anEmptyListStaysEmpty() = assertEquals(emptyList<PdfFile>(), LibraryFilters.apply(emptyList(), LibraryFilter.Pdf))

    @Test fun countsPerChip() {
        val counts = LibraryFilters.counts(files)
        assertEquals(4, counts[LibraryFilter.All])
        assertEquals(1, counts[LibraryFilter.Word])
        assertEquals(2, counts[LibraryFilter.Pdf])
        assertEquals(1, counts[LibraryFilter.Web])
    }

    @Test fun countsOfAnEmptyListAreZero() = assertEquals(0, LibraryFilters.counts(emptyList()).values.sum())

    @Test fun aFileWithoutTheFormatArgumentIsAPdf() =
        assertEquals(DocumentFormat.Pdf, PdfFile(id = 1, name = "a.pdf", folder = "", sizeBytes = 1, modifiedSeconds = 0).format)
}

class LibrarySearchTest {
    private fun file(id: Long, name: String, folder: String = "Download", format: DocumentFormat = DocumentFormat.Pdf) =
        PdfFile(id = id, name = name, folder = folder, sizeBytes = 1, modifiedSeconds = 0, format = format)

    private val files = listOf(
        file(1, "Train Ticket.pdf"),
        file(2, "invoice-march.pdf", folder = "Work"),
        file(3, "recipe.html", format = DocumentFormat.Html),
    )

    private fun ids(query: String, filter: LibraryFilter = LibraryFilter.All) =
        LibraryFilters.apply(files, filter, query).map { it.id }

    @Test fun anEmptyQueryShowsEverything() = assertEquals(listOf(1L, 2L, 3L), ids(""))
    @Test fun aBlankQueryShowsEverything() = assertEquals(listOf(1L, 2L, 3L), ids("   "))
    @Test fun matchingIgnoresCase() = assertEquals(listOf(1L), ids("TRAIN"))
    @Test fun matchesPartOfAName() = assertEquals(listOf(2L), ids("voice"))
    @Test fun everyWordMustMatch() = assertEquals(listOf(1L), ids("ticket train"))
    @Test fun aWordThatMatchesNothingGivesNothing() = assertEquals(emptyList<Long>(), ids("train zebra"))
    @Test fun theFolderNameCounts() = assertEquals(listOf(2L), ids("work"))
    @Test fun searchRespectsTheChip() {
        assertEquals(listOf(3L), ids("recipe", LibraryFilter.Web))
        assertEquals(emptyList<Long>(), ids("recipe", LibraryFilter.Pdf))
    }
    @Test fun surroundingSpacesAreIgnored() = assertEquals(listOf(1L), ids("  train  "))
    @Test fun queryCharactersAreNotPatterns() = assertEquals(emptyList<Long>(), ids(".*"))
}
