package io.github.amandhakar.documents.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareFilesTest {
    @Test fun contentAddressesCanBeShared() {
        assertTrue(ShareFiles.canShare("content"))
        assertTrue(ShareFiles.canShare("CONTENT"))
    }

    @Test fun filePathsCannotBeSharedBecauseTheyCrashOnAndroid7Plus() = assertFalse(ShareFiles.canShare("file"))

    @Test fun otherOrMissingSchemesCannotBeShared() {
        assertFalse(ShareFiles.canShare(null))
        assertFalse(ShareFiles.canShare(""))
        assertFalse(ShareFiles.canShare("http"))
        assertFalse(ShareFiles.canShare("https"))
        assertFalse(ShareFiles.canShare("content2"))
    }

    @Test fun filesOfOneTypeAreSharedUnderThatType() =
        org.junit.Assert.assertEquals("application/pdf", ShareFiles.commonMimeType(listOf("application/pdf", "application/pdf")))

    @Test fun mixedFilesAreSharedUnderAnyType() =
        org.junit.Assert.assertEquals("*/*", ShareFiles.commonMimeType(listOf("application/pdf", "text/html")))

    @Test fun nothingToShareGivesAnyType() = org.junit.Assert.assertEquals("*/*", ShareFiles.commonMimeType(emptyList()))
}
