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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsApp(
    incomingUri: Uri?,
    viewModel: DocumentViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var zoom by rememberSaveable { mutableStateOf(1f) }
    // Lives here (not in the library screen) so the list keeps its scroll position after reading a PDF.
    val gridState = rememberLazyGridState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.open(uri)
    }
    LaunchedEffect(incomingUri) {
        if (incomingUri != null) viewModel.open(incomingUri)
    }

    val inLibrary = state is ViewerState.Empty
    val goBack = {
        viewModel.close()
        zoom = 1f
    }
    BackHandler(enabled = !inLibrary, onBack = goBack)

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Documents") },
                    navigationIcon = {
                        if (!inLibrary) TextButton(onClick = goBack) { Text("Back") }
                    },
                    actions = {
                        if (state is ViewerState.Ready) {
                            TextButton(onClick = { zoom = Zoom.zoomOut(zoom) }, enabled = zoom > Zoom.MIN) { Text("−") }
                            Text("${(zoom * 100).roundToInt()}%")
                            TextButton(onClick = { zoom = Zoom.zoomIn(zoom) }, enabled = zoom < Zoom.MAX) { Text("+") }
                        }
                        TextButton(onClick = { picker.launch(arrayOf("application/pdf")) }) { Text("Browse") }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val s = state) {
                    ViewerState.Empty -> LibraryScreen(
                        viewModel = libraryViewModel,
                        gridState = gridState,
                        onOpen = { viewModel.open(it) },
                    )
                    ViewerState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    is ViewerState.Error -> Message(s.message) {
                        Button(onClick = goBack) { Text("Back to my PDFs") }
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
