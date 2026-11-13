package io.github.amandhakar.documents.markdown

import io.github.amandhakar.documents.word.DocxRun

/** The look inline text starts with: everything the surrounding block gives it (a heading's bold, a quote's colour). */
data class InlineBase(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val colorHex: String? = null,
    val fontName: String? = null,
)

/**
 * Turns the inline part of Markdown into runs: **bold**, *italic*, ***both***, ~~strike~~, `code`, [links](url),
 * `<autolinks>`, bare http(s) addresses, reference links, images (shown as their text; nothing is fetched), backslash
 * escapes, a few HTML entities, and line breaks. Emphasis follows CommonMark's pairing rules in a simplified form.
 * Free of Android calls so it can be unit tested.
 */
object MarkdownInline {
    const val LINK_COLOR = "1D5FD6"
    const val CODE_COLOR = "9B2C2C"
    const val MONO = "Courier New"
    private const val MAX_DEPTH = 6
    private const val MAX_LENGTH = 200_000

    /** Only these addresses become tappable; anything else (javascript:, file:, intent:, ...) stays plain text. */
    fun safeUrl(url: String): String? {
        val u = url.trim()
        val lower = u.lowercase()
        return if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")) u.take(2000) else null
    }

    private sealed interface Piece {
        class Text(val text: String) : Piece
        class Made(val runs: List<DocxRun>) : Piece
        class Delim(val char: Char, var count: Int, val canOpen: Boolean, val canClose: Boolean) : Piece {
            /** Style changes to apply when the walk reaches this piece: +1 starts, -1 ends. */
            val opens = ArrayList<Pair<Char, Int>>() // (kind: 'b', 'i', 's') with how many
            val closes = ArrayList<Char>()
            var leftover = count
            val kinds = ArrayList<Char>()
        }
    }

    /** [refs] maps lower-case reference names to addresses. */
    fun parse(text: String, base: InlineBase = InlineBase(), refs: Map<String, String> = emptyMap()): List<DocxRun> =
        parse(text.take(MAX_LENGTH), base, refs, null, 0)

    private fun parse(s: String, base: InlineBase, refs: Map<String, String>, link: String?, depth: Int): List<DocxRun> {
        val pieces = tokenize(s, base, refs, link, depth)
        pair(pieces)
        return render(pieces, base, link)
    }

    // ---- tokenizing ----

    private fun isPunct(c: Char) = c.code in 33..47 || c.code in 58..64 || c.code in 91..96 || c.code in 123..126 || Character.getType(c).let {
        it == Character.CONNECTOR_PUNCTUATION.toInt() || it == Character.DASH_PUNCTUATION.toInt() || it == Character.START_PUNCTUATION.toInt() ||
            it == Character.END_PUNCTUATION.toInt() || it == Character.INITIAL_QUOTE_PUNCTUATION.toInt() || it == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            it == Character.OTHER_PUNCTUATION.toInt()
    }

