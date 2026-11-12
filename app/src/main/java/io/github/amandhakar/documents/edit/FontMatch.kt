package io.github.amandhakar.documents.edit

/** The three families the new text can be drawn in (the PDF standard fonts: Helvetica, Times, Courier). */
enum class FontFamilyChoice { Sans, Serif, Mono }

/** The look of a line of text, boiled down to what can be matched: family, bold and italic. */
data class FontStyleChoice(
    val family: FontFamilyChoice = FontFamilyChoice.Sans,
    val bold: Boolean = false,
    val italic: Boolean = false,
)

/** Picks the closest standard font for a font found in a PDF. Plain text rules, so it can be unit tested. */
object FontMatch {
    private val MONO = listOf("courier", "mono", "consolas", "menlo", "typewriter", "lucida console", "fixed", "terminal")
    private val SANS = listOf(
        "sans", "arial", "helvetica", "calibri", "verdana", "tahoma", "roboto", "gothic", "segoe", "trebuchet",
        "myriad", "frutiger", "univers", "futura", "lato", "montserrat", "poppins", "inter", "nunito",
    )
    private val SERIF = listOf(
        "serif", "times", "georgia", "garamond", "palatino", "cambria", "bookman", "century", "minion",
        "roman", "baskerville", "didot", "caslon", "constantia", "merriweather", "playfair",
    )
    private val BOLD = listOf("bold", "black", "heavy", "semibold", "demi", "extrabold")
    private val ITALIC = listOf("italic", "oblique", "slanted", "kursiv")

    /**
     * [name] is the font's name (a subset tag such as "ABCDEF+" is ignored); the flags and [weight] come from the font's
     * descriptor when it has one (weight 0 when unknown). The name is trusted over the serif flag, which many fonts get wrong.
     */
    fun choose(
        name: String?,
        fixedPitch: Boolean,
        serif: Boolean,
        forceBold: Boolean,
        italic: Boolean,
        weight: Float,
    ): FontStyleChoice {
        val n = (name ?: "").substringAfter('+').lowercase()
        val family = when {
            fixedPitch || MONO.any { it in n } -> FontFamilyChoice.Mono
            SANS.any { it in n } -> FontFamilyChoice.Sans
            SERIF.any { it in n } || serif -> FontFamilyChoice.Serif
            else -> FontFamilyChoice.Sans
        }
        return FontStyleChoice(
            family = family,
            bold = forceBold || weight >= 600f || BOLD.any { it in n },
            italic = italic || ITALIC.any { it in n },
        )
    }
}
