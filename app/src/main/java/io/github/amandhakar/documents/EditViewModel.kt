package io.github.amandhakar.documents

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.amandhakar.documents.edit.PdfSigner
import io.github.amandhakar.documents.edit.PlacedSignature
import io.github.amandhakar.documents.edit.Placement
import io.github.amandhakar.documents.edit.SaveNames
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
) {
    val hasEdits: Boolean get() = placed.isNotEmpty() || placing != null
}

class EditViewModel(app: Application) : AndroidViewModel(app) {
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

    /** Keeps a newly drawn signature (on disk too) and closes the pad. */
    fun signatureDrawn(bitmap: Bitmap) {
        _state.update { it.copy(signature = bitmap, padOpen = false) }
        viewModelScope.launch(Dispatchers.IO) { store.save(bitmap) }
    }

    /** Starts placing the signature on [page], in the middle of it. */
    fun startPlacing(page: Int, pageAspect: Float) = _state.update { s ->
        val signature = s.signature ?: return@update s
        s.copy(placing = Placement.initial(page, signature.width.toFloat() / signature.height, pageAspect))
    }

    fun updatePlacing(placed: PlacedSignature) = _state.update { s -> if (s.placing == null) s else s.copy(placing = placed) }
    fun cancelPlacing() = _state.update { it.copy(placing = null) }

    fun confirmPlacing() = _state.update { s -> s.placing?.let { s.copy(placing = null, placed = s.placed + it) } ?: s }

    fun undoLast() = _state.update { it.copy(placed = it.placed.dropLast(1)) }

    /** Forgets everything not saved (another document was opened, or the person chose to discard). */
    fun clearEdits() = _state.update { it.copy(placing = null, placed = emptyList(), padOpen = false) }

    fun messageShown() = _state.update { it.copy(message = null) }

    /**
     * Writes the open PDF at [source] with the placed signatures into [destination], or over [source]
     * itself when [destination] is null. The result is checked before anything is overwritten, and if
     * writing over the original fails part-way the original is put back. [onFinished] gets true on success.
     */
    fun save(source: Uri, destination: Uri?, onFinished: (Boolean) -> Unit) {
        val snapshot = _state.value
        val signature = snapshot.signature
        if (signature == null || snapshot.placed.isEmpty() || snapshot.saving) {
            onFinished(false)
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    write(source, destination, signature, snapshot.placed)
                    null
                } catch (e: Throwable) {
                    e
                }
            }
            _state.update {
                if (result == null) {
                    it.copy(saving = false, placed = emptyList(), placing = null, message = "Saved")
                } else {
                    it.copy(saving = false, message = SaveNames.errorMessage(result, replacing = destination == null))
                }
            }
            onFinished(result == null)
        }
    }

    private fun write(source: Uri, destination: Uri?, signature: Bitmap, placements: List<PlacedSignature>) {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        val input = File.createTempFile("source", ".pdf", context.cacheDir)
        val output = File.createTempFile("signed", ".pdf", context.cacheDir)
        try {
            (resolver.openInputStream(source) ?: throw IOException("Could not read $source")).use { stream ->
                input.outputStream().use { stream.copyTo(it) }
            }
            val pages = PdfSigner.pageCount(context, input) ?: throw IOException("Could not read the PDF")
            PdfSigner.sign(context, input, signature, placements, output)
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
