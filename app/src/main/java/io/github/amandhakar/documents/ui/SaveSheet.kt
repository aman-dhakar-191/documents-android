package io.github.amandhakar.documents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * What to do with the changes: save a new copy (the main choice) or replace the original. [showErase]
 * is true when there are text changes, which can also try to remove the old text from the file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSheet(
    summary: String,
    showErase: Boolean,
    eraseOld: Boolean,
    onEraseChange: (Boolean) -> Unit,
    onSaveCopy: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text("Save your changes", style = MaterialTheme.typography.titleLarge)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Button(
                onClick = onSaveCopy,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Save as a new copy", style = MaterialTheme.typography.titleMedium)
                    Text("Keeps the original. You choose where it goes.", style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(
                onClick = onReplace,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Replace the original", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Can't be undone. The new file is checked first, and the original is put back if saving fails.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showErase) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = eraseOld, onCheckedChange = onEraseChange)
                    Column(Modifier.padding(top = 12.dp)) {
                        Text("Also remove the old text from the file", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Experimental. Only for text you changed. Without this the old text is covered, not erased.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
        }
    }
}
