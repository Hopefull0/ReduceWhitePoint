package com.whitepoint.reducer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.whitepoint.reducer.ui.ReduceWhitePointScreen

class MainActivity : ComponentActivity() {

    private var presetsState by mutableStateOf<List<DimPreset>>(emptyList())
    private var scheduleState by mutableStateOf(ScheduleConfig())
    private var solarTimesState by mutableStateOf(SolarTimes(6, 45, 18, 30, false))
    private var hasLocationPermission by mutableStateOf(false)

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            if (!DimmingEngine.isTransitioning) {
                DimmingEngine.syncFromSystem(this@MainActivity)
            }
        }
    }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasLocationPermission = granted || SolarCalculator.hasLocationPermission(this)
            solarTimesState = SolarCalculator.calculateForToday(this)
            if (scheduleState.enabled && scheduleState.mode == ScheduleMode.SUNSET_SUNRISE) {
                ScheduleManager.applyScheduleNowAndReschedule(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        refreshLocalState()

        setContent {
            val dimmerState by DimmingEngine.state.collectAsState()
            ReduceWhitePointScreen(
                state = dimmerState,
                presets = presetsState,
                schedule = scheduleState,
                solarTimes = solarTimesState,
                hasLocationPermission = hasLocationPermission,
                onToggle = { enabled ->
                    if (!DimmingEngine.setEnabled(this, enabled, animate = true)) {
                        showPermissionMissing()
                    }
                },
                onLivePreview = { previewLevel ->
                    if (!DimmingEngine.setLevelImmediate(this, previewLevel, enableForPreview = true)) {
                        showPermissionMissing()
                    }
                },
                onCommitIntensity = { finalLevel, wasEnabledAtStart ->
                    handleCommitIntensity(finalLevel, wasEnabledAtStart)
                },
                onSelectPreset = { preset ->
                    if (!DimmingEngine.animateToLevel(this, preset.level, enableIfOff = true, animate = true)) {
                        showPermissionMissing()
                    }
                },
                onAddPreset = { name, level ->
                    presetsState = DimmingPreferences.addCustomPreset(this, name, level)
                    ShortcutActionActivity.updateDynamicShortcuts(this)
                    WhitePointWidgetReceiver.refreshWidgets(this)
                },
                onDeletePreset = { preset ->
                    presetsState = DimmingPreferences.removePreset(this, preset.id)
                    ShortcutActionActivity.updateDynamicShortcuts(this)
                    WhitePointWidgetReceiver.refreshWidgets(this)
                },
                onUpdateSchedule = { updated ->
                    scheduleState = updated
                    DimmingPreferences.saveScheduleConfig(this, updated)
                    solarTimesState = SolarCalculator.calculateForToday(this)
                    ScheduleManager.applyScheduleNowAndReschedule(this)
                },
                onRequestLocationPermission = {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                onCopyCommand = { cmd ->
                    val clip = ClipData.newPlainText("ADB command", cmd)
                    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
                },
                onRecheckPermission = {
                    DimmingEngine.syncFromSystem(this)
                    if (!WhitePoint.hasPermission(this)) {
                        showPermissionMissing()
                    }
                }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        WhitePoint.registerObserver(contentResolver, observer)
        refreshLocalState()
    }

    override fun onStop() {
        WhitePoint.unregisterObserver(contentResolver, observer)
        super.onStop()
    }

    private fun refreshLocalState() {
        presetsState = DimmingPreferences.getPresets(this)
        scheduleState = DimmingPreferences.getScheduleConfig(this)
        hasLocationPermission = SolarCalculator.hasLocationPermission(this)
        solarTimesState = SolarCalculator.calculateForToday(this)
        DimmingEngine.syncFromSystem(this)
        ShortcutActionActivity.updateDynamicShortcuts(this)
    }

    private fun handleCommitIntensity(
        finalLevel: Int,
        wasEnabledAtStart: Boolean
    ) {
        if (!WhitePoint.hasPermission(this)) {
            showPermissionMissing()
            return
        }
        val clamped = finalLevel.coerceIn(0, 100)
        if (!wasEnabledAtStart) {
            DimmingPreferences.setTargetLevel(this, clamped)
            DimmingEngine.setEnabled(this, enabled = false, animate = true)
        } else {
            DimmingEngine.animateToLevel(this, clamped, enableIfOff = true, animate = false)
            DimmingEngine.notifySurfaces(this)
        }
    }

    private fun showPermissionMissing() {
        Toast.makeText(this, R.string.permission_missing, Toast.LENGTH_SHORT).show()
        DimmingEngine.syncFromSystem(this)
    }
}
