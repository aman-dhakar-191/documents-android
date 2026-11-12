package io.github.amandhakar.documents.ooxml

/** Text going into an XML file: escaped, and cleaned of characters XML doesn't allow at all. Free of Android calls so it can be unit tested. */
object XmlText {
    /** True for characters an XML 1.0 file may contain (surrogate halves are kept so pairs survive). */
    private fun allowed(c: Char): Boolean = when {
        c == '\t' || c == '\n' || c == '\r' -> true
        c < ' ' -> false
        c == '￾' || c == '￿' -> false
        else -> true
    }

    fun escape(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (c in text) {
            if (!allowed(c)) continue
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
