package io.github.amandhakar.documents.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.DisposableEffect
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
 * Lets the rest of the screen (the top bar and its menu) drive the page that is showing: find text, and print it
 * (which is how Android saves a page as a PDF). The WebView is only held while it is on screen.
 */
class WebController {
    internal var view: WebView? = null

    /** The match the find bar is on (0-based) and how many there are, as reported by the WebView. */
    var findActive by mutableIntStateOf(0)
        internal set
    var findTotal by mutableIntStateOf(0)
        internal set

    /** Highlights every match of [query] and jumps to the first. An empty query clears the highlights. */
    fun find(query: String) {
        val v = view ?: return
        if (query.isBlank()) {
            v.clearMatches()
            findActive = 0
            findTotal = 0
        } else {
            v.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) {
        view?.findNext(forward)
    }

    fun clearFind() {
        view?.clearMatches()
        findActive = 0
        findTotal = 0
    }

    /** Opens Android's print screen for the page; choosing "Save as PDF" there writes a PDF. False if no page is showing. */
    fun printToPdf(context: Context, jobName: String): Boolean {
        val v = view ?: return false
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        manager.print(jobName, v.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
        return true
    }
}

/**
 * A saved web page (.html) shown in a locked-down [WebView]. The page is untrusted: scripts are off unless
 * [allowScripts]; nothing is fetched from the internet unless [allowOnline] (switched on per page, per opening),
 * and then only https: every other online request is refused and counted so the person is told; files and other apps' content can't be reached; tapping a
 * web link asks first and then opens the phone's browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebDocumentScreen(
    uri: Uri,
    allowScripts: Boolean,
    allowOnline: Boolean,
    onAllowOnline: () -> Unit,
    controller: WebController,
    modifier: Modifier = Modifier,
) {
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
    var blocked by remember(uri, allowScripts, allowOnline) { mutableIntStateOf(0) }
    val counter = remember(uri, allowScripts, allowOnline) { AtomicInteger(0) }
    DisposableEffect(controller) { onDispose { controller.view = null } }
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
                        // A page never gets to keep cookies, not even while online parts are allowed.
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                        setDownloadListener { _, _, _, _, _ -> } // a page can't start downloads
                        setFindListener { active, total, _ ->
                            controller.findActive = active
                            controller.findTotal = total
                        }
                    }
                },
                update = { view ->
                    controller.view = view
                    view.settings.javaScriptEnabled = allowScripts
                    view.settings.blockNetworkLoads = !allowOnline
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url.toString()
                            if (LinkPolicy.decide(url) == LinkAction.Ask) askedLink = url
                            return LinkPolicy.decide(url) != LinkAction.Allow
                        }

                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            return when (WebRequestPolicy.decide(request.url.scheme, allowOnline)) {
                                RequestAction.PassThrough, RequestAction.LoadOnline -> null
                                RequestAction.Block -> {
                                    val count = counter.incrementAndGet()
                                    view.post { blocked = count }
                                    emptyResponse()
                                }
                                RequestAction.Deny -> emptyResponse()
                            }
                        }
                    }
                    // Load again only when what is shown changes (a new page, or scripts switched on or off).
                    val key = "${uri}|$allowScripts|$allowOnline"
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
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(WebNotice.title(blocked), style = MaterialTheme.typography.titleSmall)
                    Text(
                        WebNotice.explanation(allowOnline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!allowOnline) {
                        TextButton(onClick = onAllowOnline) { Text("Load online parts") }
                    }
                }
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

private fun emptyResponse() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

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
    settings.blockNetworkLoads = true // switched off only when the person allows online parts for this page
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
