package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisiblePageTest {
    @Test fun theBiggerVisiblePartWins() {
        // Viewport 1000 tall: page 0 shows 300 px, page 1 shows 700 px.
        assertEquals(1, VisiblePage.mostVisible(listOf(PageRow(0, -500, 800), PageRow(1, 300, 1000)), 1000))
    }

    @Test fun aSinglePageFillingTheScreen() =
        assertEquals(4, VisiblePage.mostVisible(listOf(PageRow(4, 0, 1200)), 1000))

    @Test fun pagesPartlyAboveTheTopCountOnlyWhatShows() =
        assertEquals(3, VisiblePage.mostVisible(listOf(PageRow(2, -900, 1000), PageRow(3, 100, 1000)), 1000))

    @Test fun nothingShowingGivesNull() {
        assertNull(VisiblePage.mostVisible(emptyList(), 1000))
        assertNull(VisiblePage.mostVisible(listOf(PageRow(0, 2000, 500)), 1000))
    }
}
