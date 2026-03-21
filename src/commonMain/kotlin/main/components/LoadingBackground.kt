package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.window
import main.theme.OverlayStyles
import org.jetbrains.compose.web.dom.Canvas
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * A internal class representing a moving halo in the loading screen background.
 */
private class Halo(
    var x: Double,
    var y: Double,
    var radius: Double,
    var vx: Double,
    var vy: Double,
    var color: String,
    var pulseOffset: Double,
    var pulseSpeed: Double
) {
    /**
     * Updates the halo's position and handles wrapping around the screen.
     */
    fun update(
        width: Int,
        height: Int,
    ) {
        x += vx
        y += vy

        // Wrap around screen edges
        if (x < -radius) x = width + radius
        if (x > width + radius) x = -radius
        if (y < -radius) y = height + radius
        if (y > height + radius) y = -radius
    }

    /**
     * Draws the halo on the canvas.
     */
    fun draw(
        ctx: CanvasRenderingContext2D,
        time: Double,
    ) {
        // Pulsating effect for the radius
        val currentPulse = 0.8 + 0.2 * sin(time * pulseSpeed + pulseOffset)
        val currentRadius = radius * currentPulse

        // Create a radial gradient for a soft halo effect
        val gradient = ctx.createRadialGradient(
            x,
            y,
            0.0,
            x,
            y,
            currentRadius,
        )
        gradient.addColorStop(
            0.0,
            color,
        )
        gradient.addColorStop(
            1.0,
            "transparent",
        )

        ctx.fillStyle = gradient
        ctx.beginPath()
        ctx.arc(
            x,
            y,
            currentRadius,
            0.0,
            2 * PI,
        )
        ctx.fill()
    }
}

/**
 * A component that renders a background animation for the loading screen using a canvas.
 * It displays several moving and pulsating halos.
 */
@Composable
fun LoadingBackground() {
    var canvasRef by remember { mutableStateOf<HTMLCanvasElement?>(null) }

    DisposableEffect(canvasRef) {
        val canvas = canvasRef ?: return@DisposableEffect onDispose {}
        val ctx = canvas.getContext("2d") as? CanvasRenderingContext2D ?: return@DisposableEffect onDispose {}

        var animationHandle = 0

        // Handle canvas resizing
        val resizeListener: (Event) -> Unit = {
            canvas.width = canvas.parentElement?.clientWidth ?: window.innerWidth
            canvas.height = canvas.parentElement?.clientHeight ?: window.innerHeight
        }

        window.addEventListener(
            "resize",
            resizeListener,
        )
        // Initial resize
        resizeListener(Event("resize"))

        // Create a set of halos
        val halos = List(
            6,
        ) {
            val radius = Random.nextDouble(
                200.0,
                800.0,
            )
            Halo(
                x = Random.nextDouble(
                    0.0,
                    canvas.width.toDouble(),
                ),
                y = Random.nextDouble(
                    0.0,
                    canvas.height.toDouble(),
                ),
                radius = radius,
                vx = Random.nextDouble(
                    -0.3,
                    0.3,
                ),
                vy = Random.nextDouble(
                    -0.3,
                    0.3,
                ),
                // Soft blue and purple halos
                color = if (Random.nextBoolean()) "rgba(100, 200, 255, 0.25)" else "rgba(150, 100, 255, 0.20)",
                pulseOffset = Random.nextDouble(
                    0.0,
                    PI * 2,
                ),
                pulseSpeed = Random.nextDouble(
                    0.0005,
                    0.0015,
                ),
            )
        }

        // Animation loop
        fun animate(time: Double) {
            ctx.clearRect(
                0.0,
                0.0,
                canvas.width.toDouble(),
                canvas.height.toDouble(),
            )

            halos.forEach {
                it.update(
                    canvas.width,
                    canvas.height,
                )
                it.draw(
                    ctx,
                    time,
                )
            }

            animationHandle = window.requestAnimationFrame { animate(it) }
        }

        animationHandle = window.requestAnimationFrame { animate(0.0) }

        onDispose {
            window.removeEventListener(
                "resize",
                resizeListener,
            )
            window.cancelAnimationFrame(animationHandle)
        }
    }

    Canvas(
        attrs = {
            classes(OverlayStyles.loadingScreenCanvas)
            ref {
                canvasRef = it
                onDispose {
                    canvasRef = null
                }
            }
        },
    )
}
