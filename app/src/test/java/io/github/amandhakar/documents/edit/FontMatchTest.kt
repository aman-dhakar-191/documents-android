package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class FontMatchTest {
    private fun choose(name: String?, fixed: Boolean = false, serif: Boolean = false, bold: Boolean = false, italic: Boolean = false, weight: Float = 0f) =
        FontMatch.choose(name, fixed, serif, bold, italic, weight)

    @Test fun arialBoldIsBoldSans() =
        assertEquals(FontStyleChoice(FontFamilyChoice.Sans, bold = true), choose("ABCDEF+Arial-BoldMT"))

    @Test fun timesItalicIsItalicSerif() =
        assertEquals(FontStyleChoice(FontFamilyChoice.Serif, italic = true), choose("TimesNewRomanPS-ItalicMT"))

    @Test fun courierIsMono() = assertEquals(FontStyleChoice(FontFamilyChoice.Mono), choose("Courier"))

    @Test fun sansSerifFontsAreSansEvenThoughTheNameSaysSerif() {
        assertEquals(FontFamilyChoice.Sans, choose("NotoSans-Regular").family)
        assertEquals(FontFamilyChoice.Sans, choose("OpenSans-Bold").family)
        assertEquals(FontFamilyChoice.Sans, choose("DejaVuSans").family)
    }

    @Test fun serifFontsAreSerif() {
        assertEquals(FontFamilyChoice.Serif, choose("NotoSerif-Regular").family)
        assertEquals(FontFamilyChoice.Serif, choose("Georgia").family)
        assertEquals(FontFamilyChoice.Serif, choose("Garamond-Bold").family)
    }

    @Test fun theDescriptorFlagsHelpWhenTheNameSaysNothing() {
        assertEquals(FontFamilyChoice.Serif, choose("XYZ123", serif = true).family)
        assertEquals(FontFamilyChoice.Mono, choose("XYZ123", fixed = true).family)
        assertEquals(FontFamilyChoice.Sans, choose("XYZ123").family)
    }

    @Test fun theNameBeatsTheSerifFlag() = assertEquals(FontFamilyChoice.Sans, choose("Calibri", serif = true).family)

    @Test fun boldFromTheFlagTheWeightOrTheName() {
        assertEquals(true, choose("XYZ", bold = true).bold)
        assertEquals(true, choose("XYZ", weight = 700f).bold)
        assertEquals(true, choose("Foo-Black").bold)
        assertEquals(false, choose("Foo-Regular", weight = 400f).bold)
    }

    @Test fun italicFromTheFlagOrTheName() {
        assertEquals(true, choose("XYZ", italic = true).italic)
        assertEquals(true, choose("Foo-Oblique").italic)
        assertEquals(false, choose("Foo-Regular").italic)
    }

    @Test fun noNameMeansPlainSans() {
        assertEquals(FontStyleChoice(), choose(null))
        assertEquals(FontStyleChoice(), choose(""))
    }

    @Test fun theSubsetTagIsIgnored() = assertEquals(FontFamilyChoice.Mono, choose("ABCDEF+CourierNewPSMT").family)
}
