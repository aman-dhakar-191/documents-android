package io.github.amandhakar.documents.markdown

import io.github.amandhakar.documents.word.DocxAlign
import io.github.amandhakar.documents.word.DocxBlock
import io.github.amandhakar.documents.word.DocxParagraph
import io.github.amandhakar.documents.word.DocxRun
import io.github.amandhakar.documents.word.DocxTable
import io.github.amandhakar.documents.word.PageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun blocks(md: String): List<DocxBlock> = MarkdownParser.parse(md).blocks
private fun paras(md: String): List<DocxParagraph> = blocks(md).filterIsInstance<DocxParagraph>()
private fun runs(md: String): List<DocxRun> = MarkdownInline.parse(md)
private fun plain(md: String) = runs(md).joinToString("") { it.text }

class MarkdownBlockTest {
    @Test fun headingsGetTheirLevelAndBold() {
        val p = paras("# One\n\n## Two\n\n###### Six")
        assertEquals(listOf(1, 2, 6), p.map { it.headingLevel })
        assertEquals(listOf("One", "Two", "Six"), p.map { it.text })
        assertTrue(p.all { it.runs.all { r -> r.bold } })
    }

    @Test fun aHashWithoutASpaceIsNotAHeading() {
        assertEquals(0, paras("#hashtag").single().headingLevel)
    }

    @Test fun closingHashesAreDropped() {
        assertEquals("Title", paras("## Title ##").single().text)
    }

    @Test fun setextHeadings() {
        val p = paras("Big\n===\n\nSmall\n---\n")
        assertEquals(listOf(1, 2), p.map { it.headingLevel })
    }

    @Test fun linesOfAParagraphJoinWithASpace() {
        assertEquals("one two three", paras("one\ntwo\nthree").single().text)
    }

    @Test fun twoSpacesMakeAHardBreak() {
        assertEquals("one\ntwo", paras("one  \ntwo").single().text)
    }

    @Test fun aBlankLineStartsANewParagraph() {
        assertEquals(listOf("a", "b"), paras("a\n\nb").map { it.text })
    }

    @Test fun aRuleBecomesALine() {
        val rule = paras("above\n\n---\n\nbelow")[1]
        assertEquals("", rule.text)
        assertNotNull(rule.layout!!.bottomBorder)
        assertEquals(4, paras("a\n\n***\n\nb\n\n___").size) // two paragraphs and two rules
    }

    @Test fun fencedCodeKeepsItsLinesAndSpaces() {
        val code = paras("```kotlin\nfun main() {\n    println(1)\n}\n```").single()
        assertEquals("fun main() {\n    println(1)\n}", code.text)
        assertEquals("Courier New", code.runs.single().fontName)
        assertEquals("F3F4F6", code.layout!!.shadingHex)
    }

    @Test fun markdownInsideACodeBlockIsNotRead() {
        assertEquals("# not a heading\n- not a list", paras("```\n# not a heading\n- not a list\n```").single().text)
    }

    @Test fun anUnclosedFenceRunsToTheEnd() {
        assertEquals("a\nb", paras("```\na\nb").single().text)
    }

    @Test fun aTildeFenceNeedsATildeToClose() {
        assertEquals("a\n```\nb", paras("~~~\na\n```\nb\n~~~").single().text)
    }

    @Test fun indentedCodeIsCode() {
        val p = paras("text\n\n    code line\n    more\n\nafter")
        assertEquals(listOf("text", "code line\nmore", "after"), p.map { it.text })
    }

    @Test fun frontMatterIsShownAsCode() {
        val p = paras("---\ntitle: Hello\ntags: [a]\n---\n\n# Body")
        assertEquals("title: Hello\ntags: [a]", p[0].text)
        assertEquals("Courier New", p[0].runs.single().fontName)
        assertEquals(1, p[1].headingLevel)
    }

    @Test fun anOpeningRuleWithoutAnEndIsJustARule() {
        val p = paras("---\ntext")
        assertEquals(2, p.size)
    }

    @Test fun htmlCommentsAreDropped() {
        assertEquals(listOf("keep"), paras("<!-- hidden\nacross lines -->\n\nkeep").map { it.text })
        assertEquals("ab", plain("a<!-- x -->b"))
    }

