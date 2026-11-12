package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureInkTest {
    private fun stroke(vararg xy: Float) = Stroke(xy.toList().chunked(2).map { InkPoint(it[0], it[1]) })

    @Test fun noStrokesIsBlank() = assertTrue(SignatureInk.isBlank(emptyList()))

    @Test fun aSingleTapIsBlank() = assertTrue(SignatureInk.isBlank(listOf(stroke(5f, 5f))))

    @Test fun manyPointsAtTheSameSpotAreBlank() = assertTrue(SignatureInk.isBlank(listOf(stroke(5f, 5f, 5f, 5f, 5f, 5f))))

    @Test fun aLineIsNotBlank() = assertFalse(SignatureInk.isBlank(listOf(stroke(0f, 0f, 10f, 10f))))

    @Test fun blankHasNoLayout() = assertNull(SignatureInk.layout(emptyList()))

    @Test fun theLayoutIsCroppedToTheInkWithPadding() {
        val layout = SignatureInk.layout(listOf(stroke(100f, 200f, 300f, 250f)), strokeWidthPx = 6f)!!
        val pad = SignatureInk.PADDING_PX + 3f
        assertEquals((200f + 2 * pad).toInt(), layout.width)
        assertEquals((50f + 2 * pad).toInt(), layout.height)
        assertEquals(1f, layout.scale, 0f)
        assertEquals(pad - 100f, layout.offsetX, 1e-4f)
        assertEquals(pad - 200f, layout.offsetY, 1e-4f)
    }

    @Test fun bigInkIsScaledDownToTheLimit() {
        val layout = SignatureInk.layout(listOf(stroke(0f, 0f, 4000f, 1000f)))!!
        assertTrue(layout.scale < 1f)
        assertTrue(layout.width <= SignatureInk.MAX_SIDE_PX)
        assertTrue(layout.height <= SignatureInk.MAX_SIDE_PX)
    }

    @Test fun aPerfectlyStraightHorizontalLineStillGetsAHeight() {
        val layout = SignatureInk.layout(listOf(stroke(0f, 5f, 100f, 5f)))
        assertNotNull(layout)
        assertTrue(layout!!.height > 1)
    }
}
