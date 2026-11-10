package io.github.amandhakar.documents.ui

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.amandhakar.documents.defaultapp.DefaultKind
import io.github.amandhakar.documents.defaultapp.DefaultPdfApp
import io.github.amandhakar.documents.defaultapp.DefaultPdfApps
import io.github.amandhakar.documents.library.PdfFile
import io.github.amandhakar.documents.library.PdfFiles
import io.github.amandhakar.documents.library.StorageAccess
import io.github.amandhakar.documents.library.Thumbnails

/** Every PDF on the phone as a card with a first-page preview. Tap a card to read it. */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    gridState: LazyGridState,
    onOpen: (Uri) -> Unit,
) {
    val state by viewModel.state.collectAsState()
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
            "To show your PDFs here, Documents needs permission to see the files on your phone. " +
                "It only looks for PDF files, and nothing leaves your phone. " +
                "You can still open a single PDF with Browse without giving this permission.",
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
            Message("No PDF files found on this phone. If you just downloaded one, it may take a moment to appear.") {
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
                if (defaultApp.kind != DefaultKind.Ours) {
                    item(key = "default-app", span = { GridItemSpan(maxLineSpan) }) {
                        DefaultAppCard(defaultApp, s.files.first().uri)
                    }
                }
                items(s.files, key = { it.id }) { file ->
                    PdfCard(file, onClick = { onOpen(file.uri) })
                }
            }
        }
    }
}

/** Explains how to make Documents the app that opens PDFs from other apps, and takes you there. */
@Composable
private fun DefaultAppCard(app: DefaultPdfApp, samplePdf: Uri) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Make Documents your default PDF app", style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun PdfCard(file: PdfFile, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Thumbnail(file, Modifier.fillMaxWidth().aspectRatio(1f / 1.414f))
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

@Composable
private fun Thumbnail(file: PdfFile, modifier: Modifier) {
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