    @Test fun rawHtmlIsShownAsWrittenAndNeverRun() {
        assertEquals("<script>alert(1)</script>", paras("<script>alert(1)</script>").single().text)
    }

    @Test fun blockQuotesAreIndentedAndGrey() {
        val q = paras("> quoted line\n> more").single()
        assertEquals("quoted line more", q.text)
        assertTrue(q.layout!!.leftPt > 0f)
        assertEquals(MarkdownParser.QUOTE_COLOR, q.runs[0].colorHex)
    }

    @Test fun nestedQuotesIndentMore() {
        val p = paras("> a\n>> b")
        assertTrue(p.last().layout!!.leftPt > p.first().layout!!.leftPt)
    }

    @Test fun aQuoteCanHoldAList() {
        val p = paras("> - one\n> - two")
        assertEquals(listOf("one", "two"), p.map { it.text })
        assertTrue(p.all { it.list != null })
    }

    @Test fun anEmptyDocumentHasNoBlocks() {
        assertTrue(blocks("").isEmpty())
        assertTrue(blocks("\n\n   \n").isEmpty())
    }

    @Test fun windowsLineEndingsAndABomAreHandled() {
        assertEquals(listOf("A", "b"), paras("\uFEFF# A\r\n\r\nb\r\n").map { it.text })
    }

    @Test fun tabsInIndentationCountAsCodeIndent() {
        assertEquals("code", paras("a\n\n\tcode").last().text)
    }
}

class MarkdownListTest {
    @Test fun bulletListsGetBullets() {
        val p = paras("- one\n- two\n* three\n+ four")
        assertEquals(listOf("one", "two", "three", "four"), p.map { it.text })
        assertTrue(p.all { it.list != null && it.list!!.level == 0 && it.list!!.label == "•" })
    }

    @Test fun numberedListsCountUpFromTheirStart() {
        val p = paras("3. three\n4. four\n5. five")
        assertEquals(listOf("3.", "4.", "5."), p.map { it.list!!.label })
        assertEquals(listOf("1.", "2."), paras("1) a\n2) b").map { it.list!!.label })
    }

    @Test fun nestedListsGoDeeper() {
        val p = paras("- a\n  - b\n    - c\n- d")
        assertEquals(listOf(0, 1, 2, 0), p.map { it.list!!.level })
        assertEquals(listOf("a", "b", "c", "d"), p.map { it.text })
        assertEquals(listOf("•", "◦", "▪", "•"), p.map { it.list!!.label })
    }

    @Test fun anOrderedListInsideABulletList() {
        val p = paras("- step\n  1. first\n  2. second")
        assertEquals(listOf("•", "1.", "2."), p.map { it.list!!.label })
    }

    @Test fun taskListsGetBoxes() {
        val p = paras("- [ ] todo\n- [x] done\n- [X] also")
        assertEquals(listOf("☐", "☑", "☑"), p.map { it.list!!.label })
        assertEquals(listOf("todo", "done", "also"), p.map { it.text })
    }

    @Test fun aListItemCanContinueOnTheNextLine() {
        assertEquals("one two", paras("- one\n  two").single().text)
        assertEquals("one two", paras("- one\ntwo").single().text) // lazy continuation
    }

    @Test fun aListItemCanHoldASecondParagraphAndCode() {
        val p = paras("- first\n\n  second para\n\n  ```\n  code\n  ```\n- next")
        assertEquals(listOf("first", "second para", "code", "next"), p.map { it.text })
        assertNotNull(p[0].list)
        assertNull(p[1].list)
        assertEquals(18f, p[1].layout!!.leftPt, 0f)
    }

    @Test fun blankLinesBetweenItemsKeepOneList() {
        val p = paras("- a\n\n- b\n\n- c")
        assertEquals(3, p.count { it.list != null })
    }

    @Test fun aParagraphAfterAListEndsIt() {
        val p = paras("- a\n- b\n\nplain")
        assertNull(p.last().list)
    }

    @Test fun aListInterruptsAParagraph() {
        val p = paras("intro\n- a\n- b")
        assertEquals(listOf("intro", "a", "b"), p.map { it.text })
    }

