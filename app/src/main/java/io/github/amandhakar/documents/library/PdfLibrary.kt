package io.github.amandhakar.documents.library

import android.content.Context
import android.provider.MediaStore.Files.FileColumns
import io.github.amandhakar.documents.document.DocumentFormat

/** Lists the PDFs, web pages, Word, PowerPoint, Excel and Markdown files on the phone, and text files saved in Download or Documents, from Android's media index. Needs [StorageAccess.hasAccess]. */
object PdfLibrary {
    /** Blocking: call from a background thread. */
    @Suppress("DEPRECATION") // DATA is only used to show the folder name.
    fun load(context: Context): List<PdfFile> {
        val collection = android.provider.MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            FileColumns._ID,
            FileColumns.DISPLAY_NAME,
            FileColumns.MIME_TYPE,
            FileColumns.SIZE,
            FileColumns.DATE_MODIFIED,
            FileColumns.DATA,
        )
        val mimes = DOCUMENT_MIMES + TEXT_MIMES
        val patterns = (DOCUMENT_ENDINGS + TEXT_ENDINGS).map { "%.$it" }
        val selection = (
            listOf("${FileColumns.MIME_TYPE} IN (${mimes.joinToString { "?" }})") +
                patterns.map { "${FileColumns.DISPLAY_NAME} LIKE ?" }
            ).joinToString(" OR ")
        val selectionArgs = (mimes + patterns).toTypedArray()
        val result = ArrayList<PdfFile>()
        context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(FileColumns.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(FileColumns.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(FileColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(FileColumns.DATE_MODIFIED)
            val dataCol = cursor.getColumnIndexOrThrow(FileColumns.DATA)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                if (size <= 0) continue // empty files can't be documents
                val format = DocumentFormat.fromMimeType(cursor.getString(mimeCol)) ?: DocumentFormat.fromFileName(name) ?: continue
                val path = cursor.getString(dataCol)
                // Apps keep many JSON / XML / log files of their own; only text files the owner saved are listed.
                if (format == DocumentFormat.Text && !PdfFiles.inUserFolder(path)) continue
                result += PdfFile(
                    id = cursor.getLong(idCol),
                    name = name,
                    folder = PdfFiles.parentFolderName(path),
                    sizeBytes = size,
                    modifiedSeconds = cursor.getLong(dateCol),
                    format = format,
                )
            }
        }
        return PdfFiles.sortedNewestFirst(result)
    }

    private val DOCUMENT_MIMES = listOf(
        "application/pdf", "text/html", "application/xhtml+xml", DocumentFormat.DOCX_MIME, DocumentFormat.PPTX_MIME,
        DocumentFormat.XLSX_MIME, "text/markdown", "text/x-markdown",
    )
    private val DOCUMENT_ENDINGS = listOf("pdf", "html", "htm", "xhtml", "docx", "pptx", "md", "markdown", "xlsx")

    /** Text files are listed only from the Download and Documents folders (see [PdfFiles.inUserFolder]). */
    private val TEXT_MIMES = listOf(
        "text/plain", "application/json", "application/xml", "text/xml", "text/csv", "text/comma-separated-values",
        "text/tab-separated-values", "application/yaml", "application/x-yaml", "text/yaml", "text/x-yaml", "application/toml",
    )
    private val TEXT_ENDINGS = listOf("txt", "json", "yaml", "yml", "xml", "csv", "tsv", "toml", "ini", "conf", "properties", "log")
}
