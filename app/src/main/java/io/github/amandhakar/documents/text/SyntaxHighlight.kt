package io.github.amandhakar.documents.text

import io.github.amandhakar.documents.document.TextSyntax

/** The kinds of text that get their own colour. */
enum class TokenKind { Key, Str, Num, Literal, Comment, Punct, Tag, Attr, Section, Error, Warn }

/** Characters [start] (inclusive) to [end] (exclusive) of a line are of [kind]. */
data class Token(val start: Int, val end: Int, val kind: TokenKind)

/**
 * Colours for text files, one line at a time. It is a quick, forgiving tokenizer for reading, not a parser: a line it
 * doesn't understand is simply left plain. Only XML comments carry over from one line to the next ([lineStates]).
 * Free of Android calls so it can be unit tested.
 */
object SyntaxHighlight {
    /** Lines longer than this are left plain, so one huge line (minified JSON) can't slow the screen down. */
    const val MAX_LINE = 4000

    /** For each line, whether it starts inside an XML comment. Only XML needs this; other kinds give all false. */
    fun lineStates(lines: List<String>, syntax: TextSyntax): BooleanArray {
        val states = BooleanArray(lines.size)
        if (syntax != TextSyntax.Xml) return states
        var inComment = false
        for (i in lines.indices) {
            states[i] = inComment
            inComment = xmlEndsInComment(lines[i], inComment)
        }
        return states
    }

    fun tokens(line: String, syntax: TextSyntax, inComment: Boolean = false): List<Token> {
        if (line.length > MAX_LINE) return emptyList()
        return when (syntax) {
            TextSyntax.Json -> json(line)
            TextSyntax.Yaml -> yaml(line)
            TextSyntax.Xml -> xml(line, inComment)
            TextSyntax.Toml, TextSyntax.Ini -> ini(line)
            TextSyntax.Log -> log(line)
            TextSyntax.Plain, TextSyntax.Csv, TextSyntax.Tsv -> emptyList()
        }
    }

    // ---- JSON ----

