package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TjRewriteTest {
    private fun bytes(vararg b: Int) = TjPart.Bytes(ByteArray(b.size) { b[it].toByte() })
    private fun glyph(remove: Boolean, advance: Float = 500f, len: Int = 1) = TjGlyph(len, remove, advance)

    @Test fun nothingRemovedLeavesTheArrayAsItWas() {
        val parts = listOf(bytes(65, 66), TjPart.Num(-20f), bytes(67))
        val out = TjRewrite.rewrite(parts, listOf(listOf(glyph(false), glyph(false)), listOf(glyph(false))))
        assertEquals(parts, out)
    }

    @Test fun aRemovedMiddleCharacterIsReplacedByItsAdvance() {
        val out = TjRewrite.rewrite(listOf(bytes(65, 66, 67)), listOf(listOf(glyph(false), glyph(true, 600f), glyph(false))))
        assertEquals(listOf(bytes(65), TjPart.Num(-600f), bytes(67)), out)
    }

    @Test fun removingTheWholeStringKeepsOnlyTheMove() {
        val out = TjRewrite.rewrite(listOf(bytes(65, 66)), listOf(listOf(glyph(true, 500f), glyph(true, 250f))))
        assertEquals(listOf<TjPart>(TjPart.Num(-750f)), out)
    }

    @Test fun removingTheEndStillMovesThePen() {
        val out = TjRewrite.rewrite(listOf(bytes(65, 66)), listOf(listOf(glyph(false), glyph(true, 400f))))
        assertEquals(listOf(bytes(65), TjPart.Num(-400f)), out)
    }

    @Test fun twoByteCharactersAreKeptOrDroppedWhole() {
        val parts = listOf(bytes(0, 65, 0, 66, 0, 67))
        val out = TjRewrite.rewrite(parts, listOf(listOf(glyph(false, len = 2), glyph(true, 500f, 2), glyph(false, len = 2))))
        assertEquals(listOf(bytes(0, 65), TjPart.Num(-500f), bytes(0, 67)), out)
    }

    @Test fun aSpacingNumberRightAfterARemovedRunIsFoldedIn() {
        val parts = listOf(bytes(65), TjPart.Num(-30f), bytes(66))
        val out = TjRewrite.rewrite(parts, listOf(listOf(glyph(true, 500f)), listOf(glyph(false))))
        assertEquals(listOf(TjPart.Num(-30f - 500f), bytes(66)), out)
    }

    @Test fun theTotalMoveIsPreservedAcrossTheWholeArray() {
        val parts = listOf(bytes(65, 66), TjPart.Num(-10f), bytes(67, 68))
        val glyphs = listOf(listOf(glyph(false, 500f), glyph(true, 300f)), listOf(glyph(true, 200f), glyph(false, 100f)))
        val out = TjRewrite.rewrite(parts, glyphs)
        val moved = out.filterIsInstance<TjPart.Num>().sumOf { it.value.toDouble() }
        // The dropped characters advanced 300 and 200 (as -300 and -200) and the original -10 stays: -510 in all.
        assertEquals(-510.0, moved, 1e-4)
    }

    @Test(expected = IllegalArgumentException::class) fun glyphsThatDontCoverTheStringAreRejected() {
        TjRewrite.rewrite(listOf(bytes(65, 66)), listOf(listOf(glyph(false))))
    }

    @Test(expected = IllegalArgumentException::class) fun missingGlyphsAreRejected() {
        TjRewrite.rewrite(listOf(bytes(65)), emptyList())
    }

    @Test fun neighbouringKeptCharactersStayInOneString() {
        val out = TjRewrite.rewrite(listOf(bytes(65, 66, 67, 68)), listOf(List(4) { glyph(false) }))
        assertEquals(listOf<TjPart>(bytes(65, 66, 67, 68)), out)
        assertTrue(out.size == 1)
    }
}
