package io.github.amandhakar.documents.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BackNavigationTest {
    @Test fun aPdfFromAnotherAppGoesBackToThatApp() =
        assertEquals(BackTarget.ExitApp, BackNavigation.fromViewer(openedFromOutside = true))

    @Test fun aPdfFromOurOwnListGoesBackToTheList() =
        assertEquals(BackTarget.Library, BackNavigation.fromViewer(openedFromOutside = false))
}
