package com.whitepoint.reducer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Manages Fixed-Time and Sunset-to-Sunrise schedules via AlarmManager.
 * Uses zero idle wake-locks and handles overnight windows cleanly.
 */
object ScheduleManager {

    const val ACTION_SCHEDULE_TICK = "com.whitepoint.reducer.ACTION_SCHEDULE_TICK"
    private const val REQUEST_CODE_ALARM = 4001

    data class WindowInfo(
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
        val isActiveNow: Boolean,
        val usedDeviceLocation: Boolean
    )

    fun getCurrentWindowInfo(
        context: Context,
        config: ScheduleConfig = DimmingPreferences.getScheduleConfig(context),
        nowMillis: Long = System.currentTimeMillis()
    ): WindowInfo {
        val (startH, startM, endH, endM, fromLoc) = when (config.mode) {
            ScheduleMode.FIXED_TIME -> Tuple5(
                config.startHour,
                config.startMinute,
                config.endHour,
                config.endMinute,
                false
            )
            ScheduleMode.SUNSET_SUNRISE -> {
                val solar = SolarCalculator.calculateForToday(context, nowMillis)
                Tuple5(
                    solar.sunsetHour,
                    solar.sunsetMinute,
                    solar.sunriseHour,
                    solar.sunriseMinute,
                    solar.usedDeviceLocation
                )
            }
        }

        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = startH * 60 + startM
        val endMinutes = endH * 60 + endM

        val isActive = isMinuteInWindow(nowMinutes, startMinutes, endMinutes)
        return WindowInfo(
            startHour = startH,
            startMinute = startM,
            endHour = endH,
            endMinute = endM,
            isActiveNow = isActive,
            usedDeviceLocation = fromLoc
        )
    }

    fun isMinuteInWindow(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        if (startMinutes == endMinutes) return false
        return if (startMinutes < endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    fun reschedule(context: Context) {
        val appContext = context.applicationContext
        val config = DimmingPreferences.getScheduleConfig(appContext)
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pending = buildAlarmPendingIntent(appContext)

        alarmManager.cancel(pending)
        if (!config.enabled) return

        val triggerAt = computeNextTransitionMillis(appContext, config, System.currentTimeMillis())
        scheduleAlarm(alarmManager, triggerAt, pending)
    }

    fun applyScheduleNowAndReschedule(
        context: Context,
        onComplete: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        val config = DimmingPreferences.getScheduleConfig(appContext)
        if (!config.enabled) {
            reschedule(appContext)
            onComplete?.invoke()
            return
        }

        val info = getCurrentWindowInfo(appContext, config)
        reschedule(appContext)

        if (!WhitePoint.hasPermission(appContext)) {
            onComplete?.invoke()
            return
        }

        if (info.isActiveNow) {
            if (config.applyPresetLevel) {
                DimmingEngine.animateToLevel(
                    context = appContext,
                    level = config.scheduleLevel,
                    enableIfOff = true,
                    animate = true
                ) {
                    onComplete?.invoke()
                }
            } else {
                DimmingEngine.setEnabled(appContext, enabled = true, animate = true) {
                    onComplete?.invoke()
                }
            }
        } else {
            DimmingEngine.setEnabled(appContext, enabled = false, animate = true) {
                onComplete?.invoke()
            }
        }
    }

    private fun computeNextTransitionMillis(
        context: Context,
        config: ScheduleConfig,
        nowMillis: Long
    ): Long {
        val todayInfo = getCurrentWindowInfo(context, config, nowMillis)
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val startToday = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, todayInfo.startHour)
            set(Calendar.MINUTE, todayInfo.startMinute)
        }.timeInMillis

        val endToday = (cal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, todayInfo.endHour)
            set(Calendar.MINUTE, todayInfo.endMinute)
        }.timeInMillis

        val candidates = mutableListOf<Long>()
        if (startToday > nowMillis + 1_000L) candidates.add(startToday)
        if (endToday > nowMillis + 1_000L) candidates.add(endToday)

        if (candidates.isNotEmpty()) {
            return candidates.min()
        }

        val tomorrowMillis = nowMillis + 24L * 60L * 60L * 1000L
        val tomorrowInfo = getCurrentWindowInfo(context, config, tomorrowMillis)
        val tomorrowCal = Calendar.getInstance().apply {
            timeInMillis = tomorrowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTomorrow = (tomorrowCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, tomorrowInfo.startHour)
            set(Calendar.MINUTE, tomorrowInfo.startMinute)
        }.timeInMillis
        val endTomorrow = (tomorrowCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, tomorrowInfo.endHour)
            set(Calendar.MINUTE, tomorrowInfo.endMinute)
        }.timeInMillis

        return minOf(startTomorrow, endTomorrow)
    }

    private fun scheduleAlarm(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pending: PendingIntent
    ) {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    private fun buildAlarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION_SCHEDULE_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private data class Tuple5(
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int,
        val fromLocation: Boolean
    )
}
