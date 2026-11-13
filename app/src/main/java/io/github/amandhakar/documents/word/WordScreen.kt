package io.github.amandhakar.documents.word

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PageInk = Color(0xFF1B1D22)
private val PageLine = Color(0xFFBFC3CA)

/** A Word document as a reading view: the text reflowed to the screen on a white page. Not Word's own page layout. */
@Composable
fun WordScreen(document: DocxDocument, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(Color.White),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
    ) {
        items(document.blocks) { block -> BlockView(block) }
        if (document.truncated) {
            item {
                Text(
                    "This document is longer than the app shows. The rest was left out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5A606C),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun BlockView(block: DocxBlock) {
    when (block) {
        is DocxParagraph -> ParagraphView(block)
        is DocxTable -> TableView(block)
        is DocxImage -> ImageView(block)
        is DocxColumns -> Unit // the reading view is one column
    }
}

@Composable
private fun ParagraphView(p: DocxParagraph) {
    val onEdit = LocalWordEdit.current
    if (p.text.isBlank() && p.list == null) {
        Spacer(Modifier.height(12.dp))
        return
    }
    val text = remember(p) { styled(p) }
    val align = when (p.align) {
        DocxAlign.Center -> TextAlign.Center
        DocxAlign.End -> TextAlign.End
        DocxAlign.Justify -> TextAlign.Justify
        DocxAlign.Start -> TextAlign.Start
    }
    val size = if (p.headingLevel > 0) WordStyle.headingSizeSp(p.headingLevel) else WordStyle.BODY_SP
    val top = if (p.headingLevel > 0) 14.dp else 2.dp
    val inner: @Composable () -> Unit = {
        Text(
            text,
            color = PageInk,
            fontSize = size.sp,
            lineHeight = (size * 1.4f).sp,
            fontWeight = if (p.headingLevel > 0) FontWeight.Bold else null,
            textAlign = align,
            modifier = Modifier.fillMaxWidth().editable(p, onEdit).padding(top = top, bottom = 2.dp),
        )
    }
    // Selecting text and tapping to edit would fight over the touch, so selection is off while editing.
    val content: @Composable () -> Unit = if (canEdit(p, onEdit)) inner else {
        { SelectionContainer { inner() } }
    }
    val list = p.list
    if (list == null) {
        content()
    } else {
        Row(Modifier.padding(start = (list.level * 22).dp)) {
            Text(list.label, color = PageInk, fontSize = WordStyle.BODY_SP.sp, modifier = Modifier.width(28.dp).padding(top = 2.dp))
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

private fun styled(p: DocxParagraph): AnnotatedString = buildAnnotatedString {
    for (run in p.runs) {
        val decorations = buildList {
            if (run.underline) add(TextDecoration.Underline)
            if (run.strike) add(TextDecoration.LineThrough)
        }
        val color = WordStyle.readableColor(run.colorHex)?.let { Color(("FF" + it).toLong(16)) } ?: Color.Unspecified
        withStyle(
            SpanStyle(
                fontWeight = if (run.bold) FontWeight.Bold else null,
                fontStyle = if (run.italic) FontStyle.Italic else null,
                textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
                // A heading's size comes from its level; other runs may carry their own size.
                fontSize = if (p.headingLevel == 0 && run.sizeHalfPoints != null) WordStyle.runSizeSp(run.sizeHalfPoints).sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                color = color,
            ),
        ) { append(run.text) }
    }
}

@Composable
private fun TableView(table: DocxTable) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).border(1.dp, PageLine)) {
        for (row in table.rows) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                for (cell in row) {
                    Column(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, PageLine).padding(6.dp)) {
                        for (block in cell) BlockView(block)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageView(image: DocxImage) {
    val bitmap by produceState<Bitmap?>(null, image) {
        value = withContext(Dispatchers.Default) { DocxImages.decode(image.bytes) }
    }
    bitmap?.let {
        Image(
            it.asImageBitmap(),
            contentDescription = "Picture in the document",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(vertical = 6.dp),
        )
    }
}

/** Decoding a picture from a document, shrunk so a huge one can't use all the memory. */
internal object DocxImages {
    private const val MAX_SIDE = 1600
    private const val MAX_PIXELS = 50_000_000L

    fun decode(bytes: ByteArray): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS) {
            null
        } else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}
