package io.github.amandhakar.documents.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.amandhakar.documents.document.Document
import io.github.amandhakar.documents.edit.PageHit
import io.github.amandhakar.documents.edit.PageRow
import io.github.amandhakar.documents.edit.PlacedSignature
import io.github.amandhakar.documents.edit.Placement
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
    listState: LazyListState = rememberLazyListState(),
    editing: EditingUi? = null,
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
                state = listState,
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
                        editing = editing,
                    )
                }
            }
        }
        // The signature being positioned floats above the pages, so the pages can be scrolled under it;
        // it lands on whichever page it is over when the tick is pressed.
        val placing = editing?.placing
        if (editing != null && placing != null) {
            val density = LocalDensity.current
            val viewportW = with(density) { maxWidth.toPx() }
            val viewportH = with(density) { maxHeight.toPx() }
            FloatingSignature(editing, placing, viewportW, viewportH) {
                val info = listState.layoutInfo
                val rows = info.visibleItemsInfo.map { PageRow(it.index, it.offset, it.size) }
                val widthOnScreen = placing.width * viewportW
                val hit = PageHit.resolve(
                    rows = rows,
                    left = placing.left * viewportW,
                    top = placing.top * viewportH,
                    width = widthOnScreen,
                    height = widthOnScreen / editing.aspect,
                    scrollX = horizontal.value.toFloat(),
                    pageWidth = widthPx.toFloat(),
                    signatureAspect = editing.aspect,
                )
                if (hit != null) editing.onPlace(hit) else editing.onMiss()
            }
        }
    }
}

/** Text selected on page [page] (0-based). */
private data class SelectedText(val page: Int, val text: PageText)

