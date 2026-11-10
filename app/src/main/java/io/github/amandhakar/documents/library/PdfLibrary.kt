package io.github.amandhakar.documents.library

import android.content.Context
import android.provider.MediaStore.Files.FileColumns

/** Lists the PDFs on the phone from Android's media index. Needs [StorageAccess.hasAccess]. */
object PdfLibrary {
    /** Blocking: call from a background thread. */
    @Suppress("DEPRECATION") // DATA is only used to show the folder name.
    fun load(context: Context): List<PdfFile> {
        val collection = android.provider.MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            FileColumns._ID,
            FileColumns.DISPLAY_NAME,
            FileColumns.SIZE,
            FileColumns.DATE_MODIFIED,
            FileColumns.DATA,
        )
        val selection = "${FileColumns.MIME_TYPE} = ? OR ${FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("application/pdf", "%.pdf")

        val result = ArrayList<PdfFile>()
        context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(FileColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(FileColumns.DATE_MODIFIED)
            val dataCol = cursor.getColumnIndexOrThrow(FileColumns.DATA)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val size = cursor.getLong(sizeCol)
                if (size <= 0) continue // empty files can't be PDFs
                result += PdfFile(
                    id = cursor.getLong(idCol),
                    name = name,
                    folder = PdfFiles.parentFolderName(cursor.getString(dataCol)),
                    sizeBytes = size,
                    modifiedSeconds = cursor.getLong(dateCol),
                )
            }
        }
        return PdfFiles.sortedNewestFirst(result)
    }
}
