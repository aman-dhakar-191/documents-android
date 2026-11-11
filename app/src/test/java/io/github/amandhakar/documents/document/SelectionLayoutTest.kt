package io.github.amandhakar.documents.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectionLayoutTest {
    private val twoLines = listOf(
        NormRect(0.1f, 0.20f, 0.5f, 0.25f),
        NormRect(0.1f, 0.30f, 0.4f, 0.35f),
    )

    private fun assertPoint(x: Float, y: Float, p: NormPoint?) {
        assertEquals(x, p!!.x, 0.0001f)
        assertEquals(y, p.y, 0.0001f)
    }

    @Test fun startHandleIsUnderTheStartOfTheFirstLine() = assertPoint(0.1f, 0.25f, SelectionLayout.startTip(twoLines))

    @Test fun endHandleIsUnderTheEndOfTheLastLine() = assertPoint(0.4f, 0.35f, SelectionLayout.endTip(twoLines))

    @Test fun oneLineHasBothHandlesOnIt() {
        val one = listOf(NormRect(0.2f, 0.4f, 0.6f, 0.45f))
        assertPoint(0.2f, 0.45f, SelectionLayout.startTip(one))
        assertPoint(0.6f, 0.45f, SelectionLayout.endTip(one))
    }

    @Test fun extentsOfTheHighlight() {
        assertEquals(0.20f, SelectionLayout.top(twoLines)!!, 0.0001f)
        assertEquals(0.35f, SelectionLayout.bottom(twoLines)!!, 0.0001f)
        assertEquals(0.30f, SelectionLayout.centerX(twoLines)!!, 0.0001f)
    }

    @Test fun lineHeightsOfTheFirstAndLastLines() {
        val lines = listOf(NormRect(0.1f, 0.20f, 0.5f, 0.26f), NormRect(0.1f, 0.30f, 0.4f, 0.33f))
        assertEquals(0.06f, SelectionLayout.startLineHeight(lines)!!, 0.0001f)
        assertEquals(0.03f, SelectionLayout.endLineHeight(lines)!!, 0.0001f)
    }

    @Test fun nothingSelectedGivesNoLineHeights() {
        assertNull(SelectionLayout.startLineHeight(emptyList()))
        assertNull(SelectionLayout.endLineHeight(emptyList()))
    }

    @Test fun nothingSelectedGivesNoPositions() {
        assertNull(SelectionLayout.startTip(emptyList()))
        assertNull(SelectionLayout.endTip(emptyList()))
        assertNull(SelectionLayout.top(emptyList()))
        assertNull(SelectionLayout.bottom(emptyList()))
        assertNull(SelectionLayout.centerX(emptyList()))
    }

    private fun menu(
        top: Float = 300f,
        bottom: Float = 320f,
        centerX: Float = 500f,
        menuW: Float = 200f,
        menuH: Float = 48f,
        pageW: Float = 1000f,
        pageH: Float = 1400f,
    ) = SelectionLayout.menuPosition(top, bottom, centerX, menuW, menuH, pageW, pageH, gap = 16f, handleHeight = 40f)

    @Test fun menuGoesAboveTheSelectionCentred() {
        val p = menu()
        assertEquals(400f, p.x, 0.0001f)
        assertEquals(236f, p.y, 0.0001f) // 300 - 16 - 48
    }

    @Test fun menuIsKeptInsideTheLeftAndRightEdges() {
        assertEquals(0f, menu(centerX = 50f).x, 0.0001f)
        assertEquals(800f, menu(centerX = 980f).x, 0.0001f)
    }

    @Test fun menuGoesBelowTheHandlesWhenThereIsNoRoomAbove() {
        val p = menu(top = 20f, bottom = 320f)
        assertEquals(376f, p.y, 0.0001f) // 320 + 40 + 16
    }

    @Test fun menuIsKeptInsideTheBottomOfThePage() {
        val p = menu(top = 10f, bottom = 1390f)
        assertEquals(1352f, p.y, 0.0001f) // 1400 - 48
    }

    @Test fun menuBiggerThanThePageStaysAtTheTopLeft() {
        val p = menu(menuW = 300f, pageW = 100f, menuH = 48f, pageH = 30f)
        assertEquals(0f, p.x, 0.0001f)
        assertEquals(0f, p.y, 0.0001f)
    }
}
