package io.github.amandhakar.documents

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.amandhakar.documents.edit.PageLines
import io.github.amandhakar.documents.edit.EraseOutcome
import io.github.amandhakar.documents.edit.FontStyleChoice
import io.github.amandhakar.documents.edit.MarkPoint
import io.github.amandhakar.documents.edit.Marks
import io.github.amandhakar.documents.edit.PageMark
import io.github.amandhakar.documents.edit.PdfSigner
import io.github.amandhakar.documents.edit.PenStroke
import io.github.amandhakar.documents.edit.PlacedText
import io.github.amandhakar.documents.edit.TextBoxMath
import io.github.amandhakar.documents.edit.TextStamp
import io.github.amandhakar.documents.edit.PdfText
import io.github.amandhakar.documents.edit.PdfTextLines
import io.github.amandhakar.documents.edit.TextEdit
import io.github.amandhakar.documents.edit.TextLine
import io.github.amandhakar.documents.edit.PlacedSignature
import io.github.amandhakar.documents.edit.Placement
import io.github.amandhakar.documents.edit.SaveNames
import io.github.amandhakar.documents.edit.SignatureImage
import io.github.amandhakar.documents.edit.SignatureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Everything about signing the open PDF: the saved signature, the ones placed so far and saving. */
data class EditState(
    /** The person's signature picture, or null until they have drawn one. */
    val signature: Bitmap? = null,
    /** The signature being dragged into place right now. */
    val placing: PlacedSignature? = null,
    /** Signatures placed but not saved yet. */
    val placed: List<PlacedSignature> = emptyList(),
    val padOpen: Boolean = false,
    val saving: Boolean = false,
    /** A one-off note for the person (saved, or what went wrong). Cleared with [EditViewModel.messageShown]. */
    val message: String? = null,
    /** True while the person is tapping lines of text to change them. */
    val textMode: Boolean = false,
    /** The text lines found so far, by page (0-based). */
    val lines: Map<Int, PageLines> = emptyMap(),
    /** Text changes not saved yet. */
    val textEdits: List<TextEdit> = emptyList(),
    /** The line being changed right now (its dialog is open). */
    val editingLine: LineEdit? = null,
    /** New text boxes placed but not saved yet. */
    val placedTexts: List<PlacedText> = emptyList(),
    /** The new text being positioned right now (shown in place of the signature), or null. */
    val floatingText: FloatingText? = null,
    /** True while tapping lines to highlight them. */
    val highlightMode: Boolean = false,
    /** True while drawing with a finger; [penPaused] lets the pages be scrolled meanwhile. */
    val penMode: Boolean = false,
    val penPaused: Boolean = false,
    val penColor: Int = Marks.PEN_COLORS[0],
    /** Highlights and pen strokes not saved yet, in the order they were made. */
    val marks: List<PageMark> = emptyList(),
) {
    /** True while any tool that takes touches on the pages is on. */
    val toolActive: Boolean get() = textMode || highlightMode || penMode

    /** Changes that would be written by Save. */
    val hasPending: Boolean
        get() = placed.isNotEmpty() || textEdits.isNotEmpty() || placedTexts.isNotEmpty() || marks.isNotEmpty()
    val hasEdits: Boolean get() = hasPending || placing != null || editingLine != null
}

/** New text being positioned: the text and the picture shown while it is moved. */
class FloatingText(val text: String, val stamp: TextStamp)

/** A line of text the person tapped: what to show in the box, and the colour that will cover the old text. */
data class LineEdit(
    val page: Int,
    val pageLines: PageLines,
    val line: TextLine,
    val cover: Int,
    val ink: Int,
    val font: FontStyleChoice,
    val initialText: String,
)

class EditViewModel(app: Application) : AndroidViewModel(app) {
    private companion object {
        // A typical portrait phone screen, to put a new signature in the middle before the real size is known.
        const val VIEWPORT_ASPECT = 0.5f
        const val MAX_IMAGE_SIDE_PX = 1000
    }

