package io.github.amandhakar.documents

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.amandhakar.documents.edit.PdfSigner
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
) {
    val hasEdits: Boolean get() = placed.isNotEmpty() || placing != null
}

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
        s.copy(placing = Placement.initial(0, signature.width.toFloat() / signature.height, VIEWPORT_ASPECT))
    }

    /** The signature being positioned moved or resized (as fractions of the visible area). */
    fun updatePlacing(placed: PlacedSignature) = _state.update { s -> if (s.placing == null) s else s.copy(placing = placed) }
    fun cancelPlacing() = _state.update { it.copy(placing = null) }

    /** The signature was dropped at [placed] on a page. */
    fun placeAt(placed: PlacedSignature) = _state.update { it.copy(placing = null, placed = it.placed + placed) }

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
