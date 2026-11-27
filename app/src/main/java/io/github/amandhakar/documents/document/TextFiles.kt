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
        val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        return text.trimEnd('�').let { if (text.endsWith('�') && bytes.size == MAX_BYTES) it else text }
    }

    /** The most text the editor takes: a phone's text box gets slow well before the 4 MB the viewer reads. */
    const val MAX_EDIT_CHARS = 512 * 1024

    /**
     * How [bytes] were written, so an edited text can be saved the same way: its encoding and whether lines end in CR LF.
     * Null when saving could change bytes the person never touched: the file isn't valid UTF-8 / UTF-16 (for example an
     * old one-byte encoding, or a binary file).
     */
    fun layoutOf(bytes: ByteArray): TextLayout? {
        val encoding = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> TextEncoding.Utf16Le
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> TextEncoding.Utf16Be
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> TextEncoding.Utf8Bom
            else -> TextEncoding.Utf8
        }
        val text = decode(bytes)
        val layout = TextLayout(encoding, crlf = text.contains("\r\n"))
        // Only when writing the text back gives exactly the same bytes is it safe to edit.
        return if (encode(normalize(text), layout).contentEquals(bytes)) layout else null
    }

    /** The text as the editor holds it: every line ending as a single LF. */
    fun normalize(text: String): String = text.replace("\r\n", "\n")

    /** [text] (with LF line endings) as bytes in [layout]: the same encoding and line endings as the file it came from. */
    fun encode(text: String, layout: TextLayout): ByteArray {
        val body = if (layout.crlf) normalize(text).replace("\n", "\r\n") else text
        return when (layout.encoding) {
            TextEncoding.Utf8 -> body.toByteArray(Charsets.UTF_8)
            TextEncoding.Utf8Bom -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body.toByteArray(Charsets.UTF_8)
            TextEncoding.Utf16Le -> byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + body.toByteArray(Charsets.UTF_16LE)
            TextEncoding.Utf16Be -> byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + body.toByteArray(Charsets.UTF_16BE)
        }
    }
}

enum class TextEncoding { Utf8, Utf8Bom, Utf16Le, Utf16Be }

/** How a text file was written: [encoding], and [crlf] when its lines end in CR LF (Windows) rather than LF. */
data class TextLayout(val encoding: TextEncoding, val crlf: Boolean)
