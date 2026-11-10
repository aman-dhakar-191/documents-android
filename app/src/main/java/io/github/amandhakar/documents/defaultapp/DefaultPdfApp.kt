package io.github.amandhakar.documents.defaultapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

enum class DefaultKind {
    /** Documents opens PDFs from other apps without asking. */
    Ours,

    /** No default is set: Android would show its "Open with" list. */
    NoChoice,

    /** Another app has been set (or is the only one) and opens PDFs directly. */
    Other,
}

data class DefaultPdfApp(val kind: DefaultKind, val packageName: String?, val label: String?)

/**
 * Android doesn't let an app make itself the default for a file type. What it can do is find out
 * who the default is now, and take the user to the right place to change it.
 */
object DefaultPdfApps {
    private val resolverPackages = setOf("android", "com.android.intentresolver")

    /**
     * Decides from the activity Android would launch for a PDF. When no default is set, that is
     * the system's own "Open with" list rather than any PDF app.
     */
    fun classify(resolvedPackage: String?, resolvedActivity: String?, ownPackage: String): DefaultKind = when {
        resolvedPackage == null -> DefaultKind.NoChoice
        resolvedPackage == ownPackage -> DefaultKind.Ours
        resolvedPackage in resolverPackages -> DefaultKind.NoChoice
        resolvedActivity?.contains("Resolver") == true || resolvedActivity?.contains("Chooser") == true ->
            DefaultKind.NoChoice
        else -> DefaultKind.Other
    }

    /** Who opens a PDF that another app asks to view? Cheap; safe to call on the main thread. */
    @Suppress("DEPRECATION") // The flags overload works on every Android version we support.
    fun detect(context: Context): DefaultPdfApp {
        // A made-up content address: Android only needs the scheme and the PDF type to pick a handler.
        val probe = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://${context.packageName}.probe/document.pdf"), "application/pdf")
        val pm = context.packageManager
        val info = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        val packageName = info?.activityInfo?.packageName
        val kind = classify(packageName, info?.activityInfo?.name, context.packageName)
        val label = if (kind == DefaultKind.Other) info?.loadLabel(pm)?.toString() else null
        return DefaultPdfApp(kind, packageName.takeIf { kind == DefaultKind.Other }, label)
    }

    /** Opens the Android settings page of [packageName], where "Open by default" lives. */
    fun openAppSettings(context: Context, packageName: String): Boolean {
        val appPage = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        return start(context, appPage) || start(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }

    /**
     * Opens [pdf] the way another app would, so Android shows its "Open with" list with the
     * Just once / Always choice. Only useful when [DefaultKind.NoChoice].
     */
    fun askWhichToUse(context: Context, pdf: Uri): Boolean = start(
        context,
        Intent(Intent.ACTION_VIEW).setDataAndType(pdf, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
