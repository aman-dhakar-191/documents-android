package io.github.amandhakar.documents.document

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

class UnsupportedFormatException(val mimeType: String?) : IOException("Unsupported format: $mimeType")

/** Opens a content/file [Uri] as a [Document], choosing the implementation by file format. */
object DocumentOpener {
    /** Blocking: call from a background thread. */
    fun open(context: Context, uri: Uri): Document {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
        val format = DocumentFormat.fromMimeType(mimeType)
            ?: DocumentFormat.fromFileName(displayName(context, uri) ?: uri.lastPathSegment)
            ?: throw UnsupportedFormatException(mimeType)
        return when (format) {
            DocumentFormat.Pdf -> openPdf(context, uri)
        }
    }

    private fun openPdf(context: Context, uri: Uri): Document {
        val direct = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Could not open $uri")
        try {
            return PdfDocument(direct)
        } catch (e: IOException) {
            // PdfRenderer needs a seekable file. Some providers hand out pipes, so retry from a
            // private copy. (A genuinely corrupt file fails again below, with the same error.)
        }
        val copy = File.createTempFile("document", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Could not read $uri")
            val fd = ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
            return PdfDocument(fd, onClose = { copy.delete() })
        } catch (e: Throwable) {
            copy.delete()
            throw e
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}
