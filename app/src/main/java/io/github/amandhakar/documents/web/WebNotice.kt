package io.github.amandhakar.documents.web

/** The words about online parts of a saved page. Free of Android calls so it can be unit tested. */
object WebNotice {
    fun title(blocked: Int): String = when {
        blocked <= 0 -> ""
        blocked == 1 -> "1 online part not shown"
        else -> "$blocked online parts not shown"
    }

    /** Why parts were skipped, and what to do about it. */
    fun explanation(online: Boolean): String = if (online) {
        "Only secure (https) addresses are loaded. The rest are skipped, so the page may look incomplete."
    } else {
        "Saved pages can't reach the internet unless you allow it for this page, so pictures and styles stored online " +
            "are skipped and the page may look incomplete."
    }

    /** The chip in the top bar: whether scripts run and whether online parts load. */
    fun modeChip(allowScripts: Boolean, allowOnline: Boolean): String =
        scriptsChip(allowScripts) + " \u00B7 " + if (allowOnline) "Online" else "Offline"

    fun scriptsChip(allowScripts: Boolean): String = if (allowScripts) "Scripts on" else "Scripts off"
}
