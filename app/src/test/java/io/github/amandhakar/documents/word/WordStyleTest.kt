package io.github.amandhakar.documents.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordStyleTest {
    @Test fun headingsGetSmallerWithTheLevel() {
        assertTrue(WordStyle.headingSizeSp(1) > WordStyle.headingSizeSp(2))
        assertTrue(WordStyle.headingSizeSp(2) > WordStyle.headingSizeSp(3))
        assertTrue(WordStyle.headingSizeSp(3) > WordStyle.headingSizeSp(4))
        assertEquals(WordStyle.BODY_SP, WordStyle.headingSizeSp(6), 0f)
        assertEquals(WordStyle.BODY_SP, WordStyle.headingSizeSp(0), 0f)
    }

    @Test fun runSizeIsHalfPointsClamped() {
        assertEquals(12f, WordStyle.runSizeSp(24), 0f)
        assertEquals(8f, WordStyle.runSizeSp(2), 0f)
        assertEquals(48f, WordStyle.runSizeSp(400), 0f)
    }

    @Test fun darkColoursAreKept() {
        assertEquals("000000", WordStyle.readableColor("000000"))
        assertEquals("0000FF", WordStyle.readableColor("0000ff"))
        assertEquals("FF0000", WordStyle.readableColor("FF0000"))
    }

    @Test fun paleColoursAreDropped() {
        assertNull(WordStyle.readableColor("FFFFFF"))
        assertNull(WordStyle.readableColor("FFFF00"))
        assertNull(WordStyle.readableColor("D9D9D9"))
    }

    @Test fun badOrMissingColoursAreDropped() {
        assertNull(WordStyle.readableColor(null))
        assertNull(WordStyle.readableColor(""))
        assertNull(WordStyle.readableColor("red"))
        assertNull(WordStyle.readableColor("12345"))
    }

    @Test fun contrastOfBlackAndWhite() {
        assertEquals(21.0, WordStyle.contrastWithWhite(intArrayOf(0, 0, 0)), 0.01)
        assertEquals(1.0, WordStyle.contrastWithWhite(intArrayOf(255, 255, 255)), 0.01)
    }
}
