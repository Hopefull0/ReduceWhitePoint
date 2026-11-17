package com.whitepoint.reducer

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.provider.Settings

/**
 * Single source of truth for the system's built-in
 * "Reduce White Point" / Extra Dim feature (Settings.Secure).
 *
 * Uses Android's native display color reduction directly with zero
 * persistent overlay windows and zero idle CPU/GPU/wake-lock overhead.
 */
object WhitePoint {

    private const val KEY_ACTIVATED = "reduce_bright_colors_activated"
    private const val KEY_LEVEL = "reduce_bright_colors_level"
    private const val DEFAULT_LEVEL = 65

    const val ADB_GRANT_COMMAND =
        "adb shell pm grant com.whitepoint.reducer android.permission.WRITE_SECURE_SETTINGS"

    private val activatedUri: Uri = Settings.Secure.getUriFor(KEY_ACTIVATED)
    private val levelUri: Uri = Settings.Secure.getUriFor(KEY_LEVEL)

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isEnabled(context: Context): Boolean =
        Settings.Secure.getInt(context.contentResolver, KEY_ACTIVATED, 0) == 1

    fun getLevel(context: Context): Int =
        Settings.Secure.getInt(context.contentResolver, KEY_LEVEL, DEFAULT_LEVEL).coerceIn(0, 100)

    /** @return false if WRITE_SECURE_SETTINGS permission is missing. */
    fun setEnabled(context: Context, enabled: Boolean): Boolean = write {
        Settings.Secure.putInt(context.contentResolver, KEY_ACTIVATED, if (enabled) 1 else 0)
    }

    /** @return false if WRITE_SECURE_SETTINGS permission is missing. */
    fun setLevel(context: Context, level: Int): Boolean = write {
        Settings.Secure.putInt(context.contentResolver, KEY_LEVEL, level.coerceIn(0, 100))
    }

    fun registerObserver(resolver: ContentResolver, observer: ContentObserver) {
        resolver.registerContentObserver(activatedUri, false, observer)
        resolver.registerContentObserver(levelUri, false, observer)
    }

    fun unregisterObserver(resolver: ContentResolver, observer: ContentObserver) {
        resolver.unregisterContentObserver(observer)
    }

    private inline fun write(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (_: SecurityException) {
        false
    }
}
