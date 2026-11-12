package io.github.amandhakar.documents

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.amandhakar.documents.document.Document
import io.github.amandhakar.documents.document.DocumentFormat
import io.github.amandhakar.documents.document.DocumentOpener
import io.github.amandhakar.documents.document.UnsupportedFormatException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface ViewerState {
    data object Empty : ViewerState
    data object Loading : ViewerState
    data class Error(val message: String) : ViewerState
    data class Ready(val document: Document) : ViewerState

    /** A web page (.html), shown by its own screen rather than as pages. */
    data class Web(val uri: Uri) : ViewerState
}

class DocumentViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<ViewerState>(ViewerState.Empty)
    val state: StateFlow<ViewerState> = _state

    private var currentUri: Uri? = null
    private var job: Job? = null

    /** Opens [uri]. Does nothing if it is already the open document (e.g. after rotation). */
    fun open(uri: Uri) {
        if (uri == currentUri) return
        currentUri = uri
        job?.cancel()
        closeCurrent()
        _state.value = ViewerState.Loading
        job = viewModelScope.launch {
            val format = withContext(Dispatchers.IO) { runCatching { DocumentOpener.formatOf(getApplication(), uri) }.getOrNull() }
            if (format == DocumentFormat.Html) {
                if (isActive) _state.value = ViewerState.Web(uri)
                return@launch
            }
            // NonCancellable so a document that finished opening is never leaked if we're cancelled.
            val result = withContext(Dispatchers.IO + NonCancellable) {
                runCatching { DocumentOpener.open(getApplication(), uri) }
            }
            if (!isActive) {
                result.getOrNull()?.close()
                return@launch
            }
            _state.value = result.fold(
                onSuccess = { ViewerState.Ready(it) },
                onFailure = { ViewerState.Error(messageFor(it)) },
            )
        }
    }

    /** Closes the open document so its file can be overwritten. Follow it with [reopen]. */
    fun releaseForWrite() {
        job?.cancel()
        job = null
        closeCurrent()
        _state.value = ViewerState.Loading
    }

    /** Opens the current document again (after it was changed on disk). */
    fun reopen() {
        val uri = currentUri ?: return
        currentUri = null
        open(uri)
    }

    /** The address of the document being read or loading, or null when none is open. */
    fun openUri(): Uri? = currentUri

    /** Closes the open document (or a failed / loading one) and goes back to the library. */
    fun close() {
        job?.cancel()
        job = null
        closeCurrent()
        currentUri = null
        _state.value = ViewerState.Empty
    }

    private fun closeCurrent() {
        (_state.value as? ViewerState.Ready)?.document?.close()
    }

    override fun onCleared() {
        closeCurrent()
    }

    private fun messageFor(error: Throwable): String = when (error) {
        is UnsupportedFormatException -> "This file type isn't supported yet. Only PDF files and web pages (.html) can be opened."
        is SecurityException -> "Couldn't open this file. It may be password-protected, which isn't supported yet."
        is IOException -> "Couldn't open this file. It may be damaged or not a valid PDF."
        else -> "Something went wrong opening this file."
    }
}
