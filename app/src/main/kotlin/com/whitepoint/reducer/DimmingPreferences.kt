package com.whitepoint.reducer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class DimPreset(
    val id: String,
    val name: String,
    val level: Int,
    val isBuiltIn: Boolean = false
)

@Immutable
enum class ScheduleMode {
    FIXED_TIME,
    SUNSET_SUNRISE
}

@Immutable
data class ScheduleConfig(
    val enabled: Boolean = false,
    val mode: ScheduleMode = ScheduleMode.FIXED_TIME,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val applyPresetLevel: Boolean = false,
    val scheduleLevel: Int = 65,
    val cachedLat: Double? = null,
    val cachedLon: Double? = null
)

/**
 * Lightweight SharedPreferences-backed configuration store for presets,
 * target intensity, and scheduling.
 */
object DimmingPreferences {

    private const val PREFS_NAME = "whitepoint_prefs"
    private const val KEY_TARGET_LEVEL = "target_level"
    private const val KEY_PRESETS = "presets_json"

    private const val KEY_SCHED_ENABLED = "sched_enabled"
    private const val KEY_SCHED_MODE = "sched_mode"
    private const val KEY_SCHED_START_H = "sched_start_h"
    private const val KEY_SCHED_START_M = "sched_start_m"
    private const val KEY_SCHED_END_H = "sched_end_h"
    private const val KEY_SCHED_END_M = "sched_end_m"
    private const val KEY_SCHED_APPLY_LEVEL = "sched_apply_level"
    private const val KEY_SCHED_LEVEL = "sched_level"
    private const val KEY_CACHED_LAT = "sched_lat"
    private const val KEY_CACHED_LON = "sched_lon"

    val defaultPresets = listOf(
        DimPreset(id = "reading", name = "Reading", level = 30, isBuiltIn = true),
        DimPreset(id = "night", name = "Night", level = 65, isBuiltIn = true),
        DimPreset(id = "ultra", name = "Ultra Dim", level = 85, isBuiltIn = true)
    )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTargetLevel(context: Context): Int {
        val fallback = WhitePoint.getLevel(context)
        return prefs(context).getInt(KEY_TARGET_LEVEL, fallback).coerceIn(0, 100)
    }

    fun setTargetLevel(context: Context, level: Int) {
        prefs(context).edit().putInt(KEY_TARGET_LEVEL, level.coerceIn(0, 100)).apply()
    }

    fun getPresets(context: Context): List<DimPreset> {
        val json = prefs(context).getString(KEY_PRESETS, null) ?: return defaultPresets
        return try {
            val arr = JSONArray(json)
            val list = ArrayList<DimPreset>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    DimPreset(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        level = obj.getInt("level").coerceIn(1, 100),
                        isBuiltIn = obj.optBoolean("builtIn", false)
                    )
                )
            }
            if (list.isEmpty()) defaultPresets else list
        } catch (_: Exception) {
            defaultPresets
        }
    }

    fun savePresets(context: Context, presets: List<DimPreset>) {
        val arr = JSONArray()
        presets.forEach { preset ->
            arr.put(
                JSONObject().apply {
                    put("id", preset.id)
                    put("name", preset.name)
                    put("level", preset.level.coerceIn(1, 100))
                    put("builtIn", preset.isBuiltIn)
                }
            )
        }
        prefs(context).edit().putString(KEY_PRESETS, arr.toString()).apply()
    }

    fun addCustomPreset(context: Context, name: String, level: Int): List<DimPreset> {
        val cleanName = name.trim().take(24)
        if (cleanName.isEmpty()) return getPresets(context)
        val current = getPresets(context).toMutableList()
        val id = "custom_${System.currentTimeMillis()}"
        current.add(DimPreset(id = id, name = cleanName, level = level.coerceIn(1, 100), isBuiltIn = false))
        savePresets(context, current)
        return current
    }

    fun removePreset(context: Context, id: String): List<DimPreset> {
        val current = getPresets(context).filterNot { it.id == id && !it.isBuiltIn }
        savePresets(context, current)
        return current
    }

    fun getScheduleConfig(context: Context): ScheduleConfig {
        val p = prefs(context)
        val modeName = p.getString(KEY_SCHED_MODE, ScheduleMode.FIXED_TIME.name)
        val mode = runCatching { ScheduleMode.valueOf(modeName ?: "") }
            .getOrDefault(ScheduleMode.FIXED_TIME)
        val lat = if (p.contains(KEY_CACHED_LAT)) Double.fromBits(p.getLong(KEY_CACHED_LAT, 0L)) else null
        val lon = if (p.contains(KEY_CACHED_LON)) Double.fromBits(p.getLong(KEY_CACHED_LON, 0L)) else null
        return ScheduleConfig(
            enabled = p.getBoolean(KEY_SCHED_ENABLED, false),
            mode = mode,
            startHour = p.getInt(KEY_SCHED_START_H, 22).coerceIn(0, 23),
            startMinute = p.getInt(KEY_SCHED_START_M, 0).coerceIn(0, 59),
            endHour = p.getInt(KEY_SCHED_END_H, 7).coerceIn(0, 23),
            endMinute = p.getInt(KEY_SCHED_END_M, 0).coerceIn(0, 59),
            applyPresetLevel = p.getBoolean(KEY_SCHED_APPLY_LEVEL, false),
            scheduleLevel = p.getInt(KEY_SCHED_LEVEL, 65).coerceIn(1, 100),
            cachedLat = lat,
            cachedLon = lon
        )
    }

    fun saveScheduleConfig(context: Context, config: ScheduleConfig) {
        val editor = prefs(context).edit()
            .putBoolean(KEY_SCHED_ENABLED, config.enabled)
            .putString(KEY_SCHED_MODE, config.mode.name)
            .putInt(KEY_SCHED_START_H, config.startHour.coerceIn(0, 23))
            .putInt(KEY_SCHED_START_M, config.startMinute.coerceIn(0, 59))
            .putInt(KEY_SCHED_END_H, config.endHour.coerceIn(0, 23))
            .putInt(KEY_SCHED_END_M, config.endMinute.coerceIn(0, 59))
            .putBoolean(KEY_SCHED_APPLY_LEVEL, config.applyPresetLevel)
            .putInt(KEY_SCHED_LEVEL, config.scheduleLevel.coerceIn(1, 100))

        if (config.cachedLat != null && config.cachedLon != null) {
            editor.putLong(KEY_CACHED_LAT, config.cachedLat.toRawBits())
            editor.putLong(KEY_CACHED_LON, config.cachedLon.toRawBits())
        }
        editor.apply()
    }
}
