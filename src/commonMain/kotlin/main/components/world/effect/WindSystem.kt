package main.components.world.effect

import kotlinx.browser.window
import lib.Vector3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight global wind provider for billboards and panels.
 * Uses a simple time-varying field and exposes a direction/intensity.
 */
object WindSystem {
    // Base wind direction in world space (XZ plane)
    var direction: Vector3 = Vector3(1f, 0f, 0f)
    // Global wind intensity multiplier (meters scale)
    var intensity: Float = .5f
    // Spatial/temporal frequencies for procedural gusting
    // Increase spatial/temporal frequencies to reduce floatiness
    var spatialFreq: Float = 8f
    var temporalFreq: Float = 4f

    /** Current time in seconds (from performance.now). */
    val timeSeconds: Float
        get() = (window.performance.now() / 1000.0).toFloat()

    /**
     * Rotates a world-space horizontal vector by yaw (radians) around Y axis.
     * Positive yaw rotates from +Z toward +X.
     */
    fun rotateY(vector: Vector3, yawRadians: Float): Vector3 {
        val c = cos(yawRadians)
        val s = sin(yawRadians)
        val x = vector.x * c - vector.z * s
        val z = vector.x * s + vector.z * c
        return Vector3(x, vector.y, z)
    }
}
