package io.github.amandhakar.documents.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.amandhakar.documents.DocumentViewModel
import io.github.amandhakar.documents.LibraryViewModel
import io.github.amandhakar.documents.ViewerState
import io.github.amandhakar.documents.document.Zoom
import kotlin.math.roundToInt

/** A PDF another app asked us to open. [serial] makes a repeat request for the same file count as new. */
data class IncomingDocument(val uri: Uri, val serial: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsApp(
    incoming: IncomingDocument?,
    onExit: () -> Unit,
    viewModel: DocumentViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var zoom by rememberSaveable { mutableStateOf(1f) }
    // Lives here (not in the library screen) so the list keeps its scroll position after reading a PDF.
    val gridState = rememberLazyGridState()

    // True while the PDF on screen was opened by another app: Back then returns to that app.
    var openedFromOutside by rememberSaveable { mutableStateOf(false) }

    fun openOurselves(uri: Uri) {
        openedFromOutside = false
        viewModel.open(uri)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openOurselves(uri)
    }
    LaunchedEffect(incoming) {
        if (incoming != null) {
            openedFromOutside = true
            viewModel.open(incoming.uri)
        }
    }

    val inLibrary = state is ViewerState.Empty
    val toLibrary: () -> Unit = {
        viewModel.close()
        zoom = 1f
        openedFromOutside = false
    }
    val goBack: () -> Unit = {
        when (BackNavigation.fromViewer(openedFromOutside)) {
            BackTarget.ExitApp -> onExit() // the document is closed when the screen is destroyed
            BackTarget.Library -> toLibrary()
        }
    }
    BackHandler(enabled = !inLibrary, onBack = goBack)

    MaterialTheme {
        Scaffold(
            topBar = {
                if (inLibrary) {
                    TopAppBar(
                        title = { Text("Documents") },
                        actions = {
                            TextButton(onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("Browse") }
                        },
                    )
                } else {
                    // Reading: no app title and a slimmer bar, so the page gets the room.
                    TopAppBar(
                        expandedHeight = 48.dp,
                        title = {},
                        navigationIcon = { TextButton(onClick = goBack) { Text("Back") } },
                        actions = {
                            if (state is ViewerState.Ready) {
                                TextButton(onClick = { zoom = Zoom.zoomOut(zoom) }, enabled = zoom > Zoom.MIN) { Text("−") }
                                Text("${(zoom * 100).roundToInt()}%")
                                TextButton(onClick = { zoom = Zoom.zoomIn(zoom) }, enabled = zoom < Zoom.MAX) { Text("+") }
                            }
                        },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    ViewerState.Empty -> LibraryScreen(
                        viewModel = libraryViewModel,
                        gridState = gridState,
                        onOpen = { openOurselves(it) },
                    )
                    ViewerState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is ViewerState.Error -> Message(s.message) {
                        Button(onClick = toLibrary) { Text("Back to my PDFs") }
                    }
                    is ViewerState.Ready -> PageList(document = s.document, zoom = zoom, onZoomChange = { zoom = it })
                }
            }
        }
    }
}

@Composable
internal fun Message(text: String, action: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center)
        action()
    }
}
