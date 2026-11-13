package io.github.amandhakar.documents

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.amandhakar.documents.officeedit.DocxEditModel
import io.github.amandhakar.documents.officeedit.DocxEditor
import io.github.amandhakar.documents.officeedit.DocxEdits
import io.github.amandhakar.documents.officeedit.OfficeEditException
import io.github.amandhakar.documents.officeedit.OfficeEditMessages
import io.github.amandhakar.documents.officeedit.PptxEditModel
import io.github.amandhakar.documents.officeedit.PptxEditor
import io.github.amandhakar.documents.officeedit.PptxEdits
import io.github.amandhakar.documents.slides.PptxDeck
import io.github.amandhakar.documents.word.DocxDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** The edits made to the open Word or PowerPoint file, and whether editing is on. Nothing is written until the person saves. */
data class OfficeEditState(
    val editing: Boolean = false,
    val docx: DocxEdits = DocxEdits(),
    val pptx: PptxEdits = PptxEdits(),
    val saving: Boolean = false,
    /** A one-off note for the person (saved, or what went wrong). Cleared with [OfficeEditViewModel.messageShown]. */
    val message: String? = null,
) {
    val changeCount: Int get() = docx.count + pptx.count
    val hasChanges: Boolean get() = !docx.isEmpty || !pptx.isEmpty
}

class OfficeEditViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(OfficeEditState())
    val state: StateFlow<OfficeEditState> = _state

    fun setEditing(on: Boolean) = _state.update { it.copy(editing = on) }

    /** Forgets every change and turns editing off (another document was opened, or this one was closed). */
    fun reset() {
        _state.value = OfficeEditState()
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun replaceWord(id: Int, text: String) = _state.update { it.copy(docx = it.docx.replace(id, text)) }
    fun deleteWord(id: Int) = _state.update { it.copy(docx = it.docx.delete(id)) }
    fun insertWordAfter(id: Int, text: String) = _state.update { it.copy(docx = it.docx.insertAfter(id, text)) }
    fun replaceSlide(slidePath: String, id: Int, text: String) = _state.update { it.copy(pptx = it.pptx.replace(slidePath, id, text)) }

    /** Takes back the latest change (of whichever kind of file is open). */
    fun undo() = _state.update { it.copy(docx = it.docx.undo(), pptx = it.pptx.undo()) }

    /**
     * Writes the Word edits into a copy at [destination], or over [source] when [destination] is null. The result is
     * checked by reading it back before anything is replaced, and the original is put back if the write fails.
     */
    fun saveWord(source: Uri, destination: Uri?, document: DocxDocument, onFinished: (Boolean) -> Unit) {
        val edits = _state.value.docx
        val expected = DocxEditModel.apply(document, edits)
        save(source, destination, ".docx", onFinished,
            write = { input, out -> DocxEditor.apply(input, out, edits) },
            verify = { DocxEditor.matches(it, expected) })
    }

    fun savePowerPoint(source: Uri, destination: Uri?, deck: PptxDeck, onFinished: (Boolean) -> Unit) {
        val edits = _state.value.pptx
        val expected = PptxEditModel.apply(deck, edits)
        save(source, destination, ".pptx", onFinished,
            write = { input, out -> PptxEditor.apply(input, out, edits) },
            verify = { PptxEditor.matches(it, expected) })
    }

    private fun save(
        source: Uri,
        destination: Uri?,
        suffix: String,
        onFinished: (Boolean) -> Unit,
        write: (InputStream, OutputStream) -> Unit,
        verify: (InputStream) -> Boolean,
    ) {
        if (_state.value.saving || !_state.value.hasChanges) {
            onFinished(false)
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val failure = withContext(Dispatchers.IO) {
                try {
                    writeFile(source, destination, suffix, write, verify)
                    null
                } catch (e: Throwable) {
                    e
                }
            }
            _state.update {
                if (failure == null) {
                    it.copy(saving = false, editing = false, docx = DocxEdits(), pptx = PptxEdits(), message = "Saved")
                } else {
                    it.copy(saving = false, message = OfficeEditMessages.forError(failure, replacing = destination == null))
                }
            }
            onFinished(failure == null)
        }
    }

    private fun writeFile(source: Uri, destination: Uri?, suffix: String, write: (InputStream, OutputStream) -> Unit, verify: (InputStream) -> Boolean) {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        val original = File.createTempFile("original", suffix, context.cacheDir)
        val edited = File.createTempFile("edited", suffix, context.cacheDir)
        try {
            (resolver.openInputStream(source) ?: throw IOException("Could not read $source")).use { stream ->
                original.outputStream().use { stream.copyTo(it) }
            }
            original.inputStream().use { input -> edited.outputStream().use { out -> write(input, out) } }
            // Never touch the target with something that doesn't read back as what was on screen.
            if (!edited.inputStream().use(verify)) throw OfficeEditException("Couldn't apply the changes safely. Nothing was changed.")
            val replacing = destination == null
            try {
                val out = resolver.openOutputStream(destination ?: source, "wt") ?: throw IOException("Could not write to ${destination ?: source}")
                out.use { stream -> edited.inputStream().use { it.copyTo(stream) } }
            } catch (e: Throwable) {
                if (replacing) restore(source, original)
                throw e
            }
        } finally {
            original.delete()
            edited.delete()
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
