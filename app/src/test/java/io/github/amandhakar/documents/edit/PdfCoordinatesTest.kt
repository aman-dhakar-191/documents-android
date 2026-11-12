package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfCoordinatesTest {
    // A 600 x 800 page whose lower-left corner is at (10, 20) (a CropBox that doesn't start at 0).
    private val box = PageBox(left = 10f, bottom = 20f, width = 600f, height = 800f)
    private val eps = 1e-3f

    private fun check(m: PdfMatrix, a: Float, b: Float, c: Float, d: Float, e: Float, f: Float) {
        assertEquals("a", a, m.a, eps)
        assertEquals("b", b, m.b, eps)
        assertEquals("c", c, m.c, eps)
        assertEquals("d", d, m.d, eps)
        assertEquals("e", e, m.e, eps)
        assertEquals("f", f, m.f, eps)
    }

    // Placed in the top-left quarter of the page as shown: left 0, top 0, half as wide, a quarter as tall.
    private val topLeft = PlacedSignature(0, 0f, 0f, 0.5f)

    @Test fun upright() {
        // Bottom-left of the signature is at the middle-left... v = 0.25 down, so y = 20 + 0.75 * 800 = 620.
        check(PdfCoordinates.imageMatrix(box, 0, topLeft, 0.25f), 300f, 0f, 0f, 200f, 10f, 620f)
    }

    @Test fun rotated90() {
        // Shown width is the page height. Image x runs down the page (+px), image y runs along +py.
        check(PdfCoordinates.imageMatrix(box, 90, topLeft, 0.25f), 0f, 400f, -150f, 0f, 10f + 0.25f * 600f, 20f)
    }

    @Test fun rotated180() {
        check(PdfCoordinates.imageMatrix(box, 180, topLeft, 0.25f), -300f, 0f, 0f, -200f, 10f + 600f, 20f + 0.25f * 800f)
    }

    @Test fun rotated270() {
        check(PdfCoordinates.imageMatrix(box, 270, topLeft, 0.25f), 0f, -400f, 150f, 0f, 10f + 0.75f * 600f, 20f + 800f)
    }

    @Test fun rotationsOutsideTheUsualRangeWrapAround() {
        assertEquals(90, PdfCoordinates.normalizeRotation(-270))
        assertEquals(0, PdfCoordinates.normalizeRotation(360))
        assertEquals(180, PdfCoordinates.normalizeRotation(540))
    }

    @Test fun anOddRotationIsTreatedAsNone() = assertEquals(0, PdfCoordinates.normalizeRotation(45))

    @Test fun shownAspectSwapsForSidewaysPages() {
        assertEquals(0.75f, box.shownAspect(0), eps)
        assertEquals(800f / 600f, box.shownAspect(90), eps)
        assertEquals(0.75f, box.shownAspect(180), eps)
        assertEquals(800f / 600f, box.shownAspect(270), eps)
    }

    @Test fun imageCornersLandWhereThePersonPlacedThem() {
        // Upright page, signature at left 0.1, top 0.2, width 0.3, height 0.1:
        val m = PdfCoordinates.imageMatrix(box, 0, PlacedSignature(0, 0.1f, 0.2f, 0.3f), 0.1f)
        val topRightX = m.a * 1f + m.c * 1f + m.e
        val topRightY = m.b * 1f + m.d * 1f + m.f
        assertEquals(10f + 0.4f * 600f, topRightX, eps)
        assertEquals(20f + 0.8f * 800f, topRightY, eps)
    }

    @Test fun theTopLeftOfThePageShownMapsToTheRightCornerForEachRotation() {
        assertEquals(10f to 820f, PdfCoordinates.point(box, 0, 0f, 0f))
        assertEquals(10f to 20f, PdfCoordinates.point(box, 90, 0f, 0f))
        assertEquals(610f to 20f, PdfCoordinates.point(box, 180, 0f, 0f))
        assertEquals(610f to 820f, PdfCoordinates.point(box, 270, 0f, 0f))
    }

    @Test fun theBottomRightOfThePageShownMapsToTheOppositeCorner() {
        assertEquals(610f to 20f, PdfCoordinates.point(box, 0, 1f, 1f))
        assertEquals(610f to 820f, PdfCoordinates.point(box, 90, 1f, 1f))
        assertEquals(10f to 820f, PdfCoordinates.point(box, 180, 1f, 1f))
        assertEquals(10f to 20f, PdfCoordinates.point(box, 270, 1f, 1f))
    }
}
