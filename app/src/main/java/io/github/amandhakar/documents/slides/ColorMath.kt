package io.github.amandhakar.documents.slides

/** Colour changes PowerPoint's theme colours ask for (lighter / darker). Free of Android calls so it can be unit tested. */
object ColorMath {
    /** "RRGGBB" to three 0..255 numbers, or null. */
    fun parse(hex: String?): IntArray? {
        if (hex == null || !Regex("^[0-9A-Fa-f]{6}$").matches(hex)) return null
        return intArrayOf(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16))
    }

    fun toHex(rgb: IntArray): String = rgb.joinToString("") { "%02X".format(it.coerceIn(0, 255)) }

    /** Applies lumMod and lumOff (both in 1/1000 of a percent, as PowerPoint writes them) to the colour's lightness. */
    fun adjustLuminance(hex: String, lumMod: Int?, lumOff: Int?): String {
        if (lumMod == null && lumOff == null) return hex
        val rgb = parse(hex) ?: return hex
        val (h, s, l) = toHsl(rgb)
        val newL = (l * (lumMod ?: 100000) / 100000.0 + (lumOff ?: 0) / 100000.0).coerceIn(0.0, 1.0)
        return toHex(fromHsl(h, s, newL))
    }

    private fun toHsl(rgb: IntArray): Triple<Double, Double, Double> {
        val r = rgb[0] / 255.0
        val g = rgb[1] / 255.0
        val b = rgb[2] / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2
        if (max == min) return Triple(0.0, 0.0, l)
        val d = max - min
        val s = if (l > 0.5) d / (2 - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6 else 0)) / 6
            g -> ((b - r) / d + 2) / 6
            else -> ((r - g) / d + 4) / 6
        }
        return Triple(h, s, l)
    }

    private fun fromHsl(h: Double, s: Double, l: Double): IntArray {
        if (s == 0.0) {
            val v = Math.round(l * 255).toInt()
            return intArrayOf(v, v, v)
        }
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun channel(t0: Double): Int {
            var t = t0
            if (t < 0) t += 1
            if (t > 1) t -= 1
            val v = when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 1.0 / 2 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
            return Math.round(v * 255).toInt()
        }
        return intArrayOf(channel(h + 1.0 / 3), channel(h), channel(h - 1.0 / 3))
    }

    /** The few colour names PowerPoint writes as `prstClr`. */
    fun named(name: String?): String? = when (name) {
        "black" -> "000000"
        "white" -> "FFFFFF"
        "red" -> "FF0000"
        "green" -> "008000"
        "blue" -> "0000FF"
        "yellow" -> "FFFF00"
        "gray", "grey" -> "808080"
        else -> null
    }
}
