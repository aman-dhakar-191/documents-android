package io.github.amandhakar.documents.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens Android's share sheet. Always pass an Activity context, so the sheet can open. */
object ShareFiles {
    /**
     * Only content:// addresses can be handed to another app. A file:// path would crash the app on
     * Android 7 and newer, so those are left out.
     */
    fun canShare(scheme: String?): Boolean = scheme.equals("content", ignoreCase = true)

    /**
     * Shares one or more PDF files. Files that can't be shared are skipped. Returns false when
     * nothing could be shared or there is no app to share with.
     */
    fun sharePdfs(context: Context, uris: List<Uri>): Boolean = shareFiles(context, uris, "application/pdf")

    /** The type to share a mix of files under: their common type, or any type (`*/*`) when they differ or there are none. */
    fun commonMimeType(types: List<String>): String = types.distinct().singleOrNull() ?: "*/*"

    /** Shares one or more files of the given [mimeType]. Same rules as [sharePdfs]. */
    fun shareFiles(context: Context, uris: List<Uri>, mimeType: String): Boolean {
        val shareable = uris.filter { canShare(it.scheme) }
        if (shareable.isEmpty()) return false
        val send = if (shareable.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, shareable[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(shareable))
        }
        send.type = mimeType
        // The receiving app may read just these files, for now. The clip lists them all so the
        // permission covers every one.
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newRawUri("Files", shareable[0])
        for (uri in shareable.drop(1)) clip.addItem(ClipData.Item(uri))
        send.clipData = clip
        return start(context, Intent.createChooser(send, if (shareable.size == 1) "Share file" else "Share files"))
    }

    /** Shares a piece of text, for example text selected in a PDF. */
    fun shareText(context: Context, text: String): Boolean {
        if (text.isBlank()) return false
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        return start(context, Intent.createChooser(send, "Share text"))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
