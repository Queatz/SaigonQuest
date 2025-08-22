package main.components.world.effect

import kotlinx.browser.window
import lib.Vector3
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Lightweight global wind provider for billboards and panels.
 * Uses a simple time-varying field and exposes a direction/intensity.
 */
object WindSystem {
    // Base wind direction in world space (XZ plane)
    var direction: Vector3 = Vector3(1f, 0f, 0f)
    // Global wind intensity multiplier (meters scale)
    private var intensity: Float = .5f
    // Spatial/temporal frequencies for procedural gusting
    // Increase spatial/temporal frequencies to reduce floatiness
    var spatialFreq: Float = 8f
    var temporalFreq: Float = 4f

    // Temporary boost state (vector-based)
    private var boostUntilTime: Float = 0f
    private var boostStartTime: Float = 0f
    private var boostDuration: Float = 1f
    private var boostVector: Vector3 = Vector3(0f, 0f, 0f)

    /** Current time in seconds (from performance.now). */
    val timeSeconds: Float
        get() = (window.performance.now() / 1000.0).toFloat()

    /** Returns the current wind intensity; vector boost no longer affects base intensity. */
    fun getIntensity(outdoors: Boolean): Float {
        val scale = if (outdoors) 1f else 0f
        return intensity * scale
    }

    /**
     * Trigger a temporary wind boost (e.g., when the player moves).
     * The boost vector is the negation of the last movement delta.
     * Repeated calls extend the boost window.
     */
    fun triggerMovementBoost(movementDelta: Vector3, durationSeconds: Float = .5f) {
        // Store the negation of the last movement (XZ plane; preserve Y if provided)
        boostVector = Vector3(-movementDelta.x, -movementDelta.y, -movementDelta.z)
        boostDuration = durationSeconds.coerceAtLeast(0.001f)
        val now = timeSeconds
        boostStartTime = now
        boostUntilTime = now + boostDuration
    }

    /** Current decayed boost vector (Zero if inactive). */
    fun getBoostVector(now: Float = timeSeconds): Vector3 {
        val influence = getBoostInfluence(now)
        return if (influence <= 0f) Vector3(0f, 0f, 0f) else boostVector.scale(influence)
    }

    /** Seconds remaining in the current boost window (0 if inactive). */
    fun getBoostTimeRemaining(): Float {
        val now = timeSeconds
        return if (now <= boostUntilTime) (boostUntilTime - now) else 0f
    }

    /** Current boost influence [0..1], where 1 at start, 0 at end of duration. */
    fun getBoostInfluence(now: Float = timeSeconds): Float {
        if (now > boostUntilTime) return 0f
        val remaining = boostUntilTime - now
        return (remaining / boostDuration).coerceIn(0f, 1f).pow(2f)
    }

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
