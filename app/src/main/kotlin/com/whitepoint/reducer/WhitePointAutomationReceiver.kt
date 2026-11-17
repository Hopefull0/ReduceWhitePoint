package com.whitepoint.reducer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Exported BroadcastReceiver for Tasker, MacroDroid, and ADB automation.
 *
 * Supported Intent Actions:
 * - com.whitepoint.reducer.ACTION_CONTROL (uses EXTRA_COMMAND)
 * - com.whitepoint.reducer.ACTION_TOGGLE
 * - com.whitepoint.reducer.ACTION_ENABLE
 * - com.whitepoint.reducer.ACTION_DISABLE
 * - com.whitepoint.reducer.ACTION_SET_INTENSITY (requires int extra "intensity" or "level", 0..100)
 * - com.whitepoint.reducer.ACTION_APPLY_PRESET (requires string extra "preset")
 *
 * Supported Extras:
 * - command (String): "toggle" | "enable" | "disable" | "set_intensity" | "apply_preset"
 * - intensity / level (Int): target percentage (0..100)
 * - preset (String): preset name or id (e.g. "Reading", "Night", "Ultra Dim")
 * - animate (Boolean, default true): smooth fade transition
 */
class WhitePointAutomationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CONTROL = "com.whitepoint.reducer.ACTION_CONTROL"
        const val ACTION_TOGGLE = "com.whitepoint.reducer.ACTION_TOGGLE"
        const val ACTION_ENABLE = "com.whitepoint.reducer.ACTION_ENABLE"
        const val ACTION_DISABLE = "com.whitepoint.reducer.ACTION_DISABLE"
        const val ACTION_SET_INTENSITY = "com.whitepoint.reducer.ACTION_SET_INTENSITY"
        const val ACTION_APPLY_PRESET = "com.whitepoint.reducer.ACTION_APPLY_PRESET"

        const val EXTRA_COMMAND = "command"
        const val EXTRA_INTENSITY = "intensity"
        const val EXTRA_LEVEL = "level"
        const val EXTRA_PRESET = "preset"
        const val EXTRA_ANIMATE = "animate"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        val command = when (action) {
            ACTION_TOGGLE -> "toggle"
            ACTION_ENABLE -> "enable"
            ACTION_DISABLE -> "disable"
            ACTION_SET_INTENSITY -> "set_intensity"
            ACTION_APPLY_PRESET -> "apply_preset"
            ACTION_CONTROL -> intent.getStringExtra(EXTRA_COMMAND)?.lowercase() ?: "toggle"
            else -> return
        }

        val animate = intent.getBooleanExtra(EXTRA_ANIMATE, true)
        val pendingResult = goAsync()
        val handler = Handler(Looper.getMainLooper())
        val fallbackFinish = Runnable { runCatching { pendingResult.finish() } }
        handler.postDelayed(fallbackFinish, 1800L)

        val finish: (Boolean) -> Unit = {
            handler.removeCallbacks(fallbackFinish)
            runCatching { pendingResult.finish() }
        }

        when (command) {
            "toggle" -> DimmingEngine.toggle(context, animate, finish)
            "enable", "on" -> {
                val explicitLevel = extractLevel(intent)
                if (explicitLevel != null) {
                    DimmingEngine.animateToLevel(context, explicitLevel, enableIfOff = true, animate = animate, onComplete = finish)
                } else {
                    DimmingEngine.setEnabled(context, enabled = true, animate = animate, onComplete = finish)
                }
            }
            "disable", "off" -> DimmingEngine.setEnabled(context, enabled = false, animate = animate, onComplete = finish)
            "set_intensity", "set_level" -> {
                val level = extractLevel(intent)
                if (level != null) {
                    DimmingEngine.animateToLevel(context, level, enableIfOff = true, animate = animate, onComplete = finish)
                } else {
                    finish(false)
                }
            }
            "apply_preset", "preset" -> {
                val query = intent.getStringExtra(EXTRA_PRESET)?.trim()
                val match = DimmingPreferences.getPresets(context).firstOrNull {
                    it.name.equals(query, ignoreCase = true) || it.id.equals(query, ignoreCase = true)
                }
                if (match != null) {
                    DimmingEngine.animateToLevel(context, match.level, enableIfOff = true, animate = animate, onComplete = finish)
                } else {
                    finish(false)
                }
            }
            else -> finish(false)
        }
    }

    private fun extractLevel(intent: Intent): Int? {
        if (intent.hasExtra(EXTRA_INTENSITY)) {
            return intent.getIntExtra(EXTRA_INTENSITY, -1).takeIf { it in 0..100 }
        }
        if (intent.hasExtra(EXTRA_LEVEL)) {
            return intent.getIntExtra(EXTRA_LEVEL, -1).takeIf { it in 0..100 }
        }
        return null
    }
}
