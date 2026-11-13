package io.github.amandhakar.documents.officeedit

import java.io.FileNotFoundException
import java.io.IOException

/** Words for the person when editing a Word or PowerPoint file. Free of Android calls so it can be unit tested. */
object OfficeEditMessages {
    /** "1 change" / "3 changes", for the Save button and sheet. */
    fun changes(count: Int): String = if (count == 1) "1 change" else "$count changes"

    /** What to tell the person when saving failed. [replacing] is true for "replace the original". */
    fun forError(error: Throwable, replacing: Boolean): String = when (error) {
        is OutOfMemoryError -> "This file is too big to edit on this phone."
        is OfficeEditException -> error.message ?: "Couldn't apply the changes. Nothing was changed."
        is SecurityException, is FileNotFoundException ->
            if (replacing) "This file can't be changed from here. Save as a new copy instead." else "Couldn't save there. Pick another place."
        is IOException -> "Couldn't save the changes. Nothing was changed."
        else -> "Couldn't save the changes. Nothing was changed."
    }

    /** The hint shown while editing. */
    const val HINT = "Tap a paragraph to change it"

    /** Why a paragraph can't be changed. */
    const val HAS_FIELD = "This paragraph holds a field (like a page number or a contents list), which can't be edited here."
}
