package io.github.amandhakar.documents.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.amandhakar.documents.document.Document
import io.github.amandhakar.documents.document.NormPoint
import io.github.amandhakar.documents.document.PageGeometry
import io.github.amandhakar.documents.document.PageText
import io.github.amandhakar.documents.document.Zoom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Every page of [document], top to bottom. At zoom above 1 the pages are wider than the screen
 * and scroll sideways. Pinching with two fingers scales the view live and, when the fingers lift,
 * calls [onZoomChange] with the new zoom (the pages are then re-rendered sharp at that size).
 *
 * Where the phone supports it (Android 15+), pressing and holding on text selects the word and
 * dragging extends the selection within that page. A bar then offers Copy.
 */
@Composable
fun PageList(
    document: Document,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    // Scale of the current pinch (1 when no pinch is happening). Only drawn, not re-rendered.
    var pinch by remember { mutableStateOf(1f) }
    // Selected text on one page. Reset when another document is opened.
    var selected by remember(document) { mutableStateOf<SelectedText?>(null) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFFE0E0E0))
            .clipToBounds()
            // Watch events first (Initial pass) so a two-finger pinch isn't taken by the scrolling
            // lists below; one-finger gestures are left alone and scroll as usual.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var pinching = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            pinching = true
                            pinch = Zoom.pinchScale(currentZoom, pinch, event.calculateZoom())
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (pinching) {
                        val newZoom = Zoom.scaled(currentZoom, pinch)
                        pinch = 1f
                        currentOnZoomChange(newZoom)
                    }
                }
            },
    ) {
        val contentWidth = maxWidth * zoom
        val widthPx = with(LocalDensity.current) { contentWidth.roundToPx() }
        val horizontal = rememberScrollState()
        Box(
            Modifier.graphicsLayer {
                scaleX = pinch
                scaleY = pinch
            },
        ) {
            LazyColumn(
                modifier = Modifier.horizontalScroll(horizontal).width(contentWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(count = document.pageCount, key = { it }) { index ->
                    PageImage(
                        document = document,
                        index = index,
                        widthPx = widthPx,
                        selection = selected?.takeIf { it.page == index }?.text,
                        onSelectionChange = { selected = it },
                    )
                }
            }
        }
        selected?.let { current ->
            SelectionBar(
                text = current.text.text,
                onClear = { selected = null },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

/** Text selected on page [page] (0-based). */
private data class SelectedText(val page: Int, val text: PageText)

@Composable
private fun SelectionBar(text: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text.replace('\n', ' '),
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("PDF text", text))
                onClear()
            }) { Text("Copy") }
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

@Composable
private fun PageImage(
    document: Document,
    index: Int,
    widthPx: Int,
    selection: PageText?,
    onSelectionChange: (SelectedText?) -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, document, index, widthPx) {
        value = try {
            document.renderPage(index, widthPx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // e.g. the document was closed while this page was loading
        }
    }
    val loaded = bitmap
    if (loaded == null) {
        // Placeholder with a typical page shape until the real size is known.
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.414f).background(Color.White))
        return
    }

    val scope = rememberCoroutineScope()
    val latest = remember { LatestJob() }
    // Works out the text between two touch points (in this page's pixels) and shows it.
    fun select(from: Offset, to: Offset, size: IntSize) {
        fun norm(p: Offset) = NormPoint(PageGeometry.fraction(p.x, size.width), PageGeometry.fraction(p.y, size.height))
        val start = norm(from)
        val stop = norm(to)
        latest.job?.cancel()
        latest.job = scope.launch {
            val result = try {
                document.selectText(index, start, stop)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // e.g. the document was closed meanwhile
            }
            if (result != null) onSelectionChange(SelectedText(index, result))
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (document.supportsTextSelection) {
                    Modifier.pointerInput(document, index) {
                        var anchor = Offset.Zero
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                anchor = offset
                                onSelectionChange(null)
                                select(offset, offset, size)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                select(anchor, change.position, size)
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "Page ${index + 1} of ${document.pageCount}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
        if (selection != null) {
            Canvas(Modifier.matchParentSize()) {
                for (r in selection.bounds) {
                    drawRect(
                        color = Color(0x553390FF),
                        topLeft = Offset(r.left * size.width, r.top * size.height),
                        size = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height),
                    )
                }
            }
        }
    }
}

/** Holds the selection lookup in progress, so a newer drag position replaces an older one. */
private class LatestJob {
    var job: Job? = null
}
