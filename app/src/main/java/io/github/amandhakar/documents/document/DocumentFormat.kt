package io.github.amandhakar.documents.document

/**
 * File formats the app can open. Only PDF is implemented; add an entry here (plus a [Document]
 * implementation and a branch in [DocumentOpener]) when another format is added.
 */
enum class DocumentFormat {
    Pdf;

    companion object {
        /** Returns the format for a MIME type (parameters like "; charset=x" are ignored), or null if unsupported. */
        fun fromMimeType(mimeType: String?): DocumentFormat? {
            val base = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            return when (base) {
                "application/pdf" -> Pdf
                else -> null
            }
        }

        /** Returns the format for a file name by its extension, or null if unsupported. */
        fun fromFileName(name: String?): DocumentFormat? {
            val ext = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
            return when (ext) {
                "pdf" -> Pdf
                else -> null
            }
        }
    }
}
