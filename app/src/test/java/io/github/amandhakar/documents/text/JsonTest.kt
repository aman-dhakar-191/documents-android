package io.github.amandhakar.documents.text

import io.github.amandhakar.documents.document.TextSyntax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JsonTest {
    private fun problem(text: String): TextProblem = try {
        Json.format(text)
        fail("expected a problem in: $text")
        throw AssertionError()
    } catch (e: JsonException) {
        e.problem
    }

    @Test fun formatsNestedValues() {
        val out = Json.format("""{"a":1,"b":[true,null,"x"],"c":{}, "d":[]}""")
        assertEquals(
            """
            {
              "a": 1,
              "b": [
                true,
                null,
                "x"
              ],
              "c": {},
              "d": []
            }
            """.trimIndent(),
            out,
        )
    }

    @Test fun keepsNumbersAndEscapesExactlyAsWritten() {
        val out = Json.format("""[1.50, -0.0e+10, "a\u00e9\n\"q\"", 12345678901234567890]""")
        assertTrue(out.contains("1.50"))
        assertTrue(out.contains("-0.0e+10"))
        assertTrue(out.contains("\"a\\u00e9\\n\\\"q\\\"\""))
        assertTrue(out.contains("12345678901234567890"))
    }

    @Test fun formattingTwiceChangesNothing() {
        val once = Json.format("""{"k":[1,{"x":"y"}]}""")
        assertEquals(once, Json.format(once))
    }

    @Test fun topLevelScalarsAreValid() {
        assertEquals("42", Json.format(" 42 "))
        assertEquals("\"hi\"", Json.format("\"hi\""))
    }

    @Test fun trailingCommaIsReportedWithItsPlace() {
        val p = problem("{\n  \"a\": 1,\n}")
        assertEquals(3, p.line)
        assertEquals(1, p.column)
        assertTrue(p.message, p.message.contains("comma"))
    }

    @Test fun commonMistakes() {
        assertTrue(problem("{'a': 1}").message.contains("double quotes"))
        assertTrue(problem("{\"a\" 1}").message.contains(":"))
        assertTrue(problem("[1 2]").message.contains(", or ]"))
        assertTrue(problem("[01]").message.contains("0"))
        assertTrue(problem("[1.]").message.contains("decimal"))
        assertTrue(problem("\"open").message.contains("closed"))
        assertTrue(problem("\"a\nb\"").message.contains("next line"))
        assertTrue(problem("[\"\\x\"]").message.contains("escape"))
        assertTrue(problem("[\"\\u12\"]").message.contains("hex"))
        assertTrue(problem("{} {}").message.contains("after the end"))
        assertTrue(problem("").message.contains("empty"))
        assertTrue(problem("[tru]").message.contains("value"))
        assertTrue(problem("{\"a\":1").message.contains("}"))
        assertTrue(problem("// comment\n{}").message.contains("value"))
    }

    @Test fun veryDeepNestingIsRefusedNotCrashing() {
        val deep = "[".repeat(100_000) + "]".repeat(100_000)
        assertTrue(problem(deep).message.contains("deeply"))
    }

    @Test fun checksForSaving() {
        assertNull(TextChecks.check("{\"a\": 1}", TextSyntax.Json))
        assertNull(TextChecks.check("  ", TextSyntax.Json)) // an emptied file isn't stopped
        assertNotNull(TextChecks.check("{a:1}", TextSyntax.Json))
        assertNull(TextChecks.check("{a:1}", TextSyntax.Plain))
    }
}
