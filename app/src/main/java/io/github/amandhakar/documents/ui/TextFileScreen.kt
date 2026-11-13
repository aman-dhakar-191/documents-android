package io.github.amandhakar.documents.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amandhakar.documents.word.DocxDocument
import io.github.amandhakar.documents.word.WordScreen

private const val CUT_NOTICE = "This file is longer than the app reads (4 MB). The rest was left out."

/**
 * A Markdown or plain text file: the Markdown drawn as a document (headings, lists, tables, code, links), or the text
 * as written when [showSource] is on or the file isn't Markdown.
 */
@Composable
fun TextFileScreen(source: String, document: DocxDocument?, truncated: Boolean, showSource: Boolean) {
    if (!showSource && document != null) {
        Column(Modifier.fillMaxSize()) {
            if (truncated) Notice()
            Box(Modifier.weight(1f)) { WordScreen(document) }
        }
        return
    }
    val lines = remember(source) { source.split('\n') }
    Column(Modifier.fillMaxSize()) {
        if (truncated) Notice()
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            ) {
                items(lines) { line ->
                    Text(
                        line.ifEmpty { " " },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun Notice() {
    Text(
        CUT_NOTICE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
