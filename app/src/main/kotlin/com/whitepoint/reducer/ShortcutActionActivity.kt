package com.whitepoint.reducer

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Translucent trampoline Activity invoked by static & dynamic Android App Shortcuts
 * for instant toggle or preset activation without pulling the user out of their current app.
 */
class ShortcutActionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!WhitePoint.hasPermission(this)) {
            Toast.makeText(this, R.string.permission_missing, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        val command = intent?.getStringExtra(WhitePointAutomationReceiver.EXTRA_COMMAND) ?: "toggle"
        when (command) {
            "apply_preset" -> {
                val query = intent?.getStringExtra(WhitePointAutomationReceiver.EXTRA_PRESET)
                val match = DimmingPreferences.getPresets(this).firstOrNull {
                    it.name.equals(query, ignoreCase = true) || it.id.equals(query, ignoreCase = true)
                }
                if (match != null) {
                    DimmingEngine.animateToLevel(this, match.level, enableIfOff = true, animate = true)
                }
            }
            else -> DimmingEngine.toggle(this, animate = true)
        }
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        fun updateDynamicShortcuts(context: Context) {
            val manager = context.getSystemService(ShortcutManager::class.java) ?: return
            val customPresets = DimmingPreferences.getPresets(context).filter { !it.isBuiltIn }.take(2)
            val dynamic = customPresets.map { preset ->
                val launchIntent = Intent(context, ShortcutActionActivity::class.java).apply {
                    action = "com.whitepoint.reducer.SHORTCUT_ACTION"
                    putExtra(WhitePointAutomationReceiver.EXTRA_COMMAND, "apply_preset")
                    putExtra(WhitePointAutomationReceiver.EXTRA_PRESET, preset.id)
                }
                ShortcutInfo.Builder(context, "dynamic_${preset.id}")
                    .setShortLabel(preset.name.take(14))
                    .setLongLabel("${preset.name} (${preset.level}%)")
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_sun_ios_large))
                    .setIntent(launchIntent)
                    .build()
            }
            runCatching { manager.dynamicShortcuts = dynamic }
        }
    }
}
