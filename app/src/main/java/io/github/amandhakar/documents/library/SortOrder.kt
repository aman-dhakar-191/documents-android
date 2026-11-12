package io.github.amandhakar.documents.library

/** How the library list is ordered. Free of Android calls so it can be unit tested. */
enum class SortOrder(val label: String) {
    Newest("Newest first"),
    Oldest("Oldest first"),
    NameAZ("Name A to Z"),
    NameZA("Name Z to A"),
    Largest("Largest first"),
    Smallest("Smallest first"),
}

object Sorting {
    /** Files in the order asked for; ties are broken by newest, then name, then id so the order never jumps around. */
    fun sort(files: List<PdfFile>, order: SortOrder): List<PdfFile> {
        val tie = compareByDescending<PdfFile> { it.modifiedSeconds }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }
        val primary: Comparator<PdfFile> = when (order) {
            SortOrder.Newest -> compareByDescending { it.modifiedSeconds }
            SortOrder.Oldest -> compareBy { it.modifiedSeconds }
            SortOrder.NameAZ -> compareBy(String.CASE_INSENSITIVE_ORDER) { PdfFiles.title(it.name) }
            SortOrder.NameZA -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { PdfFiles.title(it.name) }
            SortOrder.Largest -> compareByDescending { it.sizeBytes }
            SortOrder.Smallest -> compareBy { it.sizeBytes }
        }
        return files.sortedWith(primary.then(tie))
    }
}
