package io.github.amandhakar.documents.text

import io.github.amandhakar.documents.document.TextSyntax
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/** A mistake in a file's text: where it is (counted from 1) and what is wrong, in words for the person. */
data class TextProblem(val line: Int, val column: Int, val message: String) {
    override fun toString(): String = "Line $line, column $column: $message"
}

class JsonException(val problem: TextProblem) : Exception(problem.toString())

/**
 * Checks done before saving an edited file, so a typo that would break the file is caught first. JSON is fully checked,
 * XML is checked for being well formed, YAML only for tabs used to indent (which YAML forbids); other kinds aren't
 * checked. Free of Android calls so it can be unit tested.
 */
object TextChecks {
    fun check(text: String, syntax: TextSyntax): TextProblem? = when (syntax) {
        TextSyntax.Json -> if (text.isBlank()) null else try {
            Json.format(text)
            null
        } catch (e: JsonException) {
            e.problem
        }
        TextSyntax.Xml -> xml(text)
        TextSyntax.Yaml -> yamlTabs(text)
        else -> null
    }

    /** What the editor says while the text passes the check, or null for kinds that aren't checked. */
    fun okLabel(syntax: TextSyntax): String? = when (syntax) {
        TextSyntax.Json -> "Valid JSON"
        TextSyntax.Xml -> "Well-formed XML"
        TextSyntax.Yaml -> "Indenting looks fine (YAML is only checked for tabs)"
        else -> null
    }

    private fun yamlTabs(text: String): TextProblem? {
        text.split('\n').forEachIndexed { index, line ->
            val column = line.indexOfFirst { it != ' ' }
            if (column >= 0 && line[column] == '\t') return TextProblem(index + 1, column + 1, "YAML doesn't allow tabs for indenting. Use spaces.")
        }
        return null
    }

    private fun xml(text: String): TextProblem? {
        if (text.isBlank()) return null
        // A file that declares its own entities isn't parsed at all (that is how "billion laughs" files work): no check.
        if (text.contains("<!ENTITY")) return null
        return try {
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isValidating = false
            trySet(factory, "http://xml.org/sax/features/external-general-entities", false)
            trySet(factory, "http://xml.org/sax/features/external-parameter-entities", false)
            trySet(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            val reader = factory.newSAXParser().xmlReader
            reader.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            reader.contentHandler = DefaultHandler()
            reader.errorHandler = object : DefaultHandler() {
                override fun error(e: SAXParseException) = Unit // validity, not well-formedness
                override fun fatalError(e: SAXParseException) = throw e
            }
            reader.parse(InputSource(StringReader(text)))
            null
        } catch (e: SAXParseException) {
            TextProblem(e.lineNumber.coerceAtLeast(1), e.columnNumber.coerceAtLeast(1), e.message?.take(160) ?: "The XML isn't well formed.")
        } catch (e: Exception) {
            null // the check itself couldn't run: don't stop the person from saving
        }
    }

    private fun trySet(factory: SAXParserFactory, feature: String, on: Boolean) {
        try {
            factory.setFeature(feature, on)
        } catch (e: Exception) {
            // Not every parser knows every feature.
        }
    }
}

/**
 * A strict JSON reader (RFC 8259) that also lays the text out neatly. Numbers and strings are kept exactly as written
 * (escapes included), so formatting changes only the spaces and line breaks. Free of Android calls so it can be unit tested.
 */
object Json {
    /** Deeper nesting than this is refused rather than risking the stack. */
    const val MAX_DEPTH = 500

    /** [text] laid out with [indent] spaces per level. Throws [JsonException] when [text] isn't valid JSON. */
    fun format(text: String, indent: Int = 2): String {
        val reader = Reader(text, " ".repeat(indent))
        reader.skipSpace()
        if (reader.atEnd()) reader.fail("The file is empty.")
        reader.value(0)
        reader.skipSpace()
        if (!reader.atEnd()) reader.fail("Unexpected text after the end of the JSON.")
        return reader.out.toString()
    }

    private class Reader(val s: String, val unit: String) {
        var i = 0
        val out = StringBuilder(s.length + 16)

        fun atEnd() = i >= s.length