    @Test fun anEmptyItemIsFine() {
        assertEquals(2, paras("-\n- b").size)
    }

    @Test fun deepNestingDoesNotRunAway() {
        val md = (0 until 40).joinToString("\n") { " ".repeat(it * 2) + "- level $it" }
        val p = paras(md)
        assertTrue(p.isNotEmpty())
        assertTrue(p.all { (it.list?.level ?: 0) <= 8 })
    }

    @Test fun anItemStartingWithAHeadingKeepsTheMark() {
        val p = paras("- # Title\n  text")
        assertNotNull(p.first().list)
    }
}

class MarkdownTableTest {
    private val md = "| Name | Qty | Price |\n|:--|:-:|--:|\n| Apple | 3 | 1.50 |\n| Pear | 12 | 0.75 |\n"

    @Test fun aTableHasItsRowsAndCells() {
        val t = blocks(md).filterIsInstance<DocxTable>().single()
        assertEquals(3, t.rows.size)
        assertEquals(3, t.rows[0].size)
        assertEquals("Name", (t.rows[0][0][0] as DocxParagraph).text)
        assertEquals("Pear", (t.rows[2][0][0] as DocxParagraph).text)
    }

    @Test fun theHeaderIsBoldAndShaded() {
        val t = blocks(md).filterIsInstance<DocxTable>().single()
        assertTrue((t.rows[0][0][0] as DocxParagraph).runs.all { it.bold })
        assertFalse((t.rows[1][0][0] as DocxParagraph).runs.any { it.bold })
        assertEquals("F1F3F4", t.layout!!.cells[0][0].shadingHex)
        assertNull(t.layout!!.cells[1][0].shadingHex)
    }

    @Test fun columnAlignmentComesFromTheSeparator() {
        val t = blocks(md).filterIsInstance<DocxTable>().single()
        assertEquals(listOf(DocxAlign.Start, DocxAlign.Center, DocxAlign.End), t.rows[1].map { (it[0] as DocxParagraph).align })
    }

    @Test fun cellsAreReadAsMarkdown() {
        val t = blocks("| a |\n|---|\n| **b** and `c` |").filterIsInstance<DocxTable>().single()
        val p = t.rows[1][0][0] as DocxParagraph
        assertTrue(p.runs.any { it.bold && it.text == "b" })
        assertTrue(p.runs.any { it.fontName == "Courier New" && it.text == "c" })
    }

    @Test fun shortRowsArePaddedAndLongOnesCut() {
        val t = blocks("| a | b |\n|---|---|\n| 1 |\n| 1 | 2 | 3 |").filterIsInstance<DocxTable>().single()
        assertTrue(t.rows.all { it.size == 2 })
    }

    @Test fun anEscapedPipeStaysInTheCell() {
        val t = blocks("| a |\n|---|\n| x \\| y |").filterIsInstance<DocxTable>().single()
        assertEquals("x | y", (t.rows[1][0][0] as DocxParagraph).text)
    }

    @Test fun aPipeInsideCodeStaysInTheCell() {
        val t = blocks("| a | b |\n|---|---|\n| `x | y` | z |").filterIsInstance<DocxTable>().single()
        assertEquals(2, t.rows[1].size)
    }

    @Test fun aTableWithoutAPipeSeparatorIsNotATable() {
        assertTrue(blocks("a | b\nc | d").none { it is DocxTable })
    }

    @Test fun columnWidthsAddUpToTheTextWidth() {
        val t = blocks(md).filterIsInstance<DocxTable>().single()
        assertEquals(487f, t.layout!!.columnWidthsPt.sum(), 1f)
        assertTrue(t.layout!!.columnWidthsPt.all { it > 0f })
    }

    @Test fun aTableKeepsAParagraphBeforeAndAfter() {
        val b = blocks("before\n\n$md\nafter")
        assertTrue(b.first() is DocxParagraph && b[1] is DocxTable && b.last() is DocxParagraph)
    }

    @Test fun aHugeTableDoesNotBreakAnything() {
        val big = "| a | b |\n|---|---|\n" + (1..2000).joinToString("\n") { "| $it | x |" }
        val t = blocks(big).filterIsInstance<DocxTable>().single()
        assertEquals(2001, t.rows.size)
    }
}

