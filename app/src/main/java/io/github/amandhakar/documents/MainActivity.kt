package io.github.amandhakar.documents

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import io.github.amandhakar.documents.ui.DocumentsApp
import io.github.amandhakar.documents.ui.IncomingDocument

class MainActivity : ComponentActivity() {
    // The document another app asked us to open (null when launched from the home screen).
    private val incoming = mutableStateOf<IncomingDocument?>(null)
    private var serial = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh start carries a new request. After a rotation or restart the same intent is
        // still here, and acting on it again would reopen the PDF over whatever is being read.
        if (savedInstanceState == null) receive(intent)
        setContent { DocumentsApp(incoming = incoming.value, onExit = { finish() }) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent?) {
        viewedUri(intent)?.let { incoming.value = IncomingDocument(it, ++serial) }
    }

    private fun viewedUri(intent: Intent?): Uri? =
        if (intent?.action == Intent.ACTION_VIEW) intent.data else null
}
