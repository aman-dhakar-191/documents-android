package io.github.amandhakar.documents.document

/**
 * File formats the app can open: PDF (pages shown as pictures, see [Document]) and web pages (shown by their own
 * screen, not as pages). Add an entry here when another format is added.
 */
enum class DocumentFormat {
    Pdf,
    Html;

    companion object {
        /** Returns the format for a MIME type (parameters like "; charset=x" are ignored), or null if unsupported. */
        fun fromMimeType(mimeType: String?): DocumentFormat? {
            val base = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            return when (base) {
                "application/pdf" -> Pdf
                "text/html", "application/xhtml+xml" -> Html
                else -> null
            }
        }

        /** Returns the format for a file name by its extension, or null if unsupported. */
        fun fromFileName(name: String?): DocumentFormat? {
            val ext = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
            return when (ext) {
                "pdf" -> Pdf
                "html", "htm", "xhtml" -> Html
                else -> null
            }
        }
    }
}
