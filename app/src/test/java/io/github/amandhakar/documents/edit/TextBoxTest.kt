package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextBoxTest {
    @Test fun theFontSizeMakesTheTextSpanTheWidth() {
        // The page is 600 pt wide, the text takes half of it (300 pt) and is 5 units wide at 1 pt: 60 pt.
        assertEquals(60f, TextBoxMath.fontSizePt(0.5f, 600f, 5f), 1e-3f)
    }

    @Test fun aTinyBoxIsNotShrunkBelowTheMinimum() = assertEquals(TextBoxMath.MIN_PT, TextBoxMath.fontSizePt(0.001f, 600f, 10f), 0f)

    @Test fun aHugeBoxIsNotGrownPastTheMaximum() = assertEquals(TextBoxMath.MAX_PT, TextBoxMath.fontSizePt(0.9f, 600f, 0.1f), 0f)

    @Test(expected = IllegalArgumentException::class) fun textWithNoWidthIsRejected() {
        TextBoxMath.fontSizePt(0.5f, 600f, 0f)
    }

    @Test fun theBaselineIsBelowTheTopOfTheBox() {
        assertEquals(0.3f + 0.1f * 0.8f, TextBoxMath.baselineFromTop(0.3f, 0.1f, 0.8f), 1e-6f)
        assertTrue(TextBoxMath.baselineFromTop(0.3f, 0.1f, 0.8f) > 0.3f)
    }

    @Test fun preparingMakesOneTrimmedLine() = assertEquals("a b", TextBoxMath.prepare("  a\nb  "))

    @Test fun preparingCutsVeryLongText() = assertEquals(TextBoxMath.MAX_LENGTH, TextBoxMath.prepare("x".repeat(500)).length)

    @Test fun blankStaysBlank() = assertEquals("", TextBoxMath.prepare(" \n\t "))

    @Test fun shortTextStartsAboutTwelvePointsLongAtFullPageWidth() {
        // 2 : 1 picture at zoom 1: 2 * 14 / 595 of the width.
        assertEquals(2f * 14f / 595f, TextBoxMath.startWidth(2f, 1f), 1e-6f)
    }

    @Test fun startWidthGrowsWithZoom() {
        assertEquals(TextBoxMath.startWidth(6f, 1f) * 2f, TextBoxMath.startWidth(6f, 2f), 1e-6f)
    }

    @Test fun startWidthIsKeptWithinTheLimits() {
        assertEquals(Placement.MIN_WIDTH, TextBoxMath.startWidth(0.1f, 1f), 0f)
        assertEquals(Placement.MAX_WIDTH, TextBoxMath.startWidth(400f, 3f), 0f)
    }

    @Test fun widthFractionFromAFontSize() {
        // 12 pt text that is 5 units wide per point is 60 pt wide: a tenth of a 600 pt page.
        assertEquals(0.1f, TextBoxMath.widthFraction(12f, 5f, 600f), 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class) fun aPageWithNoWidthIsRejected() {
        TextBoxMath.widthFraction(12f, 5f, 0f)
    }
}
