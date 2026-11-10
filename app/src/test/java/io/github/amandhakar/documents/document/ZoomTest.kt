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

    @Test fun scaledMultipliesAndClamps() {
        assertEquals(3f, Zoom.scaled(1.5f, 2f), 0.0001f)
        assertEquals(Zoom.MAX, Zoom.scaled(2f, 10f), 0f)
        assertEquals(Zoom.MIN, Zoom.scaled(2f, 0.01f), 0f)
    }

    @Test fun scaledIgnoresBadFactors() {
        assertEquals(2f, Zoom.scaled(2f, Float.NaN), 0f)
        assertEquals(2f, Zoom.scaled(2f, 0f), 0f)
        assertEquals(2f, Zoom.scaled(2f, -1f), 0f)
        assertEquals(2f, Zoom.scaled(2f, Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun pinchScaleSpreadingAndPinching() {
        assertEquals(2f, Zoom.pinchScale(1f, 1f, 2f), 0.0001f)
        assertEquals(0.5f, Zoom.pinchScale(2f, 1f, 0.5f), 0.0001f)
    }

    @Test fun pinchScaleNeverLeavesTheLimits() {
        // At 1x, spreading by 10x can only reach the 4x maximum...
        assertEquals(4f, Zoom.pinchScale(1f, 1f, 10f), 0.0001f)
        // ...and pinching in from 2x can't go below 1x, i.e. a scale of 0.5.
        assertEquals(0.5f, Zoom.pinchScale(2f, 1f, 0.01f), 0.0001f)
    }

    @Test fun pinchScaleDoesNotStickAfterOverShooting() {
        var scale = 1f
        scale = Zoom.pinchScale(1f, scale, 10f) // clamped at 4x
        scale = Zoom.pinchScale(1f, scale, 0.5f) // pinch back in: 4 * 0.5 = 2x
        assertEquals(2f, scale, 0.0001f)
    }

    @Test fun pinchScaleIgnoresBadInput() {
        assertEquals(1f, Zoom.pinchScale(1f, 1f, Float.NaN), 0f)
        assertEquals(1f, Zoom.pinchScale(1f, 1f, 0f), 0f)
        assertEquals(1f, Zoom.pinchScale(1f, 1f, -2f), 0f)
        assertEquals(2f, Zoom.pinchScale(1f, Float.NaN, 2f), 0.0001f)
    }
}
