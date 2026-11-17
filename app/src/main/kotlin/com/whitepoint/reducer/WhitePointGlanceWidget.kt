package com.whitepoint.reducer

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WhitePointWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WhitePointGlanceWidget()

    companion object {
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var refreshJob: Job? = null

        fun refreshWidgets(context: Context) {
            val appContext = context.applicationContext
            refreshJob?.cancel()
            refreshJob = widgetScope.launch {
                runCatching {
                    WhitePointGlanceWidget().updateAll(appContext)
                }
            }
        }
    }
}

class WhitePointGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val hasPerm = WhitePoint.hasPermission(context)
        val isEnabled = WhitePoint.isEnabled(context)
        val level = DimmingPreferences.getTargetLevel(context)
        val presets = DimmingPreferences.getPresets(context).take(3)

        provideContent {
            WidgetContent(
                hasPermission = hasPerm,
                isEnabled = isEnabled,
                level = level,
                presets = presets
            )
        }
    }

    @Composable
    private fun WidgetContent(
        hasPermission: Boolean,
        isEnabled: Boolean,
        level: Int,
        presets: List<DimPreset>
    ) {
        val bg = ColorProvider(Color(0xE80C1224))
        val surfacePill = ColorProvider(Color(0xFF1A2238))
        val activeBlue = ColorProvider(Color(0xFF0A84FF))
        val activeSoft = ColorProvider(Color(0x4D0A84FF))
        val white = ColorProvider(Color.White)
        val muted = ColorProvider(Color(0xFF98A2B8))

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bg)
                .cornerRadius(24.dp)
                .padding(14.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    Text(
                        text = "White Point",
                        style = TextStyle(
                            color = white,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = when {
                            !hasPermission -> "Tap to complete setup"
                            isEnabled -> "Active • $level%"
                            else -> "Off • $level%"
                        },
                        style = TextStyle(
                            color = if (isEnabled) ColorProvider(Color(0xFF60A5FA)) else muted,
                            fontSize = 12.sp
                        )
                    )
                }

                Box(
                    modifier = GlanceModifier
                        .background(if (isEnabled) activeBlue else surfacePill)
                        .cornerRadius(16.dp)
                        .clickable(actionRunCallback<ToggleWidgetAction>())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isEnabled) "ON" else "OFF",
                        style = TextStyle(
                            color = white,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .background(surfacePill)
                        .cornerRadius(12.dp)
                        .clickable(
                            actionRunCallback<StepLevelWidgetAction>(
                                actionParametersOf(KEY_DELTA to -10)
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "-10%",
                        style = TextStyle(color = white, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    )
                }

                Spacer(modifier = GlanceModifier.width(6.dp))

                Box(
                    modifier = GlanceModifier
                        .background(surfacePill)
                        .cornerRadius(12.dp)
                        .clickable(
                            actionRunCallback<StepLevelWidgetAction>(
                                actionParametersOf(KEY_DELTA to 10)
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+10%",
                        style = TextStyle(color = white, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    )
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                presets.forEachIndexed { idx, preset ->
                    if (idx > 0) Spacer(modifier = GlanceModifier.width(6.dp))
                    val selected = isEnabled && preset.level == level
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .background(if (selected) activeSoft else surfacePill)
                            .cornerRadius(12.dp)
                            .clickable(
                                actionRunCallback<ApplyPresetWidgetAction>(
                                    actionParametersOf(KEY_PRESET_LEVEL to preset.level)
                                )
                            )
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${preset.level}%",
                            style = TextStyle(
                                color = if (selected) ColorProvider(Color(0xFF93C5FD)) else white,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }

    companion object {
        val KEY_DELTA = ActionParameters.Key<Int>("delta")
        val KEY_PRESET_LEVEL = ActionParameters.Key<Int>("preset_level")
    }
}

class ToggleWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (!WhitePoint.hasPermission(context)) return
        DimmingEngine.toggle(context, animate = true) {
            WhitePointWidgetReceiver.refreshWidgets(context)
        }
    }
}

class StepLevelWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (!WhitePoint.hasPermission(context)) return
        val delta = parameters[WhitePointGlanceWidget.KEY_DELTA] ?: return
        val current = DimmingPreferences.getTargetLevel(context)
        val next = (current + delta).coerceIn(5, 100)
        DimmingEngine.animateToLevel(context, next, enableIfOff = true, animate = true) {
            WhitePointWidgetReceiver.refreshWidgets(context)
        }
    }
}

class ApplyPresetWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        if (!WhitePoint.hasPermission(context)) return
        val target = parameters[WhitePointGlanceWidget.KEY_PRESET_LEVEL] ?: return
        DimmingEngine.animateToLevel(context, target, enableIfOff = true, animate = true) {
            WhitePointWidgetReceiver.refreshWidgets(context)
        }
    }
}