    private val ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "copy" to "©", "mdash" to "—", "ndash" to "–", "hellip" to "…")

    private fun tokenize(s: String, base: InlineBase, refs: Map<String, String>, link: String?, depth: Int): MutableList<Piece> {
        val out = ArrayList<Piece>()
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) {
                out += Piece.Text(text.toString())
                text.setLength(0)
            }
        }
        fun made(runs: List<DocxRun>) {
            flush()
            out += Piece.Made(runs)
        }
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < n && isPunct(s[i + 1]) -> {
                    text.append(s[i + 1])
                    i += 2
                }
                c == '\\' && i + 1 < n && s[i + 1] == '\n' -> {
                    text.append('\n')
                    i += 2
                }
                c == '&' -> {
                    val end = s.indexOf(';', i)
                    val entity = if (end in i + 2..i + 10) s.substring(i + 1, end) else null
                    val decoded = when {
                        entity == null -> null
                        entity.startsWith("#x") || entity.startsWith("#X") -> entity.drop(2).toIntOrNull(16)?.takeIf { it in 32..0x10FFFF }?.let { String(Character.toChars(it)) }
                        entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.takeIf { it in 32..0x10FFFF }?.let { String(Character.toChars(it)) }
                        else -> ENTITIES[entity]
                    }
                    if (decoded != null) {
                        text.append(decoded)
                        i = end + 1
                    } else {
                        text.append(c)
                        i++
                    }
                }
                c == '`' -> {
                    var run = 1
                    while (i + run < n && s[i + run] == '`') run++
                    val close = findBackticks(s, i + run, run)
                    if (close < 0) {
                        text.append("`".repeat(run))
                        i += run
                    } else {
                        var code = s.substring(i + run, close).replace('\n', ' ')
                        if (code.length > 1 && code.startsWith(" ") && code.endsWith(" ") && code.isNotBlank()) code = code.substring(1, code.length - 1)
                        made(listOf(DocxRun(code, bold = base.bold, italic = base.italic, strike = base.strike, colorHex = CODE_COLOR, fontName = MONO)))
                        i = close + run
                    }
                }
                c == '!' && i + 1 < n && s[i + 1] == '[' -> {
                    val parsed = parseBracket(s, i + 1, refs)
                    if (parsed != null) {
                        made(listOf(DocxRun("[Image: ${parsed.text.ifBlank { "picture" }}]", italic = true, colorHex = "5A606C")))
                        i = parsed.end
                    } else {
                        text.append(c)
                        i++
                    }
                }
                c == '[' -> {
                    val parsed = if (depth < MAX_DEPTH && link == null) parseBracket(s, i, refs) else null
                    if (parsed != null) {
                        val url = safeUrl(parsed.url)
                        val inner = parse(parsed.text, base, refs, url ?: "", depth + 1)
                        made(if (url != null) inner else inner.map { it.copy(linkUrl = null) })
                        i = parsed.end
                    } else {
                        text.append(c)
                        i++
                    }
                }
                c == '<' -> {
                    val close = s.indexOf('>', i)
                    val inside = if (close in i + 2..i + 2000) s.substring(i + 1, close) else null
                    when {
                        inside == null -> {
                            text.append(c)
                            i++
                        }
                        Regex("^(?i)https?://[^\\s<>]+$|^mailto:[^\\s<>]+$").matches(inside) -> {
                            made(listOf(DocxRun(inside, bold = base.bold, italic = base.italic, underline = true, colorHex = LINK_COLOR, linkUrl = safeUrl(inside))))
                            i = close + 1
                        }
                        Regex("^[^\\s<>@]+@[^\\s<>@]+\\.[^\\s<>@]+$").matches(inside) -> {
                            made(listOf(DocxRun(inside, underline = true, colorHex = LINK_COLOR, linkUrl = "mailto:$inside")))
                            i = close + 1
                        }
                        Regex("^(?i)br\\s*/?$").matches(inside) -> {
                            text.append('\n')
                            i = close + 1
                        }
                        Regex("^!--.*--$", RegexOption.DOT_MATCHES_ALL).matches(inside) -> i = close + 1 // an HTML comment
                        else -> {
                            text.append(c) // other HTML is shown as written, never run
                            i++
                        }
                    }
                }
                (c == 'h' || c == 'H') && link == null && (i == 0 || !s[i - 1].isLetterOrDigit()) && (s.startsWith("http://", i, ignoreCase = true) || s.startsWith("https://", i, ignoreCase = true)) -> {
                    var end = i
                    while (end < n && !s[end].isWhitespace() && s[end] != '<' && s[end] != '>') end++
                    while (end > i && s[end - 1] in ".,;:!?)\"'*_~") end--
                    val url = s.substring(i, end)
                    made(listOf(DocxRun(url, bold = base.bold, italic = base.italic, underline = true, colorHex = LINK_COLOR, linkUrl = safeUrl(url))))
                    i = end
                }
                c == '*' || c == '_' || c == '~' -> {
                    var run = 1
                    while (i + run < n && s[i + run] == c) run++
                    val before = if (i == 0) ' ' else s[i - 1]
                    val after = if (i + run >= n) ' ' else s[i + run]
                    val leftFlanking = !after.isWhitespace() && (!isPunct(after) || before.isWhitespace() || isPunct(before))
                    val rightFlanking = !before.isWhitespace() && (!isPunct(before) || after.isWhitespace() || isPunct(after))
                    val (canOpen, canClose) = when (c) {
                        '_' -> (leftFlanking && (!rightFlanking || isPunct(before))) to (rightFlanking && (!leftFlanking || isPunct(after)))
                        else -> leftFlanking to rightFlanking
                    }
                    flush()
                    out += Piece.Delim(c, run, canOpen, canClose)
                    i += run
                }
                c == '\n' -> {
                    // Two spaces before the break make it a hard one; otherwise it is a space.
                    val hard = text.endsWith("  ")
                    while (text.isNotEmpty() && text.last() == ' ') text.setLength(text.length - 1)
                    text.append(if (hard) '\n' else ' ')
                    i++
                    while (i < n && s[i] == ' ') i++
                }
                else -> {
                    text.append(c)
                    i++
                }
            }
        }
        flush()
        return out
    }

    private fun findBackticks(s: String, from: Int, count: Int): Int {
        var i = from
        while (i < s.length) {
            if (s[i] == '`') {
                var run = 1
                while (i + run < s.length && s[i + run] == '`') run++
                if (run == count) return i
                i += run
            } else {
                i++
            }
        }
        return -1
    }

    private class Bracket(val text: String, val url: String, val end: Int)

    /** `[text](url "title")`, `[text][ref]` or `[ref]` starting at [start] (a '['); null when it isn't one. */
    private fun parseBracket(s: String, start: Int, refs: Map<String, String>): Bracket? {
        var depth = 0
        var i = start
        var close = -1
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i++
                '`' -> {
                    var run = 1
                    while (i + run < s.length && s[i + run] == '`') run++
                    val end = findBackticks(s, i + run, run)
                    i = if (end >= 0) end + run - 1 else i + run - 1
                }
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        close = i
                        break
                    }
                }
            }
            i++
        }
        if (close < 0) return null
        val text = s.substring(start + 1, close)
        val next = close + 1
        if (next < s.length && s[next] == '(') {
            var p = next + 1
            while (p < s.length && s[p] == ' ') p++
            val urlStart = p
            val url: String
            if (p < s.length && s[p] == '<') {
                val end = s.indexOf('>', p)
                if (end < 0) return null
                url = s.substring(p + 1, end)
                p = end + 1
            } else {
                var parens = 0
                while (p < s.length && !s[p].isWhitespace()) {
                    if (s[p] == '(') parens++
                    if (s[p] == ')') {
                        if (parens == 0) break
                        parens--
                    }
                    p++
                }
                url = s.substring(urlStart, p)
            }
            while (p < s.length && s[p] == ' ') p++
            if (p < s.length && (s[p] == '"' || s[p] == '\'')) {
                val quote = s[p]
                val end = s.indexOf(quote, p + 1)
                if (end < 0) return null
                p = end + 1
                while (p < s.length && s[p] == ' ') p++
            }
            if (p >= s.length || s[p] != ')') return null
            return Bracket(text, url, p + 1)
        }
        if (next < s.length && s[next] == '[') {
            val end = s.indexOf(']', next)
            if (end > next) {
                val key = s.substring(next + 1, end).ifEmpty { text }.trim().lowercase()
                refs[key]?.let { return Bracket(text, it, end + 1) }
            }
        }
        refs[text.trim().lowercase()]?.let { return Bracket(text, it, next) }
        return null
    }

    // ---- emphasis ----

    /** Pairs openers and closers (CommonMark's delimiter stack, simplified) and records what each one starts or ends. */
    private fun pair(pieces: MutableList<Piece>) {
        val delims = pieces.withIndex().filter { it.value is Piece.Delim }
        val stack = ArrayList<Piece.Delim>()
        for ((_, piece) in delims) {
            val closer = piece as Piece.Delim
            if (closer.canClose) {
                while (closer.leftover > 0) {
                    val openerIndex = stack.indexOfLast { it.char == closer.char && it.canOpen && it.leftover > 0 && !(closer.char == '~' && it.leftover != closer.leftover) }
                    if (openerIndex < 0) break
                    val opener = stack[openerIndex]
                    val use = when {
                        closer.char == '~' -> if (opener.leftover >= 2 && closer.leftover >= 2) 2 else if (opener.leftover == closer.leftover) opener.leftover else 0
                        opener.leftover >= 2 && closer.leftover >= 2 -> 2
                        else -> 1
                    }
                    if (use == 0) break
                    val kind = if (closer.char == '~') 's' else if (use == 2) 'b' else 'i'
                    opener.leftover -= use
                    closer.leftover -= use
                    opener.kinds += kind
                    closer.closes += kind
                    // Anything between the two is no longer open.
                    while (stack.size > openerIndex + 1) stack.removeAt(stack.size - 1)
                    if (opener.leftover == 0) stack.removeAt(openerIndex)
                }
            }
            if (closer.leftover > 0 && closer.canOpen) stack += closer
        }
    }

    // ---- building runs ----

    private fun render(pieces: List<Piece>, base: InlineBase, link: String?): List<DocxRun> {
        val runs = ArrayList<DocxRun>()
        var bold = 0
        var italic = 0
        var strike = 0
        fun add(text: String) {
            if (text.isEmpty()) return
            runs += DocxRun(
                text, bold = base.bold || bold > 0, italic = base.italic || italic > 0, strike = base.strike || strike > 0,
                underline = !link.isNullOrEmpty(), colorHex = if (!link.isNullOrEmpty()) LINK_COLOR else base.colorHex,
                fontName = base.fontName, linkUrl = link?.takeIf { it.isNotEmpty() },
            )
        }
        for (piece in pieces) {
            when (piece) {
                is Piece.Text -> add(piece.text)
                is Piece.Made -> for (r in piece.runs) {
                    runs += r.copy(
                        bold = r.bold || bold > 0 || base.bold, italic = r.italic || italic > 0 || base.italic,
                        strike = r.strike || strike > 0 || base.strike,
                        colorHex = r.colorHex ?: base.colorHex, fontName = r.fontName ?: base.fontName,
                    )
                }
                is Piece.Delim -> {
                    // Closers end what they close, then unmatched characters are literal, then openers start what they open.
                    for (k in piece.closes) when (k) { 'b' -> bold--; 'i' -> italic--; 's' -> strike-- }
                    add(piece.char.toString().repeat(piece.leftover))
                    for (k in piece.kinds) when (k) { 'b' -> bold++; 'i' -> italic++; 's' -> strike++ }
                }
            }
        }
        return merge(runs)
    }

    private fun merge(runs: List<DocxRun>): List<DocxRun> {
        val out = ArrayList<DocxRun>()
        for (r in runs) {
            val last = out.lastOrNull()
            if (last != null && last.copy(text = "") == r.copy(text = "")) out[out.size - 1] = last.copy(text = last.text + r.text) else out += r
        }
        return out
    }
}
