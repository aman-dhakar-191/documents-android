package io.github.amandhakar.documents.edit

import java.io.FileNotFoundException
import java.io.IOException

object SaveNames {
    /** "report.pdf" gives "report-signed.pdf". Missing, blank or odd names still give a usable file name. */
    fun signedName(original: String?): String {
        val base = original.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .removeSuffix(".pdf").removeSuffix(".PDF")
            .filter { !it.isISOControl() }
            .trim()
        return (if (base.isEmpty()) "document" else base) + "-signed.pdf"
    }

    /** What to tell the person when saving failed. [replacing] is true for "replace the original". */
    fun errorMessage(error: Throwable, replacing: Boolean): String = when (error) {
        is OutOfMemoryError -> "This PDF is too big to edit on this phone."
        is SecurityException, is FileNotFoundException ->
            if (replacing) "This file can't be changed from here. Save as a new copy instead."
            else "Couldn't save there. Pick another place."
        is IOException ->
            if (error.message?.contains("password", ignoreCase = true) == true) {
                "Password-protected PDFs can't be edited yet."
            } else {
                "Couldn't save the signed PDF. Nothing was changed." + detail(error)
            }
        else -> "Couldn't change this PDF. Nothing was changed." + detail(error)
    }

    /** The kind of error and the start of its message, so a failure can be told apart (" (IllegalArgumentException: ...)"). */
    fun detail(error: Throwable): String {
        val kind = error.javaClass.simpleName.ifEmpty { "Error" }
        val message = error.message.orEmpty().replace(Regex("\\s+"), " ").trim().take(90)
        return if (message.isEmpty()) " ($kind)" else " ($kind: $message)"
    }
}
