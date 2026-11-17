package com.whitepoint.reducer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class DimmerState(
    val hasPermission: Boolean = false,
    val isEnabled: Boolean = false,
    val targetLevel: Int = 65,
    val liveLevel: Int = 65,
    val isAnimating: Boolean = false
)

/**
 * Lightweight dimming engine wrapping Android's native Reduce White Point
 * Settings.Secure API with smooth ValueAnimator transitions, zero idle wake-locks,
 * coalesced SharedPreferences writes (never writing to disk during 120Hz drags),
 * and clean lifecycle cancellation.
 */
object DimmingEngine {

    private const val FADE_IN_MS = 300L
    private const val FADE_OUT_MS = 260L
    private const val ADJUST_MS = 240L
    private const val DRAG_DISK_THROTTLE_MS = 160L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val interpolator = DecelerateInterpolator()
    private var activeAnimator: ValueAnimator? = null
    private var lastImmediateHwLevel: Int = -1
    private var lastDiskWriteUptimeMs: Long = 0L

    private val _state = MutableStateFlow(DimmerState())
    val state: StateFlow<DimmerState> = _state.asStateFlow()

    @Volatile
    var isTransitioning: Boolean = false
        private set

    fun syncFromSystem(context: Context) {
        val appContext = context.applicationContext
        val hasPerm = WhitePoint.hasPermission(appContext)
        val enabled = WhitePoint.isEnabled(appContext)
        val hwLevel = WhitePoint.getLevel(appContext).coerceIn(0, 100)
        val target = if (!isTransitioning) {
            if (enabled) {
                if (hwLevel != DimmingPreferences.getTargetLevel(appContext)) {
                    DimmingPreferences.setTargetLevel(appContext, hwLevel)
                }
                hwLevel
            } else {
                DimmingPreferences.getTargetLevel(appContext)
            }
        } else {
            DimmingPreferences.getTargetLevel(appContext)
        }

        lastImmediateHwLevel = hwLevel
        _state.value = DimmerState(
            hasPermission = hasPerm,
            isEnabled = enabled,
            targetLevel = target,
            liveLevel = if (enabled) hwLevel else target,
            isAnimating = isTransitioning
        )
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean,
        animate: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        val appContext = context.applicationContext
        if (!WhitePoint.hasPermission(appContext)) {
            syncFromSystem(appContext)
            onComplete?.invoke(false)
            return false
        }

        val target = DimmingPreferences.getTargetLevel(appContext).coerceIn(1, 100)
        DimmingPreferences.setTargetLevel(appContext, target)

        if (!animate) {
            cancelInternal()
            WhitePoint.setLevel(appContext, target)
            WhitePoint.setEnabled(appContext, enabled)
            notifySurfaces(appContext)
            onComplete?.invoke(true)
            return true
        }

        runOnMain {
            cancelInternal()
            isTransitioning = true
            if (enabled) {
                val startLevel = if (WhitePoint.isEnabled(appContext)) {
                    WhitePoint.getLevel(appContext).coerceIn(0, 100)
                } else {
                    0
                }
                WhitePoint.setLevel(appContext, startLevel)
                WhitePoint.setEnabled(appContext, true)
                _state.value = _state.value.copy(
                    hasPermission = true,
                    isEnabled = true,
                    targetLevel = target,
                    liveLevel = startLevel,
                    isAnimating = true
                )
                startLevelAnimator(
                    appContext = appContext,
                    from = startLevel,
                    to = target,
                    durationMs = FADE_IN_MS
                ) { completed ->
                    if (completed) {
                        WhitePoint.setLevel(appContext, target)
                    }
                    isTransitioning = false
                    notifySurfaces(appContext)
                    onComplete?.invoke(true)
                }
            } else {
                val startLevel = if (WhitePoint.isEnabled(appContext)) {
                    WhitePoint.getLevel(appContext).coerceIn(0, 100)
                } else {
                    0
                }
                if (startLevel == 0) {
                    WhitePoint.setEnabled(appContext, false)
                    WhitePoint.setLevel(appContext, target)
                    isTransitioning = false
                    notifySurfaces(appContext)
                    onComplete?.invoke(true)
                    return@runOnMain
                }
                _state.value = _state.value.copy(
                    hasPermission = true,
                    isEnabled = false,
                    targetLevel = target,
                    liveLevel = startLevel,
                    isAnimating = true
                )
                startLevelAnimator(
                    appContext = appContext,
                    from = startLevel,
                    to = 0,
                    durationMs = FADE_OUT_MS
                ) { completed ->
                    if (completed) {
                        WhitePoint.setEnabled(appContext, false)
                        WhitePoint.setLevel(appContext, target)
                    }
                    isTransitioning = false
                    notifySurfaces(appContext)
                    onComplete?.invoke(true)
                }
            }
        }
        return true
    }

    fun toggle(
        context: Context,
        animate: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        val next = !WhitePoint.isEnabled(context)
        return setEnabled(context, next, animate, onComplete)
    }

