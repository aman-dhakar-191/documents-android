package io.github.amandhakar.documents

import io.github.amandhakar.documents.edit.EraseOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditMessageTest {
    @Test fun withoutErasingItJustSaysSaved() {
        assertEquals("Saved", savedMessage(false, null))
        assertEquals("Saved", savedMessage(false, EraseOutcome(10, emptyList())))
    }

    @Test fun whenErasingWorkedItSaysSo() =
        assertTrue(savedMessage(true, EraseOutcome(12, emptyList())).contains("removed from the file"))

    @Test fun whenNothingCouldBeRemovedItSaysOnlyCoveredAndWhichPage() {
        val message = savedMessage(true, EraseOutcome(0, listOf(0, 2)))
        assertTrue(message.contains("only be covered"))
        assertTrue(message.contains("1, 3"))
    }

    @Test fun whenSomePagesFailedItNamesThemWithPageNumbersFromOne() {
        val message = savedMessage(true, EraseOutcome(5, listOf(1)))
        assertTrue(message.contains("except on page 2"))
    }
}
