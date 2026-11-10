package io.github.amandhakar.documents.document

import org.junit.Assert.assertEquals
import org.junit.Test

class PageGeometryTest {
    private fun assertRect(expected: NormRect, actual: NormRect) {
        assertEquals(expected.left, actual.left, 0.0001f)
        assertEquals(expected.top, actual.top, 0.0001f)
        assertEquals(expected.right, actual.right, 0.0001f)
        assertEquals(expected.bottom, actual.bottom, 0.0001f)
    }

    @Test fun fractionOfAPosition() {
        assertEquals(0.5f, PageGeometry.fraction(50f, 100), 0f)
        assertEquals(0f, PageGeometry.fraction(0f, 100), 0f)
        assertEquals(1f, PageGeometry.fraction(100f, 100), 0f)
    }

    @Test fun fractionIsKeptInsideThePage() {
        assertEquals(0f, PageGeometry.fraction(-5f, 100), 0f)
        assertEquals(1f, PageGeometry.fraction(150f, 100), 0f)
    }

    @Test fun fractionHandlesBadInput() {
        assertEquals(0f, PageGeometry.fraction(10f, 0), 0f)
        assertEquals(0f, PageGeometry.fraction(10f, -3), 0f)
        assertEquals(0f, PageGeometry.fraction(Float.NaN, 100), 0f)
    }

    @Test fun pointsFromAFraction() {
        assertEquals(298, PageGeometry.toPoints(0.5f, 595)) // 297.5 rounds up
        assertEquals(0, PageGeometry.toPoints(0f, 595))
        assertEquals(595, PageGeometry.toPoints(1f, 595))
    }

    @Test fun pointsAreKeptInsideThePage() {
        assertEquals(100, PageGeometry.toPoints(2f, 100))
        assertEquals(0, PageGeometry.toPoints(-1f, 100))
    }

    @Test fun pointsHandleBadInput() {
        assertEquals(0, PageGeometry.toPoints(0.5f, 0))
        assertEquals(0, PageGeometry.toPoints(Float.NaN, 100))
    }

    @Test fun normalizeABox() {
        assertRect(NormRect(0.1f, 0.1f, 0.3f, 0.2f), PageGeometry.normalize(100f, 200f, 300f, 400f, 1000, 2000))
    }

    @Test fun normalizeSwappedCornersGivesTheSameBox() {
        assertRect(NormRect(0.1f, 0.1f, 0.3f, 0.2f), PageGeometry.normalize(300f, 400f, 100f, 200f, 1000, 2000))
    }

    @Test fun normalizeKeepsTheBoxInsideThePage() {
        assertRect(NormRect(0f, 0f, 1f, 1f), PageGeometry.normalize(-10f, -10f, 2000f, 3000f, 1000, 2000))
    }

    @Test fun normalizeWithAnEmptyPageGivesAnEmptyBox() {
        assertRect(NormRect(0f, 0f, 0f, 0f), PageGeometry.normalize(1f, 2f, 3f, 4f, 0, 100))
        assertRect(NormRect(0f, 0f, 0f, 0f), PageGeometry.normalize(1f, 2f, 3f, 4f, 100, 0))
    }

    @Test fun joinTextPutsEachBlockOnItsOwnLine() {
        assertEquals("a\nb", PageGeometry.joinText(listOf("a ", " b")))
    }

    @Test fun joinTextDropsEmptyBlocks() {
        assertEquals("", PageGeometry.joinText(listOf("", "   ")))
        assertEquals("", PageGeometry.joinText(emptyList()))
        assertEquals("only", PageGeometry.joinText(listOf("", " only ", "\n")))
    }
}
