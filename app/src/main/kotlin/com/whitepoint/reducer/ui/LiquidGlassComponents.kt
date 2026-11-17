package com.whitepoint.reducer.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val SharedCapsuleShape: Shape = Capsule()
private val AmbientBaseColor = Color(0xFF040712)
private val ActiveBlue = Color(0xFF0A84FF)
private val ActiveCyan = Color(0xFF38BDF8)
private val InactiveSlate = Color(0xFF64748B)
private val InactiveLightSlate = Color(0xFF94A3B8)

/**
 * Damped drag physics state inspired by Kyant0/AndroidLiquidGlass catalog's
 * DampedDragAnimation: tracks press expansion, velocity-based elongation,
 * and under-damped spring settling using graphics/layout-phase state reads.
 */
@Stable
class DampedDragState(
    initialValue: Float,
    private val scope: CoroutineScope
) {
    val valueAnim: Animatable<Float, AnimationVector1D> = Animatable(initialValue)
    val pressAnim: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val velocityStretchAnim: Animatable<Float, AnimationVector1D> = Animatable(0f)

    private val pressSpring = spring<Float>(dampingRatio = 0.65f, stiffness = 320f)
    private val settleSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 320f)

    val value: Float get() = valueAnim.value
    val pressProgress: Float get() = pressAnim.value
    val velocityStretch: Float get() = velocityStretchAnim.value

    fun onPressDown() {
        scope.launch { pressAnim.animateTo(1f, pressSpring) }
    }

    fun snapValue(target: Float, normalizedVelocity: Float = 0f) {
        scope.launch {
            valueAnim.snapTo(target.coerceIn(0f, 1f))
            velocityStretchAnim.snapTo(normalizedVelocity.coerceIn(-1f, 1f))
        }
    }

    fun animateSettle(target: Float) {
        scope.launch {
            launch { valueAnim.animateTo(target.coerceIn(0f, 1f), settleSpring) }
            launch { pressAnim.animateTo(0f, pressSpring) }
            launch { velocityStretchAnim.animateTo(0f, settleSpring) }
        }
    }

    fun animateReleaseOnly() {
        scope.launch {
            launch { pressAnim.animateTo(0f, pressSpring) }
            launch { velocityStretchAnim.animateTo(0f, settleSpring) }
        }
    }
}

@Composable
fun rememberDampedDragState(initialValue: Float): DampedDragState {
    val scope = rememberCoroutineScope()
    return remember(scope) { DampedDragState(initialValue, scope) }
}

/**
 * Zero-allocation ambient refractive mesh background.
 * Caches all radial gradient Brushes in drawWithCache and reads animated factors
 * strictly in the draw phase so 120Hz drags cause zero recompositions or object churn.
 */
@Composable
fun AmbientRefractiveBackground(
    backdrop: LayerBackdrop,
    isEnabledProvider: () -> Boolean,
    intensityProvider: () -> Int,
    modifier: Modifier = Modifier
) {
    val activeFactorState: State<Float> = animateFloatAsState(
        targetValue = if (isEnabledProvider()) 1f else 0.55f,
        animationSpec = tween(420),
        label = "ambientActiveFactor"
    )
    val dimFactorState: State<Float> = animateFloatAsState(
        targetValue = (1f - (intensityProvider() / 100f) * 0.42f).coerceIn(0.45f, 1f),
        animationSpec = tween(320),
        label = "ambientDimFactor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .layerBackdrop(backdrop)
            .drawWithCache {
                val c1 = Offset(size.width * 0.18f, size.height * 0.16f)
                val r1 = size.width * 0.85f
                val b1 = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0A84FF).copy(alpha = 0.42f),
                        Color(0xFF1D4ED8).copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = c1,
                    radius = r1
                )

                val c2 = Offset(size.width * 0.88f, size.height * 0.42f)
                val r2 = size.width * 0.78f
                val b2 = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF7C3AED).copy(alpha = 0.34f),
                        Color(0xFF4C1D95).copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = c2,
                    radius = r2
                )

                val c3 = Offset(size.width * 0.24f, size.height * 0.78f)
                val r3 = size.width * 0.75f
                val b3 = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0891B2).copy(alpha = 0.28f),
                        Color(0xFF0F766E).copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = c3,
                    radius = r3
                )

                val c4 = Offset(size.width * 0.82f, size.height * 0.10f)
                val r4 = size.width * 0.48f
                val b4 = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF59E0B).copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = c4,
                    radius = r4
                )

                onDrawBehind {
                    val active = activeFactorState.value
                    val dim = dimFactorState.value
                    drawRect(AmbientBaseColor)
                    drawCircle(brush = b1, center = c1, radius = r1, alpha = active * dim)
                    drawCircle(brush = b2, center = c2, radius = r2, alpha = dim)
                    drawCircle(brush = b3, center = c3, radius = r3, alpha = active * dim)
                    drawCircle(brush = b4, center = c4, radius = r4, alpha = active * dim)
                }
            }
    )
}

