package io.github.amandhakar.documents.text

import io.github.amandhakar.documents.document.TextSyntax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TextChecksTest {
    @Test fun wellFormedXmlPasses() {
        assertNull(TextChecks.check("<?xml version=\"1.0\"?>\n<a x=\"1\"><b/>text</a>", TextSyntax.Xml))
    }

    @Test fun brokenXmlSaysWhere() {
        val p = TextChecks.check("<a>\n  <b>\n</a>", TextSyntax.Xml)
        assertNotNull(p)
        assertEquals(3, p!!.line)
    }

    @Test fun xmlWithAnExternalDtdIsCheckedWithoutFetchingIt() {
        val plist = "<?xml version=\"1.0\"?>\n<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n<plist><dict/></plist>"
        assertNull(TextChecks.check(plist, TextSyntax.Xml))
    }

    @Test fun xmlThatDeclaresEntitiesIsNotParsed() {
        val bomb = "<?xml version=\"1.0\"?><!DOCTYPE l [<!ENTITY a \"aaaa\"><!ENTITY b \"&a;&a;&a;&a;\">]><l>&b;</l>"
        assertNull(TextChecks.check(bomb, TextSyntax.Xml))
    }

    @Test fun yamlTabsAreCaught() {
        val p = TextChecks.check("a:\n\tb: 1\n", TextSyntax.Yaml)
        assertEquals(2, p!!.line)
        assertEquals(1, p.column)
        assertNull(TextChecks.check("a:\n  b: \"x\tz\"\n", TextSyntax.Yaml)) // a tab inside a value is fine
    }

    @Test fun otherKindsAreNotChecked() {
        assertNull(TextChecks.check("[broken", TextSyntax.Toml))
        assertNull(TextChecks.check("\"a,b", TextSyntax.Csv))
    }

    @Test fun problemReadsAsASentence() {
        assertEquals("Line 2, column 5: Expected , or }.", TextProblem(2, 5, "Expected , or }.").toString())
    }

    @Test fun onlyCheckedKindsSayTheyPassed() {
        assertEquals("Valid JSON", TextChecks.okLabel(TextSyntax.Json))
        assertEquals(null, TextChecks.okLabel(TextSyntax.Csv))
        assertEquals(null, TextChecks.okLabel(TextSyntax.Plain))
    }
}
