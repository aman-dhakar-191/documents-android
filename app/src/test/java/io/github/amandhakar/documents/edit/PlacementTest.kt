package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementTest {
    private val sig = 3f // signature is three times wider than tall
    private val page = 0.707f // a portrait page

    private fun inside(p: PlacedSignature) {
        val h = Placement.height(p.width, sig, page)
        assertTrue("left ${p.left}", p.left >= 0f)
        assertTrue("top ${p.top}", p.top >= 0f)
        assertTrue("right ${p.left + p.width}", p.left + p.width <= 1.0001f)
        assertTrue("bottom ${p.top + h}", p.top + h <= 1.0001f)
    }

    @Test fun heightFollowsTheShapesOfSignatureAndPage() =
        assertEquals(0.35f * 0.707f / 3f, Placement.height(0.35f, 3f, 0.707f), 1e-6f)

    @Test fun aSquareSignatureOnASquarePageHasEqualFractions() =
        assertEquals(0.4f, Placement.height(0.4f, 1f, 1f), 1e-6f)

    @Test(expected = IllegalArgumentException::class) fun zeroAspectIsRejected() {
        Placement.height(0.3f, 0f, 1f)
    }

    @Test fun startsCentredOnThePage() {
        val p = Placement.initial(2, sig, page)
        assertEquals(2, p.page)
        assertEquals((1f - p.width) / 2f, p.left, 1e-5f)
        val h = Placement.height(p.width, sig, page)
        assertEquals((1f - h) / 2f, p.top, 1e-5f)
        inside(p)
    }

    @Test fun movingMovesByTheGivenAmount() {
        val start = PlacedSignature(0, 0.2f, 0.3f, 0.3f)
        val moved = Placement.moved(start, 0.1f, 0.05f, sig, page)
        assertEquals(0.3f, moved.left, 1e-6f)
        assertEquals(0.35f, moved.top, 1e-6f)
    }

    @Test fun cannotBeDraggedOffAnEdge() {
        val start = PlacedSignature(0, 0.5f, 0.5f, 0.3f)
        inside(Placement.moved(start, 5f, 5f, sig, page))
        inside(Placement.moved(start, -5f, -5f, sig, page))
        assertEquals(0f, Placement.moved(start, -5f, -5f, sig, page).left, 0f)
    }

    @Test fun resizingStaysWithinLimits() {
        val start = PlacedSignature(0, 0.1f, 0.1f, 0.3f)
        assertEquals(Placement.MAX_WIDTH, Placement.resized(start, 5f, sig, page).width, 1e-6f)
        assertEquals(Placement.MIN_WIDTH, Placement.resized(start, -5f, sig, page).width, 1e-6f)
    }

    @Test fun growingAtTheRightEdgePushesItBackOntoThePage() {
        val grown = Placement.resized(PlacedSignature(0, 0.7f, 0.1f, 0.3f), 0.5f, sig, page)
        inside(grown)
    }

    @Test fun aVeryTallSignatureIsShrunkToFitThePageHeight() {
        val tall = Placement.fit(PlacedSignature(0, 0f, 0f, 0.9f), 0.2f, 1.4f)
        assertTrue(Placement.height(tall.width, 0.2f, 1.4f) <= 1.0001f)
    }

    @Test fun thePageNumberIsKept() = assertEquals(7, Placement.moved(PlacedSignature(7, 0f, 0f, 0.3f), 0.1f, 0.1f, sig, page).page)
}
