package main.components.world.effect

import kotlinx.browser.window
import lib.Vector3

/**
 * Lightweight global wind provider for billboards and panels.
 * Uses a simple time-varying field and exposes a direction/intensity.
 */
object WindSystem {
    // Base wind direction in world space (XZ plane)
    var direction: Vector3 = Vector3(1f, 0f, 0f)
    // Global wind intensity multiplier (meters scale)
    private var intensity: Float = .125f
    // Spatial/temporal frequencies for procedural gusting
    // Increase spatial/temporal frequencies to reduce floatiness
    var spatialFreq: Float = 8f
    var temporalFreq: Float = 4f

    /** Current time in seconds (from performance.now). */
    val timeSeconds: Float
        get() = (window.performance.now() / 1000.0).toFloat()

    /** Returns the current wind intensity; vector boost no longer affects base intensity. */
    fun getIntensity(outdoors: Boolean): Float {
        val scale = if (outdoors) 1f else 0f
        return intensity * scale
    }
}
