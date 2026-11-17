package com.whitepoint.reducer.ui

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import com.whitepoint.reducer.DimPreset
import com.whitepoint.reducer.DimmerState
import com.whitepoint.reducer.R
import com.whitepoint.reducer.ScheduleConfig
import com.whitepoint.reducer.ScheduleManager
import com.whitepoint.reducer.ScheduleMode
import com.whitepoint.reducer.SolarTimes
import com.whitepoint.reducer.WhitePoint
import kotlinx.coroutines.delay
import java.util.Locale

private val HeaderBadgeShape = RoundedRectangle(22.dp)
private val InnerBoxShape14 = RoundedRectangle(14.dp)
private val InnerBoxShape16 = RoundedRectangle(16.dp)

@Composable
fun ReduceWhitePointScreen(
    state: DimmerState,
    presets: List<DimPreset>,
    schedule: ScheduleConfig,
    solarTimes: SolarTimes,
    hasLocationPermission: Boolean,
    onToggle: (Boolean) -> Unit,
    onLivePreview: (Int) -> Unit,
    onCommitIntensity: (Int, wasEnabledAtStart: Boolean) -> Unit,
    onSelectPreset: (DimPreset) -> Unit,
    onAddPreset: (String, Int) -> Unit,
    onDeletePreset: (DimPreset) -> Unit,
    onUpdateSchedule: (ScheduleConfig) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onCopyCommand: (String) -> Unit,
    onRecheckPermission: () -> Unit
) {
    val rootBackdrop = rememberLayerBackdrop {
        drawRect(Color(0xFF040712))
        drawContent()
    }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragLevel by remember { mutableIntStateOf(state.targetLevel) }
    var showAddPresetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.targetLevel, isDraggingSlider) {
        if (!isDraggingSlider) {
            dragLevel = state.targetLevel
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientRefractiveBackground(
            backdrop = rootBackdrop,
            isEnabledProvider = { state.isEnabled || isDraggingSlider },
            intensityProvider = { if (isDraggingSlider) dragLevel else state.targetLevel }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSection(
                rootBackdrop = rootBackdrop,
                isEnabled = state.isEnabled
            )

            if (!state.hasPermission) {
                AdbSetupCard(
                    rootBackdrop = rootBackdrop,
                    onCopyCommand = onCopyCommand,
                    onRecheckPermission = onRecheckPermission
                )
            }

            PowerControlCard(
                rootBackdrop = rootBackdrop,
                hasPermission = state.hasPermission,
                isEnabled = state.isEnabled,
                isPreviewing = isDraggingSlider && !state.isEnabled,
                onToggle = onToggle
            )

            IntensityAndPresetsCard(
                rootBackdrop = rootBackdrop,
                state = state,
                presets = presets,
                isDraggingProvider = { isDraggingSlider },
                dragLevelProvider = { dragLevel },
                onDragStateChanged = { dragging, level ->
                    isDraggingSlider = dragging
                    dragLevel = level
                },
                onLivePreview = onLivePreview,
                onCommitIntensity = onCommitIntensity,
                onSelectPreset = onSelectPreset,
                onOpenAddPreset = { showAddPresetDialog = true },
                onDeletePreset = onDeletePreset,
                onEnableFromPreview = { onToggle(true) }
            )

            ScheduleCard(
                rootBackdrop = rootBackdrop,
                hasPermission = state.hasPermission,
                schedule = schedule,
                solarTimes = solarTimes,
                hasLocationPermission = hasLocationPermission,
                onUpdateSchedule = onUpdateSchedule,
                onRequestLocationPermission = onRequestLocationPermission
            )

            QuickSettingsAndAutomationCard(
                rootBackdrop = rootBackdrop,
                onCopyCommand = onCopyCommand
            )

            Spacer(Modifier.height(16.dp))
        }

        if (showAddPresetDialog) {
            val saveLevel = if (isDraggingSlider) dragLevel else state.targetLevel
            AddPresetOverlay(
                backdrop = rootBackdrop,
                currentLevel = saveLevel,
                onDismiss = { showAddPresetDialog = false },
                onSave = { name ->
                    onAddPreset(name, saveLevel)
                    showAddPresetDialog = false
                }
            )
        }
    }
}

