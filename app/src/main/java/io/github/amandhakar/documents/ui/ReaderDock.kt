package io.github.amandhakar.documents.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.amandhakar.documents.edit.ReaderTool

/** The icon that stands for a tool in the dock. */
private fun iconOf(tool: ReaderTool): ImageVector = when (tool) {
    ReaderTool.Sign -> DocIcons.Edit
    ReaderTool.AddText -> DocIcons.TextFields
    ReaderTool.EditText -> DocIcons.EditText
    ReaderTool.Highlight -> DocIcons.Highlight
    ReaderTool.Draw -> DocIcons.Brush
}

/** The floating bar of editing tools under the page: icon and word for each, the switched-on one tinted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderDock(active: ReaderTool?, enabled: Boolean, onPick: (ReaderTool) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().height(72.dp),
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (tool in ReaderTool.entries) {
                val on = tool == active
                val bg = if (on) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
                val fg = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                Surface(
                    color = bg,
                    contentColor = fg,
                    shape = RoundedCornerShape(30.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().semantics { selected = on },
                    onClick = { onPick(tool) },
                    enabled = enabled,
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    ) {
                        Icon(iconOf(tool), contentDescription = null, modifier = Modifier.size(24.dp))
                        Text(tool.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}