        fun fail(message: String): Nothing {
            var line = 1
            var col = 1
            for (k in 0 until minOf(i, s.length)) {
                if (s[k] == '\n') {
                    line++
                    col = 1
                } else {
                    col++
                }
            }
            throw JsonException(TextProblem(line, col, message))
        }

        fun skipSpace() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++
        }

        fun newline(depth: Int) {
            out.append('\n')
            repeat(depth) { out.append(unit) }
        }

        fun value(depth: Int) {
            if (depth > MAX_DEPTH) fail("The JSON is nested too deeply.")
            if (atEnd()) fail("The JSON ends too early.")
            when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> array(depth)
                '"' -> string()
                't' -> word("true")
                'f' -> word("false")
                'n' -> word("null")
                else -> if (c == '-' || c in '0'..'9') number() else fail("Expected a value (text, number, true, false, null, { or [).")
            }
        }

        fun obj(depth: Int) {
            i++
            out.append('{')
            skipSpace()
            if (i < s.length && s[i] == '}') {
                i++
                out.append('}')
                return
            }
            while (true) {
                skipSpace()
                newline(depth + 1)
                if (atEnd() || s[i] != '"') fail(if (!atEnd() && s[i] == '}') "Remove the comma before }." else "Expected a name in double quotes.")
                string()
                skipSpace()
                if (atEnd() || s[i] != ':') fail("Expected : after the name.")
                i++
                out.append(": ")
                skipSpace()
                value(depth + 1)
                skipSpace()
                if (atEnd()) fail("The JSON ends before the closing }.")
                when (s[i]) {
                    ',' -> {
                        i++
                        out.append(',')
                    }
                    '}' -> {
                        i++
                        newline(depth)
                        out.append('}')
                        return
                    }
                    else -> fail("Expected , or }.")
                }
            }
        }

        fun array(depth: Int) {
            i++
            out.append('[')
            skipSpace()
            if (i < s.length && s[i] == ']') {
                i++
                out.append(']')
                return
            }
            while (true) {
                skipSpace()
                if (!atEnd() && s[i] == ']') fail("Remove the comma before ].")
                newline(depth + 1)
                value(depth + 1)
                skipSpace()
                if (atEnd()) fail("The JSON ends before the closing ].")
                when (s[i]) {
                    ',' -> {
                        i++
                        out.append(',')
                    }
                    ']' -> {
                        i++
                        newline(depth)
                        out.append(']')
                        return
                    }
                    else -> fail("Expected , or ].")
                }
            }
        }

        fun string() {
            val start = i
            i++
            while (true) {
                if (atEnd()) {
                    i = start
                    fail("This text isn't closed with a \".")
                }
                val c = s[i]
                when {
                    c == '"' -> {
                        i++
                        out.append(s, start, i)
                        return
                    }
                    c == '\\' -> {
                        if (i + 1 >= s.length) fail("This text isn't closed with a \".")
                        when (s[i + 1]) {
                            '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> i += 2
                            'u' -> {
                                if (i + 6 > s.length || !s.substring(i + 2, i + 6).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) fail("\\u needs four hex digits.")
                                i += 6
                            }
                            else -> fail("Unknown escape \\${s[i + 1]}.")
                        }
                    }
                    c < ' ' -> fail(if (c == '\n') "Text can't run onto the next line. Close it with \" or write \\n." else "Text can't hold control characters.")
                    else -> i++
                }
            }
        }

        fun number() {
            val start = i
            if (s[i] == '-') i++
            if (atEnd()) fail("Expected a digit.")
            if (s[i] == '0') {
                i++
            } else if (s[i] in '1'..'9') {
                while (i < s.length && s[i] in '0'..'9') i++
            } else {
                fail("Expected a digit.")
            }
            if (i < s.length && s[i] in '0'..'9') fail("Numbers can't start with 0.")
            if (i < s.length && s[i] == '.') {
                i++
                if (atEnd() || s[i] !in '0'..'9') fail("Expected a digit after the decimal point.")
                while (i < s.length && s[i] in '0'..'9') i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (atEnd() || s[i] !in '0'..'9') fail("Expected a digit in the exponent.")
                while (i < s.length && s[i] in '0'..'9') i++
            }
            out.append(s, start, i)
        }

        fun word(w: String) {
            if (!s.startsWith(w, i)) fail("Expected a value (text, number, true, false, null, { or [).")
            i += w.length
            out.append(w)
        }
    }
}
