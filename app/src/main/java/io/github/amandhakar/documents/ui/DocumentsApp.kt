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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
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
import io.github.amandhakar.documents.edit.ReaderChrome
import io.github.amandhakar.documents.web.FindText
import io.github.amandhakar.documents.slides.SlideScreen
import io.github.amandhakar.documents.word.WordPagesScreen
import io.github.amandhakar.documents.word.WordScreen
import io.github.amandhakar.documents.web.WebController
import io.github.amandhakar.documents.web.WebNotice
import io.github.amandhakar.documents.edit.ReaderTool
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.amandhakar.documents.DocumentViewModel
import io.github.amandhakar.documents.EditViewModel
import io.github.amandhakar.documents.LibraryState
import io.github.amandhakar.documents.LibraryViewModel
import io.github.amandhakar.documents.ViewerState
import io.github.amandhakar.documents.document.Zoom
import io.github.amandhakar.documents.edit.FontFamilyChoice
import io.github.amandhakar.documents.edit.Marks
import io.github.amandhakar.documents.edit.PdfText
import io.github.amandhakar.documents.edit.TextBoxMath
import io.github.amandhakar.documents.edit.SaveNames
import io.github.amandhakar.documents.library.MultiSelect
import io.github.amandhakar.documents.web.WebDocumentScreen
import io.github.amandhakar.documents.document.DocumentFormat
import io.github.amandhakar.documents.library.PdfFiles
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
    val query by libraryViewModel.query.collectAsState()
    val librarySort by libraryViewModel.sort.collectAsState()
    var searching by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var zoom by rememberSaveable { mutableStateOf(1f) }
    // Word files: paper pages (default) or the reflowed reading view, and the zoom of the pages.
    var wordPages by rememberSaveable { mutableStateOf(ViewPrefs.wordPages(context)) }
    var wordZoom by rememberSaveable { mutableStateOf(1f) }
    // Lives here (not in the library screen) so the list keeps its scroll position after reading a PDF.
    val gridState = rememberLazyGridState()
    var askSaveChoice by rememberSaveable { mutableStateOf(false) }
    var askDiscard by rememberSaveable { mutableStateOf(false) }
    var askNewText by rememberSaveable { mutableStateOf(false) }
    // Scripts in a web page are off until the person turns them on (per opening).
    var allowScripts by rememberSaveable { mutableStateOf(false) }
    // Online parts of a web page (pictures, styles) load only after the person allows it, per opening.
    var allowOnline by rememberSaveable { mutableStateOf(false) }
    // The find-in-page bar on a web page, and what was typed in it.
    var findOpen by rememberSaveable { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    val webController = remember { WebController() }
    // Save also removes the old text under Change text edits (experimental), instead of only covering it.
    var eraseOld by rememberSaveable { mutableStateOf(false) }

    // True while the PDF on screen was opened by another app: Back then returns to that app.
    var openedFromOutside by rememberSaveable { mutableStateOf(false) }

    fun openOurselves(uri: Uri) {
        wordZoom = 1f
        openedFromOutside = false
        allowScripts = false
        allowOnline = false
        findOpen = false
        findQuery = ""
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
            allowScripts = false
            allowOnline = false
            findOpen = false
            findQuery = ""
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
            editViewModel.save(source, target, eraseOld) { saved -> if (saved) openOurselves(target) }
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
    // Back while the search box is open closes it and shows every file again.
    val closeSearch: () -> Unit = {
        searching = false
        libraryViewModel.setQuery("")
    }
    BackHandler(enabled = inLibrary && searching && !selectionActive, onBack = closeSearch)
    // Back while finding in a web page closes the find bar first.
    val closeFind: () -> Unit = {
        findOpen = false
        findQuery = ""
        webController.clearFind()
    }
    BackHandler(enabled = !inLibrary && findOpen && state is ViewerState.Web, onBack = closeFind)

    val shareChosen: () -> Unit = {
        val files = MultiSelect.chosen((libraryState as? LibraryState.Ready)?.files.orEmpty(), selectedIds)
        if (ShareFiles.shareFiles(context, files.map { it.uri }, ShareFiles.commonMimeType(files.map { it.format.mimeType }))) {
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

    DocsTheme {
        if (edit.padOpen) {
            SignaturePadDialog(
                onDone = { bitmap -> editViewModel.signatureDrawn(bitmap) },
                onPickImage = { uri, removeWhite -> editViewModel.signatureFromImage(uri, removeWhite) },
                onCancel = { editViewModel.closePad() },
            )
        }
        if (askSaveChoice) {
            SaveSheet(
                summary = ReaderChrome.changeSummary(edit.placed.size, edit.textEdits.size, edit.placedTexts.size, edit.marks.size),
                showErase = edit.textEdits.isNotEmpty(),
                eraseOld = eraseOld,
                onEraseChange = { eraseOld = it },
                onSaveCopy = {
                    askSaveChoice = false
                    saveCopy.launch(SaveNames.signedName(displayNameOf(context, viewModel.openUri())))
                },
                onReplace = {
                    askSaveChoice = false
                    val source = viewModel.openUri()
                    if (source != null) {
                        viewModel.releaseForWrite()
                        editViewModel.save(source, null, eraseOld) { viewModel.reopen() }
                    }
                },
                onDismiss = { askSaveChoice = false },
            )
        }
        edit.editingLine?.let { target ->
            var text by remember(target) { mutableStateOf(target.initialText) }
            var font by remember(target) { mutableStateOf(target.font) }
            val unusable = PdfText.unsupported(PdfText.clean(text))
            AlertDialog(
                onDismissRequest = { editViewModel.cancelLineEdit() },
                title = { Text("Change text") },
                text = {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Text("Font (matched to the old text; change it if it is wrong)", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((family, label) in listOf(
                            FontFamilyChoice.Sans to "Sans",
                            FontFamilyChoice.Serif to "Serif",
                            FontFamilyChoice.Mono to "Mono",
                        )) {
                            ChoiceButton(label, font.family == family) { font = font.copy(family = family) }
                        }
                        ChoiceButton("B", font.bold) { font = font.copy(bold = !font.bold) }
                        ChoiceButton("I", font.italic) { font = font.copy(italic = !font.italic) }
                    }
                  }
                },
                confirmButton = {
                    Button(enabled = unusable.isEmpty(), onClick = { editViewModel.applyLineEdit(text, font) }) { Text("Apply") }
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
            AddTextSheet(
                onAdd = { text, matchNearby, font ->
                    askNewText = false
                    editViewModel.startPlacingText(text, zoom, matchNearby, font)
                },
                onDismiss = { askNewText = false },
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
                    LibraryHeader(
                        searching = searching,
                        query = query,
                        onQuery = { libraryViewModel.setQuery(it) },
                        onSearch = { searching = true },
                        onCloseSearch = closeSearch,
                        sort = librarySort,
                        onSort = { libraryViewModel.setSort(it) },
                        onBrowse = { picker.launch(arrayOf("application/pdf", "text/html", "application/xhtml+xml", DocumentFormat.DOCX_MIME, DocumentFormat.PPTX_MIME)) },
                    )
                } else if (state is ViewerState.Web && findOpen) {
                    WebFindBar(
                        query = findQuery,
                        label = FindText.label(findQuery, webController.findActive, webController.findTotal),
                        canStep = findQuery.isNotBlank() && webController.findTotal > 0,
                        onQuery = {
                            findQuery = it
                            webController.find(it)
                        },
                        onNext = { webController.findNext(it) },
                        onClose = closeFind,
                    )
                } else if (state is ViewerState.Docx || state is ViewerState.Pptx) {
                    TopAppBar(
                        expandedHeight = 48.dp,
                        title = {},
                        navigationIcon = {
                            IconButton(onClick = goBack) { Icon(DocIcons.ArrowBack, contentDescription = "Back") }
                        },
                        actions = {
                            if (state is ViewerState.Docx) {
                                if (wordPages) {
                                    CompactButton("\u2212", enabled = wordZoom > Zoom.MIN) { wordZoom = Zoom.zoomOut(wordZoom) }
                                    Text(ReaderChrome.zoomLabel(wordZoom))
                                    CompactButton("+", enabled = wordZoom < Zoom.MAX) { wordZoom = Zoom.zoomIn(wordZoom) }
                                }
                                CompactButton(if (wordPages) "Reading" else "Pages") {
                                    wordPages = !wordPages
                                    ViewPrefs.setWordPages(context, wordPages)
                                }
                            }
                            val officeUri = (state as? ViewerState.Docx)?.uri ?: (state as? ViewerState.Pptx)?.uri
                            val officeMime = if (state is ViewerState.Pptx) DocumentFormat.PPTX_MIME else DocumentFormat.DOCX_MIME
                            IconButton(
                                onClick = {
                                    val shared = officeUri != null && ShareFiles.shareFiles(context, listOf(officeUri), officeMime)
                                    if (!shared) Toast.makeText(context, "This file can't be shared.", Toast.LENGTH_SHORT).show()
                                },
                            ) { Icon(DocIcons.Share, contentDescription = "Share") }
                        },
                    )
                } else if (state is ViewerState.Web) {
                    TopAppBar(
                        expandedHeight = 48.dp,
                        title = {},
                        navigationIcon = {
                            IconButton(onClick = goBack) { Icon(DocIcons.ArrowBack, contentDescription = "Back") }
                        },
                        actions = {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(
                                    WebNotice.modeChip(allowScripts, allowOnline),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                            IconButton(onClick = { findOpen = true }) { Icon(DocIcons.Search, contentDescription = "Find in page") }
                            var menuOpen by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(DocIcons.MoreVert, contentDescription = "More options")
                            }
                            val webUri = (state as? ViewerState.Web)?.uri
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Save as PDF") },
                                    onClick = {
                                        menuOpen = false
                                        val name = displayNameOf(context, webUri)?.let { PdfFiles.title(it) } ?: "Web page"
                                        if (!webController.printToPdf(context, name)) {
                                            Toast.makeText(context, "The page isn't ready to save yet.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(DocIcons.Share, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        val shared = webUri != null &&
                                            ShareFiles.shareFiles(context, listOf(webUri), DocumentFormat.Html.mimeType)
                                        if (!shared) Toast.makeText(context, "This file can't be shared.", Toast.LENGTH_SHORT).show()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (allowOnline) "Stop loading online parts" else "Load online parts") },
                                    onClick = {
                                        menuOpen = false
                                        allowOnline = !allowOnline
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (allowScripts) "Turn scripts off" else "Allow scripts on this page") },
                                    onClick = {
                                        menuOpen = false
                                        allowScripts = !allowScripts
                                    },
                                )
                            }
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
                                    Text(ReaderChrome.zoomLabel(zoom))
                                    CompactButton("+", enabled = zoom < Zoom.MAX) { zoom = Zoom.zoomIn(zoom) }
                                    val pending = ReaderChrome.pendingCount(
                                        edit.placed.size, edit.textEdits.size, edit.placedTexts.size, edit.marks.size,
                                    )
                                    if (pending > 0) {
                                        Button(
                                            onClick = { askSaveChoice = true },
                                            enabled = !edit.saving,
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            modifier = Modifier.height(40.dp),
                                        ) { Text(ReaderChrome.saveLabel(pending)) }
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
                    is ViewerState.Docx -> if (wordPages) WordPagesScreen(s.document, wordZoom) else WordScreen(s.document)
                    is ViewerState.Pptx -> SlideScreen(deck = s.deck)
                    is ViewerState.Web -> WebDocumentScreen(
                        uri = s.uri,
                        allowScripts = allowScripts,
                        allowOnline = allowOnline,
                        onAllowOnline = { allowOnline = true },
                        controller = webController,
                    )
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
                            bottomPadding = 104.dp,
                            editing = EditingUi(
                                signature = image ?: noSignature,
                                aspect = edit.signature?.let { it.width.toFloat() / it.height } ?: 1f,
                                floating = floatingImage ?: image ?: noSignature,
                                floatingAspect = edit.floatingText?.stamp?.aspect
                                    ?: edit.signature?.let { it.width.toFloat() / it.height } ?: 1f,
                                floatingIsText = edit.floatingText != null,
                                wantsNearby = edit.floatingText?.matchNearby == true,
                                placedTexts = edit.placedTexts,
                                placed = edit.placed,
                                placing = if (image != null || floatingImage != null) edit.placing else null,
                                onPlacingChange = { editViewModel.updatePlacing(it) },
                                onPlace = { place, nearby -> editViewModel.placeAt(place, nearby) },
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
                                onLineTapped = { page, line, cover, ink -> editViewModel.lineTapped(page, line, cover, ink) },
                            ),
                        )
                        if (edit.toolActive) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                                shape = RoundedCornerShape(24.dp),
                                shadowElevation = 6.dp,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Row(
                                    Modifier.padding(start = 16.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    when {
                                        edit.textMode || edit.highlightMode -> Text(
                                            ReaderChrome.hint(ReaderChrome.activeTool(edit.textMode, edit.highlightMode, edit.penMode)).orEmpty(),
                                        )
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
                        if (edit.placing == null && edit.floatingText == null) {
                            val active = ReaderChrome.activeTool(edit.textMode, edit.highlightMode, edit.penMode)
                            ReaderDock(
                                active = active,
                                enabled = !edit.saving,
                                onPick = { tool ->
                                    when (tool) {
                                        ReaderTool.Sign -> sign()
                                        ReaderTool.AddText -> {
                                            editViewModel.exitTools()
                                            askNewText = true
                                        }
                                        ReaderTool.EditText ->
                                            if (active == tool) editViewModel.exitTools() else editViewModel.enterTextMode()
                                        ReaderTool.Highlight ->
                                            if (active == tool) editViewModel.exitTools() else editViewModel.enterHighlightMode()
                                        ReaderTool.Draw ->
                                            if (active == tool) editViewModel.exitTools() else editViewModel.enterPenMode()
                                    }
                                },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                            )
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

/** A small on/off button: filled when [selected], outlined otherwise. */
@Composable
internal fun ChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = 12.dp)
    if (selected) {
        Button(onClick = onClick, contentPadding = padding) { Text(label) }
    } else {
        androidx.compose.material3.OutlinedButton(onClick = onClick, contentPadding = padding) { Text(label) }
    }
}
