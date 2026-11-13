package io.github.amandhakar.documents.document

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reading Markdown and plain text files: a size limit and a decoder that never fails. Free of Android calls so it can be unit tested. */
object TextFiles {
    /** The most of a file that is read; a longer one is cut here and the screen says so. */
    const val MAX_BYTES = 4 * 1024 * 1024

    /** Up to [limit] bytes of [input], and whether there was more. */
    fun readLimited(input: InputStream, limit: Int = MAX_BYTES): Pair<ByteArray, Boolean> {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        var more = false
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            val take = minOf(n, limit - total)
            out.write(buffer, 0, take)
            total += take
            if (take < n || total >= limit) {
                more = take < n || input.read() >= 0
                break
            }
        }
        return out.toByteArray() to more
    }

    /**
     * The text of [bytes]: UTF-16 when it starts with that byte order mark, else UTF-8 (a bad byte becomes a replacement
     * character, and a cut-off character at the very end is dropped). A leading UTF-8 mark is removed.
     */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        return text.trimEnd('�').let { if (text.endsWith('�') && bytes.size == MAX_BYTES) it else text }
    }
}
