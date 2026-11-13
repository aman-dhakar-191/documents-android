package io.github.amandhakar.documents.library

import android.content.Context
import android.provider.MediaStore.Files.FileColumns
import io.github.amandhakar.documents.document.DocumentFormat

/** Lists the PDFs, web pages, Word files and presentations on the phone from Android's media index. Needs [StorageAccess.hasAccess]. */
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
        val selection = "${FileColumns.MIME_TYPE} IN (?, ?, ?, ?, ?, ?, ?) OR ${FileColumns.DISPLAY_NAME} LIKE ? OR " +
            "${FileColumns.DISPLAY_NAME} LIKE ? OR ${FileColumns.DISPLAY_NAME} LIKE ? OR ${FileColumns.DISPLAY_NAME} LIKE ? OR " +
            "${FileColumns.DISPLAY_NAME} LIKE ? OR ${FileColumns.DISPLAY_NAME} LIKE ? OR ${FileColumns.DISPLAY_NAME} LIKE ? OR " +
            "${FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf(
            "application/pdf", "text/html", "application/xhtml+xml", DocumentFormat.DOCX_MIME, DocumentFormat.PPTX_MIME,
            "text/markdown", "text/x-markdown",
            "%.pdf", "%.html", "%.htm", "%.xhtml", "%.docx", "%.pptx", "%.md", "%.markdown",
        )

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
                result += PdfFile(
                    id = cursor.getLong(idCol),
                    name = name,
                    folder = PdfFiles.parentFolderName(cursor.getString(dataCol)),
                    sizeBytes = size,
                    modifiedSeconds = cursor.getLong(dateCol),
                    format = format,
                )
            }
        }
        return PdfFiles.sortedNewestFirst(result)
    }
}
