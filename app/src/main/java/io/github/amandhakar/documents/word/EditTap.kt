package io.github.amandhakar.documents.word

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Set while a Word document is being edited: called with the paragraph the person tapped. Null when just reading. */
val LocalWordEdit = staticCompositionLocalOf<((DocxParagraph) -> Unit)?> { null }

/** Makes a paragraph tappable (with a faint highlight) when editing is on and the paragraph can be changed. */
internal fun Modifier.editable(p: DocxParagraph, onEdit: ((DocxParagraph) -> Unit)?): Modifier =
    if (onEdit != null && p.editable && p.id >= 0) {
        this.background(Color(0x142B6BE8)).clickable { onEdit(p) }
    } else {
        this
    }

internal fun canEdit(p: DocxParagraph, onEdit: ((DocxParagraph) -> Unit)?): Boolean = onEdit != null && p.editable && p.id >= 0
