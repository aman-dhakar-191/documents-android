package io.github.amandhakar.documents.document

/**
 * File formats the app can open: PDF (pages shown as pictures, see [Document]), web pages, Word .docx files, PowerPoint .pptx
 * files, Excel .xlsx files, Markdown and text files (JSON, YAML, XML, CSV and the like are [Text]; [TextSyntax] tells them
 * apart). Each but PDF is shown by its own screen, not as pages. Add an entry here when another format is added.
 */
enum class DocumentFormat {
    Pdf,
    Html,
    Docx,
    Pptx,
    Markdown,
    Text,
    Xlsx;

    /** The MIME type to use when handing a file of this format to another app. */
    val mimeType: String
        get() = when (this) {
            Pdf -> "application/pdf"
            Html -> "text/html"
            Docx -> DOCX_MIME
            Pptx -> PPTX_MIME
            Markdown -> "text/markdown"
            Text -> "text/plain"
            Xlsx -> XLSX_MIME
        }

    companion object {
        const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        /** Returns the format for a MIME type (parameters like "; charset=x" are ignored), or null if unsupported. */
        fun fromMimeType(mimeType: String?): DocumentFormat? {
            val base = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            return when (base) {
                "application/pdf" -> Pdf
                "text/html", "application/xhtml+xml" -> Html
                DOCX_MIME -> Docx
                PPTX_MIME -> Pptx
                "text/markdown", "text/x-markdown", "text/x-web-markdown" -> Markdown
                XLSX_MIME -> Xlsx
                else -> if (TextSyntax.fromMimeType(base) != null) Text else null
            }
        }

        /** Returns the format for a file name by its extension, or null if unsupported. */
        fun fromFileName(name: String?): DocumentFormat? {
            val ext = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
            return when (ext) {
                "pdf" -> Pdf
                "html", "htm", "xhtml" -> Html
                "docx" -> Docx
                "pptx" -> Pptx
                "md", "markdown", "mdown", "mkd", "mkdn" -> Markdown
                "txt", "text" -> Text
                "xlsx" -> Xlsx
                else -> if (TextSyntax.fromFileName(name) != null) Text else null
            }
        }
    }
}
