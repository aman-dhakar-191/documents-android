package io.github.amandhakar.documents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.amandhakar.documents.library.LibraryFilter
import io.github.amandhakar.documents.library.LibraryFilters
import io.github.amandhakar.documents.library.MultiSelect
import io.github.amandhakar.documents.library.PdfFile
import io.github.amandhakar.documents.library.PdfLibrary
import io.github.amandhakar.documents.library.StorageAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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

    // The PDFs ticked in the list. Lives here so it survives turning the phone.
    private val _selected = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected

    // Which kind of file the chips above the list are showing.
    private val _filter = MutableStateFlow(LibraryFilter.All)
    val filter: StateFlow<LibraryFilter> = _filter

    private var job: Job? = null

    fun setFilter(filter: LibraryFilter) {
        _filter.value = filter
        _selected.value = emptySet() // what was ticked may no longer be on screen
    }

    fun toggleSelected(id: Long) {
        _selected.update { MultiSelect.toggle(it, id) }
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun selectAll() {
        (_state.value as? LibraryState.Ready)?.let { _selected.value = MultiSelect.all(LibraryFilters.apply(it.files, _filter.value)) }
    }

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
                val files = withContext(Dispatchers.IO) { PdfLibrary.load(context) }
                _selected.update { MultiSelect.prune(it, files.map { file -> file.id }) }
                LibraryState.Ready(files)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LibraryState.Failed("Couldn't read the list of files.")
            }
        }
    }
}
