package io.github.amandhakar.documents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.amandhakar.documents.library.PdfFile
import io.github.amandhakar.documents.library.PdfLibrary
import io.github.amandhakar.documents.library.StorageAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LibraryState {
    data object Loading : LibraryState
    data object NeedsPermission : LibraryState
    data class Ready(val files: List<PdfFile>) : LibraryState
    data class Failed(val message: String) : LibraryState
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state

    private var job: Job? = null

    /** Re-checks permission and re-reads the list. The old list stays on screen while it loads. */
    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val context = getApplication<Application>()
            if (!StorageAccess.hasAccess(context)) {
                _state.value = LibraryState.NeedsPermission
                return@launch
            }
            if (_state.value !is LibraryState.Ready) _state.value = LibraryState.Loading
            _state.value = try {
                LibraryState.Ready(withContext(Dispatchers.IO) { PdfLibrary.load(context) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LibraryState.Failed("Couldn't read the list of PDF files.")
            }
        }
    }
}
