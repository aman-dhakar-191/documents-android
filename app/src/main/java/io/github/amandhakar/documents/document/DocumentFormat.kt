package io.github.amandhakar.documents.document

/**
 * File formats the app can open: PDF (pages shown as pictures, see [Document]), web pages and Word .docx files (each
 * shown by its own screen, not as pages). Add an entry here when another format is added.
 */
enum class DocumentFormat {
    Pdf,
    Html,
    Docx;

    /** The MIME type to use when handing a file of this format to another app. */
    val mimeType: String
        get() = when (this) {
            Pdf -> "application/pdf"
            Html -> "text/html"
            Docx -> DOCX_MIME
        }

    companion object {
        const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

        /** Returns the format for a MIME type (parameters like "; charset=x" are ignored), or null if unsupported. */
        fun fromMimeType(mimeType: String?): DocumentFormat? {
            val base = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            return when (base) {
                "application/pdf" -> Pdf
                "text/html", "application/xhtml+xml" -> Html
                DOCX_MIME -> Docx
                else -> null
            }
        }

        /** Returns the format for a file name by its extension, or null if unsupported. */
        fun fromFileName(name: String?): DocumentFormat? {
            val ext = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
            return when (ext) {
                "pdf" -> Pdf
                "html", "htm", "xhtml" -> Html
                "docx" -> Docx
                else -> null
            }
        }
    }
}
