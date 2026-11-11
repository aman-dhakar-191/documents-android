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
}
