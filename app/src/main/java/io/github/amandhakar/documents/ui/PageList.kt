package io.github.amandhakar.documents.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.amandhakar.documents.document.Document
import io.github.amandhakar.documents.document.NormPoint
import io.github.amandhakar.documents.document.PageGeometry
import io.github.amandhakar.documents.document.PageText
import io.github.amandhakar.documents.document.SelectionLayout
import io.github.amandhakar.documents.document.TextEdge
import io.github.amandhakar.documents.document.Zoom
import io.github.amandhakar.documents.share.ShareFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Every page of [document], top to bottom. At zoom above 1 the pages are wider than the screen
 * and scroll sideways. Pinching with two fingers scales the view live and, when the fingers lift,
 * calls [onZoomChange] with the new zoom (the pages are then re-rendered sharp at that size).
 *
 * Where the phone supports it (Android 15+), text can be selected like in any text view: press and
 * hold on a word, then drag the two handles to select more or less. A small menu offers Select all
 * (all the text on that page), Share and Copy, and tapping anywhere else clears the selection.
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
            }
            // A tap that nothing else used (not a scroll, not the handles or the menu, which take
            // their own touches) clears the selection.
            .pointerInput(document) {
                detectTapGestures(onTap = { selected = null })
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
    }
}

/** Text selected on page [page] (0-based). */
private data class SelectedText(val page: Int, val text: PageText)

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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest = remember { LatestJob() }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    // True while a finger is still down from the long press: the menu waits until it lifts.
    var holding by remember { mutableStateOf(false) }

    fun norm(p: Offset) = NormPoint(PageGeometry.fraction(p.x, pageSize.width), PageGeometry.fraction(p.y, pageSize.height))

    // Looks up the text between two ends and shows it. A newer request replaces an older one.
    fun select(start: TextEdge, stop: TextEdge) {
        latest.job?.cancel()
        latest.job = scope.launch {
            val result = try {
                document.selectText(index, start, stop)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // e.g. the document was closed meanwhile, or an end from another page
            }
            if (result != null) onSelectionChange(SelectedText(index, result))
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { pageSize = it }
            .then(
                if (document.supportsTextSelection) {
                    Modifier.pointerInput(document, index) {
                        var anchor = Offset.Zero
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                anchor = offset
                                holding = true
                                onSelectionChange(null)
                                select(TextEdge.At(norm(offset)), TextEdge.At(norm(offset)))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                select(TextEdge.At(norm(anchor)), TextEdge.At(norm(change.position)))
                            },
                            onDragEnd = { holding = false },
                            onDragCancel = { holding = false },
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
        if (selection != null && pageSize.width > 0 && pageSize.height > 0) {
            SelectionOverlay(
                selection = selection,
                pageSize = pageSize,
                showMenu = !holding,
                onEdges = { start, stop -> select(start, stop) },
                onSelectAll = { select(TextEdge.At(NormPoint(0f, 0f)), TextEdge.At(NormPoint(1f, 1f))) },
                onShare = { ShareFiles.shareText(context, selection.text) },
                onCopy = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("PDF text", selection.text))
                    onSelectionChange(null)
                },
            )
        }
    }
}

private val HandleSize = 44.dp
private val HandleRadius = 9.dp
private val HandleColor = Color(0xFF3390FF)

