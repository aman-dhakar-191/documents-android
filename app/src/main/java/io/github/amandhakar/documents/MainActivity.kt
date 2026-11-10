package io.github.amandhakar.documents

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import io.github.amandhakar.documents.ui.DocumentsApp

class MainActivity : ComponentActivity() {
    // The document another app asked us to open (null when launched from the home screen).
    private val incomingUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingUri.value = viewedUri(intent)
        setContent { DocumentsApp(incomingUri = incomingUri.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewedUri(intent)?.let { incomingUri.value = it }
    }

    private fun viewedUri(intent: Intent?): Uri? =
        if (intent?.action == Intent.ACTION_VIEW) intent.data else null
}
