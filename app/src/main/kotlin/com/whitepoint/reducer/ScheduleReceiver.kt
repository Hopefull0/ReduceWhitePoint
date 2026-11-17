package com.whitepoint.reducer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Handles scheduled dimmer transitions and restores schedules after device boot
 * or timezone/clock updates.
 */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val relevant = action == ScheduleManager.ACTION_SCHEDULE_TICK ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        if (!relevant) return

        val config = DimmingPreferences.getScheduleConfig(context)
        if (!config.enabled) {
            ScheduleManager.reschedule(context)
            return
        }

        val pendingResult = goAsync()
        val safetyFinish = Runnable {
            runCatching { pendingResult.finish() }
        }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(safetyFinish, 2000L)

        ScheduleManager.applyScheduleNowAndReschedule(context) {
            handler.removeCallbacks(safetyFinish)
            runCatching { pendingResult.finish() }
        }
    }
}
