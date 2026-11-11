package io.github.amandhakar.documents.library

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiSelectTest {
    private fun file(id: Long) = PdfFile(id = id, name = "f$id.pdf", folder = "", sizeBytes = 1, modifiedSeconds = 0)

    @Test fun toggleAddsAnUnselectedFile() = assertEquals(setOf(1L, 2L), MultiSelect.toggle(setOf(1L), 2L))

    @Test fun toggleRemovesASelectedFile() = assertEquals(setOf(1L), MultiSelect.toggle(setOf(1L, 2L), 2L))

    @Test fun toggleTheOnlyFileGivesAnEmptySelection() = assertEquals(emptySet<Long>(), MultiSelect.toggle(setOf(7L), 7L))

    @Test fun pruneDropsFilesThatAreGone() =
        assertEquals(setOf(1L, 3L), MultiSelect.prune(setOf(1L, 2L, 3L), listOf(1L, 3L, 4L)))

    @Test fun pruneWithNothingAvailableEmptiesTheSelection() =
        assertEquals(emptySet<Long>(), MultiSelect.prune(setOf(1L, 2L), emptyList()))

    @Test fun pruneKeepsAnEmptySelectionEmpty() = assertEquals(emptySet<Long>(), MultiSelect.prune(emptySet(), listOf(1L)))

    @Test fun allSelectsEveryFile() = assertEquals(setOf(1L, 2L, 3L), MultiSelect.all(listOf(file(1), file(2), file(3))))

    @Test fun allOfAnEmptyListIsEmpty() = assertEquals(emptySet<Long>(), MultiSelect.all(emptyList()))

    @Test fun chosenKeepsTheListOrder() {
        val files = listOf(file(3), file(1), file(2))
        assertEquals(listOf(3L, 2L), MultiSelect.chosen(files, setOf(2L, 3L)).map { it.id })
    }

    @Test fun chosenIgnoresIdsNotInTheList() =
        assertEquals(emptyList<Long>(), MultiSelect.chosen(listOf(file(1)), setOf(9L)).map { it.id })

    @Test fun titleShowsTheCount() {
        assertEquals("1 selected", MultiSelect.title(1))
        assertEquals("12 selected", MultiSelect.title(12))
    }
}
