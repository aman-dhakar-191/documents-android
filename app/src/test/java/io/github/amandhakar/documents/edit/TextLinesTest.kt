package io.github.amandhakar.documents.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLinesTest {
    private fun word(text: String, left: Float, baseline: Float = 100f, width: Float = 30f, size: Float = 10f) =
        TextWord(text, left, baseline, width, height = size, fontSize = size)

    @Test fun wordsOnTheSameBaselineJoinIntoOneLine() {
        val lines = LineGrouping.group(listOf(word("Hello", 10f), word("world", 45f)))
        assertEquals(1, lines.size)
        assertEquals("Hello world", lines[0].text)
        assertEquals(10f, lines[0].left, 0f)
        assertEquals(75f, lines[0].right, 0f)
    }

    @Test fun aBigGapStartsANewLine() {
        // A second column: 100 points to the right is more than 1.5 font sizes away.
        val lines = LineGrouping.group(listOf(word("left", 10f), word("right", 180f)))
        assertEquals(listOf("left", "right"), lines.map { it.text })
    }

    @Test fun aDifferentBaselineStartsANewLine() {
        val lines = LineGrouping.group(listOf(word("one", 10f, baseline = 100f), word("two", 10f, baseline = 114f)))
        assertEquals(listOf("one", "two"), lines.map { it.text })
    }

    @Test fun aSlightlyOffBaselineStillJoins() {
        val lines = LineGrouping.group(listOf(word("a", 10f, baseline = 100f), word("b", 45f, baseline = 101f)))
        assertEquals(1, lines.size)
    }

    @Test fun blankWordsAreIgnoredAndNothingGivesNothing() {
        assertEquals(listOf("x"), LineGrouping.group(listOf(word(" ", 0f), word("x", 5f))).map { it.text })
        assertEquals(0, LineGrouping.group(emptyList()).size)
    }

    @Test fun theLineBoxCoversTheLettersAndTheirDescenders() {
        val line = LineGrouping.group(listOf(word("Hi", 10f, baseline = 100f, size = 12f)))[0]
        assertEquals(88f, line.top, 1e-4f)
        assertEquals(103f, line.bottom, 1e-4f)
        assertEquals(100f, line.baseline, 0f)
    }

    private val lines = listOf(
        TextLine("first", 10f, 10f, 110f, 22f, 20f, 10f),
        TextLine("second", 10f, 30f, 110f, 42f, 40f, 10f),
    )

    @Test fun aTapInsideALineFindsIt() = assertEquals("second", LineHit.find(lines, 50f, 35f)?.text)

    @Test fun aTapJustOutsideStillFindsItWithinTheSlop() = assertEquals("first", LineHit.find(lines, 50f, 24f, slop = 3f)?.text)

    @Test fun aTapFarFromAnyLineFindsNothing() = assertNull(LineHit.find(lines, 50f, 200f))

    @Test fun whenTwoLinesAreInRangeTheCloserWins() = assertEquals("second", LineHit.find(lines, 50f, 26.5f, slop = 6f)?.text)

    @Test fun textThatFitsKeepsItsSize() = assertEquals(10f, TextFit.size(10f, 100f, 80f), 0f)

    @Test fun longerTextIsShrunkToFit() = assertEquals(8f, TextFit.size(10f, 100f, 125f), 1e-4f)

    @Test fun textIsNeverShrunkBelowSixtyPercent() = assertEquals(6f, TextFit.size(10f, 100f, 500f), 1e-4f)

    @Test fun nonsenseSizesAreLeftAlone() {
        assertEquals(0f, TextFit.size(0f, 100f, 200f), 0f)
        assertEquals(10f, TextFit.size(10f, 0f, 200f), 0f)
    }

    @Test fun plainLatinTextIsSupported() = assertTrue(PdfText.unsupported("Total: ₹ not yet, café €5 “quoted”").let { it == listOf('₹') })

    @Test fun hindiAndEmojiAreReportedOnce() =
        assertEquals(listOf('अ', '\uD83D', '\uDE00'), PdfText.unsupported("अअ ok 😀"))

    @Test fun cleaningTurnsBreaksIntoSpacesAndTrims() = assertEquals("a b c", PdfText.clean("  a\nb\tc \r"))

    @Test fun anEmptyReplacementIsFine() {
        assertEquals("", PdfText.clean("   "))
        assertEquals(emptyList<Char>(), PdfText.unsupported(""))
    }

    @Test fun averageColour() {
        assertEquals(0xFF7F7F7F.toInt(), ColorSample.average(listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())))
        assertEquals(0xFFFFFFFF.toInt(), ColorSample.average(emptyList()))
        assertEquals(0xFF102030.toInt(), ColorSample.average(listOf(0xFF102030.toInt(), 0xFF102030.toInt())))
    }

    @Test fun anEditKnowsWhichLineItReplaces() {
        val page = PageLines(600f, 800f, lines)
        val a = TextEdit.forLine(0, page, lines[0], "new", 0xFFFFFFFF.toInt())
        val b = TextEdit.forLine(0, page, lines[0], "newer", 0xFFFFFFFF.toInt())
        val c = TextEdit.forLine(0, page, lines[1], "x", 0xFFFFFFFF.toInt())
        assertTrue(a.sameLineAs(b))
        assertTrue(!a.sameLineAs(c))
        assertEquals(10f / 600f, a.left, 1e-6f)
        assertEquals(100f / 600f, a.width, 1e-6f)
        assertEquals(20f / 800f, a.baseline, 1e-6f)
        assertNotNull(a)
    }
}
