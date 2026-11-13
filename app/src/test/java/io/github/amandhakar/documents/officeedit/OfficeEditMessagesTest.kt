package io.github.amandhakar.documents.officeedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class OfficeEditMessagesTest {
    @Test fun changeCountsArePluralised() {
        assertEquals("1 change", OfficeEditMessages.changes(1))
        assertEquals("2 changes", OfficeEditMessages.changes(2))
        assertEquals("0 changes", OfficeEditMessages.changes(0))
    }

    @Test fun replacingAFileThatCantBeChangedSuggestsACopy() {
        assertTrue(OfficeEditMessages.forError(SecurityException(), true).contains("new copy"))
        assertTrue(OfficeEditMessages.forError(FileNotFoundException(), true).contains("new copy"))
    }

    @Test fun savingACopyThatCantBeWrittenSuggestsAnotherPlace() {
        assertTrue(OfficeEditMessages.forError(SecurityException(), false).contains("another place"))
    }

    @Test fun editErrorsKeepTheirOwnMessage() {
        assertEquals("custom", OfficeEditMessages.forError(OfficeEditException("custom"), false))
    }

    @Test fun everyFailureSaysNothingWasChangedOrWhy() {
        assertTrue(OfficeEditMessages.forError(IOException("x"), true).contains("Nothing was changed"))
        assertTrue(OfficeEditMessages.forError(IllegalStateException(), true).contains("Nothing was changed"))
        assertTrue(OfficeEditMessages.forError(OutOfMemoryError(), true).contains("too big"))
    }
}
