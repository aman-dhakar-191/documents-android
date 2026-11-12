package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageHitTest {
    // Two pages, each 800 px wide and 1000 px tall, with an 8 px gap: page 0 at 0..1000, page 1 at 1008..2008.
    private val rows = listOf(PageRow(0, 0, 1000), PageRow(1, 1008, 1000))
    private val aspect = 3f
    private val eps = 1e-4f

    private fun resolve(left: Float, top: Float, width: Float = 240f, scrollX: Float = 0f, rows: List<PageRow> = this.rows) =
        PageHit.resolve(rows, left, top, width, width / aspect, scrollX, 800f, aspect)

    @Test fun insideThePageItIsOn() {
        val p = resolve(left = 200f, top = 300f)!!
        assertEquals(0, p.page)
        assertEquals(0.25f, p.left, eps)
        assertEquals(0.3f, p.top, eps)
        assertEquals(0.3f, p.width, eps)
    }

    @Test fun onTheSecondPageRelativeToThatPage() {
        val p = resolve(left = 400f, top = 1508f)!!
        assertEquals(1, p.page)
        assertEquals(0.5f, p.left, eps)
        assertEquals(0.5f, p.top, eps)
    }

    @Test fun aPageScrolledUpSoItStartsAboveTheScreen() {
        val p = resolve(left = 0f, top = 100f, rows = listOf(PageRow(4, -600, 1000)))!!
        assertEquals(4, p.page)
        assertEquals(0.7f, p.top, eps)
    }

    @Test fun sidewaysScrollIsAddedToTheLeftEdge() {
        val p = resolve(left = 100f, top = 300f, scrollX = 300f)!!
        assertEquals(0.5f, p.left, eps)
    }

    @Test fun straddlingTwoPagesGoesToTheOneItOverlapsMore() {
        // Height is 80 px: from 970 to 1050: 30 px on page 0, 42 px on page 1.
        val p = resolve(left = 100f, top = 970f)!!
        assertEquals(1, p.page)
        assertTrue(p.top >= 0f)
    }

    @Test fun isKeptInsideThePageWhenItOverhangs() {
        val p = resolve(left = 700f, top = 970f)!!
        assertTrue(p.left + p.width <= 1.0001f)
        assertTrue(p.left >= 0f)
    }

    @Test fun overTheGapOrNothingGivesNull() {
        assertNull(resolve(left = 0f, top = 5000f))
        assertNull(PageHit.resolve(emptyList(), 0f, 0f, 100f, 30f, 0f, 800f, aspect))
    }

    @Test fun nonsenseSizesGiveNull() {
        assertNull(PageHit.resolve(rows, 0f, 0f, 0f, 10f, 0f, 800f, aspect))
        assertNull(PageHit.resolve(rows, 0f, 0f, 100f, 30f, 0f, 0f, aspect))
    }

    @Test fun theResultIsAPlacementThatSavingCanUse() = assertNotNull(resolve(left = 10f, top = 10f))
}
