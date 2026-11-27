package io.github.amandhakar.documents.document

/**
 * What kind of text a [DocumentFormat.Text] file holds, which decides its colours, its checks before saving and whether it
 * can be shown as a table. Free of Android calls so it can be unit tested.
 */
enum class TextSyntax(val label: String, val mimeType: String) {
    Plain("Text", "text/plain"),
    Json("JSON", "application/json"),
    Yaml("YAML", "application/yaml"),
    Xml("XML", "text/xml"),
    Csv("CSV", "text/csv"),
    Tsv("TSV", "text/tab-separated-values"),
    Toml("TOML", "application/toml"),
    Ini("Settings", "text/plain"),
    Log("Log", "text/plain");

    /** Shown as a grid of cells by default (a "Text" button shows it as written). */
    val isTable: Boolean get() = this == Csv || this == Tsv

    /** The character between the cells of a [isTable] file. */
    val delimiter: Char get() = if (this == Tsv) '\t' else ','

    companion object {
        private val BY_EXTENSION = mapOf(
            "txt" to Plain, "text" to Plain,
            "json" to Json, "geojson" to Json, "webmanifest" to Json,
            "yaml" to Yaml, "yml" to Yaml,
            "xml" to Xml, "xsd" to Xml, "xsl" to Xml, "xslt" to Xml, "rss" to Xml, "atom" to Xml, "gpx" to Xml, "kml" to Xml,
            "csv" to Csv, "tsv" to Tsv, "tab" to Tsv,
            "toml" to Toml,
            "ini" to Ini, "cfg" to Ini, "conf" to Ini, "properties" to Ini,
            "log" to Log,
        )

        /** The kind of text for a file name, by its ending; null when the ending isn't a text kind the app knows. */
        fun fromFileName(name: String?): TextSyntax? {
            val ext = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase().orEmpty()
            return BY_EXTENSION[ext]
        }

        /**
         * The kind of text for a MIME type, or null. Plain `text/plain` gives null on purpose: phones call many files that,
         * so the file name decides (a .md or .json named text/plain is still Markdown or JSON).
         */
        fun fromMimeType(mimeType: String?): TextSyntax? = when (mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()) {
            "application/json", "text/json", "application/geo+json", "application/manifest+json" -> Json
            "application/yaml", "application/x-yaml", "text/yaml", "text/x-yaml" -> Yaml
            "application/xml", "text/xml" -> Xml
            "text/csv", "text/comma-separated-values", "application/csv" -> Csv
            "text/tab-separated-values" -> Tsv
            "application/toml", "text/x-toml" -> Toml
            else -> null
        }

        /** The kind of text for a file: its name first (more specific), then its MIME type, else plain text. */
        fun of(name: String?, mimeType: String?): TextSyntax = fromFileName(name) ?: fromMimeType(mimeType) ?: Plain
    }
}
