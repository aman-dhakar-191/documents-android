package io.github.amandhakar.documents.ui

/** Where Back goes from the reading screen. */
enum class BackTarget {
    /** Leave the app, which returns to the app that asked us to open the PDF. */
    ExitApp,

    /** Show the list of PDFs. */
    Library,
}

object BackNavigation {
    /**
     * A PDF another app opened with us goes back to that app. A PDF opened from our own list goes
     * back to the list.
     */
    fun fromViewer(openedFromOutside: Boolean): BackTarget =
        if (openedFromOutside) BackTarget.ExitApp else BackTarget.Library
}