@Composable
private fun HeaderSection(
    rootBackdrop: LayerBackdrop,
    isEnabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .drawBackdrop(
                    backdrop = rootBackdrop,
                    shape = { HeaderBadgeShape },
                    effects = {
                        liquidGlassEffects(
                            blurRadius = 10.dp,
                            refractionHeight = 14.dp,
                            refractionAmount = 22.dp,
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Default },
                    onDrawSurface = {
                        if (isEnabled) {
                            val tint = Color(0xFF0A84FF)
                            drawRect(tint, blendMode = BlendMode.Hue)
                            drawRect(tint.copy(alpha = 0.28f))
                        } else {
                            drawRect(Color.White.copy(alpha = 0.12f))
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_monochrome),
                contentDescription = null,
                tint = if (isEnabled) Color(0xFF93C5FD) else Color(0xFFE2E8F0),
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Reduce White Point",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Display Accommodations",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun AdbSetupCard(
    rootBackdrop: LayerBackdrop,
    onCopyCommand: (String) -> Unit,
    onRecheckPermission: () -> Unit
) {
    LiquidGlassCard(
        backdrop = rootBackdrop,
        surfaceTint = Color(0xFF451A03).copy(alpha = 0.42f)
    ) { cardBackdrop ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = LucideIcons.ShieldAlert,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "One-time ADB setup required",
                color = Color(0xFFFBBF24),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "1. Enable \"Disable permission monitoring\" in Developer options.\n2. Run this command via USB / Wireless ADB:",
            color = Color(0xFFE2E8F0),
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(InnerBoxShape14)
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(12.dp)
        ) {
            SelectionContainer {
                Text(
                    text = WhitePoint.ADB_GRANT_COMMAND,
                    color = Color(0xFFE2E8F0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LiquidGlassPill(
                backdrop = cardBackdrop,
                onClick = { onCopyCommand(WhitePoint.ADB_GRANT_COMMAND) },
                selected = true,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = LucideIcons.Copy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Copy command", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            LiquidGlassPill(
                backdrop = cardBackdrop,
                onClick = onRecheckPermission,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = LucideIcons.RotateCcw,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Re-check", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PowerControlCard(
    rootBackdrop: LayerBackdrop,
    hasPermission: Boolean,
    isEnabled: Boolean,
    isPreviewing: Boolean,
    onToggle: (Boolean) -> Unit
) {
    LiquidGlassCard(backdrop = rootBackdrop) { cardBackdrop ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reduce White Point",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        !hasPermission -> "Waiting for WRITE_SECURE_SETTINGS"
                        isPreviewing -> "Previewing dimming live"
                        isEnabled -> "Active • Reducing bright colors"
                        else -> "Inactive"
                    },
                    color = if (isEnabled) Color(0xFF60A5FA) else Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }
            LiquidGlassSwitch(
                backdrop = cardBackdrop,
                checked = isEnabled,
                enabled = hasPermission,
                onCheckedChange = onToggle
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntensityAndPresetsCard(
    rootBackdrop: LayerBackdrop,
    state: DimmerState,
    presets: List<DimPreset>,
    isDraggingProvider: () -> Boolean,
    dragLevelProvider: () -> Int,
    onDragStateChanged: (Boolean, Int) -> Unit,
    onLivePreview: (Int) -> Unit,
    onCommitIntensity: (Int, wasEnabledAtStart: Boolean) -> Unit,
    onSelectPreset: (DimPreset) -> Unit,
    onOpenAddPreset: () -> Unit,
    onDeletePreset: (DimPreset) -> Unit,
    onEnableFromPreview: () -> Unit
) {
    var dragWasEnabledAtStart by remember { mutableStateOf(state.isEnabled) }
    var recentPreviewLevel by remember { mutableStateOf<Int?>(null) }
    var selectedPresetId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(recentPreviewLevel, state.isEnabled) {
        if (state.isEnabled) {
            recentPreviewLevel = null
        } else if (recentPreviewLevel != null) {
            delay(5500L)
            recentPreviewLevel = null
        }
    }

    val isDragging = isDraggingProvider()
    val displayedLevel = if (isDragging) dragLevelProvider() else state.targetLevel
    val activePreset = if (isDragging) {
        null
    } else {
        presets.firstOrNull { it.id == selectedPresetId && it.level == displayedLevel }
            ?: presets.firstOrNull { it.level == displayedLevel }
    }

    LiquidGlassCard(backdrop = rootBackdrop) { cardBackdrop ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = LucideIcons.SlidersHorizontal,
                contentDescription = null,
                tint = Color(0xFF93C5FD),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Intensity",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            RollingPercentageText(
                percentage = displayedLevel,
                accent = if (state.isEnabled || isDragging) Color(0xFF60A5FA) else Color(0xFF94A3B8)
            )
        }

        Spacer(Modifier.height(12.dp))

        LiquidGlassIntensitySlider(
            cardBackdrop = cardBackdrop,
            value = displayedLevel,
            isDimmerEnabled = state.isEnabled,
            enabled = state.hasPermission,
            onPreviewValueChange = { newVal ->
                if (!isDraggingProvider()) {
                    selectedPresetId = null
                    dragWasEnabledAtStart = state.isEnabled
                }
                onDragStateChanged(true, newVal)
                onLivePreview(newVal)
            },
            onDragFinished = { finalVal ->
                val wasEnabled = dragWasEnabledAtStart
                onDragStateChanged(false, finalVal)
                onCommitIntensity(finalVal, wasEnabled)
                if (!wasEnabled) {
                    recentPreviewLevel = finalVal
                }
            }
        )

        AnimatedVisibility(visible = recentPreviewLevel != null && !state.isEnabled) {
            val previewVal = recentPreviewLevel ?: displayedLevel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Previewed $previewVal% while Off",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
                LiquidGlassPill(
                    backdrop = cardBackdrop,
                    onClick = {
                        recentPreviewLevel = null
                        onEnableFromPreview()
                    },
                    selected = true,
                    horizontalPadding = 12.dp,
                    verticalPadding = 6.dp
                ) {
                    Text("Enable at $previewVal%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = LucideIcons.Sparkles,
                contentDescription = null,
                tint = Color(0xFFC4B5FD),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Presets",
                color = Color(0xFFE2E8F0),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            LiquidGlassPill(
                backdrop = cardBackdrop,
                onClick = onOpenAddPreset,
                enabled = state.hasPermission,
                horizontalPadding = 10.dp,
                verticalPadding = 6.dp
            ) {
                Icon(
                    imageVector = LucideIcons.Plus,
                    contentDescription = "Save custom preset",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Save", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset ->
                val isSelected = activePreset?.id == preset.id
                LiquidGlassPill(
                    backdrop = cardBackdrop,
                    onClick = {
                        selectedPresetId = preset.id
                        onSelectPreset(preset)
                    },
                    enabled = state.hasPermission,
                    selected = isSelected,
                    horizontalPadding = 12.dp,
                    verticalPadding = 8.dp
                ) {
                    Text(
                        text = "${preset.name} · ${preset.level}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!preset.isBuiltIn) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = LucideIcons.Trash2,
                            contentDescription = "Delete ${preset.name}",
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onDeletePreset(preset) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    rootBackdrop: LayerBackdrop,
    hasPermission: Boolean,
    schedule: ScheduleConfig,
    solarTimes: SolarTimes,
    hasLocationPermission: Boolean,
    onUpdateSchedule: (ScheduleConfig) -> Unit,
    onRequestLocationPermission: () -> Unit
) {
    val context = LocalContext.current

    LiquidGlassCard(backdrop = rootBackdrop) { cardBackdrop ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = LucideIcons.Moon,
                contentDescription = null,
                tint = Color(0xFF93C5FD),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Schedule",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (schedule.enabled) {
                        val win = ScheduleManager.getCurrentWindowInfo(context, schedule)
                        if (win.isActiveNow) "In active dimming window" else "Scheduled • Waiting for window"
                    } else {
                        "Off • Manual control only"
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
            LiquidGlassSwitch(
                backdrop = cardBackdrop,
                checked = schedule.enabled,
                enabled = hasPermission,
                onCheckedChange = { enabled ->
                    onUpdateSchedule(schedule.copy(enabled = enabled))
                }
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiquidGlassPill(
                backdrop = cardBackdrop,
                onClick = { onUpdateSchedule(schedule.copy(mode = ScheduleMode.FIXED_TIME)) },
                selected = schedule.mode == ScheduleMode.FIXED_TIME,
                horizontalPadding = 10.dp,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = LucideIcons.Clock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Fixed Time",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
            LiquidGlassPill(
                backdrop = cardBackdrop,
                onClick = { onUpdateSchedule(schedule.copy(mode = ScheduleMode.SUNSET_SUNRISE)) },
                selected = schedule.mode == ScheduleMode.SUNSET_SUNRISE,
                horizontalPadding = 10.dp,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = LucideIcons.Sunrise,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Sunset–Sunrise",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (schedule.mode == ScheduleMode.FIXED_TIME) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidGlassPill(
                    backdrop = cardBackdrop,
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                onUpdateSchedule(schedule.copy(startHour = hour, startMinute = minute))
                            },
                            schedule.startHour,
                            schedule.startMinute,
                            true
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Start  ${formatTime(schedule.startHour, schedule.startMinute)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                LiquidGlassPill(
                    backdrop = cardBackdrop,
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                onUpdateSchedule(schedule.copy(endHour = hour, endMinute = minute))
                            },
                            schedule.endHour,
                            schedule.endMinute,
                            true
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "End  ${formatTime(schedule.endHour, schedule.endMinute)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(InnerBoxShape16)
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today: ${formatTime(solarTimes.sunsetHour, solarTimes.sunsetMinute)} → ${formatTime(solarTimes.sunriseHour, solarTimes.sunriseMinute)}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (solarTimes.usedDeviceLocation) "Device location" else "Timezone fallback",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                if (!hasLocationPermission) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Grant coarse location for exact local solar times.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        LiquidGlassPill(
                            backdrop = cardBackdrop,
                            onClick = onRequestLocationPermission,
                            horizontalPadding = 10.dp,
                            verticalPadding = 6.dp
                        ) {
                            Icon(
                                imageVector = LucideIcons.MapPin,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Allow", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSettingsAndAutomationCard(
    rootBackdrop: LayerBackdrop,
    onCopyCommand: (String) -> Unit
) {
    var showAutomationDetails by remember { mutableStateOf(false) }

    LiquidGlassCard(backdrop = rootBackdrop) { cardBackdrop ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = LucideIcons.LayoutGrid,
                contentDescription = null,
                tint = Color(0xFF93C5FD),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Toggle anytime from the White Point tile in Quick Settings, or add the Home Screen widget. Long-press the Quick Settings tile to open this screen.",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        LiquidGlassPill(
            backdrop = cardBackdrop,
            onClick = { showAutomationDetails = !showAutomationDetails },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = LucideIcons.Terminal,
                contentDescription = null,
                tint = Color(0xFF60A5FA),
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (showAutomationDetails) "Hide Tasker & ADB Intent Hook" else "Tasker, MacroDroid & ADB Intent Hook",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        AnimatedVisibility(visible = showAutomationDetails) {
            val sampleCmd =
                "adb shell am broadcast -n com.whitepoint.reducer/.WhitePointAutomationReceiver -a com.whitepoint.reducer.ACTION_CONTROL --es command set_intensity --ei intensity 70"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Action: com.whitepoint.reducer.ACTION_CONTROL\nExtras: command (toggle | enable | disable | set_intensity | apply_preset), intensity (0..100), preset (name/id), animate (true|false)",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                LiquidGlassPill(
                    backdrop = cardBackdrop,
                    onClick = { onCopyCommand(sampleCmd) },
                    selected = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = LucideIcons.Copy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Copy ADB Broadcast Example", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun RollingPercentageText(percentage: Int, accent: Color) {
    AnimatedContent(
        targetState = percentage,
        transitionSpec = {
            if (targetState >= initialState) {
                (slideInVertically { -it / 2 } + fadeIn()).togetherWith(slideOutVertically { it / 2 } + fadeOut())
            } else {
                (slideInVertically { it / 2 } + fadeIn()).togetherWith(slideOutVertically { -it / 2 } + fadeOut())
            }
        },
        label = "rollingPercentage"
    ) { target ->
        Text(
            text = "$target%",
            color = accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AddPresetOverlay(
    backdrop: LayerBackdrop,
    currentLevel: Int,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.56f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassCard(
            backdrop = backdrop,
            cornerRadius = 28.dp,
            surfaceTint = Color(0xFF0F172A).copy(alpha = 0.72f),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
        ) { cardBackdrop ->
            Text(
                text = "Save Preset ($currentLevel%)",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(InnerBoxShape14)
                    .background(Color.White.copy(alpha = 0.10f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (name.isEmpty()) {
                    Text(
                        text = "e.g., Bedtime, Cinema, Reading",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = name,
                    onValueChange = { if (it.length <= 24) name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF60A5FA)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LiquidGlassPill(
                    backdrop = cardBackdrop,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = Color.White, fontSize = 13.sp)
                }
                LiquidGlassPill(
                    backdrop = cardBackdrop,
                    onClick = { onSave(name) },
                    enabled = name.trim().isNotEmpty(),
                    selected = true,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Preset", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour, minute)
