package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class SaveNamesTest {
    @Test fun addsSignedBeforeTheExtension() = assertEquals("report-signed.pdf", SaveNames.signedName("report.pdf"))

    @Test fun upperCaseExtension() = assertEquals("Scan-signed.pdf", SaveNames.signedName("Scan.PDF"))

    @Test fun noExtension() = assertEquals("notes-signed.pdf", SaveNames.signedName("notes"))

    @Test fun missingOrBlankName() {
        assertEquals("document-signed.pdf", SaveNames.signedName(null))
        assertEquals("document-signed.pdf", SaveNames.signedName("   "))
        assertEquals("document-signed.pdf", SaveNames.signedName(".pdf"))
    }

    @Test fun pathPiecesAreDropped() {
        assertEquals("a-signed.pdf", SaveNames.signedName("../../etc/a.pdf"))
        assertEquals("b-signed.pdf", SaveNames.signedName("C:\\docs\\b.pdf"))
    }

    @Test fun controlCharactersAreDropped() = assertEquals("ab-signed.pdf", SaveNames.signedName("a\u0000b\n.pdf"))

    @Test fun deniedWriteToTheOriginalSuggestsASeparateCopy() {
        assertTrue(SaveNames.errorMessage(SecurityException("denied"), replacing = true).contains("new copy"))
        assertTrue(SaveNames.errorMessage(FileNotFoundException("x"), replacing = true).contains("new copy"))
    }

    @Test fun deniedWriteToAChosenPlaceAsksForAnotherPlace() =
        assertTrue(SaveNames.errorMessage(SecurityException("denied"), replacing = false).contains("another place"))

    @Test fun passwordProtectedFilesAreExplained() =
        assertTrue(SaveNames.errorMessage(IOException("Cannot decrypt PDF, the password is incorrect"), replacing = true).contains("Password"))

    @Test fun otherIoErrorsSayNothingWasChanged() =
        assertTrue(SaveNames.errorMessage(IOException("disk full"), replacing = true).contains("Nothing was changed"))

    @Test fun outOfMemoryIsExplained() =
        assertTrue(SaveNames.errorMessage(OutOfMemoryError(), replacing = false).contains("too big"))

    @Test fun anythingElseSaysNothingWasChanged() =
        assertTrue(SaveNames.errorMessage(IllegalStateException("x"), replacing = true).contains("Nothing was changed"))
}
