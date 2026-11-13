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
        file(5, DocumentFormat.Pptx),
    )

    @Test fun allShowsEverythingInTheSameOrder() = assertEquals(listOf(1L, 2L, 3L, 4L, 5L), LibraryFilters.apply(files, LibraryFilter.All).map { it.id })

    @Test fun pdfShowsOnlyPdfs() = assertEquals(listOf(1L, 3L), LibraryFilters.apply(files, LibraryFilter.Pdf).map { it.id })

    @Test fun wordShowsOnlyWordFiles() = assertEquals(listOf(4L), LibraryFilters.apply(files, LibraryFilter.Word).map { it.id })

    @Test fun slidesShowsOnlyPresentations() = assertEquals(listOf(5L), LibraryFilters.apply(files, LibraryFilter.Slides).map { it.id })

    @Test fun webShowsOnlyWebPages() = assertEquals(listOf(2L), LibraryFilters.apply(files, LibraryFilter.Web).map { it.id })

    @Test fun anEmptyListStaysEmpty() = assertEquals(emptyList<PdfFile>(), LibraryFilters.apply(emptyList(), LibraryFilter.Pdf))

    @Test fun countsPerChip() {
        val counts = LibraryFilters.counts(files)
        assertEquals(5, counts[LibraryFilter.All])
        assertEquals(1, counts[LibraryFilter.Slides])
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

    @Test fun anEmptyQueryShowsEverything() = assertEquals(setOf(1L, 2L, 3L), ids("").toSet())
    @Test fun aBlankQueryShowsEverything() = assertEquals(setOf(1L, 2L, 3L), ids("   ").toSet())
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

class SortingTest {
    private fun f(id: Long, name: String, modified: Long, size: Long, format: io.github.amandhakar.documents.document.DocumentFormat = io.github.amandhakar.documents.document.DocumentFormat.Pdf) =
        PdfFile(id = id, name = name, folder = "", sizeBytes = size, modifiedSeconds = modified, format = format)

    private val files = listOf(
        f(1, "banana.pdf", 300, 50),
        f(2, "Apple.pdf", 100, 900),
        f(3, "cherry.docx", 200, 10, io.github.amandhakar.documents.document.DocumentFormat.Docx),
        f(4, "apple notes.pdf", 100, 900),
    )

    private fun ids(order: SortOrder) = Sorting.sort(files, order).map { it.id }

    @Test fun newestFirstBreaksTiesByName() = assertEquals(listOf(1L, 3L, 4L, 2L), ids(SortOrder.Newest))
    @Test fun oldestFirst() = assertEquals(listOf(4L, 2L, 3L, 1L), ids(SortOrder.Oldest))
    @Test fun nameAToZIgnoresCaseAndExtension() = assertEquals(listOf(2L, 4L, 1L, 3L), ids(SortOrder.NameAZ))
    @Test fun nameZToA() = assertEquals(listOf(3L, 1L, 4L, 2L), ids(SortOrder.NameZA))
    @Test fun largestFirstBreaksTiesByNewestThenName() = assertEquals(listOf(4L, 2L, 1L, 3L), ids(SortOrder.Largest))
    @Test fun smallestFirst() = assertEquals(listOf(3L, 1L, 4L, 2L), ids(SortOrder.Smallest))
    @Test fun sortingAnEmptyListGivesAnEmptyList() = assertEquals(emptyList<PdfFile>(), Sorting.sort(emptyList(), SortOrder.NameAZ))
    @Test fun sortingDoesNotChangeTheInput() {
        val before = files.map { it.id }
        Sorting.sort(files, SortOrder.NameZA)
        assertEquals(before, files.map { it.id })
    }

    @Test fun everyOrderKeepsEveryFile() {
        for (order in SortOrder.entries) assertEquals(listOf(1L, 2L, 3L, 4L), ids(order).sorted())
    }

    @Test fun applyFiltersThenSorts() {
        val shown = LibraryFilters.apply(files, LibraryFilter.Pdf, "", SortOrder.NameAZ).map { it.id }
        assertEquals(listOf(2L, 4L, 1L), shown)
    }

    @Test fun applyDefaultsToNewestFirst() = assertEquals(listOf(1L, 3L, 4L, 2L), LibraryFilters.apply(files, LibraryFilter.All).map { it.id })

    @Test fun everyOrderHasALabel() = assertEquals(6, SortOrder.entries.map { it.label }.distinct().size)

    @Test fun markdownShowsOnlyMarkdownFiles() {
        val list = listOf(
            PdfFile(1, "a.pdf", "Download", 10, 100),
            PdfFile(2, "notes.md", "Download", 10, 200, DocumentFormat.Markdown),
            PdfFile(3, "log.txt", "Download", 10, 300, DocumentFormat.Text),
        )
        assertEquals(listOf(2L), LibraryFilters.apply(list, LibraryFilter.Markdown).map { it.id })
        assertEquals(1, LibraryFilters.counts(list)[LibraryFilter.Markdown])
        assertEquals(3, LibraryFilters.counts(list)[LibraryFilter.All])
    }
}
