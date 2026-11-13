package io.github.amandhakar.documents.library

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.util.Locale

class PdfFilesTest {
    private fun file(id: Long, name: String, modified: Long) =
        PdfFile(id = id, name = name, folder = "", sizeBytes = 1, modifiedSeconds = modified)

    @Test fun titleDropsPdfExtension() {
        assertEquals("Report", PdfFiles.title("Report.pdf"))
        assertEquals("Report", PdfFiles.title("Report.PDF"))
        assertEquals("a.b", PdfFiles.title("a.b.pdf"))
    }

    @Test fun titleDropsPowerPointExtension() {
        assertEquals("Deck", PdfFiles.title("Deck.pptx"))
        assertEquals("Deck", PdfFiles.title("Deck.PPTX"))
    }

    @Test fun titleDropsMarkdownExtensions() {
        assertEquals("README", PdfFiles.title("README.md"))
        assertEquals("notes", PdfFiles.title("notes.MARKDOWN"))
    }

    @Test fun titleDropsWordExtension() {
        assertEquals("Letter", PdfFiles.title("Letter.docx"))
        assertEquals("Letter", PdfFiles.title("Letter.DOCX"))
    }

    @Test fun titleDropsWebPageExtensions() {
        assertEquals("page", PdfFiles.title("page.html"))
        assertEquals("page", PdfFiles.title("page.HTM"))
        assertEquals("page", PdfFiles.title("page.xhtml"))
        assertEquals("a.b", PdfFiles.title("a.b.html"))
        assertEquals(".html", PdfFiles.title(".html"))
    }

    @Test fun titleKeepsOtherNamesAndNeverGoesBlank() {
        assertEquals("notes", PdfFiles.title("notes"))
        assertEquals("pdf", PdfFiles.title("pdf"))
        assertEquals(".pdf", PdfFiles.title(".pdf"))
    }

    @Test fun sizeInBytesAndKilobytes() {
        assertEquals("0 B", PdfFiles.formatSize(0))
        assertEquals("0 B", PdfFiles.formatSize(-5))
        assertEquals("1023 B", PdfFiles.formatSize(1023))
        assertEquals("1 KB", PdfFiles.formatSize(1024))
        assertEquals("2 KB", PdfFiles.formatSize(1536))
    }

    @Test fun sizeInMegabytesAndGigabytes() {
        assertEquals("1.0 MB", PdfFiles.formatSize(1_048_575)) // would round to "1024 KB"
        assertEquals("1.0 MB", PdfFiles.formatSize(1_048_576))
        assertEquals("5.2 MB", PdfFiles.formatSize(5_452_595))
        assertEquals("1.0 GB", PdfFiles.formatSize(1_073_741_824))
    }

    @Test fun dateUsesTheGivenZone() {
        val midnightUtc = 1_731_196_800L // 10 Nov 2024 00:00 UTC
        assertEquals("10 Nov 2024", PdfFiles.formatDate(midnightUtc, ZoneOffset.UTC, Locale.ENGLISH))
        assertEquals("9 Nov 2024", PdfFiles.formatDate(midnightUtc, ZoneOffset.ofHours(-12), Locale.ENGLISH))
    }

    @Test fun unknownDateIsEmpty() {
        assertEquals("", PdfFiles.formatDate(0, ZoneOffset.UTC, Locale.ENGLISH))
        assertEquals("", PdfFiles.formatDate(-1, ZoneOffset.UTC, Locale.ENGLISH))
    }

    @Test fun parentFolderName() {
        assertEquals("Download", PdfFiles.parentFolderName("/storage/emulated/0/Download/a.pdf"))
        assertEquals("Work", PdfFiles.parentFolderName("/storage/emulated/0/Documents/Work/a.pdf"))
    }

    @Test fun parentFolderNameWhenThereIsNone() {
        assertEquals("", PdfFiles.parentFolderName(null))
        assertEquals("", PdfFiles.parentFolderName(""))
        assertEquals("", PdfFiles.parentFolderName("a.pdf"))
        assertEquals("", PdfFiles.parentFolderName("/a.pdf"))
    }

    @Test fun sortsNewestFirstThenByNameThenId() {
        val sorted = PdfFiles.sortedNewestFirst(
            listOf(
                file(1, "old.pdf", 100),
                file(2, "b.pdf", 300),
                file(3, "A.pdf", 300),
                file(5, "same.pdf", 200),
                file(4, "same.pdf", 200),
            ),
        )
        assertEquals(listOf(3L, 2L, 4L, 5L, 1L), sorted.map { it.id })
    }

    @Test fun sortingAnEmptyListGivesAnEmptyList() {
        assertEquals(emptyList<PdfFile>(), PdfFiles.sortedNewestFirst(emptyList()))
    }
}
