package io.github.amandhakar.documents.web

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** The result of reading a saved web page. */
private sealed interface PageLoad {
    data class Loaded(val html: String) : PageLoad
    data object TooBig : PageLoad
    data object Failed : PageLoad
}

/**
 * A saved web page (.html) shown in a locked-down [WebView]. The page is untrusted: scripts are off unless
 * [allowScripts]; nothing is fetched from the internet (the app has no internet permission, and every online
 * request is refused and counted so the person is told); files and other apps' content can't be reached; tapping a
 * web link asks first and then opens the phone's browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebDocumentScreen(uri: Uri, allowScripts: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val page by produceState<PageLoad?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(uri) ?: return@withContext PageLoad.Failed
                val bytes = HtmlText.readLimited(stream) ?: return@withContext PageLoad.TooBig
                PageLoad.Loaded(HtmlText.decode(bytes))
            } catch (e: IOException) {
                PageLoad.Failed
            } catch (e: SecurityException) {
                PageLoad.Failed
            }
        }
    }
    // Online parts refused so far; starts again whenever the page is loaded again.
    var blocked by remember(uri, allowScripts) { mutableIntStateOf(0) }
    val counter = remember(uri, allowScripts) { AtomicInteger(0) }
    var askedLink by remember { mutableStateOf<String?>(null) }

    Box(modifier.fillMaxSize()) {
        when (val loaded = page) {
            null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            PageLoad.Failed -> Notice("Couldn't read this page.")
            PageLoad.TooBig -> Notice("This page is too big to open (over ${HtmlText.MAX_BYTES / (1024 * 1024)} MB).")
            is PageLoad.Loaded -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        harden(settings)
                        setDownloadListener { _, _, _, _, _ -> } // a page can't start downloads
                    }
                },
                update = { view ->
                    view.settings.javaScriptEnabled = allowScripts
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            if (LinkPolicy.decide(url) == LinkAction.Ask) askedLink = url
                            return LinkPolicy.decide(url) != LinkAction.Allow
                        }

                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val scheme = request.url.scheme?.lowercase()
                            if (scheme != "http" && scheme != "https") return null
                            val count = counter.incrementAndGet()
                            view.post { blocked = count }
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                    }
                    // Load again only when what is shown changes (a new page, or scripts switched on or off).
                    val key = "${uri}|$allowScripts"
                    if (view.tag != key) {
                        view.tag = key
                        view.loadDataWithBaseURL(null, loaded.html, "text/html", "utf-8", null)
                    }
                },
            )
        }
        if (blocked > 0) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
                tonalElevation = 3.dp,
            ) {
                Text(
                    "$blocked online ${if (blocked == 1) "part was" else "parts were"} blocked, so this page may look incomplete. " +
                        "Documents has no internet access.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }

    askedLink?.let { url ->
        AlertDialog(
            onDismissRequest = { askedLink = null },
            title = { Text("Open this link?") },
            text = { Text(url) },
            confirmButton = {
                TextButton(onClick = {
                    askedLink = null
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (e: android.content.ActivityNotFoundException) {
                        // No app can open it: nothing to do.
                    }
                }) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { askedLink = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Notice(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, modifier = Modifier.fillMaxWidth())
    }
}

/** Switches off everything a saved page has no business doing. */
private fun harden(settings: WebSettings) {
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.blockNetworkLoads = true
    settings.domStorageEnabled = false
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportMultipleWindows(false)
    settings.setGeolocationEnabled(false)
    settings.mediaPlaybackRequiresUserGesture = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    // Reading comfort: fit the page to the screen and allow pinch zoom.
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
}
