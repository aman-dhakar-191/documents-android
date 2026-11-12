package io.github.amandhakar.documents.word

/** How a Word document is drawn on the white page of the reading view. Free of Android calls so it can be unit tested. */
object WordStyle {
    /** Heading text sizes in sp: Title / Heading 1 is the largest. Normal text is [BODY_SP]. */
    const val BODY_SP = 16f

    fun headingSizeSp(level: Int): Float = when (level) {
        1 -> 26f
        2 -> 22f
        3 -> 19f
        4 -> 17f
        else -> BODY_SP
    }

    /** A run's own size (half points) as sp, kept between 8 and 48 so one odd value can't make the text unreadable. */
    fun runSizeSp(halfPoints: Int): Float = (halfPoints / 2f).coerceIn(8f, 48f)

    /**
     * The run's colour if it can be read on the white page (contrast at least 3 to 1), else null so the
     * normal text colour is used. White or pale text from a dark-shaded table would otherwise vanish.
     */
    fun readableColor(hex: String?): String? {
        val rgb = parse(hex) ?: return null
        return if (contrastWithWhite(rgb) >= 3.0) hex!!.uppercase() else null
    }

    /** "RRGGBB" to three 0..255 numbers, or null if it isn't six hex digits. */
    fun parse(hex: String?): IntArray? {
        if (hex == null || !Regex("^[0-9A-Fa-f]{6}$").matches(hex)) return null
        return intArrayOf(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16))
    }

    /** WCAG contrast ratio between the colour and white. */
    fun contrastWithWhite(rgb: IntArray): Double {
        fun channel(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val luminance = 0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1]) + 0.0722 * channel(rgb[2])
        return 1.05 / (luminance + 0.05)
    }
}
