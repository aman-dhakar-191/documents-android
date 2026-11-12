package io.github.amandhakar.documents.web

/** The words about online parts of a saved page that were not loaded. Free of Android calls so it can be unit tested. */
object WebNotice {
    fun title(blocked: Int): String = when {
        blocked <= 0 -> ""
        blocked == 1 -> "1 online part not shown"
        else -> "$blocked online parts not shown"
    }

    const val EXPLANATION =
        "Documents has no internet access, so pictures and styles stored online are skipped and the page may look incomplete. " +
            "Everything saved in the file is shown."

    /** The chip in the top bar: whether the page's scripts are running. */
    fun scriptsChip(allowScripts: Boolean): String = if (allowScripts) "Scripts on" else "Scripts off"
}
