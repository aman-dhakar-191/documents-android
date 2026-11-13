package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun glyphs(text: String, start: Float, size: Float = 11f, advance: Float = 5f): List<Glyph> =
    text.mapIndexed { i, c -> Glyph(c.toString(), start + i * advance, advance, size) }

class WordSplittingTest {
    @Test fun aNormalLineIsOnePiece() {
        val pieces = WordSplitting.split(glyphs("Hello wide world", 72f))
        assertEquals(1, pieces.size)
    }

    @Test fun twoColumnsOnOneRowAreCutAtTheGutter() {
        val row = glyphs("left column text", 90f) + glyphs("right column text", 324f)
        val pieces = WordSplitting.split(row)
        assertEquals(2, pieces.size)
        assertEquals("left column text", pieces[0].joinToString("") { it.text })
        assertEquals("right column text", pieces[1].joinToString("") { it.text })
    }

    @Test fun tableCellsAreCutIntoPieces() {
        val row = glyphs("Item", 72f) + glyphs("Qty", 200f) + glyphs("Price", 300f)
        assertEquals(3, WordSplitting.split(row).size)
    }

    @Test fun aGapJustUnderTheLimitStaysTogether() {
        val a = glyphs("ab", 72f) // ends at 82
        val b = glyphs("cd", 82f + 11f * 1.4f)
        assertEquals(1, WordSplitting.split(a + b).size)
    }

    @Test fun aGapJustOverTheLimitSplits() {
        val a = glyphs("ab", 72f)
        val b = glyphs("cd", 82f + 11f * 1.6f)
        assertEquals(2, WordSplitting.split(a + b).size)
    }

    @Test fun biggerTextAllowsAWiderGap() {
        val a = glyphs("Title", 72f, size = 26f)
        val b = glyphs("x", 72f + 25f + 26f * 1.4f, size = 26f)
        assertEquals(1, WordSplitting.split(a + b).size)
    }

    @Test fun emptyInputGivesNothing() = assertTrue(WordSplitting.split(emptyList()).isEmpty())

    @Test fun overlappingCharactersDoNotSplit() {
        val g = listOf(Glyph("f", 72f, 5f, 11f), Glyph("i", 70f, 5f, 11f))
        assertEquals(1, WordSplitting.split(g).size)
    }

    @Test fun sameTextIgnoresSpaces() {
        val pieces = WordSplitting.split(glyphs("ab", 72f) + glyphs("cd", 300f))
        assertTrue(WordSplitting.sameText(pieces, "ab cd"))
        assertTrue(WordSplitting.sameText(pieces, "abcd "))
    }

    @Test fun sameTextNoticesMissingOrExtraCharacters() {
        val pieces = WordSplitting.split(glyphs("ab", 72f) + glyphs("cd", 300f))
        assertFalse(WordSplitting.sameText(pieces, "abc"))
        assertFalse(WordSplitting.sameText(pieces, "abcde"))
    }
}
