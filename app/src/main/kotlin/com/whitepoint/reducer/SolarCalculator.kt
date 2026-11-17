package com.whitepoint.reducer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.compose.runtime.Immutable
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

@Immutable
data class SolarTimes(
    val sunriseHour: Int,
    val sunriseMinute: Int,
    val sunsetHour: Int,
    val sunsetMinute: Int,
    val usedDeviceLocation: Boolean
) {
    val sunriseMinutesOfDay: Int get() = sunriseHour * 60 + sunriseMinute
    val sunsetMinutesOfDay: Int get() = sunsetHour * 60 + sunsetMinute
}

/**
 * Pure astronomical Sunset-to-Sunrise calculator using NOAA solar equations.
 * Reads cached last-known location passively when ACCESS_COARSE_LOCATION is
 * granted (zero GPS wake-locks), and falls back gracefully to timezone-based
 * solar estimation when location permission is not granted.
 */
object SolarCalculator {

    private const val OFFICIAL_ZENITH = 90.833 // degrees

    fun hasLocationPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun resolveCoordinates(context: Context): Triple<Double, Double, Boolean> {
        if (hasLocationPermission(context)) {
            val lm = context.getSystemService(LocationManager::class.java)
            val best = try {
                listOf(
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER,
                    LocationManager.GPS_PROVIDER
                ).mapNotNull { provider ->
                    runCatching { lm?.getLastKnownLocation(provider) }.getOrNull()
                }.maxByOrNull(Location::getTime)
            } catch (_: SecurityException) {
                null
            }
            if (best != null) {
                val cfg = DimmingPreferences.getScheduleConfig(context)
                DimmingPreferences.saveScheduleConfig(
                    context,
                    cfg.copy(cachedLat = best.latitude, cachedLon = best.longitude)
                )
                return Triple(best.latitude, best.longitude, true)
            }
        }

        val cached = DimmingPreferences.getScheduleConfig(context)
        if (cached.cachedLat != null && cached.cachedLon != null) {
            return Triple(cached.cachedLat, cached.cachedLon, true)
        }

        // Graceful fallback from local timezone offset (15 deg longitude per UTC hour)
        val tz = TimeZone.getDefault()
        val offsetHours = tz.getOffset(System.currentTimeMillis()) / 3_600_000.0
        val approxLon = (offsetHours * 15.0).coerceIn(-180.0, 180.0)
        val approxLat = 40.0
        return Triple(approxLat, approxLon, false)
    }

    fun calculateForToday(context: Context, nowMillis: Long = System.currentTimeMillis()): SolarTimes {
        val (lat, lon, fromLocation) = resolveCoordinates(context)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val tzHours = cal.timeZone.getOffset(nowMillis) / 3_600_000.0

        val sunriseUtc = computeSolarEventUtcHours(cal, lat, lon, isSunrise = true)
        val sunsetUtc = computeSolarEventUtcHours(cal, lat, lon, isSunrise = false)

        val (srH, srM) = toLocalHourMinute(sunriseUtc, tzHours, defaultHour = 6, defaultMinute = 45)
        val (ssH, ssM) = toLocalHourMinute(sunsetUtc, tzHours, defaultHour = 18, defaultMinute = 30)

        return SolarTimes(
            sunriseHour = srH,
            sunriseMinute = srM,
            sunsetHour = ssH,
            sunsetMinute = ssM,
            usedDeviceLocation = fromLocation
        )
    }

    private fun toLocalHourMinute(
        utcHours: Double?,
        tzOffsetHours: Double,
        defaultHour: Int,
        defaultMinute: Int
    ): Pair<Int, Int> {
        if (utcHours == null || utcHours.isNaN()) return defaultHour to defaultMinute
        var local = (utcHours + tzOffsetHours) % 24.0
        if (local < 0.0) local += 24.0
        val totalMinutes = ((local * 60.0).toInt()).coerceIn(0, 23 * 60 + 59)
        return (totalMinutes / 60) to (totalMinutes % 60)
    }

    private fun computeSolarEventUtcHours(
        cal: Calendar,
        latitude: Double,
        longitude: Double,
        isSunrise: Boolean
    ): Double? {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val n1 = floor(275.0 * month / 9.0)
        val n2 = floor((month + 9.0) / 12.0)
        val n3 = 1.0 + floor((year - 4.0 * floor(year / 4.0) + 2.0) / 3.0)
        val dayOfYear = n1 - (n2 * n3) + day - 30.0

        val lngHour = longitude / 15.0
        val t = if (isSunrise) {
            dayOfYear + ((6.0 - lngHour) / 24.0)
        } else {
            dayOfYear + ((18.0 - lngHour) / 24.0)
        }

        val m = (0.9856 * t) - 3.289
        var l = m + (1.916 * sin(Math.toRadians(m))) + (0.020 * sin(Math.toRadians(2.0 * m))) + 282.634
        l = normalizeDegrees(l)

        var ra = Math.toDegrees(kotlin.math.atan(0.91764 * tan(Math.toRadians(l))))
        ra = normalizeDegrees(ra)

        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra = (ra + (lQuadrant - raQuadrant)) / 15.0

        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))

        val cosH = (cos(Math.toRadians(OFFICIAL_ZENITH)) - (sinDec * sin(Math.toRadians(latitude)))) /
            (cosDec * cos(Math.toRadians(latitude)))

        if (cosH > 1.0 || cosH < -1.0) {
            return null // Polar day/night fallback
        }

        val hDeg = if (isSunrise) {
            360.0 - Math.toDegrees(acos(cosH))
        } else {
            Math.toDegrees(acos(cosH))
        }
        val hHours = hDeg / 15.0

        val localMeanTime = hHours + ra - (0.06571 * t) - 6.622
        var utc = (localMeanTime - lngHour) % 24.0
        if (utc < 0.0) utc += 24.0
        return utc
    }

    private fun normalizeDegrees(deg: Double): Double {
        var r = deg % 360.0
        if (r < 0.0) r += 360.0
        return r
    }
}
