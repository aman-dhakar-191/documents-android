package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarksTest {
    private val page = PageLines(600f, 800f, emptyList())
    private val line = TextLine("hello", 60f, 80f, 300f, 100f, 96f, 12f)
    private val other = TextLine("world", 60f, 120f, 300f, 140f, 136f, 12f)

    @Test fun aHighlightCoversTheLineAsPageFractions() {
        val h = Marks.highlightFor(2, page, line)
        assertEquals(2, h.page)
        assertEquals(0.1f, h.left, 1e-6f)
        assertEquals(0.1f, h.top, 1e-6f)
        assertEquals(0.4f, h.width, 1e-6f)
        assertEquals(0.025f, h.height, 1e-6f)
    }

    @Test fun highlightingTwiceTakesTheHighlightAway() {
        val h = Marks.highlightFor(0, page, line)
        val once = Marks.toggle(emptyList(), h)
        assertEquals(1, once.size)
        assertTrue(Marks.toggle(once, h).isEmpty())
    }

    @Test fun differentLinesAreKeptSeparately() {
        val marks = Marks.toggle(Marks.toggle(emptyList(), Marks.highlightFor(0, page, line)), Marks.highlightFor(0, page, other))
        assertEquals(2, marks.size)
    }

    @Test fun theSameLineOnAnotherPageIsAnotherHighlight() {
        val marks = Marks.toggle(Marks.toggle(emptyList(), Marks.highlightFor(0, page, line)), Marks.highlightFor(1, page, line))
        assertEquals(2, marks.size)
    }

    @Test fun togglingAHighlightLeavesPenStrokesAlone() {
        val stroke = PenStroke(0, listOf(MarkPoint(0f, 0f), MarkPoint(1f, 1f)), Marks.PEN_WIDTH_FRACTION, Marks.PEN_COLORS[0])
        val h = Marks.highlightFor(0, page, line)
        val after = Marks.toggle(Marks.toggle(listOf(stroke), h), h)
        assertEquals(listOf<PageMark>(stroke), after)
    }

    @Test fun thinningKeepsTheEndsAndDropsCrowdedPoints() {
        val points = (0..100).map { MarkPoint(it * 0.0001f, 0f) } // 0.01 long in total, steps of 0.0001
        val thin = Marks.thin(points, minStep = 0.002f)
        assertEquals(points.first(), thin.first())
        assertEquals(points.last(), thin.last())
        assertTrue(thin.size < 15)
    }

    @Test fun spreadOutPointsAreAllKept() {
        val points = (0..10).map { MarkPoint(it * 0.05f, 0f) }
        assertEquals(points, Marks.thin(points, minStep = 0.002f))
    }

    @Test fun veryShortStrokesAreUntouched() {
        val two = listOf(MarkPoint(0f, 0f), MarkPoint(0.0001f, 0f))
        assertEquals(two, Marks.thin(two))
    }

    @Test fun aTapIsNotAStroke() {
        assertFalse(Marks.isStroke(listOf(MarkPoint(0.5f, 0.5f))))
        assertFalse(Marks.isStroke(listOf(MarkPoint(0.5f, 0.5f), MarkPoint(0.5f, 0.5f))))
        assertTrue(Marks.isStroke(listOf(MarkPoint(0.5f, 0.5f), MarkPoint(0.6f, 0.5f))))
    }
}
