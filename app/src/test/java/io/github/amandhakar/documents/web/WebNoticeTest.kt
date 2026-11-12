package io.github.amandhakar.documents.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebNoticeTest {
    @Test fun nothingBlockedHasNoTitle() = assertEquals("", WebNotice.title(0))
    @Test fun negativeCountHasNoTitle() = assertEquals("", WebNotice.title(-1))
    @Test fun oneBlockedPart() = assertEquals("1 online part not shown", WebNotice.title(1))
    @Test fun severalBlockedParts() = assertEquals("2 online parts not shown", WebNotice.title(2))
    @Test fun offlineExplanationSaysItIsAllowedPerPage() = assertTrue(WebNotice.explanation(false).contains("unless you allow it"))
    @Test fun onlineExplanationSaysOnlyHttpsLoads() = assertTrue(WebNotice.explanation(true).contains("https"))
    @Test fun chipShowsBothSettings() {
        assertEquals("Scripts off \u00B7 Offline", WebNotice.modeChip(false, false))
        assertEquals("Scripts on \u00B7 Online", WebNotice.modeChip(true, true))
    }
    @Test fun chipSaysWhetherScriptsRun() {
        assertEquals("Scripts off", WebNotice.scriptsChip(false))
        assertEquals("Scripts on", WebNotice.scriptsChip(true))
    }
}
