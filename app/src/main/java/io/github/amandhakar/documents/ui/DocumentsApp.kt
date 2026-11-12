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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import io.github.amandhakar.documents.edit.Marks
import io.github.amandhakar.documents.edit.PdfText
import io.github.amandhakar.documents.edit.TextBoxMath
import io.github.amandhakar.documents.edit.SaveNames
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
    var askSaveChoice by rememberSaveable { mutableStateOf(false) }
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    var askNewText by rememberSaveable { mutableStateOf(false) }

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
            edit.toolActive -> editViewModel.exitTools()
            edit.hasPending -> askDiscard = true
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
    val sign: () -> Unit = {
        editViewModel.exitTools()
        if (edit.signature == null) editViewModel.openPad() else editViewModel.startPlacing()
    }
    val signatureImage = remember(edit.signature) { edit.signature?.asImageBitmap() }
    // Stands in for the signature picture when there is none yet (text changes don't need one).
    val noSignature = remember { ImageBitmap(1, 1) }

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
                onDone = { bitmap -> editViewModel.signatureDrawn(bitmap) },
                onPickImage = { uri, removeWhite -> editViewModel.signatureFromImage(uri, removeWhite) },
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
        edit.editingLine?.let { target ->
            var text by remember(target) { mutableStateOf(target.initialText) }
            val unusable = PdfText.unsupported(PdfText.clean(text))
            AlertDialog(
                onDismissRequest = { editViewModel.cancelLineEdit() },
                title = { Text("Change text") },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("New text") },
                        isError = unusable.isNotEmpty(),
                        supportingText = {
                            Text(
                                if (unusable.isEmpty()) {
                                    "The old text is covered, not erased from the file."
                                } else {
                                    "Can't use ${unusable.joinToString(" ")} yet: only Latin letters, digits and common symbols."
                                },
                            )
                        },
                    )
                },
                confirmButton = {
                    Button(enabled = unusable.isEmpty(), onClick = { editViewModel.applyLineEdit(text) }) { Text("Apply") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { editViewModel.applyLineEdit("") }) { Text("Remove text") }
                        TextButton(onClick = { editViewModel.cancelLineEdit() }) { Text("Cancel") }
                    }
                },
            )
        }
        if (askNewText) {
            var typed by remember { mutableStateOf("") }
            val unusable = PdfText.unsupported(TextBoxMath.prepare(typed))
            AlertDialog(
                onDismissRequest = { askNewText = false },
                title = { Text("Add text") },
                text = {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Text") },
                        isError = unusable.isNotEmpty(),
                        supportingText = {
                            Text(
                                if (unusable.isEmpty()) {
                                    "Then drag it onto a page and resize it."
                                } else {
                                    "Can't use ${unusable.joinToString(" ")} yet: only Latin letters, digits and common symbols."
                                },
                            )
                        },
                    )
                },
                confirmButton = {
                    Button(
                        enabled = unusable.isEmpty() && TextBoxMath.prepare(typed).isNotEmpty(),
                        onClick = {
                            askNewText = false
                            editViewModel.startPlacingText(typed)
                        },
                    ) { Text("Add") }
                },
                dismissButton = { TextButton(onClick = { askNewText = false }) { Text("Cancel") } },
            )
        }
        if (askDiscard) {
            AlertDialog(
                onDismissRequest = { askDiscard = false },
                title = { Text("Discard changes?") },
                text = { Text("You haven't saved the signatures or text changes you made.") },
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
                                if (edit.placing == null) {
                                    CompactButton("\u2212", enabled = zoom > Zoom.MIN) { zoom = Zoom.zoomOut(zoom) }
                                    Text("${(zoom * 100).roundToInt()}%")
                                    CompactButton("+", enabled = zoom < Zoom.MAX) { zoom = Zoom.zoomIn(zoom) }
                                    IconButton(onClick = sign, enabled = !edit.saving) {
                                        Icon(DocIcons.Edit, contentDescription = "Add signature")
                                    }
                                    if (edit.hasPending) {
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
                                        DropdownMenuItem(
                                            text = { Text(if (edit.textMode) "Stop changing text" else "Change text") },
                                            onClick = {
                                                menuOpen = false
                                                if (edit.textMode) editViewModel.exitTools() else editViewModel.enterTextMode()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add text") },
                                            onClick = {
                                                menuOpen = false
                                                editViewModel.exitTools()
                                                askNewText = true
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Highlight") },
                                            onClick = {
                                                menuOpen = false
                                                editViewModel.enterHighlightMode()
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Draw") },
                                            onClick = {
                                                menuOpen = false
                                                editViewModel.enterPenMode()
                                            },
                                        )
                                        if (edit.marks.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("Undo last highlight or drawing") },
                                                onClick = {
                                                    menuOpen = false
                                                    editViewModel.undoLastMark()
                                                },
                                            )
                                        }
                                        if (edit.placedTexts.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("Undo last added text") },
                                                onClick = {
                                                    menuOpen = false
                                                    editViewModel.undoLastNewText()
                                                },
                                            )
                                        }
                                        if (edit.textEdits.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("Undo last text change") },
                                                onClick = {
                                                    menuOpen = false
                                                    editViewModel.undoLastText()
                                                },
                                            )
                                        }
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
                        val floatingImage = remember(edit.floatingText) { edit.floatingText?.stamp?.bitmap?.asImageBitmap() }
                        PageList(
                            document = s.document,
                            zoom = zoom,
                            onZoomChange = { zoom = it },
                            editing = EditingUi(
                                signature = image ?: noSignature,
                                aspect = edit.signature?.let { it.width.toFloat() / it.height } ?: 1f,
                                floating = floatingImage ?: image ?: noSignature,
                                floatingAspect = edit.floatingText?.stamp?.aspect
                                    ?: edit.signature?.let { it.width.toFloat() / it.height } ?: 1f,
                                floatingIsText = edit.floatingText != null,
                                placedTexts = edit.placedTexts,
                                placed = edit.placed,
                                placing = if (image != null || floatingImage != null) edit.placing else null,
                                onPlacingChange = { editViewModel.updatePlacing(it) },
                                onPlace = { editViewModel.placeAt(it) },
                                onMiss = {
                                    Toast.makeText(context, "Move the signature over a page first.", Toast.LENGTH_SHORT).show()
                                },
                                onCancel = { editViewModel.cancelPlacing() },
                                onRedraw = { editViewModel.openPad() },
                                textMode = edit.textMode,
                                highlightMode = edit.highlightMode,
                                penMode = edit.penMode,
                                penPaused = edit.penPaused,
                                penColor = edit.penColor,
                                marks = edit.marks,
                                onHighlightTapped = { page, line -> editViewModel.highlightTapped(page, line) },
                                onStroke = { page, points -> editViewModel.strokeDrawn(page, points) },
                                lines = edit.lines,
                                textEdits = edit.textEdits,
                                onNeedLines = { page -> viewModel.openUri()?.let { editViewModel.requestLines(page, it) } },
                                onLineTapped = { page, line, cover -> editViewModel.lineTapped(page, line, cover) },
                            ),
                        )
                        if (edit.toolActive) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                                shape = RoundedCornerShape(24.dp),
                                shadowElevation = 6.dp,
                                tonalElevation = 3.dp,
                            ) {
                                Row(
                                    Modifier.padding(start = 16.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    when {
                                        edit.textMode -> Text("Tap a line of text to change it")
                                        edit.highlightMode -> Text("Tap a line to highlight it")
                                        else -> {
                                            for (color in Marks.PEN_COLORS) {
                                                Box(
                                                    Modifier
                                                        .padding(end = 8.dp)
                                                        .size(if (edit.penColor == color) 28.dp else 22.dp)
                                                        .background(Color(color), CircleShape)
                                                        .clickable { editViewModel.setPenColor(color) },
                                                )
                                            }
                                            TextButton(onClick = { editViewModel.setPenPaused(!edit.penPaused) }) {
                                                Text(if (edit.penPaused) "Scrolling: tap to draw" else "Drawing: tap to scroll")
                                            }
                                        }
                                    }
                                    TextButton(onClick = { editViewModel.exitTools() }) { Text("Done") }
                                }
                            }
                        }
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