/**
 * Enforces Kyant0's strict effect order: vibrancy -> blur -> lens.
 * Keeps lens refractionHeight <= cornerRadius and guards API 33+ for RenderEffect lens.
 */
fun BackdropEffectScope.liquidGlassEffects(
    blurRadius: Dp,
    refractionHeight: Dp,
    refractionAmount: Dp,
    depthEffect: Boolean = true,
    chromaticAberration: Boolean = true
) {
    vibrancy()
    blur(blurRadius.toPx())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        lens(
            refractionHeight = refractionHeight.toPx(),
            refractionAmount = refractionAmount.toPx(),
            depthEffect = depthEffect,
            chromaticAberration = chromaticAberration
        )
    }
}

/**
 * G2-continuous Liquid Glass card that exports its own LayerBackdrop via exportedBackdrop
 * so nested glass pills/buttons inside it never trigger RenderThread SIGSEGV recursion.
 */
@Composable
fun LiquidGlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    surfaceTint: Color = Color(0xFF0F172A).copy(alpha = 0.46f),
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.(cardBackdrop: LayerBackdrop) -> Unit
) {
    val cardBackdrop = rememberLayerBackdrop()
    val shape = remember(cornerRadius) { RoundedRectangle(cornerRadius) }
    val refractionHeight = remember(cornerRadius) {
        if (cornerRadius > 16.dp) 16.dp else cornerRadius
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    liquidGlassEffects(
                        blurRadius = 12.dp,
                        refractionHeight = refractionHeight,
                        refractionAmount = 28.dp,
                        depthEffect = true,
                        chromaticAberration = true
                    )
                },
                highlight = { Highlight.Default },
                shadow = { Shadow.Default },
                exportedBackdrop = cardBackdrop,
                onDrawSurface = {
                    drawRect(surfaceTint)
                }
            )
            .padding(contentPadding)
    ) {
        content(cardBackdrop)
    }
}

/**
 * Interactive G2-continuous glass pill/button reading pressProgress & tintAlpha strictly
 * in graphics/draw phases so presses animate at 120Hz with zero recomposition.
 */
@Composable
fun LiquidGlassPill(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    tintColor: Color = ActiveBlue,
    shape: Shape = SharedCapsuleShape,
    refractionHeight: Dp = 10.dp,
    refractionAmount: Dp = 18.dp,
    horizontalPadding: Dp = 14.dp,
    verticalPadding: Dp = 10.dp,
    content: @Composable RowScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val pressProgress = remember { Animatable(0f) }
    val tintAlphaState = animateFloatAsState(
        targetValue = if (selected) 0.48f else 0.14f,
        animationSpec = tween(220),
        label = "pillTintAlpha"
    )
    val latestSelected by rememberUpdatedState(selected)
    val latestTint by rememberUpdatedState(tintColor)

    Row(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    liquidGlassEffects(
                        blurRadius = 6.dp,
                        refractionHeight = refractionHeight,
                        refractionAmount = refractionAmount,
                        depthEffect = true,
                        chromaticAberration = true
                    )
                },
                highlight = { if (latestSelected) Highlight.Default else Highlight.Ambient },
                shadow = { null },
                layerBlock = {
                    val p = pressProgress.value
                    val maxScale = if (size.width > 0f) (size.width + 10.dp.toPx()) / size.width else 1.04f
                    val s = lerp(1f, maxScale.coerceAtMost(1.06f), p)
                    scaleX = s
                    scaleY = s
                    alpha = if (enabled) 1f else 0.45f
                },
                onDrawSurface = {
                    if (latestSelected) {
                        drawRect(latestTint, blendMode = BlendMode.Hue)
                        drawRect(latestTint.copy(alpha = tintAlphaState.value))
                    } else {
                        drawRect(Color.White.copy(alpha = 0.08f))
                    }
                }
            )
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val spec = spring<Float>(0.55f, 340f, 0.001f)
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    scope.launch { pressProgress.animateTo(1f, spec) }
                    waitForUpOrCancellation()
                    scope.launch { pressProgress.animateTo(0f, spec) }
                }
            }
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * iOS 26 Liquid Glass interactive toggle switch using Capsule(), DampedDragState,
 * and rememberCombinedBackdrop. Reads animated state strictly inside layout/draw/layerBlock
 * lambdas so 120Hz drags and spring settles cause zero recompositions.
 */