    private val store = SignatureStore(app)
    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.load() }
            _state.update { it.copy(signature = it.signature ?: saved) }
        }
    }

    fun openPad() = _state.update { it.copy(padOpen = true) }
    fun closePad() = _state.update { it.copy(padOpen = false) }

    /**
     * Keeps a new signature picture (drawn or from a photo; on disk too) and closes the pad. If nothing was
     * being placed, it starts being placed now.
     */
    fun signatureDrawn(bitmap: Bitmap) {
        _state.update { s ->
            val aspect = bitmap.width.toFloat() / bitmap.height
            val placing = s.placing?.let { Placement.fit(it, aspect, VIEWPORT_ASPECT) } ?: Placement.initial(0, aspect, VIEWPORT_ASPECT)
            s.copy(signature = bitmap, padOpen = false, placing = placing)
        }
        viewModelScope.launch(Dispatchers.IO) { store.save(bitmap) }
    }

    /** Uses the picture at [uri] as the signature, with a white background made see-through when [removeWhite]. */
    fun signatureFromImage(uri: Uri, removeWhite: Boolean) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { runCatching { loadSignatureImage(uri, removeWhite) }.getOrNull() }
            if (bitmap == null) {
                _state.update { it.copy(message = "Couldn't use that image.") }
            } else {
                signatureDrawn(bitmap)
            }
        }
    }

    private fun loadSignatureImage(uri: Uri, removeWhite: Boolean): Bitmap? {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = SignatureImage.sampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_SIDE_PX)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        if (!removeWhite) return decoded
        val width = decoded.width
        val height = decoded.height
        val pixels = IntArray(width * height)
        decoded.getPixels(pixels, 0, width, 0, 0, width, height)
        val cleared = SignatureImage.whiteToTransparent(pixels)
        val ink = SignatureImage.inkBounds(cleared, width, height) ?: return null
        val box = SignatureImage.padded(ink, 6, width, height)
        val out = Bitmap.createBitmap(box.width, box.height, Bitmap.Config.ARGB_8888)
        out.setPixels(cleared, box.top * width + box.left, width, 0, 0, box.width, box.height)
        return out
    }

    /** Starts positioning the signature, in the middle of the screen. */
    fun startPlacing() = _state.update { s ->
        val signature = s.signature ?: return@update s
        s.copy(
            floatingText = null, highlightMode = false, penMode = false, textMode = false,
            placing = Placement.initial(0, signature.width.toFloat() / signature.height, VIEWPORT_ASPECT),
        )
    }

    /** The signature being positioned moved or resized (as fractions of the visible area). */
    /** Starts positioning a new piece of [text]; false (and a message) when it can't be used. */
    fun startPlacingText(text: String) {
        val prepared = TextBoxMath.prepare(text)
        val bad = PdfText.unsupported(prepared)
        val stamp = if (prepared.isEmpty() || bad.isNotEmpty()) null else TextStamp.render(prepared)
        _state.update { s ->
            if (stamp == null) {
                s.copy(message = if (bad.isNotEmpty()) "Can't use ${bad.joinToString(" ")} yet." else "Type some text first.")
            } else {
                s.copy(
                    textMode = false, highlightMode = false, penMode = false,
                    floatingText = FloatingText(prepared, stamp),
                    placing = Placement.initial(0, stamp.aspect, VIEWPORT_ASPECT),
                )
            }
        }
    }

    fun undoLastNewText() = _state.update { it.copy(placedTexts = it.placedTexts.dropLast(1)) }

    fun updatePlacing(placed: PlacedSignature) = _state.update { s -> if (s.placing == null) s else s.copy(placing = placed) }
    fun cancelPlacing() = _state.update { it.copy(placing = null, floatingText = null) }

    /** The signature was dropped at [placed] on a page. */
    fun placeAt(placed: PlacedSignature) = _state.update { s ->
        val text = s.floatingText
        if (text != null) {
            s.copy(
                placing = null,
                floatingText = null,
                placedTexts = s.placedTexts + PlacedText(placed, text.text, text.stamp.aspect, text.stamp.baselineRatio),
            )
        } else {
            s.copy(placing = null, placed = s.placed + placed)
        }
    }

    fun undoLast() = _state.update { it.copy(placed = it.placed.dropLast(1)) }
    fun undoLastText() = _state.update { it.copy(textEdits = it.textEdits.dropLast(1)) }

    fun enterTextMode() = _state.update {
        it.copy(textMode = true, highlightMode = false, penMode = false, placing = null, floatingText = null)
    }

    fun exitTextMode() = _state.update { it.copy(textMode = false, editingLine = null) }

    fun enterHighlightMode() = _state.update {
        it.copy(highlightMode = true, textMode = false, penMode = false, editingLine = null, placing = null, floatingText = null)
    }

    fun enterPenMode() = _state.update {
        it.copy(penMode = true, penPaused = false, highlightMode = false, textMode = false, editingLine = null, placing = null, floatingText = null)
    }

    /** Turns every tool that takes touches on the pages off. */
    fun exitTools() = _state.update { it.copy(textMode = false, highlightMode = false, penMode = false, editingLine = null) }

    fun setPenPaused(paused: Boolean) = _state.update { it.copy(penPaused = paused) }
    fun setPenColor(color: Int) = _state.update { it.copy(penColor = color) }

    /** Highlights the tapped [line] of [page], or takes its highlight away if it already has one. */
    fun highlightTapped(page: Int, line: TextLine) = _state.update { s ->
        val pageLines = s.lines[page] ?: return@update s
        s.copy(marks = Marks.toggle(s.marks, Marks.highlightFor(page, pageLines, line)))
    }

    /** Keeps a stroke drawn on [page] with the pen. Taps are ignored. */
    fun strokeDrawn(page: Int, points: List<MarkPoint>) = _state.update { s ->
        if (!Marks.isStroke(points)) return@update s
        s.copy(marks = s.marks + PenStroke(page, Marks.thin(points), Marks.PEN_WIDTH_FRACTION, s.penColor))
    }

    fun undoLastMark() = _state.update { it.copy(marks = it.marks.dropLast(1)) }

    // A private copy of the open PDF that PdfBox reads the text from (PdfBox needs a file).
    private var workingFile: File? = null
    private var workingSource: Uri? = null
    private val loadingPages = mutableSetOf<Int>()

    @Synchronized
    private fun workingCopy(source: Uri): File {
        val existing = workingFile
        if (existing != null && workingSource == source && existing.exists()) return existing
        existing?.delete()
        val context = getApplication<Application>()
        val file = File.createTempFile("working", ".pdf", context.cacheDir)
        try {
            (context.contentResolver.openInputStream(source) ?: throw IOException("Could not read $source")).use { stream ->
                file.outputStream().use { stream.copyTo(it) }
            }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
        workingFile = file
        workingSource = source
        return file
    }

    @Synchronized
    private fun dropWorkingCopy() {
        workingFile?.delete()
        workingFile = null
        workingSource = null
    }

    /** Finds the text lines on [page] of the PDF at [source] (once; the result is kept). */
    fun requestLines(page: Int, source: Uri) {
        if (page in _state.value.lines || !loadingPages.add(page)) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { PdfTextLines.read(getApplication(), workingCopy(source), page) }
            }
            loadingPages.remove(page)
            _state.update { s ->
                result.fold(
                    onSuccess = { found ->
                        s.copy(
                            lines = s.lines + (page to found),
                            message = if (found.supported) s.message else "Text on rotated pages can't be changed yet.",
                        )
                    },
                    onFailure = {
                        s.copy(
                            lines = s.lines + (page to PageLines(0f, 0f, emptyList())),
                            message = "Couldn't read the text on page ${page + 1}.",
                        )
                    },
                )
            }
        }
    }

    /** The person tapped [line] on [page]; [cover] is the background colour found around it. */
    fun lineTapped(page: Int, line: TextLine, cover: Int, ink: Int) = _state.update { s ->
        val pageLines = s.lines[page] ?: return@update s
        val existing = s.textEdits.firstOrNull { it.sameLineAs(TextEdit.forLine(page, pageLines, line, "", cover)) }
        s.copy(
            editingLine = LineEdit(
                page, pageLines, line, existing?.cover ?: cover, existing?.ink ?: ink, existing?.font ?: line.font,
                existing?.text ?: line.text,
            ),
        )
    }

    fun cancelLineEdit() = _state.update { it.copy(editingLine = null) }

    /** Keeps [text] as the replacement for the line being changed. Changing a line back to what it was removes the change. */
    fun applyLineEdit(text: String, font: FontStyleChoice? = null) = _state.update { s ->
        val target = s.editingLine ?: return@update s
        val cleaned = PdfText.clean(text)
        if (PdfText.unsupported(cleaned).isNotEmpty()) return@update s
        val edit = TextEdit.forLine(target.page, target.pageLines, target.line, cleaned, target.cover, target.ink, font ?: target.font)
        val others = s.textEdits.filterNot { it.sameLineAs(edit) }
        val unchanged = cleaned == target.line.text
        s.copy(editingLine = null, textEdits = if (unchanged) others else others + edit)
    }

    /** Forgets everything not saved (another document was opened, or the person chose to discard). */
    fun clearEdits() {
        dropWorkingCopy()
        loadingPages.clear()
        _state.update {
            it.copy(
                placing = null, placed = emptyList(), padOpen = false, placedTexts = emptyList(), floatingText = null,
                textMode = false, lines = emptyMap(), textEdits = emptyList(), editingLine = null,
                highlightMode = false, penMode = false, marks = emptyList(),
            )
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    /**
     * Writes the open PDF at [source] with the placed signatures into [destination], or over [source]
     * itself when [destination] is null. The result is checked before anything is overwritten, and if
     * writing over the original fails part-way the original is put back. [onFinished] gets true on success.
     */
    fun save(source: Uri, destination: Uri?, eraseOld: Boolean = false, onFinished: (Boolean) -> Unit) {
        val snapshot = _state.value
        val signature = snapshot.signature
        if (!snapshot.hasPending || snapshot.saving || (snapshot.placed.isNotEmpty() && signature == null)) {
            onFinished(false)
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            var outcome: EraseOutcome? = null
            val result = withContext(Dispatchers.IO) {
                try {
                    outcome = write(
                        source, destination, signature, snapshot.placed, snapshot.textEdits, snapshot.placedTexts,
                        snapshot.marks, eraseOld,
                    )
                    null
                } catch (e: Throwable) {
                    e
                }
            }
            _state.update {
                if (result == null) {
                    it.copy(
                        saving = false, placed = emptyList(), placing = null, textEdits = emptyList(), placedTexts = emptyList(),
                        lines = emptyMap(), textMode = false, highlightMode = false, penMode = false, marks = emptyList(),
                        message = savedMessage(eraseOld, outcome),
                    )
                } else {
                    it.copy(saving = false, message = SaveNames.errorMessage(result, replacing = destination == null))
                }
            }
            if (result == null) dropWorkingCopy() // the file changed: text positions must be read again
            onFinished(result == null)
        }
    }

    private fun write(source: Uri, destination: Uri?, signature: Bitmap?, placements: List<PlacedSignature>, textEdits: List<TextEdit>, newTexts: List<PlacedText>, marks: List<PageMark>, eraseOld: Boolean): EraseOutcome {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        val input = File.createTempFile("source", ".pdf", context.cacheDir)
        val output = File.createTempFile("signed", ".pdf", context.cacheDir)
        try {
            (resolver.openInputStream(source) ?: throw IOException("Could not read $source")).use { stream ->
                input.outputStream().use { stream.copyTo(it) }
            }
            val pages = PdfSigner.pageCount(context, input) ?: throw IOException("Could not read the PDF")
            val outcome = PdfSigner.edit(context, input, signature, placements, textEdits, newTexts, marks, eraseOld, output)
            // Never touch the target with something that doesn't open as the same PDF.
            if (!PdfSigner.looksValid(context, output, expectedPages = pages)) {
                throw IOException("The signed PDF did not pass its check")
            }
            val replacing = destination == null
            try {
                val out = resolver.openOutputStream(destination ?: source, "wt")
                    ?: throw IOException("Could not write to ${destination ?: source}")
                out.use { stream -> output.inputStream().use { it.copyTo(stream) } }
            } catch (e: Throwable) {
                if (replacing) restore(source, input)
                throw e
            }
            return outcome
        } finally {
            input.delete()
            output.delete()
        }
    }

    /** Puts the original bytes back after a failed overwrite. Best effort: if this fails too, the error from the write is still reported. */
    private fun restore(target: Uri, original: File) {
        try {
            getApplication<Application>().contentResolver.openOutputStream(target, "wt")?.use { stream ->
                original.inputStream().use { it.copyTo(stream) }
            }
        } catch (e: Exception) {
            // Nothing more can be done here.
        }
    }
}

/** What to tell the person after a save: plain "Saved", or how the removal of old text went. */
internal fun savedMessage(eraseOld: Boolean, outcome: EraseOutcome?): String = when {
    !eraseOld || outcome == null -> "Saved"
    outcome.failedPages.isEmpty() && outcome.removedCharacters > 0 -> "Saved. The old text was removed from the file."
    outcome.removedCharacters == 0 ->
        "Saved, but the old text could only be covered, not removed (page ${outcome.failedPages.joinToString { (it + 1).toString() }})."
    else ->
        "Saved. The old text was removed, except on page ${outcome.failedPages.joinToString { (it + 1).toString() }}, where it is only covered."
}
