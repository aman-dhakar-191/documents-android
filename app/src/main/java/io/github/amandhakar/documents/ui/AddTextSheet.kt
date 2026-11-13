package io.github.amandhakar.documents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.amandhakar.documents.edit.FontStyleChoice
import io.github.amandhakar.documents.edit.PdfText
import io.github.amandhakar.documents.edit.TextBoxMath

/** Type the text, choose whether it copies the text nearby, then drag it onto a page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTextSheet(onAdd: (text: String, matchNearby: Boolean, font: FontStyleChoice) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var matchNearby by remember { mutableStateOf(true) }
    var font by remember { mutableStateOf(FontStyleChoice()) }
    val unusable = PdfText.unsupported(TextBoxMath.prepare(typed))
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.imePadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Add text", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text("Text") },
                isError = unusable.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        if (unusable.isEmpty()) {
                            "Then drag it onto a page and resize it."
                        } else {
                            "Can't use ${unusable.joinToString(" ")} yet: only Latin letters, digits and common symbols."
                        },
                    )
                },
            )
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Match the text nearby", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Copies the font, size and colour of the closest text where you drop it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = matchNearby, onCheckedChange = { matchNearby = it })
                }
            }
            if (!matchNearby) {
                FontPicker(font) { font = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height52()) { Text("Cancel") }
                Button(
                    onClick = { onAdd(typed, matchNearby, font) },
                    enabled = unusable.isEmpty() && TextBoxMath.prepare(typed).isNotEmpty(),
                    modifier = Modifier.weight(2f).height52(),
                ) { Text("Add") }
            }
        }
    }
}

private fun Modifier.height52(): Modifier = this.then(Modifier.height(52.dp))
