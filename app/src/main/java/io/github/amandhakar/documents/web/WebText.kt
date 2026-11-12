package io.github.amandhakar.documents.web

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset

/** What a tap on a link in a web page should do. */
enum class LinkAction { Allow, Ask, Block }

/** Decides what to do with a link tapped in a web page. Plain text rules, so it can be unit tested. */
object LinkPolicy {
    /**
     * Jumping inside the same page is allowed; web links, mail and phone links ask first and then open outside the app;
     * everything else (files, `content:`, `javascript:`, `intent:`, `data:`, an unknown or missing scheme) is blocked.
     */
    fun decide(url: String): LinkAction {
        val trimmed = url.trim()
        if (trimmed.startsWith("#") || trimmed.startsWith("about:blank#", ignoreCase = true)) return LinkAction.Allow
        val scheme = trimmed.substringBefore(':', missingDelimiterValue = "").lowercase()
        if (scheme.isEmpty() || !scheme.all { it in 'a'..'z' }) return LinkAction.Block
        val rest = trimmed.substringAfter(':')
        return when (scheme) {
            "http", "https" -> if (rest.startsWith("//") && rest.length > 2) LinkAction.Ask else LinkAction.Block
            "mailto", "tel" -> if (rest.isNotBlank()) LinkAction.Ask else LinkAction.Block
            else -> LinkAction.Block
        }
    }
}

/** Reading a saved web page into text. */
object HtmlText {
    /** Pages bigger than this aren't opened (a WebView holds the whole page in memory). */
    const val MAX_BYTES = 8 * 1024 * 1024

    private val CHARSET = Regex("charset\\s*=\\s*[\"']?\\s*([A-Za-z0-9_.:\\-]+)", RegexOption.IGNORE_CASE)

    /** All of [stream], or null if it is longer than [limit] bytes. Closes the stream. */
    fun readLimited(stream: InputStream, limit: Int = MAX_BYTES): ByteArray? = stream.use {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) return@use null
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }

    /**
     * The page's text: a byte order mark decides the encoding, otherwise a `charset=` in the first 2 KB (as in
     * `<meta charset="...">`), otherwise UTF-8. An unknown charset name falls back to UTF-8.
     */
    fun decode(bytes: ByteArray): String {
        fun b(i: Int) = bytes.getOrElse(i) { 0 }.toInt() and 0xFF
        return when {
            bytes.size >= 3 && b(0) == 0xEF && b(1) == 0xBB && b(2) == 0xBF -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            bytes.size >= 2 && b(0) == 0xFE && b(1) == 0xFF -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            bytes.size >= 2 && b(0) == 0xFF && b(1) == 0xFE -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            else -> {
                val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
                val declared = CHARSET.find(head)?.groupValues?.get(1)
                val charset = declared?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
                String(bytes, charset)
            }
        }
    }
}
