package main.components.chat

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Canvas
import org.w3c.dom.CanvasLineCap
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.ROUND
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.PI

@Composable
fun RecordingPreview(
    isRecording: Boolean,
) {
    val scope = rememberCoroutineScope()

    Canvas(
        attrs = {
            style {
                property("flex-grow", "1")
                height(40.px)
                backgroundColor(rgba(255, 255, 255, 0.05))
                borderRadius(20.px)
                margin(0.px, 8.px)
                property("box-shadow", "inset 0 0 10px rgba(255, 255, 255, 0.05)")
                boxSizing("border-box")
            }
            ref { el ->
                // Initial size sync
                if (el.clientWidth > 0) {
                    el.width = el.clientWidth
                    el.height = el.clientHeight
                }

                val ctx = el.getContext("2d") as? CanvasRenderingContext2D
                if (ctx != null) {
                    val job = scope.launch {
                        var localTime = 0.0
                        while (isRecording) {
                            // Ensure drawing surface matches layout size
                            if (el.width != el.clientWidth || el.height != el.clientHeight) {
                                if (el.clientWidth > 0) {
                                    el.width = el.clientWidth
                                    el.height = el.clientHeight
                                }
                            }

                            val w = el.width.toDouble()
                            val h = el.height.toDouble()
                            
                            if (w > 0 && h > 0) {
                                ctx.clearRect(0.0, 0.0, w, h)
                                
                                val barGap = 4.0
                                val horizontalPadding = 16.0
                                val contentAreaWidth = w - 2.0 * horizontalPadding

                                if (contentAreaWidth > 0) {
                                    // Target density of 8px per bar (4px bar + 4px gap)
                                    val barCount = (contentAreaWidth / 8.0).toInt().coerceIn(1, 40)

                                    val totalGap = (barCount - 1) * barGap
                                    val availableWidth = contentAreaWidth - totalGap
                                    val barWidth = (availableWidth / barCount).coerceAtLeast(1.0)
                                    val centerY = h / 2.0

                                    ctx.lineCap = CanvasLineCap.ROUND

                                    // Glow effect
                                    ctx.shadowBlur = 12.0

                                    for (i in 0 until barCount) {
                                        val x = horizontalPadding + i * (barWidth + barGap) + barWidth / 2.0

                                        // Calculate norm based on x relative to available width for smoother taper
                                        val norm = (x - horizontalPadding) / contentAreaWidth
                                        val envelope = sin(norm * PI)

                                        // Fluid wave motion using multiple sine waves for complexity
                                        val wave1 = sin(localTime * 2.8 + i * 0.15)
                                        val wave2 = sin(localTime * 1.6 - i * 0.45)
                                        val wave3 = sin(localTime * 4.2 + i * 0.1)
                                        val combined = abs(wave1 * 0.4 + wave2 * 0.3 + wave3 * 0.3)

                                        // Add a small baseline so it doesn't completely disappear in the middle
                                        val amplitude = (h * 0.8) * (0.1 + 0.9 * combined) * envelope

                                        // Scale line width with envelope to ensure perfect taper to 0
                                        ctx.lineWidth = barWidth * envelope

                                        // Color gradient shifts over time and across bars, with alpha taper
                                        val hue = (localTime * 40 + i * 2) % 360
                                        val alpha = 0.9 * envelope
                                        val color = "hsla($hue, 85%, 65%, .9)"

                                        ctx.strokeStyle = color
                                        ctx.shadowColor = color

                                        ctx.beginPath()
                                        ctx.moveTo(x, centerY - amplitude / 2.0)
                                        ctx.lineTo(x, centerY + amplitude / 2.0)
                                        ctx.stroke()
                                    }
                                }
                                
                                localTime += 0.05
                            }
                            
                            delay(16) // ~60fps for maximum fluidity
                        }
                    }
                    onDispose {
                        job.cancel()
                    }
                } else {
                    onDispose {}
                }
            }
        }
    )
}
