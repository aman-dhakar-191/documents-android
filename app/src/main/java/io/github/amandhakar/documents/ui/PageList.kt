package io.github.amandhakar.documents.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.amandhakar.documents.document.Document
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** Every page of [document], top to bottom. At zoom above 1 the pages are wider than the screen and scroll sideways. */
@Composable
fun PageList(document: Document, zoom: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFE0E0E0)).clipToBounds()) {
        val contentWidth = maxWidth * zoom
        val widthPx = with(LocalDensity.current) { contentWidth.roundToPx() }
        val horizontal = rememberScrollState()
        LazyColumn(
            modifier = Modifier.horizontalScroll(horizontal).width(contentWidth),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(count = document.pageCount, key = { it }) { index ->
                PageImage(document, index, widthPx)
            }
        }
    }
}

@Composable
private fun PageImage(document: Document, index: Int, widthPx: Int) {
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
    } else {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "Page ${index + 1} of ${document.pageCount}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}
