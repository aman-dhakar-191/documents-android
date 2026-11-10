package io.github.amandhakar.documents.defaultapp

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultPdfAppsTest {
    private val own = "io.github.amandhakar.documents"

    private fun kind(pkg: String?, activity: String?) = DefaultPdfApps.classify(pkg, activity, own)

    @Test fun ourOwnActivityMeansWeAreTheDefault() =
        assertEquals(DefaultKind.Ours, kind(own, "$own.MainActivity"))

    @Test fun anotherPdfAppMeansItIsTheDefault() {
        assertEquals(DefaultKind.Other, kind("com.google.android.apps.docs", "com.google.android.apps.docs.Viewer"))
        assertEquals(DefaultKind.Other, kind("com.example.pdfviewer", "com.example.pdfviewer.Main"))
    }

    @Test fun theSystemOpenWithListMeansNoDefaultIsSet() {
        assertEquals(DefaultKind.NoChoice, kind("android", "com.android.internal.app.ResolverActivity"))
        assertEquals(DefaultKind.NoChoice, kind("android", null))
        assertEquals(DefaultKind.NoChoice, kind("com.android.intentresolver", "com.android.intentresolver.ChooserActivity"))
    }

    @Test fun aResolverOrChooserActivityFromAnyPackageMeansNoDefault() {
        assertEquals(DefaultKind.NoChoice, kind("com.vendor.system", "com.vendor.system.MyResolverActivity"))
        assertEquals(DefaultKind.NoChoice, kind("com.vendor.system", "com.vendor.system.ChooserActivity"))
    }

    @Test fun nothingResolvedMeansNoDefault() = assertEquals(DefaultKind.NoChoice, kind(null, null))

    @Test fun anotherAppWithNoActivityNameIsStillAnotherApp() =
        assertEquals(DefaultKind.Other, kind("com.example.pdfviewer", null))

    @Test fun ourPackageWinsEvenWithAResolverLookingName() =
        assertEquals(DefaultKind.Ours, kind(own, "$own.ResolverActivity"))
}
