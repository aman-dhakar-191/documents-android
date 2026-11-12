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
}
