package io.github.amandhakar.documents.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.amandhakar.documents.document.TextSyntax
import io.github.amandhakar.documents.sheets.CsvTable
import io.github.amandhakar.documents.sheets.Workbook
import io.github.amandhakar.documents.text.SyntaxHighlight
import io.github.amandhakar.documents.text.TextChecks
import io.github.amandhakar.documents.text.TokenKind
import io.github.amandhakar.documents.word.DocxDocument
import io.github.amandhakar.documents.word.WordScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val CUT_NOTICE = "This file is longer than the app reads (4 MB). The rest was left out."

/**
 * A Markdown or other text file. Markdown is drawn as a document (headings, lists, tables, code, links) unless
 * [showSource] is on; CSV / TSV can be shown as a table ([table], when [showTable]); everything else is the text as
 * written, with line numbers and colours for JSON, YAML, XML, INI / TOML and logs. While [editing], the text is an editor
 * whose changes go to [onDraft]; [draft] (once there is one) is shown in place of the file's text.
 */
@Composable
fun TextFileScreen(
    source: String,
    document: DocxDocument?,
    truncated: Boolean,
    showSource: Boolean,
    syntax: TextSyntax = TextSyntax.Plain,
    table: Workbook? = null,
    showTable: Boolean = false,
    editing: Boolean = false,
    draft: String? = null,
    onDraft: (String) -> Unit = {},
) {
    val text = draft ?: source
    if (editing) {
        Editor(text, syntax, onDraft)
        return
    }
    if (!showSource && document != null && draft == null) {
        Column(Modifier.fillMaxSize()) {
            if (truncated) Notice(CUT_NOTICE)
            Box(Modifier.weight(1f)) { WordScreen(document) }
        }
        return
    }
    if (showTable && table != null) {
        var sheet by remember { mutableIntStateOf(0) }
        var selected by remember(text) { mutableStateOf<Pair<Int, Int>?>(null) }
        // After editing, the table shows the edited text.
        val shown = remember(text, table) {
            if (draft == null) table else CsvTable.parse(text, syntax.delimiter, table.sheets.firstOrNull()?.name ?: "Sheet")
        }
        SheetScreen(shown, sheet, { sheet = it }, selected, { selected = it }, onEdit = null, notice = if (truncated && draft == null) CUT_NOTICE else null)
        return
    }
    Lines(text, syntax, truncated && draft == null)
}

@Composable
private fun Lines(text: String, syntax: TextSyntax, truncated: Boolean) {
    val lines = remember(text) { text.split('\n') }
    val states = remember(lines, syntax) { SyntaxHighlight.lineStates(lines, syntax) }
    val colors = syntaxColors()
    val gutter = remember(lines.size) { lines.size.toString().length * 8 + 12 }
    Column(Modifier.fillMaxSize()) {
        if (truncated) Notice(CUT_NOTICE)
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface),
                contentPadding = PaddingValues(end = 16.dp, top = 16.dp, bottom = 16.dp),
            ) {
                items(lines.size) { i ->
                    Row {
                        // Line numbers help find the line a check points to; they aren't selected with the text.
                        DisableSelection {
                            Text(
                                (i + 1).toString(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.End,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.width(gutter.dp).padding(end = 8.dp),
                            )
                        }
                        Text(
                            highlighted(lines[i].ifEmpty { " " }, syntax, states[i], colors),
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
}

/** The text as an editor: plain monospace text, no spelling fixes, and a line above it naming the first problem found. */
@Composable
private fun Editor(text: String, syntax: TextSyntax, onDraft: (String) -> Unit) {
    // Checking a long file takes a moment, so it waits until typing pauses and runs off the main thread.
    val problem by produceState<String?>(null, text, syntax) {
        delay(600)
        value = withContext(Dispatchers.Default) { TextChecks.check(text, syntax)?.toString() }
    }
    Column(Modifier.fillMaxSize()) {
        val status = problem ?: TextChecks.okLabel(syntax)
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = onDraft,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private class SyntaxColors(val map: Map<TokenKind, SpanStyle>)

@Composable
private fun syntaxColors(): SyntaxColors {
    val dark = isSystemInDarkTheme()
    val error = MaterialTheme.colorScheme.error
    return remember(dark, error) {
        fun c(light: Long, night: Long) = Color(if (dark) night else light)
        SyntaxColors(
            mapOf(
                TokenKind.Key to SpanStyle(color = c(0xFF1F5FA8, 0xFF8AB4F8)),
                TokenKind.Str to SpanStyle(color = c(0xFF1B7F3B, 0xFF81C995)),
                TokenKind.Num to SpanStyle(color = c(0xFF9A4C00, 0xFFFCAD70)),
                TokenKind.Literal to SpanStyle(color = c(0xFF8E24AA, 0xFFD7AEFB)),
                TokenKind.Comment to SpanStyle(color = c(0xFF6F6F6F, 0xFF9AA0A6), fontStyle = FontStyle.Italic),
                TokenKind.Punct to SpanStyle(color = c(0xFF5F6368, 0xFFBDC1C6)),
                TokenKind.Tag to SpanStyle(color = c(0xFF1F5FA8, 0xFF8AB4F8)),
                TokenKind.Attr to SpanStyle(color = c(0xFF9A4C00, 0xFFFCAD70)),
                TokenKind.Section to SpanStyle(color = c(0xFF8E24AA, 0xFFD7AEFB), fontWeight = FontWeight.Bold),
                TokenKind.Error to SpanStyle(color = error, fontWeight = FontWeight.Bold),
                TokenKind.Warn to SpanStyle(color = c(0xFF9A6700, 0xFFFDD663), fontWeight = FontWeight.Bold),
            ),
        )
    }
}

private fun highlighted(line: String, syntax: TextSyntax, inComment: Boolean, colors: SyntaxColors): AnnotatedString {
    val tokens = SyntaxHighlight.tokens(line, syntax, inComment)
    if (tokens.isEmpty()) return AnnotatedString(line)
    return buildAnnotatedString {
        append(line)
        for (t in tokens) {
            val style = colors.map[t.kind] ?: continue
            if (t.start < t.end && t.end <= line.length) addStyle(style, t.start, t.end)
        }
    }
}
