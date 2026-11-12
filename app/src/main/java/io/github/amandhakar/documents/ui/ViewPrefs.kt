package io.github.amandhakar.documents.ui

import android.content.Context

/** Small remembered choices about how things are shown. */
object ViewPrefs {
    private const val FILE = "view_prefs"
    private const val WORD_PAGES = "word_pages"

    /** True to show Word files as paper pages (the default), false for the reflowed reading view. */
    fun wordPages(context: Context): Boolean = try {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(WORD_PAGES, true)
    } catch (e: RuntimeException) {
        true
    }

    fun setWordPages(context: Context, pages: Boolean) {
        try {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(WORD_PAGES, pages).apply()
        } catch (e: RuntimeException) {
            // Not remembered: the choice still applies until the app closes.
        }
    }
}
