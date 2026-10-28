package dev.ofarafal.lumpeksscanner.data

import android.content.Context

/** Proste ustawienia aplikacji w SharedPreferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CHECK, value).apply()

    private companion object {
        const val KEY_AUTO_CHECK = "auto_check_updates"
    }
}
