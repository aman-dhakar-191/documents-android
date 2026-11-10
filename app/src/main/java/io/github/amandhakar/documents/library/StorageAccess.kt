package io.github.amandhakar.documents.library

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi

/**
 * Permission to list PDFs anywhere on the phone, for the library screen. Opening a single PDF
 * (picker or "open with") never needs this.
 *
 * Android 11+ has no narrower way to see other apps' non-media files than "All files access",
 * which the user switches on in Settings. Android 10 and older use the normal storage permission.
 */
object StorageAccess {
    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    /** Android 11+: opens this app's "All files access" screen (falls back to the full list). */
    @RequiresApi(Build.VERSION_CODES.R)
    fun openAllFilesSettings(context: Context) {
        val forThisApp = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        try {
            context.startActivity(forThisApp)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}
