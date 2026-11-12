package io.github.amandhakar.documents.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import io.github.amandhakar.documents.convert.PdfConverter

/** Asks how to turn a PDF into Word or PowerPoint: editable text (a best guess at the layout) or a picture of each page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertSheet(target: PdfConverter.Target, onPick: (PdfConverter.Mode) -> Unit, onDismiss: () -> Unit) {
    val name = if (target == PdfConverter.Target.Word) "Word" else "PowerPoint"
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text("Convert to $name", style = MaterialTheme.typography.titleLarge)
            Text("Choose how the pages come across.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { onPick(PdfConverter.Mode.Text) },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Editable text", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (target == PdfConverter.Target.Word) {
                            "Paragraphs, headings and bullets you can edit. The layout is a best guess; tables and pictures are left out."
                        } else {
                            "Each paragraph as a text box where it was. The layout is a best guess; tables and pictures are left out."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            OutlinedButton(
                onClick = { onPick(PdfConverter.Mode.Pictures) },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Page pictures", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Each page as a picture. Looks exactly like the PDF, but you can't edit the text. Works for scans.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel") }
        }
    }
}