class MarkdownInlineTest {
    @Test fun boldAndItalic() {
        val r = runs("a **b** c *d* e ***f***")
        assertEquals("a b c d e f", r.joinToString("") { it.text })
        assertTrue(r.first { it.text == "b" }.bold)
        assertTrue(r.first { it.text == "d" }.italic)
        val both = r.first { it.text == "f" }
        assertTrue(both.bold && both.italic)
        assertFalse(r.first { it.text.startsWith("a ") }.bold)
    }

    @Test fun underscoresWorkAtWordEdgesButNotInsideWords() {
        assertTrue(runs("_it_ here").first().italic)
        assertEquals("snake_case_name", plain("snake_case_name"))
        assertFalse(runs("snake_case_name").any { it.italic })
    }

    @Test fun strikethrough() {
        assertTrue(runs("~~gone~~ stays").first().strike)
        assertEquals("gone stays", plain("~~gone~~ stays"))
    }

    @Test fun nestedEmphasis() {
        val r = runs("**bold and *both* bold**")
        assertTrue(r.first { it.text == "both" }.let { it.bold && it.italic })
        assertTrue(r.first { it.text.startsWith("bold and") }.let { it.bold && !it.italic })
    }

    @Test fun unmatchedMarksAreLiteral() {
        assertEquals("2 * 3 = 6", plain("2 * 3 = 6"))
        assertEquals("**open", plain("**open"))
        assertEquals("a * b * c", plain("a * b * c"))
    }

    @Test fun codeSpans() {
        val r = runs("use `a*b*c` here")
        val c = r.first { it.fontName == "Courier New" }
        assertEquals("a*b*c", c.text)
        assertFalse(c.italic)
        assertEquals("a`b", plain("`` a`b ``"))
    }

    @Test fun anUnclosedBacktickIsLiteral() {
        assertEquals("a ` b", plain("a ` b"))
    }

    @Test fun linksBecomeTappable() {
        val r = runs("see [the docs](https://example.com/a?b=1) now")
        val l = r.first { it.linkUrl != null }
        assertEquals("the docs", l.text)
        assertEquals("https://example.com/a?b=1", l.linkUrl)
        assertTrue(l.underline)
        assertEquals(MarkdownInline.LINK_COLOR, l.colorHex)
        assertEquals("see the docs now", r.joinToString("") { it.text })
    }

    @Test fun aLinkTitleIsIgnored() {
        assertEquals("https://x.y", runs("[t](https://x.y \"title\")").first { it.linkUrl != null }.linkUrl)
    }

    @Test fun linkTextCanBeEmphasised() {
        val l = runs("[**bold link**](https://x.y)").first { it.linkUrl != null }
        assertTrue(l.bold)
    }

    @Test fun emphasisAroundALinkKeepsBoth() {
        val l = runs("**[a](https://x.y)**").first { it.linkUrl != null }
        assertTrue(l.bold)
    }

    @Test fun unsafeAddressesAreNotLinks() {
        assertTrue(runs("[x](javascript:alert(1))").all { it.linkUrl == null })
        assertEquals("x", plain("[x](javascript:alert(1))"))
        assertTrue(runs("[x](file:///etc/passwd)").all { it.linkUrl == null })
        assertTrue(runs("[x](intent://x#Intent;end)").all { it.linkUrl == null })
        assertNull(MarkdownInline.safeUrl("data:text/html,<script>"))
        assertEquals("mailto:a@b.co", MarkdownInline.safeUrl("mailto:a@b.co"))
    }

    @Test fun autolinksAndBareAddresses() {
        assertEquals("https://a.b/c", runs("<https://a.b/c>").single().linkUrl)
        assertEquals("https://a.b/c", runs("go to https://a.b/c.").first { it.linkUrl != null }.linkUrl)
        assertEquals("go to https://a.b/c.", plain("go to https://a.b/c."))
        assertEquals("mailto:me@x.org", runs("<me@x.org>").single().linkUrl)
    }

    @Test fun referenceLinks() {
        val r = MarkdownInline.parse("a [ref][id] and [id]", refs = mapOf("id" to "https://r.example"))
        assertEquals(2, r.count { it.linkUrl == "https://r.example" })
    }

