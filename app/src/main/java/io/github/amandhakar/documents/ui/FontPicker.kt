package io.github.amandhakar.documents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.amandhakar.documents.edit.FontFamilyChoice
import io.github.amandhakar.documents.edit.FontStyleChoice

/** The words for each font family choice, in the order the drop-down lists them. */
internal val FontFamilies = listOf(
    FontFamilyChoice.Sans to "Sans",
    FontFamilyChoice.Serif to "Serif",
    FontFamilyChoice.Mono to "Mono",
)

/**
 * The font choice for text put on a PDF: a drop-down for the family (Sans, Serif, Mono) with Bold and Italic toggles
 * beside it, so all of it fits on one line whatever the width of the dialog.
 */
@Composable
fun FontPicker(font: FontStyleChoice, onChange: (FontStyleChoice) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = FontFamilies.firstOrNull { it.first == font.family }?.second ?: "Sans"
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            OutlinedButton(onClick = { open = true }, contentPadding = PaddingValues(start = 16.dp, end = 8.dp)) {
                Text(current)
                Box(Modifier.width(4.dp))
                Icon(DocIcons.ArrowDown, contentDescription = "Choose a font, now $current", modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for ((family, label) in FontFamilies) {
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = { if (family == font.family) Icon(DocIcons.Check, contentDescription = "Selected") },
                        onClick = {
                            open = false
                            onChange(font.copy(family = family))
                        },
                    )
                }
            }
        }
        ChoiceButton("B", font.bold) { onChange(font.copy(bold = !font.bold)) }
        ChoiceButton("I", font.italic) { onChange(font.copy(italic = !font.italic)) }
    }
}
