package io.github.amandhakar.documents.library

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** A PDF found on the phone, as listed by Android's media index. */
data class PdfFile(
    val id: Long,
    val name: String,
    /** Name of the folder it is in, e.g. "Download". Empty if unknown. */
    val folder: String,
    val sizeBytes: Long,
    val modifiedSeconds: Long,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
}

/** Text and ordering for the PDF cards. Kept free of Android calls so it can be unit tested. */
object PdfFiles {
    /** "Report.PDF" -> "Report". Names without a .pdf ending are left alone. */
    fun title(name: String): String {
        val trimmed = if (name.endsWith(".pdf", ignoreCase = true)) name.dropLast(4) else name
        return trimmed.ifBlank { name }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "${bytes.coerceAtLeast(0)} B"
        val kb = bytes / 1024.0
        val kbRounded = kb.roundToInt()
        if (kbRounded < 1024) return "$kbRounded KB"
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb)
        return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0)
    }

    /** "10 Nov 2024", or "" when the date is unknown (0 or negative). */
    fun formatDate(
        seconds: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        if (seconds <= 0) return ""
        return DateTimeFormatter.ofPattern("d MMM yyyy", locale).withZone(zone).format(Instant.ofEpochSecond(seconds))
    }

    /** "/storage/emulated/0/Download/a.pdf" -> "Download". Empty when there is no folder. */
    fun parentFolderName(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return path.substringBeforeLast('/', "").substringAfterLast('/')
    }

    /** Newest first; ties by name (ignoring case), then id, so the order is stable. */
    fun sortedNewestFirst(files: List<PdfFile>): List<PdfFile> = files.sortedWith(
        compareByDescending<PdfFile> { it.modifiedSeconds }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.id },
    )
}
