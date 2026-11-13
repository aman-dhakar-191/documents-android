package io.github.amandhakar.documents.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** The licence notice for the fonts the Word page view ships with, read from the app's assets. */
@Composable
fun FontLicencesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        try {
            context.assets.open("licenses/fonts.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "The font licence text could not be read. The fonts (Carlito, Caladea and Liberation) are under the SIL Open Font License 1.1."
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font licences") },
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
