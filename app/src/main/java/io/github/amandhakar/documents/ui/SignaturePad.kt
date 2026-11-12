package io.github.amandhakar.documents.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.amandhakar.documents.edit.InkPoint
import io.github.amandhakar.documents.edit.SignatureInk
import io.github.amandhakar.documents.edit.SignatureStore
import io.github.amandhakar.documents.edit.Stroke

/** A full-screen box to draw a signature with a finger. Done keeps it; the picture is cropped to the ink. */
@Composable
fun SignaturePadDialog(onDone: (Bitmap) -> Unit, onCancel: () -> Unit) {
    var strokes by remember { mutableStateOf(listOf<Stroke>()) }
    var current by remember { mutableStateOf(listOf<InkPoint>()) }
    val ink = Color(0xFF1A237E)
    val penPx = with(LocalDensity.current) { 6.dp.toPx() }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) { Icon(DocIcons.Close, contentDescription = "Cancel") }
                    Text("Draw your signature", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { strokes = emptyList(); current = emptyList() }, enabled = strokes.isNotEmpty()) {
                        Text("Clear")
                    }
                    IconButton(
                        onClick = { SignatureStore.render(strokes, penPx)?.let(onDone) },
                        enabled = !SignatureInk.isBlank(strokes),
                    ) { Icon(DocIcons.Check, contentDescription = "Use this signature") }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(Color.White, RoundedCornerShape(16.dp)),
                ) {
                    if (strokes.isEmpty() && current.isEmpty()) {
                        Text(
                            "Sign here",
                            color = Color(0xFF9E9E9E),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { at -> current = listOf(InkPoint(at.x, at.y)) },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        current = current + InkPoint(change.position.x, change.position.y)
                                    },
                                    onDragEnd = {
                                        if (current.isNotEmpty()) strokes = strokes + Stroke(current)
                                        current = emptyList()
                                    },
                                    onDragCancel = { current = emptyList() },
                                )
                            },
                    ) {
                        val style = DrawStroke(width = penPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        for (points in strokes.map { it.points } + listOf(current)) {
                            if (points.size < 2) continue
                            val path = Path().apply {
                                moveTo(points[0].x, points[0].y)
                                for (p in points.drop(1)) lineTo(p.x, p.y)
                            }
                            drawPath(path, ink, style = style)
                        }
                    }
                }
                Text(
                    "Your signature is saved on this phone only, so you can reuse it.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}
