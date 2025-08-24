package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.random.Random.Default.nextFloat

/**
 * Sparkle overlay that emits sparkles from the center of its content at the given rate.
 * - Uses additive blending (plus-lighter) so sparkles glitter over underlying elements
 * - Sparkles are non-interactive (pointer-events: none)
 * - Each sparkle randomizes rotation speed, scale (0.125..1), hue and brightness
 */
@Composable
fun Sparkle(
    ratePerSecond: Float = 1f,
    speedMultiplier: Float = .1f,
    durationMultiplier: Float = 1f,
    content: @Composable () -> Unit,
) {
    data class Particle(
        val id: Int,
        val dxPx: Int,
        val dyPx: Int,
        val durationMs: Long,
        val startScale: Double,
        val rotateToDeg: Int,
        val hueDeg: Int,
        val brightness: Double,
        val sizePx: Int,
        val started: Boolean,
    )

    val particles = remember { mutableStateListOf<Particle>() }

    LaunchedEffect(ratePerSecond) {
        particles.clear()
        if (ratePerSecond <= 0f) return@LaunchedEffect
        var id = 0
        val intervalMs = (1000.0 / ratePerSecond).coerceAtMost(2000.0)
        while (true) {
            val angle = Random.nextDouble(0.0, 2 * PI)
            val distance = Random.nextDouble(24.0, 64.0) * speedMultiplier // px
            val dx = (cos(angle) * distance).roundToInt()
            val dy = (sin(angle) * distance).roundToInt()
            val duration = (Random.nextInt(1000, 2000) * durationMultiplier).toLong()
            val startScale = Random.nextDouble(0.125, 1.0)
            val rotateTo = if (Random.nextBoolean()) Random.nextInt(90, 240) else Random.nextInt(360, 960)
            val hue = Random.nextInt(0, 360)
            val brightness = Random.nextDouble(0.125, 1.5)
            val sizePx = Random.nextInt(10, 20)

            val p = Particle(
                id = id++,
                dxPx = dx,
                dyPx = dy,
                durationMs = duration,
                startScale = startScale,
                rotateToDeg = rotateTo,
                hueDeg = hue,
                brightness = brightness,
                sizePx = sizePx,
                started = false,
            )
            particles.add(p)

            // Trigger transition next tick
            launch {
                delay(16)
                val idx = particles.indexOfFirst { it.id == p.id }
                if (idx != -1) {
                    particles[idx] = particles[idx].copy(started = true)
                }
            }

            // Auto-remove after animation duration
            launch {
                delay(duration + 100)
                particles.removeAll { it.id == p.id }
            }

            delay((intervalMs * (1f + nextFloat())).toLong().coerceAtLeast(1L))
        }
    }

    Div(attrs = {
        style {
            // Inline to avoid altering layout of children
            display(DisplayStyle.InlineBlock)
            position(Position.Relative)
        }
    }) {
        content()
        Div(attrs = {
            style {
                position(Position.Absolute)
                top(0.px)
                left(0.px)
                // fill container
                property("right", "0")
                property("bottom", "0")
                property("pointer-events", "none")
            }
        }) {
            particles.forEach { p ->
                Img(
                    src = "/raw/data/ui/sparkle.png",
                    attrs = {
                        style {
                            position(Position.Absolute)
                            // Start centered or transitioned position
                            if (!p.started) {
                                left(50.percent)
                                top(50.percent)
                                property(
                                    "transform",
                                    "translate(-50%, -50%) scale(${p.startScale}) rotate(0deg)"
                                )
                                opacity(1)
                            } else {
                                property("left", "calc(50% + ${p.dxPx}px)")
                                property("top", "calc(50% + ${p.dyPx}px)")
                                property(
                                    "transform",
                                    "translate(-50%, -50%) scale(0) rotate(${p.rotateToDeg}deg)"
                                )
                                opacity(0)
                            }

                            // Transitions and visuals
                            property(
                                "transition",
                                "left ${p.durationMs}ms ease-out, top ${p.durationMs}ms ease-out, transform ${p.durationMs}ms ease-out, opacity ${p.durationMs}ms ease-out"
                            )
                            property("filter", "hue-rotate(${p.hueDeg}deg) brightness(${p.brightness})")
                            property("mix-blend-mode", "plus-lighter")
                            property("pointer-events", "none")
                            property("width", "${p.sizePx}px")
                        }
                    }
                )
            }
        }
    }
}
