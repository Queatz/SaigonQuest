package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.jetbrains.compose.web.dom.Div
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

// Produce stable pseudo-random base values per seedKey
data class Base(
    val rotBase: Float,
    val sclBase: Float,
    val rotAmp: Float,
    val sclAmp: Float,
    val rotOmega: Float,
    val sclOmega: Float,
    val rotPhase: Float,
    val sclPhase: Float,
)

/**
 * RandomJitter wraps its [content] with a small, stable rotate + scale transform and slowly animates it.
 * - Stable per [seedKey] across recompositions
 * - Subtle by default (rotation +/- 3deg, scale ~1.0)
 * - Slow sinusoidal animation of rotation and scale (period/speed derived from seedKey)
 */
@Composable
fun RandomJitter(
    rotationRangeDeg: ClosedFloatingPointRange<Float> = (-6f)..6f,
    scaleRange: ClosedFloatingPointRange<Float> = 0.9f..1.1f,
    seedKey: Any? = null,
    content: @Composable () -> Unit,
) {
    val base = remember(seedKey) {
        val seed = (seedKey?.hashCode() ?: 0)
        fun rnd(nudge: Int): Float {
            // Simple LCG-like hash to 0..1 range
            val x = seed * 1664525 + 1013904223 + nudge
            return (((x ushr 1) % 10_000) / 10_000f).coerceIn(0f, 1f)
        }
        val rotRange = rotationRangeDeg.endInclusive - rotationRangeDeg.start
        val sclRange = scaleRange.endInclusive - scaleRange.start

        val rotBase = rotationRangeDeg.start + rotRange * rnd(1)
        val sclBase = scaleRange.start + sclRange * rnd(2)

        // Amplitudes limited to remain within provided ranges, using up to 40% of nearest boundary distance
        val rotMaxAmp = min(abs(rotBase - rotationRangeDeg.start), abs(rotationRangeDeg.endInclusive - rotBase)) * 0.4f
        val sclMaxAmp = min(abs(sclBase - scaleRange.start), abs(scaleRange.endInclusive - sclBase)) * 0.4f
        val rotAmp = (0.15f + 0.85f * rnd(3)) * rotMaxAmp // 15%..100% of maxAmp
        val sclAmp = (0.15f + 0.85f * rnd(4)) * sclMaxAmp

        // Angular speeds (omega in radians per second), slow and subtle, 2π / period
        // Periods between ~6s..12s, derived from seed
        val rotPeriodSec = 6f + 6f * rnd(5)
        val sclPeriodSec = 6f + 6f * rnd(6)
        val rotOmega = (2f * PI.toFloat()) / rotPeriodSec
        val sclOmega = (2f * PI.toFloat()) / sclPeriodSec

        // Phase offsets 0..2π
        val rotPhase = 2f * PI.toFloat() * rnd(7)
        val sclPhase = 2f * PI.toFloat() * rnd(8)

        Base(
            rotBase = rotBase,
            sclBase = sclBase,
            rotAmp = rotAmp,
            sclAmp = sclAmp,
            rotOmega = rotOmega,
            sclOmega = sclOmega,
            rotPhase = rotPhase,
            sclPhase = sclPhase
        )
    }

    var rotNow by remember(seedKey) { mutableStateOf(base.rotBase) }
    var sclNow by remember(seedKey) { mutableStateOf(base.sclBase) }

    LaunchedEffect(seedKey) {
        // Evolve a time parameter; avoid relying on wall-clock for determinism between tabs
        var t = 0f
        val dt = 1f / 24f // ~24 FPS
        while (true) {
            rotNow = base.rotBase + base.rotAmp * sin(base.rotOmega * t * 2f + base.rotPhase)
            sclNow = (base.sclBase + base.sclAmp * sin(base.sclOmega * t * 2f + base.sclPhase)).coerceIn(scaleRange.start, scaleRange.endInclusive)
            t += dt
            delay(42) // ~24 FPS
        }
    }

    Div(attrs = {
        style {
            // Center transform around the icon center
            property("transform-origin", "50% 50%")
            property("will-change", "transform")
            property("transform", "rotate(${rotNow}deg) scale($sclNow)")
        }
    }) {
        content()
    }
}
