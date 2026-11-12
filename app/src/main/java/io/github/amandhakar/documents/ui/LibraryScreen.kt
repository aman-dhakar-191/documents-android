package io.github.amandhakar.documents.ui

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.amandhakar.documents.LibraryState
import io.github.amandhakar.documents.LibraryViewModel
import io.github.amandhakar.documents.document.DocumentFormat
import io.github.amandhakar.documents.library.LibraryFilter
import io.github.amandhakar.documents.library.LibraryFilters
import io.github.amandhakar.documents.defaultapp.DefaultKind
import io.github.amandhakar.documents.defaultapp.DefaultPdfApp
import io.github.amandhakar.documents.defaultapp.DefaultPdfApps
import io.github.amandhakar.documents.library.PdfFile
import io.github.amandhakar.documents.library.PdfFiles
import io.github.amandhakar.documents.library.StorageAccess
import io.github.amandhakar.documents.library.Thumbnails

/** Every PDF, Word file, presentation and web page on the phone as a card (PDFs with a first-page preview), with chips to filter by kind. Tap a card to open it. */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    gridState: LazyGridState,
    onOpen: (Uri) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val query by viewModel.query.collectAsState()
    val context = LocalContext.current

    // Android 10 and older ask with a normal permission dialog.
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }

    var defaultApp by remember { mutableStateOf(DefaultPdfApps.detect(context)) }

    LaunchedEffect(Unit) { viewModel.refresh() }
    // Coming back from Android's settings screen (or from another app) re-checks and re-reads,
    // including who the default PDF app is, since the user may just have changed it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
        defaultApp = DefaultPdfApps.detect(context)
    }

    when (val s = state) {
        LibraryState.Loading -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        LibraryState.NeedsPermission -> Message(
            "To show your files here, Documents needs permission to see the files on your phone. " +
                "It only looks for PDF, Word, PowerPoint and web page files, and nothing leaves your phone. " +
                "You can still open a single file with Browse without giving this permission.",
        ) {
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    StorageAccess.openAllFilesSettings(context)
                } else {
                    legacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }) { Text("Allow access to files") }
        }
        is LibraryState.Failed -> Message(s.message) {
            Button(onClick = { viewModel.refresh() }) { Text("Try again") }
        }
        is LibraryState.Ready -> if (s.files.isEmpty()) {
            Message("No PDF files or web pages found on this phone. If you just downloaded one, it may take a moment to appear.") {
                Button(onClick = { viewModel.refresh() }) { Text("Refresh") }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                    FilterRow(filter, LibraryFilters.counts(s.files)) { viewModel.setFilter(it) }
                }
                val samplePdf = s.files.firstOrNull { it.format == DocumentFormat.Pdf }
                if (defaultApp.kind != DefaultKind.Ours && filter != LibraryFilter.Web && samplePdf != null) {
                    item(key = "default-app", span = { GridItemSpan(maxLineSpan) }) {
                        DefaultAppCard(defaultApp, samplePdf.uri)
                    }
                }
                val shown = LibraryFilters.apply(s.files, filter, query)
                if (shown.isEmpty()) {
                    item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (query.isBlank()) "No ${filter.label.lowercase()} found." else "Nothing matches \"${query.trim()}\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(shown, key = { it.id }) { file ->
                    PdfCard(
                        file = file,
                        selected = file.id in selected,
                        selecting = selected.isNotEmpty(),
                        // Once something is ticked, tapping ticks or unticks; otherwise it opens the PDF.
                        onClick = { if (selected.isNotEmpty()) viewModel.toggleSelected(file.id) else onOpen(file.uri) },
                        onLongClick = { viewModel.toggleSelected(file.id) },
                    )
                }
            }
        }
    }
}

/** The chips that pick which kind of file to list: All, PDF, Web pages (each with how many there are). */
@Composable
private fun FilterRow(selected: LibraryFilter, counts: Map<LibraryFilter, Int>, onPick: (LibraryFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (filter in LibraryFilter.entries) {
            FilterChip(
                selected = filter == selected,
                onClick = { onPick(filter) },
                label = { Text("${filter.label} ${counts[filter] ?: 0}") },
            )
        }
    }
}

/** One slim row that offers to make Documents the app that opens PDFs from other apps; "Set up" shows the steps. */
@Composable
private fun DefaultAppCard(app: DefaultPdfApp, samplePdf: Uri) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = if (open) 16.dp else 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Make Documents your default PDF app",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "Set up") }
            }
            if (open) {
                Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val otherPackage = app.packageName
                    if (app.kind == DefaultKind.Other && otherPackage != null) {
                        val name = app.label ?: "another app"
                        Text(
                            "PDFs you open from other apps currently go to $name. To change that, open its " +
                                "settings, tap \"Open by default\" (or \"Set as default\"), then \"Clear defaults\". " +
                                "Next time you open a PDF, choose Documents and tap Always.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { DefaultPdfApps.openAppSettings(context, otherPackage) }) {
                            Text("Open $name settings")
                        }
                    } else {
                        Text(
                            "No app is set for PDFs yet. Tap the button, choose Documents, then tap Always.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { DefaultPdfApps.askWhichToUse(context, samplePdf) }) {
                            Text("Choose default app")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfCard(
    file: PdfFile,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val ring = if (selected) {
        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
    } else {
        Modifier
    }
    Card(Modifier.fillMaxWidth().then(ring).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            Thumbnail(file, Modifier.fillMaxWidth().aspectRatio(1f / 1.414f))
            if (selecting) SelectBadge(selected, Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Column(Modifier.padding(10.dp)) {
            Text(
                PdfFiles.title(file.name),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(file.folder, PdfFiles.formatSize(file.sizeBytes), PdfFiles.formatDate(file.modifiedSeconds))
                    .filter { it.isNotEmpty() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The round tick box in a card's corner while choosing PDFs: filled with a tick when chosen. */
@Composable
private fun SelectBadge(selected: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) primary else Color.White.copy(alpha = 0.85f))
            .border(2.dp, primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Text("✓", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Thumbnail(file: PdfFile, modifier: Modifier) {
    if (file.format != DocumentFormat.Pdf) {
        // A web page or Word file has no first page to show: an icon and its type instead.
        val (icon, label) = when (file.format) {
            DocumentFormat.Docx -> DocIcons.Description to "Word"
            DocumentFormat.Pptx -> DocIcons.Slideshow to "PowerPoint"
            else -> DocIcons.Public to "Web page"
        }
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<Bitmap?>(initialValue = null, file.id, file.modifiedSeconds) {
        value = Thumbnails.load(context, file)
    }
    Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        } else {
            Text("PDF", color = Color.Gray)
        }
    }
}
