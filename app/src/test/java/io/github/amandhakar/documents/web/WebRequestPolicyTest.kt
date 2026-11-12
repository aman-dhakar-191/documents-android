package io.github.amandhakar.documents.web

import org.junit.Assert.assertEquals
import org.junit.Test

class WebRequestPolicyTest {
    @Test fun httpsIsBlockedByDefault() = assertEquals(RequestAction.Block, WebRequestPolicy.decide("https", false))
    @Test fun httpsLoadsWhenAllowed() = assertEquals(RequestAction.LoadOnline, WebRequestPolicy.decide("https", true))
    @Test fun httpIsBlockedByDefault() = assertEquals(RequestAction.Block, WebRequestPolicy.decide("http", false))
    @Test fun httpIsBlockedEvenWhenOnlineIsAllowed() = assertEquals(RequestAction.Block, WebRequestPolicy.decide("http", true))
    @Test fun schemeCaseIsIgnored() {
        assertEquals(RequestAction.Block, WebRequestPolicy.decide("HTTPS", false))
        assertEquals(RequestAction.LoadOnline, WebRequestPolicy.decide("HtTpS", true))
        assertEquals(RequestAction.Block, WebRequestPolicy.decide("HTTP", true))
    }
    @Test fun pageOwnDataPassesThrough() {
        assertEquals(RequestAction.PassThrough, WebRequestPolicy.decide("data", false))
        assertEquals(RequestAction.PassThrough, WebRequestPolicy.decide("about", false))
        assertEquals(RequestAction.PassThrough, WebRequestPolicy.decide("blob", true))
    }
    @Test fun otherSchemesAreDeniedWhateverTheSwitchSays() {
        for (scheme in listOf("ftp", "file", "content", "intent", "javascript", "weird")) {
            assertEquals(scheme, RequestAction.Deny, WebRequestPolicy.decide(scheme, true))
            assertEquals(scheme, RequestAction.Deny, WebRequestPolicy.decide(scheme, false))
        }
    }
    @Test fun aRequestWithNoSchemeIsDenied() = assertEquals(RequestAction.Deny, WebRequestPolicy.decide(null, true))
}

class FindTextTest {
    @Test fun blankQueryHasNoLabel() = assertEquals("", FindText.label("  ", 0, 0))
    @Test fun noMatches() = assertEquals("No matches", FindText.label("cat", 0, 0))
    @Test fun firstOfThree() = assertEquals("1 of 3", FindText.label("cat", 0, 3))
    @Test fun lastOfThree() = assertEquals("3 of 3", FindText.label("cat", 2, 3))
    @Test fun anOutOfRangeActiveIndexIsClamped() {
        assertEquals("3 of 3", FindText.label("cat", 9, 3))
        assertEquals("1 of 3", FindText.label("cat", -4, 3))
    }
}
