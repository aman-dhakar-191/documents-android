package io.github.amandhakar.documents.slides

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {
    @Test fun noChangeKeepsTheColour() = assertEquals("4F81BD", ColorMath.adjustLuminance("4F81BD", null, null))

    @Test fun fullLumOffMakesWhite() = assertEquals("FFFFFF", ColorMath.adjustLuminance("4F81BD", 0, 100000))

    @Test fun zeroLumModMakesBlack() = assertEquals("000000", ColorMath.adjustLuminance("4F81BD", 0, 0))

    @Test fun lightenAndDarken() {
        val base = ColorMath.parse("4F81BD")!!.sum()
        assertTrue(ColorMath.parse(ColorMath.adjustLuminance("4F81BD", 40000, 60000))!!.sum() > base)
        assertTrue(ColorMath.parse(ColorMath.adjustLuminance("4F81BD", 50000, null))!!.sum() < base)
    }

    @Test fun greysStayGrey() {
        val rgb = ColorMath.parse(ColorMath.adjustLuminance("808080", 50000, null))!!
        assertTrue(rgb[0] == rgb[1] && rgb[1] == rgb[2])
    }

    @Test fun badColoursAreLeftAlone() = assertEquals("nonsense", ColorMath.adjustLuminance("nonsense", 50000, null))

    @Test fun parsesHex() {
        assertEquals(listOf(255, 0, 16), ColorMath.parse("FF0010")!!.toList())
        assertNull(ColorMath.parse("FF00"))
        assertNull(ColorMath.parse(null))
    }

    @Test fun namedColours() {
        assertEquals("000000", ColorMath.named("black"))
        assertEquals("FFFFFF", ColorMath.named("white"))
        assertNull(ColorMath.named("chartreuse"))
    }
}
