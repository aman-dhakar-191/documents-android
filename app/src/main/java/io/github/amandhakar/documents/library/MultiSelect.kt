package io.github.amandhakar.documents.library

/** Which PDFs are ticked in the library list. Kept free of Android calls so it can be unit tested. */
object MultiSelect {
    /** Adds [id] if it isn't selected, removes it if it is. */
    fun toggle(selected: Set<Long>, id: Long): Set<Long> = if (id in selected) selected - id else selected + id

    /** Drops anything that is no longer in the list (a file was deleted or moved meanwhile). */
    fun prune(selected: Set<Long>, available: Collection<Long>): Set<Long> {
        val still = available.toSet()
        return selected.filterTo(LinkedHashSet()) { it in still }
    }

    fun all(files: List<PdfFile>): Set<Long> = files.mapTo(LinkedHashSet()) { it.id }

    /** The selected files, in the order they appear in the list. */
    fun chosen(files: List<PdfFile>, selected: Set<Long>): List<PdfFile> = files.filter { it.id in selected }

    fun title(count: Int): String = "$count selected"
}