@Composable
fun LiquidGlassSwitch(
    backdrop: Backdrop,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val switchTrackBackdrop = rememberLayerBackdrop()
    val combinedThumbBackdrop = rememberCombinedBackdrop(backdrop, switchTrackBackdrop)

    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val latestChecked by rememberUpdatedState(checked)
    val latestOnCheckedChange by rememberUpdatedState(onCheckedChange)

    val dragState = rememberDampedDragState(initialValue = if (checked) 1f else 0f)
    var isCurrentlyDragging by remember { mutableIntStateOf(0) }

    LaunchedEffect(checked) {
        if (isCurrentlyDragging == 0) {
            dragState.animateSettle(if (checked) 1f else 0f)
        }
    }

    val switchWidth = 64.dp
    val switchHeight = 34.dp
    val thumbWidth = 36.dp
    val thumbHeight = 28.dp
    val horizontalInset = 3.dp

    val switchWidthPx = with(density) { switchWidth.toPx() }
    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val insetPx = with(density) { horizontalInset.toPx() }
    val maxTravelPx = (switchWidthPx - thumbWidthPx - insetPx * 2f).coerceAtLeast(1f)

    fun fireSwitchTick() {
        if (!view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(
        modifier = modifier
            .size(width = switchWidth, height = switchHeight)
            .pointerInput(enabled, maxTravelPx) {
                if (!enabled) return@pointerInput
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val velocityTracker = VelocityTracker().apply {
                        addPosition(down.uptimeMillis, down.position)
                    }
                    dragState.onPressDown()
                    val startProgress = dragState.value
                    var totalDx = 0f
                    var dragged = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change: PointerInputChange = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break

                        if (!change.pressed) {
                            change.consume()
                            isCurrentlyDragging = 0
                            if (!dragged) {
                                val next = !latestChecked
                                fireSwitchTick()
                                dragState.animateSettle(if (next) 1f else 0f)
                                latestOnCheckedChange(next)
                            } else {
                                val vx = velocityTracker.calculateVelocity().x
                                val target = when {
                                    vx > 240f -> true
                                    vx < -240f -> false
                                    else -> dragState.value >= 0.5f
                                }
                                fireSwitchTick()
                                dragState.animateSettle(if (target) 1f else 0f)
                                if (target != latestChecked) {
                                    latestOnCheckedChange(target)
                                }
                            }
                            break
                        }

                        val delta = change.positionChange()
                        totalDx += delta.x
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        if (!dragged && change.isConsumed) {
                            isCurrentlyDragging = 0
                            dragState.animateReleaseOnly()
                            break
                        }

                        if (!dragged && abs(totalDx) > touchSlop) {
                            dragged = true
                            isCurrentlyDragging = 1
                        }

                        if (dragged) {
                            change.consume()
                            val nextProgress = (startProgress + totalDx / maxTravelPx).coerceIn(0f, 1f)
                            val vxNorm = (velocityTracker.calculateVelocity().x / 1800f).coerceIn(-1f, 1f)
                            dragState.snapValue(nextProgress, vxNorm)
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Track captured into switchTrackBackdrop with cached geometry in drawWithCache
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(switchTrackBackdrop)
                .drawWithCache {
                    val corner = CornerRadius(size.height / 2f, size.height / 2f)
                    val borderStroke = Stroke(width = 1.dp.toPx())
                    val inactiveTrack = Color(0xFF475569).copy(alpha = if (enabled) 0.58f else 0.30f)
                    val activeTrack = ActiveBlue.copy(alpha = if (enabled) 0.92f else 0.45f)
                    onDrawBehind {
                        val p = dragState.value.coerceIn(0f, 1f)
                        drawRoundRect(
                            color = lerpColor(inactiveTrack, activeTrack, p),
                            cornerRadius = corner
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = lerp(0.16f, 0.28f, p)),
                            cornerRadius = corner,
                            style = borderStroke
                        )
                    }
                }
        )

        // Sliding Liquid Glass droplet thumb: layout offset + layerBlock + drawSurface all skip recomposition
        Box(
            modifier = Modifier
                .offset {
                    val p = dragState.value.coerceIn(0f, 1f)
                    IntOffset((insetPx + maxTravelPx * p).roundToInt(), 0)
                }
                .size(width = thumbWidth, height = thumbHeight)
                .drawBackdrop(
                    backdrop = combinedThumbBackdrop,
                    shape = { SharedCapsuleShape },
                    effects = {
                        liquidGlassEffects(
                            blurRadius = 3.dp,
                            refractionHeight = 10.dp,
                            refractionAmount = 16.dp,
                            depthEffect = true,
                            chromaticAberration = true
                        )
                    },
                    highlight = { Highlight.Default },
                    shadow = { Shadow.Default },
                    layerBlock = {
                        val press = dragState.pressProgress
                        val velStretch = abs(dragState.velocityStretch) * 0.14f
                        val baseScale = lerp(1f, 1.12f, press)
                        scaleX = baseScale + velStretch
                        scaleY = lerp(1f, 1.08f, press)
                        alpha = if (enabled) 1f else 0.50f
                    },
                    onDrawSurface = {
                        val p = dragState.value.coerceIn(0f, 1f)
                        val press = dragState.pressProgress
                        if (p > 0.05f) {
                            drawRect(ActiveBlue, blendMode = BlendMode.Hue)
                        }
                        drawRect(Color.White.copy(alpha = lerp(0.88f, 0.26f, press)))
                    }
                )
        )
    }
}

/**
 * Interactive Liquid Glass Intensity slider using DampedDragState + rememberCombinedBackdrop.
 * All frame-by-frame motion, track fill, and scale deformation execute strictly in layout,
 * drawWithCache, and graphics layerBlock phases with zero recompositions per frame.
 */
@Composable
fun LiquidGlassIntensitySlider(
    cardBackdrop: Backdrop,
    value: Int,
    isDimmerEnabled: Boolean,
    enabled: Boolean,
    onPreviewValueChange: (Int) -> Unit,
    onDragFinished: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackBackdrop = rememberLayerBackdrop()
    val combinedThumbBackdrop = rememberCombinedBackdrop(cardBackdrop, trackBackdrop)
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val latestValue by rememberUpdatedState(value)
    val latestOnPreview by rememberUpdatedState(onPreviewValueChange)
    val latestOnFinished by rememberUpdatedState(onDragFinished)

    val dragState = rememberDampedDragState(initialValue = value.coerceIn(0, 100) / 100f)
    var isDraggingInt by remember { mutableIntStateOf(0) }
    var lastHapticStep by remember { mutableIntStateOf(value) }
    var liveFraction by remember { mutableFloatStateOf(value.coerceIn(0, 100) / 100f) }

    LaunchedEffect(value) {
        if (isDraggingInt == 0) {
            val targetFrac = value.coerceIn(0, 100) / 100f
            liveFraction = targetFrac
            dragState.animateSettle(targetFrac)
        }
    }

    val thumbWidthDp = 46.dp
    val thumbHeightDp = 28.dp
    val thumbWidthPx = with(density) { thumbWidthDp.toPx() }

    val isActiveAccent = isDimmerEnabled || isDraggingInt == 1
    val accentColorState = animateColorAsState(
        targetValue = if (isActiveAccent) ActiveBlue else InactiveSlate,
        animationSpec = tween(220),
        label = "sliderAccent"
    )
    val secondaryAccentColorState = animateColorAsState(
        targetValue = if (isActiveAccent) ActiveCyan else InactiveLightSlate,
        animationSpec = tween(220),
        label = "sliderSecondaryAccent"
    )

    fun triggerTick(newVal: Int) {
        if (abs(newVal - lastHapticStep) >= 2 || newVal == 0 || newVal == 100) {
            lastHapticStep = newVal
            if (!view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val usableWidthPx = (trackWidthPx - thumbWidthPx).coerceAtLeast(1f)

        fun xToFraction(xPx: Float): Float =
            ((xPx - thumbWidthPx / 2f) / usableWidthPx).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, usableWidthPx) {
                    if (!enabled) return@pointerInput
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val velocityTracker = VelocityTracker().apply {
                            addPosition(down.uptimeMillis, down.position)
                        }
                        dragState.onPressDown()

                        val currentThumbCenterX = (liveFraction * usableWidthPx) + thumbWidthPx / 2f
                        val startedOnThumb = abs(down.position.x - currentThumbCenterX) <= thumbWidthPx * 0.75f
                        val startFraction = if (startedOnThumb) liveFraction else xToFraction(down.position.x)
                        var totalDx = 0f
                        var dragged = false
                        var latestLevel = latestValue

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break

                            if (!change.pressed) {
                                change.consume()
                                isDraggingInt = 0
                                if (!dragged) {
                                    val tappedFrac = xToFraction(down.position.x)
                                    val tappedLevel = (tappedFrac * 100f).roundToInt().coerceIn(0, 100)
                                    liveFraction = tappedFrac
                                    triggerTick(tappedLevel)
                                    dragState.animateSettle(tappedFrac)
                                    latestOnPreview(tappedLevel)
                                    latestOnFinished(tappedLevel)
                                } else {
                                    dragState.animateSettle(liveFraction)
                                    latestOnFinished(latestLevel)
                                }
                                break
                            }

                            val delta = change.positionChange()
                            totalDx += delta.x
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            if (!dragged && change.isConsumed) {
                                isDraggingInt = 0
                                dragState.animateReleaseOnly()
                                break
                            }

                            if (!dragged && abs(totalDx) > touchSlop) {
                                dragged = true
                                isDraggingInt = 1
                            }

                            if (dragged) {
                                change.consume()
                                val nextFrac = if (startedOnThumb) {
                                    (startFraction + totalDx / usableWidthPx).coerceIn(0f, 1f)
                                } else {
                                    xToFraction(change.position.x)
                                }
                                val nextLevel = (nextFrac * 100f).roundToInt().coerceIn(0, 100)
                                val vxNorm = (velocityTracker.calculateVelocity().x / 2400f).coerceIn(-1f, 1f)

                                liveFraction = nextFrac
                                latestLevel = nextLevel
                                dragState.snapValue(nextFrac, vxNorm)
                                triggerTick(nextLevel)
                                latestOnPreview(nextLevel)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track captured into trackBackdrop with drawWithCache for zero per-frame allocation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .layerBackdrop(trackBackdrop)
                    .drawWithCache {
                        val cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                        val trackBg = Color.White.copy(alpha = 0.14f)
                        val minFillWidth = cornerRadius.x * 2f
                        onDrawBehind {
                            val displayFrac = dragState.value.coerceIn(0f, 1f)
                            val c1 = accentColorState.value
                            val c2 = secondaryAccentColorState.value
                            drawRoundRect(
                                color = trackBg,
                                cornerRadius = cornerRadius
                            )
                            val fillWidth = (size.width * displayFrac).coerceIn(minFillWidth, size.width)
                            drawRoundRect(
                                brush = Brush.horizontalGradient(listOf(c1, c2)),
                                size = size.copy(width = fillWidth),
                                cornerRadius = cornerRadius
                            )
                        }
                    }
            )

            // Glass droplet thumb refracting both cardBackdrop and trackBackdrop
            Box(
                modifier = Modifier
                    .offset {
                        val displayFrac = dragState.value.coerceIn(0f, 1f)
                        IntOffset((usableWidthPx * displayFrac).roundToInt(), 0)
                    }
                    .size(width = thumbWidthDp, height = thumbHeightDp)
                    .drawBackdrop(
                        backdrop = combinedThumbBackdrop,
                        shape = { SharedCapsuleShape },
                        effects = {
                            liquidGlassEffects(
                                blurRadius = 2.dp,
                                refractionHeight = 10.dp,
                                refractionAmount = 16.dp,
                                depthEffect = true,
                                chromaticAberration = true
                            )
                        },
                        highlight = { Highlight.Default },
                        shadow = { Shadow.Default },
                        layerBlock = {
                            val press = dragState.pressProgress
                            val velStretch = abs(dragState.velocityStretch) * 0.12f
                            val baseScale = lerp(1f, 1.14f, press)
                            scaleX = baseScale + velStretch
                            scaleY = (baseScale - velStretch * 0.35f).coerceAtLeast(1f)
                            alpha = if (enabled) 1f else 0.55f
                        },
                        onDrawSurface = {
                            val press = dragState.pressProgress
                            drawRect(Color.White.copy(alpha = lerp(0.50f, 0.24f, press)))
                        }
                    )
            )
        }
    }
}
