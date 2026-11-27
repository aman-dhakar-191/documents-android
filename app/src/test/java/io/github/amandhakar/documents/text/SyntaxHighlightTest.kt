package io.github.amandhakar.documents.text

import io.github.amandhakar.documents.document.TextSyntax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlightTest {
    private fun kinds(line: String, syntax: TextSyntax, inComment: Boolean = false) =
        SyntaxHighlight.tokens(line, syntax, inComment).map { line.substring(it.start, it.end) to it.kind }

    @Test fun jsonKeysStringsNumbersAndWords() {
        val k = kinds("  \"name\": \"Aman\", \"age\": 30, \"ok\": true, \"x\": null", TextSyntax.Json)
        assertTrue(k.contains("\"name\"" to TokenKind.Key))
        assertTrue(k.contains("\"Aman\"" to TokenKind.Str))
        assertTrue(k.contains("30" to TokenKind.Num))
        assertTrue(k.contains("true" to TokenKind.Literal))
        assertTrue(k.contains("null" to TokenKind.Literal))
    }

    @Test fun jsonEscapedQuoteStaysInsideTheString() {
        val k = kinds("\"a\\\"b\": 1", TextSyntax.Json)
        assertEquals("\"a\\\"b\"" to TokenKind.Key, k.first())
    }

    @Test fun yamlKeysValuesAndComments() {
        val k = kinds("  port: 8080 # the port", TextSyntax.Yaml)
        assertTrue(k.contains("port" to TokenKind.Key))
        assertTrue(k.contains("8080" to TokenKind.Num))
        assertTrue(k.contains("# the port" to TokenKind.Comment))
        assertTrue(kinds("- name: \"web\"", TextSyntax.Yaml).contains("\"web\"" to TokenKind.Str))
        assertTrue(kinds("enabled: yes", TextSyntax.Yaml).contains("yes" to TokenKind.Literal))
        assertEquals(listOf("# all" to TokenKind.Comment), kinds("# all", TextSyntax.Yaml))
        // A URL's colon isn't a key.
        assertTrue(kinds("url: http://x.y/z#a", TextSyntax.Yaml).none { it.second == TokenKind.Comment })
    }

    @Test fun xmlTagsAttributesAndComments() {
        val k = kinds("<item id=\"7\">Text</item>", TextSyntax.Xml)
        assertTrue(k.contains("<item" to TokenKind.Tag))
        assertTrue(k.contains("id" to TokenKind.Attr))
        assertTrue(k.contains("\"7\"" to TokenKind.Str))
        assertTrue(k.contains("</item" to TokenKind.Tag))
        assertTrue(kinds("<br/>", TextSyntax.Xml).contains("/>" to TokenKind.Tag))
    }

    @Test fun xmlCommentsCarryOverLines() {
        val lines = listOf("<a>", "<!-- start", "still comment", "end -->  <b/>", "<c/>")
        val states = SyntaxHighlight.lineStates(lines, TextSyntax.Xml)
        assertEquals(listOf(false, false, true, true, false), states.toList())
        assertEquals(listOf("still comment" to TokenKind.Comment), kinds(lines[2], TextSyntax.Xml, states[2]))
        val k = kinds(lines[3], TextSyntax.Xml, states[3])
        assertEquals("end -->" to TokenKind.Comment, k.first())
        assertTrue(k.contains("<b" to TokenKind.Tag))
    }

    @Test fun iniSectionsKeysAndComments() {
        assertEquals(listOf("[server]" to TokenKind.Section), kinds("[server]", TextSyntax.Ini))
        assertEquals(listOf("; note" to TokenKind.Comment), kinds("; note", TextSyntax.Ini))
        val k = kinds("timeout = 30", TextSyntax.Toml)
        assertTrue(k.contains("timeout" to TokenKind.Key))
        assertTrue(k.contains("30" to TokenKind.Num))
        assertTrue(kinds("name = \"x\"", TextSyntax.Toml).contains("\"x\"" to TokenKind.Str))
    }

    @Test fun logLevels() {
        assertEquals(listOf("ERROR" to TokenKind.Error), kinds("2026-11-27 10:00 ERROR something broke", TextSyntax.Log))
        assertEquals(listOf("WARN" to TokenKind.Warn), kinds("[WARN] low disk", TextSyntax.Log))
        assertEquals(listOf("E" to TokenKind.Error), kinds("11-27 09:30:00.123  1234  5678 E Tag: boom", TextSyntax.Log))
        assertTrue(kinds("everything fine", TextSyntax.Log).isEmpty())
    }

    @Test fun hugeLinesAndPlainTextAreLeftPlain() {
        assertTrue(SyntaxHighlight.tokens("\"a\":" + "1,".repeat(5000), TextSyntax.Json).isEmpty())
        assertTrue(SyntaxHighlight.tokens("\"a\": 1", TextSyntax.Plain).isEmpty())
    }

    @Test fun tokensStayInsideTheLineAndInOrder() {
        val samples = listOf(
            "\"open string" to TextSyntax.Json, "<a b=\"c" to TextSyntax.Xml, "<!-- x" to TextSyntax.Xml,
            "key: 'unclosed" to TextSyntax.Yaml, "- - - x" to TextSyntax.Yaml, "=" to TextSyntax.Ini, "<" to TextSyntax.Xml,
        )
        for ((line, syntax) in samples) {
            var last = 0
            for (t in SyntaxHighlight.tokens(line, syntax)) {
                assertTrue(line, t.start >= last && t.end <= line.length && t.end > t.start)
                last = t.end
            }
        }
    }
}
