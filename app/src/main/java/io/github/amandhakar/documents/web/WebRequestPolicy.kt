package io.github.amandhakar.documents.web

/** What to do with one request a web page makes. Free of Android calls so it can be unit tested. */
enum class RequestAction {
    /** Let the page's own data through (data:, about:, blob: and similar; nothing leaves the phone). */
    PassThrough,

    /** An online address that is allowed to load. */
    LoadOnline,

    /** Refuse it, and count it so the person is told. */
    Block,

    /** Refuse it without counting: not a web address, and not part of the page itself. */
    Deny,
}

object WebRequestPolicy {
    /**
     * [scheme] is the request's address scheme. Online addresses are blocked unless [allowOnline], and
     * even then only secure ones (https) load: a plain http address is always refused.
     */
    fun decide(scheme: String?, allowOnline: Boolean): RequestAction = when (scheme?.lowercase()) {
        "https" -> if (allowOnline) RequestAction.LoadOnline else RequestAction.Block
        "http" -> RequestAction.Block
        // data:, about: and blob: are the page's own content. Anything else (ftp:, file:, content:, a made-up
        // scheme) is refused outright, so the online switch can never open a door other than https.
        "data", "about", "blob" -> RequestAction.PassThrough
        else -> RequestAction.Deny
    }
}

/** The words for the find-in-page bar. */
object FindText {
    /** "2 of 5", or "No matches" when a search found nothing, or "" before anything is typed. */
    fun label(query: String, active: Int, total: Int): String = when {
        query.isBlank() -> ""
        total <= 0 -> "No matches"
        else -> "${active.coerceIn(0, total - 1) + 1} of $total"
    }
}
