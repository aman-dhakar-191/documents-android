package io.github.amandhakar.documents.document

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomTest {
    @Test fun zoomInStepsUp() = assertEquals(1.5f, Zoom.zoomIn(1f), 0f)

    @Test fun zoomOutStepsDown() = assertEquals(1.5f, Zoom.zoomOut(2f), 0f)

    @Test fun zoomInStopsAtMax() = assertEquals(Zoom.MAX, Zoom.zoomIn(Zoom.MAX), 0f)

    @Test fun zoomOutStopsAtMin() = assertEquals(Zoom.MIN, Zoom.zoomOut(Zoom.MIN), 0f)

    @Test fun outOfRangeInputIsClamped() {
        assertEquals(Zoom.MAX, Zoom.zoomIn(99f), 0f)
        assertEquals(Zoom.MIN, Zoom.zoomOut(-3f), 0f)
    }
}
