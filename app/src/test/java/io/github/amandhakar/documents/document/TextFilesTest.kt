package io.github.amandhakar.documents.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class TextFilesTest {
    @Test fun aShortFileIsReadWhole() {
        val (bytes, more) = TextFiles.readLimited(ByteArrayInputStream("hello".toByteArray()))
        assertEquals("hello", String(bytes))
        assertFalse(more)
    }

    @Test fun aLongFileIsCutAtTheLimitAndSaysSo() {
        val data = ByteArray(1000) { 'a'.code.toByte() }
        val (bytes, more) = TextFiles.readLimited(ByteArrayInputStream(data), limit = 100)
        assertEquals(100, bytes.size)
        assertTrue(more)
    }

    @Test fun aFileExactlyAtTheLimitIsNotCut() {
        val data = ByteArray(100) { 'a'.code.toByte() }
        val (bytes, more) = TextFiles.readLimited(ByteArrayInputStream(data), limit = 100)
        assertEquals(100, bytes.size)
        assertFalse(more)
    }

    @Test fun anEmptyFileIsEmpty() {
        val (bytes, more) = TextFiles.readLimited(ByteArrayInputStream(ByteArray(0)))
        assertEquals(0, bytes.size)
        assertFalse(more)
    }

    @Test fun aStreamThatGivesTinyPiecesIsStillCutRight() {
        val slow = object : InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 500) 'x'.code else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (n >= 500) return -1
                b[off] = 'x'.code.toByte()
                n++
                return 1
            }
        }
        val (bytes, more) = TextFiles.readLimited(slow, limit = 200)
        assertEquals(200, bytes.size)
        assertTrue(more)
    }

    @Test fun aReadErrorIsPassedOn() {
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("gone")
        }
        try {
            TextFiles.readLimited(broken)
            assertTrue("should have thrown", false)
        } catch (e: IOException) {
            assertEquals("gone", e.message)
        }
    }

    @Test fun utf8WithAndWithoutAMark() {
        assertEquals("héllo", TextFiles.decode("héllo".toByteArray()))
        assertEquals("héllo", TextFiles.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "héllo".toByteArray()))
    }

    @Test fun utf16FilesAreRead() {
        assertEquals("hi €", TextFiles.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi €".toByteArray(Charsets.UTF_16LE)))
        assertEquals("hi €", TextFiles.decode(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "hi €".toByteArray(Charsets.UTF_16BE)))
    }

    @Test fun badBytesBecomeReplacementCharactersNotErrors() {
        val text = TextFiles.decode(byteArrayOf('a'.code.toByte(), 0xFF.toByte(), 'b'.code.toByte()))
        assertEquals("a�b", text)
    }

    @Test fun aCutInTheMiddleOfACharacterIsDropped() {
        val bytes = ByteArray(TextFiles.MAX_BYTES) { 'a'.code.toByte() }
        bytes[bytes.size - 1] = 0xC3.toByte() // the first half of "é", cut off
        val text = TextFiles.decode(bytes)
        assertFalse(text.endsWith('�'))
        assertEquals(TextFiles.MAX_BYTES - 1, text.length)
    }
}
