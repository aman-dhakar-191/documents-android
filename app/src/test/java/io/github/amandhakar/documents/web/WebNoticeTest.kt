package io.github.amandhakar.documents.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebNoticeTest {
    @Test fun nothingBlockedHasNoTitle() = assertEquals("", WebNotice.title(0))
    @Test fun negativeCountHasNoTitle() = assertEquals("", WebNotice.title(-1))
    @Test fun oneBlockedPart() = assertEquals("1 online part not shown", WebNotice.title(1))
    @Test fun severalBlockedParts() = assertEquals("2 online parts not shown", WebNotice.title(2))
    @Test fun explanationSaysThereIsNoInternet() = assertTrue(WebNotice.EXPLANATION.contains("no internet access"))
    @Test fun chipSaysWhetherScriptsRun() {
        assertEquals("Scripts off", WebNotice.scriptsChip(false))
        assertEquals("Scripts on", WebNotice.scriptsChip(true))
    }
}