/** The highlight, the two handles and the floating menu for [selection], drawn over one page. */
@Composable
private fun BoxScope.SelectionOverlay(
    selection: PageText,
    pageSize: IntSize,
    showMenu: Boolean,
    onEdges: (TextEdge, TextEdge) -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
) {
    val density = LocalDensity.current
    val widthPx = pageSize.width.toFloat()
    val heightPx = pageSize.height.toFloat()
    fun px(p: NormPoint) = Offset(p.x * widthPx, p.y * heightPx)
    fun norm(o: Offset) = NormPoint(PageGeometry.fraction(o.x, pageSize.width), PageGeometry.fraction(o.y, pageSize.height))

    Canvas(Modifier.matchParentSize()) {
        for (r in selection.bounds) {
            drawRect(
                color = Color(0x553390FF),
                topLeft = Offset(r.left * size.width, r.top * size.height),
                size = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height),
            )
        }
    }

    var dragging by remember { mutableStateOf(false) }
    // The end that stays where it is while the other one is dragged.
    val fixed = remember { arrayOfNulls<TextEdge>(1) }

    SelectionLayout.startTip(selection.bounds)?.let { tip ->
        SelectionHandle(
            tip = px(tip),
            onDragStart = {
                fixed[0] = selection.stop
                dragging = true
            },
            onDrag = { target -> fixed[0]?.let { onEdges(TextEdge.At(norm(target)), it) } },
            onDragEnd = { dragging = false },
        )
    }
    SelectionLayout.endTip(selection.bounds)?.let { tip ->
        SelectionHandle(
            tip = px(tip),
            onDragStart = {
                fixed[0] = selection.start
                dragging = true
            },
            onDrag = { target -> fixed[0]?.let { onEdges(it, TextEdge.At(norm(target))) } },
            onDragEnd = { dragging = false },
        )
    }

    if (showMenu && !dragging) {
        val top = SelectionLayout.top(selection.bounds)
        val bottom = SelectionLayout.bottom(selection.bounds)
        val centerX = SelectionLayout.centerX(selection.bounds)
        if (top != null && bottom != null && centerX != null) {
            var menuSize by remember { mutableStateOf(IntSize.Zero) }
            val position = SelectionLayout.menuPosition(
                selectionTop = top * heightPx,
                selectionBottom = bottom * heightPx,
                selectionCenterX = centerX * widthPx,
                menuWidth = menuSize.width.toFloat(),
                menuHeight = menuSize.height.toFloat(),
                pageWidth = widthPx,
                pageHeight = heightPx,
                gap = with(density) { 8.dp.toPx() },
                handleHeight = with(density) { HandleSize.toPx() },
            )
            SelectionMenu(
                onSelectAll = onSelectAll,
                onShare = onShare,
                onCopy = onCopy,
                modifier = Modifier
                    // Invisible for the first frame, until its size is known and it can be placed.
                    .alpha(if (menuSize == IntSize.Zero) 0f else 1f)
                    .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                    .onSizeChanged { menuSize = it },
            )
        }
    }
}

/** The small floating menu above the selection: Select all, Share and Copy. */
@Composable
private fun SelectionMenu(
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row {
            TextButton(onClick = onSelectAll) { Text("Select all") }
            TextButton(onClick = onShare) { Text("Share") }
            TextButton(onClick = onCopy) { Text("Copy") }
        }
    }
}

/**
 * A draggable knob under one end of the selection. [tip] is where the text line it belongs to
 * ends, in the page's pixels. While it is dragged, [onDrag] gets where the tip should now be.
 *
 * The handle moves as the selection changes, so the finger's position is measured against the
 * handle itself: where the finger is now, relative to where it first touched, added to the tip.
 */
@Composable
private fun SelectionHandle(
    tip: Offset,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val currentTip by rememberUpdatedState(tip)
    val currentStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentEnd by rememberUpdatedState(onDragEnd)
    val sizePx = with(LocalDensity.current) { HandleSize.toPx() }

    Box(
        Modifier
            .offset { IntOffset((tip.x - sizePx / 2f).roundToInt(), tip.y.roundToInt()) }
            .size(HandleSize)
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Take the touch for ourselves, so it neither scrolls nor clears the selection.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val grab = down.position
                    currentStart()
                    drag(down.id) { change ->
                        change.consume()
                        currentDrag(currentTip + (change.position - grab))
                    }
                    currentEnd()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = HandleRadius.toPx()
            val centerX = size.width / 2f
            val knobY = radius + 2.dp.toPx()
            drawLine(HandleColor, Offset(centerX, 0f), Offset(centerX, knobY), strokeWidth = 2.dp.toPx())
            drawCircle(HandleColor, radius, Offset(centerX, knobY))
        }
    }
}

/** Holds the selection lookup in progress, so a newer drag position replaces an older one. */
private class LatestJob {
    var job: Job? = null
}