    private fun json(line: String): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    val end = stringEnd(line, i, '"')
                    var j = end
                    while (j < line.length && line[j].isWhitespace()) j++
                    out += Token(i, end, if (j < line.length && line[j] == ':') TokenKind.Key else TokenKind.Str)
                    i = end
                }
                c == '-' || c.isDigit() -> {
                    var j = i + 1
                    while (j < line.length && (line[j].isDigit() || line[j] in ".eE+-")) j++
                    out += Token(i, j, TokenKind.Num)
                    i = j
                }
                c.isLetter() -> {
                    var j = i
                    while (j < line.length && line[j].isLetter()) j++
                    if (line.substring(i, j) in JSON_WORDS) out += Token(i, j, TokenKind.Literal)
                    i = j
                }
                c in "{}[],:" -> {
                    out += Token(i, i + 1, TokenKind.Punct)
                    i++
                }
                else -> i++
            }
        }
        return out
    }

    private val JSON_WORDS = setOf("true", "false", "null")

    /** The index just after the string that opens at [start] (or the end of the line when it isn't closed). */
    private fun stringEnd(line: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < line.length) {
            val c = line[i]
            if (c == '\\' && quote == '"') i += 2
            else if (c == quote) return i + 1
            else i++
        }
        return line.length
    }

    // ---- YAML ----

    private val YAML_WORDS = setOf("true", "false", "yes", "no", "on", "off", "null", "~", "True", "False", "TRUE", "FALSE", "Null", "NULL")
    private val NUMBER = Regex("[-+]?(\\d[\\d_]*(\\.\\d*)?([eE][-+]?\\d+)?|\\.\\d+|0x[0-9a-fA-F]+|\\.inf|\\.nan)")

    private fun yaml(line: String): List<Token> {
        val out = ArrayList<Token>()
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length
        if (trimmed.startsWith("#")) return listOf(Token(indent, line.length, TokenKind.Comment))
        if (trimmed == "---" || trimmed == "...") return listOf(Token(indent, line.length, TokenKind.Punct))
        var i = indent
        // List dashes ("- - item").
        while (i < line.length && line[i] == '-' && (i + 1 == line.length || line[i + 1] == ' ')) {
            out += Token(i, i + 1, TokenKind.Punct)
            i++
            while (i < line.length && line[i] == ' ') i++
        }
        // A key: plain or quoted text before ": " (or a colon at the end of the line).
        val keyEnd = yamlKeyEnd(line, i)
        if (keyEnd > i) {
            out += Token(i, keyEnd, TokenKind.Key)
            out += Token(keyEnd, keyEnd + 1, TokenKind.Punct)
            i = keyEnd + 1
        }
        while (i < line.length && line[i] == ' ') i++
        if (i >= line.length) return out
        // The value, up to a comment.
        val c = line[i]
        if (c == '"' || c == '\'') {
            val end = stringEnd(line, i, c)
            out += Token(i, end, TokenKind.Str)
            commentAfter(line, end)?.let { out += it }
            return out
        }
        if (c == '|' || c == '>' || c == '&' || c == '*' || c == '!') {
            var j = i
            while (j < line.length && line[j] != ' ') j++
            out += Token(i, j, TokenKind.Punct)
            commentAfter(line, j)?.let { out += it }
            return out
        }
        val comment = commentAfter(line, i)
        val valueEnd = comment?.start ?: line.length
        val value = line.substring(i, valueEnd).trimEnd()
        if (value.isNotEmpty()) {
            val kind = when {
                value in YAML_WORDS -> TokenKind.Literal
                NUMBER.matches(value) -> TokenKind.Num
                else -> null
            }
            if (kind != null) out += Token(i, i + value.length, kind)
        }
        comment?.let { out += it }
        return out
    }

    /** Where the key starting at [from] ends (the index of its colon), or [from] when the line has no key there. */
    private fun yamlKeyEnd(line: String, from: Int): Int {
        if (from >= line.length) return from
        var i = from
        if (line[i] == '"' || line[i] == '\'') {
            i = stringEnd(line, i, line[i])
            return if (i < line.length && line[i] == ':' && (i + 1 == line.length || line[i + 1] == ' ')) i else from
        }
        if (line[i] in "[{#") return from
        while (i < line.length) {
            if (line[i] == ':' && (i + 1 == line.length || line[i + 1] == ' ')) return i
            if (line[i] == '#' && i > from && line[i - 1] == ' ') return from
            i++
        }
        return from
    }

    /** A " # comment" that starts at or after [from], outside quotes. */
    private fun commentAfter(line: String, from: Int): Token? {
        var i = from
        var quote: Char? = null
        while (i < line.length) {
            val c = line[i]
            if (quote != null) {
                if (c == quote) quote = null
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '#' && (i == 0 || line[i - 1] == ' ' || line[i - 1] == '\t')) {
                return Token(i, line.length, TokenKind.Comment)
            }
            i++
        }
        return null
    }

    // ---- XML ----

    private fun xmlEndsInComment(line: String, startsInComment: Boolean): Boolean {
        var inComment = startsInComment
        var i = 0
        while (i < line.length) {
            if (inComment) {
                val end = line.indexOf("-->", i)
                if (end < 0) return true
                inComment = false
                i = end + 3
            } else {
                val start = line.indexOf("<!--", i)
                if (start < 0) return false
                inComment = true
                i = start + 4
            }
        }
        return inComment
    }

    private fun xml(line: String, startsInComment: Boolean): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        if (startsInComment) {
            val end = line.indexOf("-->")
            if (end < 0) return listOf(Token(0, line.length, TokenKind.Comment))
            out += Token(0, end + 3, TokenKind.Comment)
            i = end + 3
        }
        while (i < line.length) {
            val lt = line.indexOf('<', i)
            if (lt < 0) break
            if (line.startsWith("<!--", lt)) {
                val end = line.indexOf("-->", lt + 4)
                val stop = if (end < 0) line.length else end + 3
                out += Token(lt, stop, TokenKind.Comment)
                i = stop
                continue
            }
            // The tag name, with its "<", "</", "<?" or "<!".
            var j = lt + 1
            if (j < line.length && line[j] in "/?!") j++
            while (j < line.length && !line[j].isWhitespace() && line[j] != '>' && line[j] != '/') j++
            out += Token(lt, j, TokenKind.Tag)
            // Attributes until ">".
            while (j < line.length && line[j] != '>') {
                val c = line[j]
                when {
                    c == '"' || c == '\'' -> {
                        val end = stringEnd(line, j, c)
                        out += Token(j, end, TokenKind.Str)
                        j = end
                    }
                    c.isLetter() || c == '_' || c == ':' -> {
                        var k = j
                        while (k < line.length && (line[k].isLetterOrDigit() || line[k] in "_:.-")) k++
                        out += Token(j, k, TokenKind.Attr)
                        j = k
                    }
                    else -> j++
                }
            }
            if (j < line.length) {
                val closeStart = if (j > 0 && line[j - 1] in "/?") j - 1 else j
                out += Token(closeStart, j + 1, TokenKind.Tag)
                j++
            }
            i = j
        }
        return out.filter { it.end > it.start }.sortedBy { it.start }.fold(ArrayList()) { acc, t ->
            // Overlaps (the "/>" end after an attribute token) keep the earlier token whole.
            if (acc.isEmpty() || t.start >= acc.last().end) acc += t
            acc
        }
    }

    // ---- TOML, INI, .properties ----

    private fun ini(line: String): List<Token> {
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed[0] == '#' || trimmed[0] == ';' || trimmed[0] == '!') return listOf(Token(indent, line.length, TokenKind.Comment))
        if (trimmed[0] == '[') return listOf(Token(indent, line.length, TokenKind.Section))
        val out = ArrayList<Token>()
        val sep = line.indexOfFirst { it == '=' || it == ':' }
        if (sep <= indent) return emptyList()
        out += Token(indent, line.substring(0, sep).trimEnd().length, TokenKind.Key)
        out += Token(sep, sep + 1, TokenKind.Punct)
        var i = sep + 1
        while (i < line.length && line[i] == ' ') i++
        if (i >= line.length) return out
        val c = line[i]
        if (c == '"' || c == '\'') {
            out += Token(i, stringEnd(line, i, c), TokenKind.Str)
        } else {
            val hash = line.indexOf(" #", i).let { if (it < 0) line.length else it }
            val value = line.substring(i, hash).trim()
            if (value in setOf("true", "false")) out += Token(i, i + value.length, TokenKind.Literal)
            else if (NUMBER.matches(value)) out += Token(i, i + value.length, TokenKind.Num)
            if (hash < line.length) out += Token(hash + 1, line.length, TokenKind.Comment)
        }
        return out
    }

    // ---- logs ----

    private val LEVELS = Regex("\\b(FATAL|ERROR|ERR|SEVERE|WARN|WARNING|INFO|DEBUG|TRACE|VERBOSE)\\b")
    private val LEVEL_LETTER = Regex("^\\s*(\\d[\\d\\-: .]*\\s+)?(\\d+\\s+\\d+\\s+)?([EWIDV])[ /]")

    private fun log(line: String): List<Token> {
        val match = LEVELS.find(line)
        if (match != null) {
            val kind = when (match.value) {
                "FATAL", "ERROR", "ERR", "SEVERE" -> TokenKind.Error
                "WARN", "WARNING" -> TokenKind.Warn
                else -> TokenKind.Comment
            }
            return listOf(Token(match.range.first, match.range.last + 1, kind))
        }
        // Android logcat style ("11-27 09:30:00.123  1234  5678 E Tag: message").
        val letter = LEVEL_LETTER.find(line)?.groups?.get(3) ?: return emptyList()
        val kind = when (letter.value) {
            "E" -> TokenKind.Error
            "W" -> TokenKind.Warn
            else -> return emptyList()
        }
        return listOf(Token(letter.range.first, letter.range.last + 1, kind))
    }
}
