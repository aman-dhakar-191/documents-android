package io.github.amandhakar.documents.word

/**
 * The fonts bundled with the app, each with the same letter widths as a font Word documents commonly use (so text wraps
 * the way it does in Word): Carlito for Calibri, Caladea for Cambria, and Liberation Sans, Serif and Mono for Arial,
 * Times New Roman and Courier New.
 */
enum class WordFace { Carlito, Caladea, LiberationSans, LiberationSerif, LiberationMono }

/** Which bundled font stands in for a font a document names, and how wide and tall its text is. Free of Android calls so it can be unit tested. */
object WordFonts {
    /** Used when a document names no font, or one nothing here resembles: Calibri's stand-in, Word's usual default. */
    val DEFAULT = WordFace.Carlito

    private val exact: Map<String, WordFace> = buildMap {
        for (n in listOf("calibri", "calibri light", "carlito")) put(n, WordFace.Carlito)
        for (n in listOf("cambria", "cambria math", "caladea")) put(n, WordFace.Caladea)
        for (n in listOf(
            "times new roman", "times", "liberation serif", "georgia", "garamond", "book antiqua", "palatino", "palatino linotype",
            "century schoolbook", "century", "bookman old style", "baskerville", "constantia", "minion pro", "cambria text", "tinos",
            "rockwell", "perpetua", "sitka text", "sylfaen", "mongolian baiti", "batang", "simsun", "ms mincho",
        )) put(n, WordFace.LiberationSerif)
        for (n in listOf(
            "arial", "arial narrow", "arial black", "helvetica", "helvetica neue", "liberation sans", "arimo", "verdana", "tahoma",
            "trebuchet ms", "segoe ui", "segoe ui light", "century gothic", "franklin gothic book", "franklin gothic medium", "lucida sans",
            "lucida sans unicode", "lucida grande", "gill sans mt", "gill sans", "open sans", "roboto", "source sans pro", "aptos",
            "aptos display", "corbel", "candara", "microsoft sans serif", "ms gothic", "calibri (body)", "noto sans", "dejavu sans",
        )) put(n, WordFace.LiberationSans)
        for (n in listOf(
            "courier new", "courier", "liberation mono", "cousine", "consolas", "lucida console", "monaco", "menlo", "andale mono",
            "dejavu sans mono", "source code pro", "fira code", "ocr a extended",
        )) put(n, WordFace.LiberationMono)
    }

    /** The bundled font for a font name from a document: an exact match first, then clues in the name, else [DEFAULT]. */
    fun faceFor(name: String?): WordFace {
        val n = name?.trim()?.lowercase().orEmpty()
        if (n.isEmpty()) return DEFAULT
        exact[n]?.let { return it }
        return when {
            "mono" in n || "courier" in n || "console" in n || "typewriter" in n || n.endsWith(" code") -> WordFace.LiberationMono
            "sans" in n || "gothic" in n || "grotesk" in n || "arial" in n || "helvetica" in n || "verdana" in n -> WordFace.LiberationSans
            "serif" in n || "roman" in n || "times" in n || "garamond" in n || "palat" in n || "antiqua" in n || "georgia" in n || "didot" in n || "bodoni" in n -> WordFace.LiberationSerif
            "calibri" in n || "carlito" in n -> WordFace.Carlito
            "cambria" in n || "caladea" in n -> WordFace.Caladea
            else -> DEFAULT
        }
    }

    private class Table(val widths: IntArray)

    private val tables: Map<WordFace, Array<IntArray>> = mapOf(
        WordFace.Carlito to arrayOf(FontTables.CARLITO_REGULAR, FontTables.CARLITO_BOLD, FontTables.CARLITO_ITALIC, FontTables.CARLITO_BOLD_ITALIC),
        WordFace.Caladea to arrayOf(FontTables.CALADEA_REGULAR, FontTables.CALADEA_BOLD, FontTables.CALADEA_ITALIC, FontTables.CALADEA_BOLD_ITALIC),
        WordFace.LiberationSans to arrayOf(FontTables.LIBERATIONSANS_REGULAR, FontTables.LIBERATIONSANS_BOLD, FontTables.LIBERATIONSANS_ITALIC, FontTables.LIBERATIONSANS_BOLD_ITALIC),
        WordFace.LiberationSerif to arrayOf(FontTables.LIBERATIONSERIF_REGULAR, FontTables.LIBERATIONSERIF_BOLD, FontTables.LIBERATIONSERIF_ITALIC, FontTables.LIBERATIONSERIF_BOLD_ITALIC),
        WordFace.LiberationMono to arrayOf(FontTables.LIBERATIONMONO_REGULAR, FontTables.LIBERATIONMONO_BOLD, FontTables.LIBERATIONMONO_ITALIC, FontTables.LIBERATIONMONO_BOLD_ITALIC),
    )

    /** Where each codepoint sits in the width tables, or -1. */
    private val index: Map<Int, Int> = FontTables.CODEPOINTS.withIndex().associate { (i, c) -> c to i }

    /** Average advance of an ordinary letter in thousandths of an em, for characters the tables don't cover (accents on other alphabets, symbols). */
    private fun fallbackWidth(face: WordFace, style: Int): Int = tables.getValue(face)[style][index.getValue('n'.code)]

    /** A character's advance in ems (thousandths / 1000) in [face] with this look. Characters outside the tables count as an "n". */
    fun advanceEm(face: WordFace, bold: Boolean, italic: Boolean, codePoint: Int): Float {
        val style = (if (bold) 1 else 0) + (if (italic) 2 else 0)
        val i = index[codePoint]
        val w = if (i != null) tables.getValue(face)[style][i] else fallbackWidth(face, style)
        return w / 1000f
    }

    /** The natural height of one line of [face] text in ems (Word's single line spacing, before any multiple). */
    fun lineHeightEm(face: WordFace): Float = when (face) {
        WordFace.Carlito -> FontTables.CARLITO_LINE_EM
        WordFace.Caladea -> FontTables.CALADEA_LINE_EM
        WordFace.LiberationSans -> FontTables.LIBERATIONSANS_LINE_EM
        WordFace.LiberationSerif -> FontTables.LIBERATIONSERIF_LINE_EM
        WordFace.LiberationMono -> FontTables.LIBERATIONMONO_LINE_EM
    }
}
