package io.github.amandhakar.documents.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.amandhakar.documents.DocumentViewModel
import io.github.amandhakar.documents.EditViewModel
import io.github.amandhakar.documents.LibraryState
import io.github.amandhakar.documents.LibraryViewModel
import io.github.amandhakar.documents.ViewerState
import io.github.amandhakar.documents.document.Zoom
import io.github.amandhakar.documents.edit.PageRow
import io.github.amandhakar.documents.edit.SaveNames
import io.github.amandhakar.documents.edit.VisiblePage
import io.github.amandhakar.documents.library.MultiSelect
import io.github.amandhakar.documents.share.ShareFiles
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
    editViewModel: EditViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val libraryState by libraryViewModel.state.collectAsState()
    val selectedIds by libraryViewModel.selected.collectAsState()
    val edit by editViewModel.state.collectAsState()
    val context = LocalContext.current
    var zoom by rememberSaveable { mutableStateOf(1f) }
    // Lives here (not in the library screen) so the list keeps its scroll position after reading a PDF.
    val gridState = rememberLazyGridState()
    // Shared with the page list so Sign can tell which page is showing.
    val pageListState = rememberLazyListState()
    var askSaveChoice by rememberSaveable { mutableStateOf(false) }
    var askDiscard by rememberSaveable { mutableStateOf(false) }

    // True while the PDF on screen was opened by another app: Back then returns to that app.
    var openedFromOutside by rememberSaveable { mutableStateOf(false) }

    fun openOurselves(uri: Uri) {
        openedFromOutside = false
        editViewModel.clearEdits()
        viewModel.open(uri)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openOurselves(uri)
    }
    LaunchedEffect(incoming) {
        if (incoming != null) {
            openedFromOutside = true
            libraryViewModel.clearSelection()
            editViewModel.clearEdits()
            viewModel.open(incoming.uri)
        }
    }

    val inLibrary = state is ViewerState.Empty
    val toLibrary: () -> Unit = {
        viewModel.close()
        zoom = 1f
        openedFromOutside = false
    }
    val leaveViewer: () -> Unit = {
        editViewModel.clearEdits()
        when (BackNavigation.fromViewer(openedFromOutside)) {
            BackTarget.ExitApp -> onExit() // the document is closed when the screen is destroyed
            BackTarget.Library -> toLibrary()
        }
    }
    // Back while placing a signature just stops placing; with unsaved signatures it asks first.
    val goBack: () -> Unit = {
        when {
            edit.placing != null -> editViewModel.cancelPlacing()
            edit.placed.isNotEmpty() -> askDiscard = true
            else -> leaveViewer()
        }
    }
    BackHandler(enabled = !inLibrary, onBack = goBack)

    val saveCopy = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        val source = viewModel.openUri()
        if (target != null && source != null) {
            editViewModel.save(source, target) { saved -> if (saved) openOurselves(target) }
        }
    }
    LaunchedEffect(edit.message) {
        edit.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            editViewModel.messageShown()
        }
    }
    // The page showing the most on screen: the one a new signature goes on.
    fun visiblePage(): Int {
        val info = pageListState.layoutInfo
        val rows = info.visibleItemsInfo.map { PageRow(it.index, it.offset, it.size) }
        return VisiblePage.mostVisible(rows, info.viewportEndOffset - info.viewportStartOffset) ?: 0
    }
    val sign: () -> Unit = {
        if (edit.signature == null) editViewModel.openPad() else editViewModel.startPlacing(visiblePage(), A4_ASPECT)
    }
    val signatureImage = remember(edit.signature) { edit.signature?.asImageBitmap() }

    // Choosing PDFs in the list: Back (or Cancel) unticks everything instead of leaving.
    val selectionActive = inLibrary && selectedIds.isNotEmpty()
    BackHandler(enabled = selectionActive) { libraryViewModel.clearSelection() }

    val shareChosen: () -> Unit = {
        val files = MultiSelect.chosen((libraryState as? LibraryState.Ready)?.files.orEmpty(), selectedIds)
        if (ShareFiles.sharePdfs(context, files.map { it.uri })) {
            libraryViewModel.clearSelection()
        } else {
            Toast.makeText(context, "These files can't be shared.", Toast.LENGTH_SHORT).show()
        }
    }
    val shareOpen: () -> Unit = {
        val uri = viewModel.openUri()
        if (uri == null || !ShareFiles.sharePdfs(context, listOf(uri))) {
            Toast.makeText(context, "This file can't be shared.", Toast.LENGTH_SHORT).show()
        }
    }

    MaterialTheme {
        if (edit.padOpen) {
            SignaturePadDialog(
                onDone = { bitmap ->
                    val wasPlacing = edit.placing != null
                    editViewModel.signatureDrawn(bitmap)
                    // Drawing again while placing keeps the spot; a first signature goes straight onto the page.
                    if (!wasPlacing) editViewModel.startPlacing(visiblePage(), A4_ASPECT)
                },
                onCancel = { editViewModel.closePad() },
            )
        }
        if (askSaveChoice) {
            AlertDialog(
                onDismissRequest = { askSaveChoice = false },
                title = { Text("Save signed PDF") },
                text = { Text("Save it as a new file, or replace this one? Replacing can't be undone.") },
                confirmButton = {
                    Button(onClick = {
                        askSaveChoice = false
                        saveCopy.launch(SaveNames.signedName(displayNameOf(context, viewModel.openUri())))
                    }) { Text("Save as new copy") }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { askSaveChoice = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            askSaveChoice = false
                            val source = viewModel.openUri()
                            if (source != null) {
                                viewModel.releaseForWrite()
                                editViewModel.save(source, null) { viewModel.reopen() }
                            }
                        }) { Text("Replace original") }
                    }
                },
            )
        }
        if (askDiscard) {
            AlertDialog(
                onDismissRequest = { askDiscard = false },
                title = { Text("Discard signatures?") },
                text = { Text("You haven't saved the signatures you added.") },
                confirmButton = {
                    Button(onClick = {
                        askDiscard = false
                        leaveViewer()
                    }) { Text("Discard") }
                },
                dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Keep editing") } },
            )
        }
        Scaffold(
            topBar = {
                if (selectionActive) {
                    TopAppBar(
                        title = { Text(MultiSelect.title(selectedIds.size)) },
                        navigationIcon = { TextButton(onClick = { libraryViewModel.clearSelection() }) { Text("Cancel") } },
                        actions = {
                            TextButton(onClick = { libraryViewModel.selectAll() }) { Text("Select all") }
                            TextButton(onClick = shareChosen) { Text("Share") }
                        },
                    )
                } else if (inLibrary) {
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
                        navigationIcon = {
                            IconButton(onClick = goBack) { Icon(DocIcons.ArrowBack, contentDescription = "Back") }
                        },
                        actions = {
                            if (state is ViewerState.Ready) {
                                if (edit.placing != null) {
                                    IconButton(onClick = { editViewModel.openPad() }) {
                                        Icon(DocIcons.Edit, contentDescription = "Draw the signature again")
                                    }
                                    IconButton(onClick = { editViewModel.cancelPlacing() }) {
                                        Icon(DocIcons.Close, contentDescription = "Cancel")
                                    }
                                    IconButton(onClick = { editViewModel.confirmPlacing() }) {
                                        Icon(DocIcons.Check, contentDescription = "Place signature here")
                                    }
                                } else {
                                    CompactButton("\u2212", enabled = zoom > Zoom.MIN) { zoom = Zoom.zoomOut(zoom) }
                                    Text("${(zoom * 100).roundToInt()}%")
                                    CompactButton("+", enabled = zoom < Zoom.MAX) { zoom = Zoom.zoomIn(zoom) }
                                    IconButton(onClick = sign, enabled = !edit.saving) {
                                        Icon(DocIcons.Edit, contentDescription = "Add signature")
                                    }
                                    if (edit.placed.isNotEmpty()) {
                                        IconButton(onClick = { askSaveChoice = true }, enabled = !edit.saving) {
                                            Icon(DocIcons.Save, contentDescription = "Save signed PDF")
                                        }
                                    }
                                    var menuOpen by remember { mutableStateOf(false) }
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(DocIcons.MoreVert, contentDescription = "More options")
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Share") },
                                            leadingIcon = { Icon(DocIcons.Share, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                shareOpen()
                                            },
                                        )
                                        if (edit.placed.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("Undo last signature") },
                                                onClick = {
                                                    menuOpen = false
                                                    editViewModel.undoLast()
                                                },
                                            )
                                        }
                                    }
                                }
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
                    is ViewerState.Ready -> {
                        val image = signatureImage
                        PageList(
                            document = s.document,
                            zoom = zoom,
                            onZoomChange = { zoom = it },
                            listState = pageListState,
                            editing = if (image != null && edit.signature != null) {
                                EditingUi(
                                    signature = image,
                                    aspect = edit.signature!!.width.toFloat() / edit.signature!!.height,
                                    placed = edit.placed,
                                    placing = edit.placing,
                                    onPlacingChange = { editViewModel.updatePlacing(it) },
                                )
                            } else {
                                null
                            },
                        )
                        if (edit.saving) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}

/** A text button with less padding, so Back, zoom and Share all fit in the slim reading bar on a narrow phone. */
@Composable
private fun CompactButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.defaultMinSize(minWidth = 40.dp),
    ) { Text(text) }
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

/** The file's display name (for suggesting a name for the signed copy), or null when it can't be read. */
private fun displayNameOf(context: android.content.Context, uri: Uri?): String? {
    if (uri == null) return null
    return try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment
    } catch (e: Exception) {
        uri.lastPathSegment
    }
}

/** Width / height of a typical portrait page, used to place a new signature before the page's real shape is needed. */
private const val A4_ASPECT = 1f / 1.414f