    @Test fun anUnknownReferenceIsText() {
        assertEquals("[nope]", plain("[nope]"))
        assertEquals("[a][b]", plain("[a][b]"))
    }

    @Test fun imagesShowTheirDescription() {
        assertEquals("[Image: logo]", plain("![logo](https://x.y/a.png)"))
        assertTrue(runs("![logo](https://x.y/a.png)").all { it.linkUrl == null })
    }

    @Test fun backslashEscapes() {
        assertEquals("*not italic*", plain("\\*not italic\\*"))
        assertEquals("a_b", plain("a\\_b"))
        assertEquals("\\n", plain("\\n"))
    }

    @Test fun htmlEntities() {
        assertEquals("a & b < c", plain("a &amp; b &lt; c"))
        assertEquals("© A", plain("&copy; &#65;"))
        assertEquals("&bogus;", plain("&bogus;"))
        assertEquals("&#99999999999;", plain("&#99999999999;"))
    }

    @Test fun lineBreakTags() {
        assertEquals("a\nb", plain("a<br>b"))
        assertEquals("a\nb", plain("a<br/>b"))
    }

    @Test fun otherHtmlIsShownAsWritten() {
        assertEquals("<b>bold</b>", plain("<b>bold</b>"))
        assertEquals("<img src=x onerror=alert(1)>", plain("<img src=x onerror=alert(1)>"))
    }

    @Test fun theBlockStyleReachesEveryRun() {
        val r = MarkdownInline.parse("plain **b**", InlineBase(colorHex = "5A606C", italic = true))
        assertTrue(r.all { it.colorHex == "5A606C" && it.italic })
    }

    @Test fun hostileInputsDontHang() {
        val cases = listOf("*".repeat(50000), "[".repeat(20000) + "]".repeat(20000), "`".repeat(30001), "_a".repeat(30000), "[a](".repeat(5000), "<".repeat(30000), "&".repeat(30000), "**a *b** c*".repeat(3000))
        for (c in cases) {
            val start = System.nanoTime()
            MarkdownInline.parse(c)
            assertTrue("took too long for ${c.take(10)}", (System.nanoTime() - start) / 1_000_000 < 5000)
        }
    }

    @Test fun nestedBracketsInLinksDontOverflowTheStack() {
        val s = "[".repeat(5000) + "x" + "](https://a.b)".repeat(1)
        MarkdownInline.parse(s)
    }
}

class MarkdownDocumentTest {
    private val sample = """
        # Project Notes

        Some **bold**, some *italic* and a [link](https://example.com).

        ## Tasks
        - [x] write parser
        - [ ] ship it
          - nested point

        1. first
        2. second

        > A quote

        ```
        code here
        ```

        | a | b |
        |---|---|
        | 1 | 2 |

        ---
        The end.
    """.trimIndent()

    @Test fun aRealisticDocumentReadsEndToEnd() {
        val doc = MarkdownParser.parse(sample)
        val text = doc.plainText()
        assertTrue(text.contains("Project Notes"))
        assertTrue(text.contains("write parser"))
        assertTrue(text.contains("code here"))
        assertTrue(text.contains("The end."))
        assertTrue(doc.blocks.any { it is DocxTable })
    }

    @Test fun theDocumentCanBePaginatedAndEveryParagraphKeepsItsLayout() {
        val doc = MarkdownParser.parse(sample.repeat(30))
        val pages = PageLayout.paginate(doc)
        assertTrue(pages.size > 1)
        assertTrue(doc.blocks.filterIsInstance<DocxParagraph>().all { it.layout != null })
    }

    @Test fun aHugeFileIsCappedNotSlow() {
        val start = System.nanoTime()
        val doc = MarkdownParser.parse("line of text\n\n".repeat(150_000))
        assertTrue(doc.blocks.size <= 150_000)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 15_000)
    }

    @Test fun paragraphIdsAreNotSetSoEditingStaysOff() {
        assertTrue(MarkdownParser.parse(sample).blocks.filterIsInstance<DocxParagraph>().all { it.id == -1 })
    }
}
