package main.components.player

import kotlinx.browser.window

/**
 * Small helper to compute frame delta time in seconds with sensible defaults and clamping.
 */
class FrameDeltaTracker {
    private var lastFrameTimeMs: Double = 0.0

    /**
     * Returns the delta time in seconds since the previous call.
     * Clamps to [maxSeconds] to avoid huge jumps after tab pauses; returns [defaultSeconds] for the first call.
     */
    fun nextDeltaSeconds(maxSeconds: Double = 0.1, defaultSeconds: Double = 0.016): Double {
        val now = window.performance.now()
        val delta = if (lastFrameTimeMs > 0.0) {
            kotlin.math.min((now - lastFrameTimeMs) / 1000.0, maxSeconds)
        } else defaultSeconds
        lastFrameTimeMs = now
        return delta
    }

    /** Resets the tracker so the next delta returns the default. */
    fun reset() { lastFrameTimeMs = 0.0 }
}
