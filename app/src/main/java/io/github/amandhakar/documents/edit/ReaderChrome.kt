package io.github.amandhakar.documents.edit

/** The tools in the dock under the reader, in order. */
enum class ReaderTool(val label: String) {
    Sign("Sign"),
    AddText("Add text"),
    EditText("Edit text"),
    Highlight("Highlight"),
    Draw("Draw"),
}

/** Words and choices for the reader's bars. Free of Android calls so it can be unit tested. */
object ReaderChrome {
    /** How many changes Save would write. */
    fun pendingCount(signatures: Int, textEdits: Int, newTexts: Int, marks: Int): Int =
        signatures + textEdits + newTexts + marks

    /** "Save · 3", or just "Save" when the count is zero. */
    fun saveLabel(count: Int): String = if (count > 0) "Save · $count" else "Save"

    /** Which dock tool is switched on, from the three tool modes (at most one is on at a time). */
    fun activeTool(textMode: Boolean, highlightMode: Boolean, penMode: Boolean): ReaderTool? = when {
        textMode -> ReaderTool.EditText
        highlightMode -> ReaderTool.Highlight
        penMode -> ReaderTool.Draw
        else -> null
    }

    /** The hint under the bar while a tool is on; Draw shows its colours instead of a sentence. */
    fun hint(tool: ReaderTool?): String? = when (tool) {
        ReaderTool.EditText -> "Tap a line of text to change it"
        ReaderTool.Highlight -> "Tap a line to highlight it"
        else -> null
    }

    /** The zoom label, e.g. 1.25 -> "125%". */
    fun zoomLabel(zoom: Float): String = "${Math.round(zoom * 100)}%"
}
