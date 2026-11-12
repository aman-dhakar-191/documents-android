package io.github.amandhakar.documents.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** The top of the library: the title with Search and Browse, or the search box while searching. */
@Composable
fun LibraryHeader(
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onBrowse: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(64.dp)
            .padding(start = if (searching) 12.dp else 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            val focus = FocusRequester()
            LaunchedEffect(Unit) { focus.requestFocus() }
            TextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text("Search files") },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            IconButton(onClick = onCloseSearch) { Icon(DocIcons.Close, contentDescription = "Close search") }
        } else {
            Text(
                "Documents",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSearch) { Icon(DocIcons.Search, contentDescription = "Search files") }
            IconButton(onClick = onBrowse) { Icon(DocIcons.FolderOpen, contentDescription = "Browse for a file") }
        }
    }
}