/** A request for the text between two ends of a selection. */
private class SelectRequest(val start: TextEdge, val stop: TextEdge)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageImage(
    document: Document,
    index: Int,
    widthPx: Int,
    selection: PageText?,
    onSelectionChange: (SelectedText?) -> Unit,
    editing: EditingUi?,
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
    // Lookups wait in a queue that keeps only the newest, so a fast drag can't build up a backlog.
    val requests = remember { LatestRequests<SelectRequest>() }
    LaunchedEffect(document, index, requests) {
        requests.process { request ->
            val result = try {
                document.selectText(index, request.start, request.stop)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // e.g. the document was closed meanwhile, or an end from another page
            }
            if (result != null) onSelectionChange(SelectedText(index, result))
        }
    }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    // True while a finger is still down from the long press: the menu waits until it lifts.
    var holding by remember { mutableStateOf(false) }
    // The spot (in this page's pixels) the magnifier bubble shows, or Unspecified for no bubble.
    var loupe by remember { mutableStateOf(Offset.Unspecified) }

    fun norm(p: Offset) = NormPoint(PageGeometry.fraction(p.x, pageSize.width), PageGeometry.fraction(p.y, pageSize.height))

    // Asks for the text between two ends. A newer request replaces one still waiting.
    fun select(start: TextEdge, stop: TextEdge) {
        requests.submit(SelectRequest(start, stop))
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { pageSize = it }
            // A magnifier bubble above the finger while selecting, so the edge of the selection can be seen.
            .magnifier(
                sourceCenter = { loupe },
                magnifierCenter = {
                    if (loupe.isSpecified) Offset(loupe.x, loupe.y - 80.dp.toPx()) else Offset.Unspecified
                },
                zoom = 1.6f,
                size = DpSize(128.dp, 56.dp),
                cornerRadius = 14.dp,
                elevation = 4.dp,
            )
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
                                loupe = change.position
                                select(TextEdge.At(norm(anchor)), TextEdge.At(norm(change.position)))
                            },
                            onDragEnd = {
                                holding = false
                                loupe = Offset.Unspecified
                            },
                            onDragCancel = {
                                holding = false
                                loupe = Offset.Unspecified
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
        if (editing != null && pageSize.width > 0 && pageSize.height > 0) {
            val pageAspect = pageSize.width.toFloat() / pageSize.height
            for (placed in editing.placed) {
                if (placed.page == index) SignatureOnPage(editing, placed, pageSize, pageAspect)
            }
        }
        if (selection != null && pageSize.width > 0 && pageSize.height > 0) {
            SelectionOverlay(
                selection = selection,
                pageSize = pageSize,
                showMenu = !holding,
                onEdges = { start, stop -> select(start, stop) },
                onSelectAll = { select(TextEdge.At(NormPoint(0f, 0f)), TextEdge.At(NormPoint(1f, 1f))) },
                onShare = { ShareFiles.shareText(context, selection.text) },
                onLoupe = { loupe = it },
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

/** Which handle is being dragged. */
private enum class HandleSide { Start, End }

/**
 * The highlight, the two handles and the floating menu for [selection], drawn over one page.
 * While a handle is dragged, [onLoupe] is told where to show the magnifier (and told
 * [Offset.Unspecified] when it should go away).
 */
@Composable
private fun BoxScope.SelectionOverlay(
    selection: PageText,
    pageSize: IntSize,
    showMenu: Boolean,
    onEdges: (TextEdge, TextEdge) -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onLoupe: (Offset) -> Unit,
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

    var active by remember { mutableStateOf<HandleSide?>(null) }
    // Where the dragged handle is under the finger right now. The selection itself catches up a moment
    // later (looking up text takes time), so the handle and the magnifier follow this instead of it.
    var dragTarget by remember { mutableStateOf<Offset?>(null) }
    // The end that stays where it is while the other one is dragged.
    val fixed = remember { arrayOfNulls<TextEdge>(1) }

    SelectionLayout.startTip(selection.bounds)?.let { tip ->
        val half = (SelectionLayout.startLineHeight(selection.bounds) ?: 0f) * heightPx / 2f
        SelectionHandle(
            tip = if (active == HandleSide.Start) dragTarget ?: px(tip) else px(tip),
            onDragStart = {
                fixed[0] = selection.stop
                active = HandleSide.Start
                dragTarget = px(tip)
            },
            onDrag = { target ->
                dragTarget = target
                onLoupe(Offset(target.x, target.y - half)) // the middle of the line the handle is under
                fixed[0]?.let { onEdges(TextEdge.At(norm(target)), it) }
            },
            onDragEnd = {
                active = null
                dragTarget = null
                onLoupe(Offset.Unspecified)
            },
        )
    }
    SelectionLayout.endTip(selection.bounds)?.let { tip ->
        val half = (SelectionLayout.endLineHeight(selection.bounds) ?: 0f) * heightPx / 2f
        SelectionHandle(
            tip = if (active == HandleSide.End) dragTarget ?: px(tip) else px(tip),
            onDragStart = {
                fixed[0] = selection.start
                active = HandleSide.End
                dragTarget = px(tip)
            },
            onDrag = { target ->
                dragTarget = target
                onLoupe(Offset(target.x, target.y - half))
                fixed[0]?.let { onEdges(it, TextEdge.At(norm(target))) }
            },
            onDragEnd = {
                active = null
                dragTarget = null
                onLoupe(Offset.Unspecified)
            },
        )
    }

    val currentOnLoupe by rememberUpdatedState(onLoupe)
    DisposableEffect(Unit) {
        onDispose { currentOnLoupe(Offset.Unspecified) }
    }

    if (showMenu && active == null) {
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

/**
 * What the page list needs to draw signatures: the picture, those already placed on pages, and the one
 * being positioned ([placing], given as fractions of the visible area, not of a page).
 */
class EditingUi(
    val signature: ImageBitmap,
    val aspect: Float,
    val placed: List<PlacedSignature>,
    val placing: PlacedSignature?,
    val onPlacingChange: (PlacedSignature) -> Unit,
    /** The tick: the signature was dropped on this page spot. */
    val onPlace: (PlacedSignature) -> Unit,
    /** The tick was pressed while the signature was not over any page. */
    val onMiss: () -> Unit,
    val onCancel: () -> Unit,
    val onRedraw: () -> Unit,
)

private val SignatureFrame = Color(0xFF3390FF)

/** A signature already placed on a page, drawn where it will be saved. */
@Composable
private fun BoxScope.SignatureOnPage(editing: EditingUi, placed: PlacedSignature, pageSize: IntSize, pageAspect: Float) {
    val density = LocalDensity.current
    val height = Placement.height(placed.width, editing.aspect, pageAspect)
    Image(
        bitmap = editing.signature,
        contentDescription = "Your signature",
        modifier = Modifier
            .offset { IntOffset((placed.left * pageSize.width).roundToInt(), (placed.top * pageSize.height).roundToInt()) }
            .size(
                with(density) { (placed.width * pageSize.width).toDp() },
                with(density) { (height * pageSize.height).toDp() },
            ),
        contentScale = ContentScale.FillBounds,
    )
}

/**
 * The signature being positioned, above the pages: drag it to move it, drag the round handle in its corner to
 * resize it, and scroll the pages underneath with any other finger-drag. A small bar offers draw again, cancel
 * and tick ([onConfirm]).
 */
@Composable
private fun BoxScope.FloatingSignature(
    editing: EditingUi,
    placing: PlacedSignature,
    viewportW: Float,
    viewportH: Float,
    onConfirm: () -> Unit,
) {
    val density = LocalDensity.current
    val current by rememberUpdatedState(placing)
    val viewportAspect = viewportW / viewportH
    val widthPx = placing.width * viewportW
    val heightPx = widthPx / editing.aspect
    val leftPx = placing.left * viewportW
    val topPx = placing.top * viewportH

    Box(
        Modifier
            .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
            .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
            .border(2.dp, SignatureFrame)
            .pointerInput(viewportW, viewportH, editing.aspect) {
                detectDragGestures { change, drag ->
                    change.consume()
                    editing.onPlacingChange(
                        Placement.moved(current, drag.x / viewportW, drag.y / viewportH, editing.aspect, viewportAspect),
                    )
                }
            },
    ) {
        Image(
            bitmap = editing.signature,
            contentDescription = "Your signature",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .background(SignatureFrame, CircleShape)
                .pointerInput(viewportW, viewportH, editing.aspect) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        editing.onPlacingChange(Placement.resized(current, drag.x / viewportW, editing.aspect, viewportAspect))
                    }
                },
        )
    }

    // The bar goes under the signature, or above it when there is no room below.
    val barW = with(density) { (3 * 48).dp.toPx() }
    val barH = with(density) { 48.dp.toPx() }
    val gap = with(density) { 8.dp.toPx() }
    val barLeft = leftPx.coerceIn(0f, (viewportW - barW).coerceAtLeast(0f))
    val below = topPx + heightPx + gap
    val barTop = if (below + barH <= viewportH) below else (topPx - barH - gap).coerceAtLeast(0f)
    Surface(
        modifier = Modifier.offset { IntOffset(barLeft.roundToInt(), barTop.roundToInt()) },
        shape = RoundedCornerShape(50),
        shadowElevation = 6.dp,
        tonalElevation = 3.dp,
    ) {
        Row {
            IconButton(onClick = editing.onRedraw) { Icon(DocIcons.Edit, contentDescription = "Change the signature") }
            IconButton(onClick = editing.onCancel) { Icon(DocIcons.Close, contentDescription = "Cancel") }
            IconButton(onClick = onConfirm) { Icon(DocIcons.Check, contentDescription = "Place signature here") }
        }
    }
}
