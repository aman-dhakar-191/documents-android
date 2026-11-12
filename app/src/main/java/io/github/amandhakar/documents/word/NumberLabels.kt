package io.github.amandhakar.documents.word

/** The bullet or number text for list items. Free of Android calls so it can be unit tested. */
object NumberLabels {
    fun bullet(level: Int): String = when (level) {
        0 -> "•"
        1 -> "◦"
        else -> "▪"
    }

    /** [count] is 1-based. [format] is Word's number format name (decimal, lowerLetter, upperRoman, bullet ...). */
    fun label(format: String, count: Int, level: Int): String = when (format) {
        "bullet" -> bullet(level)
        "lowerLetter" -> letters(count, false) + "."
        "upperLetter" -> letters(count, true) + "."
        "lowerRoman" -> roman(count).lowercase() + "."
        "upperRoman" -> roman(count) + "."
        "none" -> ""
        else -> "$count."
    }

    /** 1 -> a, 26 -> z, 27 -> aa. */
    fun letters(n: Int, upper: Boolean): String {
        if (n < 1) return ""
        val sb = StringBuilder()
        var x = n
        while (x > 0) {
            x--
            sb.append(('a' + x % 26))
            x /= 26
        }
        val s = sb.reverse().toString()
        return if (upper) s.uppercase() else s
    }

    fun roman(n: Int): String {
        if (n < 1 || n > 3999) return n.toString()
        val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var x = n
        val sb = StringBuilder()
        for (i in values.indices) {
            while (x >= values[i]) {
                sb.append(symbols[i])
                x -= values[i]
            }
        }
        return sb.toString()
    }
}

/** Keeps the running number of each list so "1. 2. 3." come out right, restarting lower levels. */
class ListCounter {
    private val counts = HashMap<Pair<String, Int>, Int>()

    /** The number for the next item of list [numId] at [level]; [start] is where that level begins. */
    fun next(numId: String, level: Int, start: Int = 1): Int {
        // A higher level starting again restarts everything below it.
        counts.keys.filter { it.first == numId && it.second > level }.forEach { counts.remove(it) }
        val key = numId to level
        val value = (counts[key] ?: (start - 1)) + 1
        counts[key] = value
        return value
    }
}
