package io.github.amandhakar.documents.word

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun run(text: String, size: Float = 10f, face: WordFace = WordFace.LiberationMono, bold: Boolean = false) = WrapRun(text, face, bold, false, size)

/** A monospace font at 10 pt makes every character 6 pt wide, so line widths are easy to count. */
class TextWrapTest {
    private fun lines(text: String, width: Float, first: Float = width): List<String> {
        val ends = TextWrap.lineEnds(listOf(run(text)), first, width)
        var start = 0
        return ends.map { e -> text.substring(start, e).also { start = e } }
    }

    @Test fun emptyTextIsOneLine() {
        assertEquals(listOf(0), TextWrap.lineEnds(emptyList(), 100f, 100f))
        assertEquals(listOf(0), TextWrap.lineEnds(listOf(run("")), 100f, 100f))
    }

    @Test fun textThatFitsIsOneLine() {
        assertEquals(listOf("hello world"), lines("hello world", 66f)) // 11 chars x 6 = 66
    }

    @Test fun wordsMoveToTheNextLineWhole() {
        assertEquals(listOf("hello ", "world"), lines("hello world", 65f))
        assertEquals(listOf("one two ", "three"), lines("one two three", 48f))
    }

    @Test fun spacesAtTheEndOfALineDontCount() {
        // "hello" is 30 pt; the space after it would not fit in 30 but hangs past the edge.
        assertEquals(listOf("hello ", "world"), lines("hello world", 30f))
    }

    @Test fun aWordLongerThanTheLineIsCutInsideIt() {
        assertEquals(listOf("abcde", "fghij", "k"), lines("abcdefghijk", 30f))
    }

    @Test fun aLongWordAfterShortOnesStartsOnItsOwnLineFirst() {
        assertEquals(listOf("a ", "abcdefgh", "ij"), lines("a abcdefghij", 48f))
    }

    @Test fun hyphensAllowABreak() {
        assertEquals(listOf("well-", "known"), lines("well-known", 30f))
    }

    @Test fun anExplicitLineBreakEndsTheLine() {
        assertEquals(listOf("ab\n", "cd"), lines("ab\ncd", 600f))
    }

    @Test fun aLineBreakAtTheEndMakesAnEmptyLastLine() {
        assertEquals(listOf("ab\n", ""), lines("ab\n", 600f))
    }

    @Test fun theFirstLineCanHaveItsOwnWidth() {
        // First line 36 pt (6 chars), the rest 72 pt (12 chars).
        assertEquals(listOf("alpha ", "beta gamma "), lines("alpha beta gamma delta", 72f, 36f).take(2))
    }

    @Test fun tabsJumpToTheNextStop() {
        // "ab" is 12 pt, then a tab to 36, then "cd" ends at 48.
        assertEquals(listOf("ab\tcd"), lines("ab\tcd", 48f))
        assertEquals(2, lines("ab\tcd", 47f).size)
    }

    @Test fun differentSizesAreMeasuredEach() {
        val big = run("AAAA", size = 20f) // 4 x 12
        val small = run("bbbb", size = 10f) // 4 x 6
        assertEquals(listOf(8), TextWrap.lineEnds(listOf(big, small), 72f, 72f))
        assertEquals(listOf(4, 8), TextWrap.lineEnds(listOf(big, small), 50f, 50f))
    }

    @Test fun boldTextIsWiderInAProportionalFont() {
        val regular = TextWrap.lineEnds(listOf(run("hello world again", 12f, WordFace.LiberationSans)), 60f, 60f).size
        val bold = TextWrap.lineEnds(listOf(run("hello world again", 12f, WordFace.LiberationSans, bold = true)), 60f, 60f).size
        assertTrue(bold >= regular)
    }

    @Test fun everyCharacterEndsUpOnExactlyOneLine() {
        val text = "The committee reviewed the proposal, which was well-known; seeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee end.\nNew\tline"
        val ends = TextWrap.lineEnds(listOf(run(text, 11f, WordFace.Carlito)), 120f, 100f)
        assertEquals(text.length, ends.last())
        assertEquals(ends.sorted(), ends)
        assertEquals(ends.distinct(), ends.filterIndexed { i, e -> i == 0 || e > ends[i - 1] }.distinct())
    }

    @Test fun anAbsurdlyNarrowLineStillMakesProgress() {
        val ends = TextWrap.lineEnds(listOf(run("abcdef")), 0f, 0f)
        assertEquals(6, ends.last())
        assertEquals(6, ends.size) // one character per line at the minimum width
    }

    @Test fun aHugeTextDoesNotTakeForever() {
        val text = "word ".repeat(200_000)
        val ends = TextWrap.lineEnds(listOf(run(text, 11f, WordFace.Carlito)), 400f, 400f)
        assertEquals(text.length, ends.last())
    }

    @Test fun surrogatePairsAreNotSplitApart() {
        val ends = TextWrap.lineEnds(listOf(run("a😀b")), 1000f, 1000f)
        assertEquals(listOf(4), ends)
    }
}

class RealFontWrapTest {
    @Test fun aCalibriParagraphWrapsAboutLikeWord() {
        // Word fits roughly 90 characters of 11 pt Calibri on a 6 inch (432 pt) line.
        val text = ("The committee reviewed the proposal in detail and agreed that the schedule is realistic if the supplier delivers on time. ").repeat(4)
        val ends = TextWrap.lineEnds(listOf(WrapRun(text, WordFace.Carlito, false, false, 11f)), 432f, 432f)
        val firstLine = ends[0]
        assertTrue("first line had $firstLine characters", firstLine in 80..100)
    }
}