    /**
     * Updates the hardware level instantaneously during continuous 60/120Hz drags while
     * coalescing SharedPreferences writes (~160ms throttle) to avoid main-thread I/O stutters.
     */
    fun setLevelImmediate(context: Context, level: Int, enableForPreview: Boolean = false): Boolean {
        val appContext = context.applicationContext
        if (!WhitePoint.hasPermission(appContext)) return false

        cancelInternal()
        isTransitioning = false
        val clamped = level.coerceIn(0, 100)

        val nowMs = android.os.SystemClock.uptimeMillis()
        if (nowMs - lastDiskWriteUptimeMs >= DRAG_DISK_THROTTLE_MS) {
            lastDiskWriteUptimeMs = nowMs
            DimmingPreferences.setTargetLevel(appContext, clamped)
        }

        if (clamped != lastImmediateHwLevel) {
            lastImmediateHwLevel = clamped
            WhitePoint.setLevel(appContext, clamped)
        }

        val currentlyEnabled = WhitePoint.isEnabled(appContext)
        val effectiveEnabled = if (enableForPreview && !currentlyEnabled) {
            WhitePoint.setEnabled(appContext, true)
            true
        } else {
            currentlyEnabled
        }

        _state.value = _state.value.copy(
            hasPermission = true,
            isEnabled = effectiveEnabled,
            targetLevel = clamped,
            liveLevel = clamped,
            isAnimating = false
        )
        return true
    }

    fun animateToLevel(
        context: Context,
        level: Int,
        enableIfOff: Boolean = false,
        animate: Boolean = true,
        onComplete: ((Boolean) -> Unit)? = null
    ): Boolean {
        val appContext = context.applicationContext
        if (!WhitePoint.hasPermission(appContext)) {
            syncFromSystem(appContext)
            onComplete?.invoke(false)
            return false
        }

        val clamped = level.coerceIn(0, 100)
        DimmingPreferences.setTargetLevel(appContext, clamped)
        lastDiskWriteUptimeMs = android.os.SystemClock.uptimeMillis()
        lastImmediateHwLevel = clamped

        val wasEnabled = WhitePoint.isEnabled(appContext)
        val shouldBeEnabled = wasEnabled || enableIfOff

        if (!shouldBeEnabled) {
            cancelInternal()
            WhitePoint.setLevel(appContext, clamped)
            notifySurfaces(appContext)
            onComplete?.invoke(true)
            return true
        }

        if (!animate) {
            cancelInternal()
            WhitePoint.setLevel(appContext, clamped)
            WhitePoint.setEnabled(appContext, true)
            notifySurfaces(appContext)
            onComplete?.invoke(true)
            return true
        }

        runOnMain {
            cancelInternal()
            isTransitioning = true
            val start = if (wasEnabled) WhitePoint.getLevel(appContext).coerceIn(0, 100) else 0
            if (!wasEnabled) {
                WhitePoint.setLevel(appContext, 0)
                WhitePoint.setEnabled(appContext, true)
            }
            _state.value = _state.value.copy(
                hasPermission = true,
                isEnabled = true,
                targetLevel = clamped,
                liveLevel = start,
                isAnimating = true
            )
            startLevelAnimator(
                appContext = appContext,
                from = start,
                to = clamped,
                durationMs = ADJUST_MS
            ) { completed ->
                if (completed) {
                    WhitePoint.setLevel(appContext, clamped)
                }
                isTransitioning = false
                notifySurfaces(appContext)
                onComplete?.invoke(true)
            }
        }
        return true
    }

    fun cancel() {
        runOnMain {
            cancelInternal()
            isTransitioning = false
        }
    }

    fun notifySurfaces(context: Context) {
        val appContext = context.applicationContext
        syncFromSystem(appContext)
        WhitePointTileService.requestTileUpdate(appContext)
        WhitePointWidgetReceiver.refreshWidgets(appContext)
    }

    private fun startLevelAnimator(
        appContext: Context,
        from: Int,
        to: Int,
        durationMs: Long,
        onFinish: (Boolean) -> Unit
    ) {
        if (from == to) {
            onFinish(true)
            return
        }
        var lastWritten = from
        var cancelled = false
        val animator = ValueAnimator.ofInt(from, to).apply {
            duration = durationMs
            interpolator = this@DimmingEngine.interpolator
            addUpdateListener { anim ->
                val value = anim.animatedValue as Int
                if (value != lastWritten) {
                    lastWritten = value
                    lastImmediateHwLevel = value
                    WhitePoint.setLevel(appContext, value)
                    _state.value = _state.value.copy(liveLevel = value)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (activeAnimator === animation) {
                        activeAnimator = null
                    }
                    onFinish(!cancelled)
                }
            })
        }
        activeAnimator = animator
        animator.start()
    }

    private fun cancelInternal() {
        activeAnimator?.cancel()
        activeAnimator = null
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
