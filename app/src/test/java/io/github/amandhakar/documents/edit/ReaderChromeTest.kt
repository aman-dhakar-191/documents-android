package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderChromeTest {
    @Test fun countsEveryKindOfChange() = assertEquals(7, ReaderChrome.pendingCount(1, 2, 3, 1))
    @Test fun noChangesIsZero() = assertEquals(0, ReaderChrome.pendingCount(0, 0, 0, 0))
    @Test fun saveLabelShowsTheCount() = assertEquals("Save · 2", ReaderChrome.saveLabel(2))
    @Test fun saveLabelWithOneChange() = assertEquals("Save · 1", ReaderChrome.saveLabel(1))
    @Test fun saveLabelWithNothingIsPlain() = assertEquals("Save", ReaderChrome.saveLabel(0))
    @Test fun saveLabelNeverShowsANegativeCount() = assertEquals("Save", ReaderChrome.saveLabel(-3))

    @Test fun noModeMeansNoActiveTool() = assertNull(ReaderChrome.activeTool(false, false, false))
    @Test fun eachModeMapsToItsTool() {
        assertEquals(ReaderTool.EditText, ReaderChrome.activeTool(true, false, false))
        assertEquals(ReaderTool.Highlight, ReaderChrome.activeTool(false, true, false))
        assertEquals(ReaderTool.Draw, ReaderChrome.activeTool(false, false, true))
    }

    @Test fun hintsExistForTheTappingTools() {
        assertNotNull(ReaderChrome.hint(ReaderTool.EditText))
        assertNotNull(ReaderChrome.hint(ReaderTool.Highlight))
    }
    @Test fun otherToolsHaveNoHint() {
        assertNull(ReaderChrome.hint(ReaderTool.Draw))
        assertNull(ReaderChrome.hint(ReaderTool.Sign))
        assertNull(ReaderChrome.hint(null))
    }

    @Test fun dockHasFiveToolsInOrder() =
        assertEquals(listOf("Sign", "Add text", "Edit text", "Highlight", "Draw"), ReaderTool.entries.map { it.label })

    @Test fun zoomLabelRounds() {
        assertEquals("100%", ReaderChrome.zoomLabel(1f))
        assertEquals("125%", ReaderChrome.zoomLabel(1.25f))
        assertEquals("50%", ReaderChrome.zoomLabel(0.5f))
    }
}
