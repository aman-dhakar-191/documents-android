package io.github.amandhakar.documents.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class LinkPolicyTest {
    private fun decide(url: String) = LinkPolicy.decide(url)

    @Test fun jumpsInsideThePageAreAllowed() {
        assertEquals(LinkAction.Allow, decide("#section-2"))
        assertEquals(LinkAction.Allow, decide("about:blank#top"))
    }

    @Test fun webLinksAskFirst() {
        assertEquals(LinkAction.Ask, decide("https://example.com/a"))
        assertEquals(LinkAction.Ask, decide("HTTP://example.com"))
        assertEquals(LinkAction.Ask, decide("  https://example.com  "))
    }

    @Test fun mailAndPhoneAskFirst() {
        assertEquals(LinkAction.Ask, decide("mailto:a@b.com"))
        assertEquals(LinkAction.Ask, decide("tel:+911234567890"))
    }

    @Test fun localAndDangerousSchemesAreBlocked() {
        assertEquals(LinkAction.Block, decide("file:///sdcard/secret.txt"))
        assertEquals(LinkAction.Block, decide("content://media/external/file/1"))
        assertEquals(LinkAction.Block, decide("javascript:alert(1)"))
        assertEquals(LinkAction.Block, decide("intent://scan/#Intent;scheme=zxing;end"))
        assertEquals(LinkAction.Block, decide("data:text/html,<h1>x</h1>"))
        assertEquals(LinkAction.Block, decide("market://details?id=x"))
    }

    @Test fun brokenLinksAreBlocked() {
        assertEquals(LinkAction.Block, decide(""))
        assertEquals(LinkAction.Block, decide("page2.html"))
        assertEquals(LinkAction.Block, decide("https:"))
        assertEquals(LinkAction.Block, decide("https://"))
        assertEquals(LinkAction.Block, decide("mailto:"))
        assertEquals(LinkAction.Block, decide("1http://x.com"))
    }
}

class HtmlTextTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test fun plainAsciiIsUtf8() = assertEquals("<p>Hi</p>", HtmlText.decode("<p>Hi</p>".toByteArray()))

    @Test fun utf8BomIsDroppedAndUsed() =
        assertEquals("é", HtmlText.decode(bytes(0xEF, 0xBB, 0xBF, 0xC3, 0xA9)))

    @Test fun utf16BomsAreUsed() {
        assertEquals("A", HtmlText.decode(bytes(0xFE, 0xFF, 0x00, 0x41)))
        assertEquals("A", HtmlText.decode(bytes(0xFF, 0xFE, 0x41, 0x00)))
    }

    @Test fun aMetaCharsetIsHonoured() {
        val html = "<html><head><meta charset=\"iso-8859-1\"></head>caf\u00E9".toByteArray(Charsets.ISO_8859_1)
        assertEquals("<html><head><meta charset=\"iso-8859-1\"></head>caf\u00E9", HtmlText.decode(html))
    }

    @Test fun anOldStyleContentTypeMetaIsHonoured() {
        val html = "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=windows-1252\">\u20AC".toByteArray(charset("windows-1252"))
        assertEquals(true, HtmlText.decode(html).endsWith("\u20AC"))
    }

    @Test fun anUnknownCharsetFallsBackToUtf8() =
        assertEquals("<meta charset=\"nonsense-9\">é", HtmlText.decode("<meta charset=\"nonsense-9\">é".toByteArray()))

    @Test fun emptyInputIsEmptyText() = assertEquals("", HtmlText.decode(ByteArray(0)))

    @Test fun readingStopsAtTheLimit() {
        assertEquals(5, HtmlText.readLimited(ByteArrayInputStream(ByteArray(5)), limit = 5)!!.size)
        assertNull(HtmlText.readLimited(ByteArrayInputStream(ByteArray(6)), limit = 5))
    }

    @Test fun readingAnEmptyStreamGivesNoBytes() = assertEquals(0, HtmlText.readLimited(ByteArrayInputStream(ByteArray(0)))!!.size)
}
