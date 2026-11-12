package io.github.amandhakar.documents.edit

/** One element of a PDF text-showing array (the operand of TJ): some bytes of text, or a spacing number. */
sealed interface TjPart {
    class Bytes(val bytes: ByteArray) : TjPart {
        override fun equals(other: Any?): Boolean = other is Bytes && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
        override fun toString(): String = "Bytes(${bytes.joinToString(",")})"
    }

    data class Num(val value: Float) : TjPart
}

/** One character of a text string being rewritten: how many bytes it takes, whether to drop it, and how far it advances (thousandths of the font size). */
data class TjGlyph(val byteLength: Int, val remove: Boolean, val advance: Float)

/** Plain-number logic for dropping characters from a text-showing array without moving the text that follows. */
object TjRewrite {
    /**
     * [parts] is the TJ array. [glyphs] gives, for each [TjPart.Bytes] in order, its characters. Characters marked
     * `remove` are dropped, and each run of dropped characters is replaced by one number that moves the pen on by the
     * same distance, so everything after it stays where it was. A string must be exactly covered by its glyphs.
     */
    fun rewrite(parts: List<TjPart>, glyphs: List<List<TjGlyph>>): List<TjPart> {
        val out = mutableListOf<TjPart>()
        var pending = 0f
        var kept = java.io.ByteArrayOutputStream()

        fun flushKept() {
            if (kept.size() > 0) {
                out += TjPart.Bytes(kept.toByteArray())
                kept = java.io.ByteArrayOutputStream()
            }
        }

        fun flushPending() {
            if (pending != 0f) {
                out += TjPart.Num(-pending)
                pending = 0f
            }
        }

        var stringIndex = 0
        for (part in parts) {
            when (part) {
                is TjPart.Num -> {
                    flushKept()
                    // Moving by a number while characters were dropped: fold the two together.
                    if (pending != 0f) {
                        out += TjPart.Num(part.value - pending)
                        pending = 0f
                    } else {
                        out += part
                    }
                }
                is TjPart.Bytes -> {
                    val list = glyphs.getOrNull(stringIndex++) ?: throw IllegalArgumentException("missing characters for a string")
                    require(list.sumOf { it.byteLength } == part.bytes.size) { "characters don't cover the string" }
                    var offset = 0
                    for (g in list) {
                        if (g.remove) {
                            flushKept()
                            pending += g.advance
                        } else {
                            if (pending != 0f) {
                                flushKept()
                                flushPending()
                            }
                            kept.write(part.bytes, offset, g.byteLength)
                        }
                        offset += g.byteLength
                    }
                }
            }
        }
        flushKept()
        flushPending()
        return out
    }
}
