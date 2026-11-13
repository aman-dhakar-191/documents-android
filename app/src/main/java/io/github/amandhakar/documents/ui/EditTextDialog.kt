package io.github.amandhakar.documents.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp

/**
 * Change the text of a paragraph: a box with its text, and Apply. For Word there are also Delete (the paragraph goes) and
 * Add below (a new paragraph after it); for PowerPoint, clearing the text and applying leaves the paragraph empty.
 */
@Composable
fun EditTextDialog(
    title: String,
    initial: String,
    onApply: (String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onAddBelow: ((String) -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial) }
    var adding by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (adding) "New paragraph below" else title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (adding) "Text" else "Paragraph") },
            )
        },
        confirmButton = {
            Button(onClick = { if (adding) onAddBelow?.invoke(text) else onApply(text) }) { Text(if (adding) "Add" else "Apply") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!adding && onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                if (!adding && onAddBelow != null) TextButton(onClick = { adding = true; text = "" }) { Text("Add below") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
