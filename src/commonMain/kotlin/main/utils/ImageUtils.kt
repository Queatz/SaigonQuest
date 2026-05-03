package main.utils

import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.Image
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object ImageUtils {
    /**
     * Scales an image to the given scale factor using a canvas.
     * Returns a Data URL of the scaled image.
     */
    suspend fun scaleImage(src: String, scale: Double): String = suspendCoroutine { continuation ->
        val img = Image()
        img.onload = {
            try {
                val canvas = document.createElement("canvas") as HTMLCanvasElement
                val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
                
                val targetWidth = (img.width * scale).toInt()
                val targetHeight = (img.height * scale).toInt()
                
                canvas.width = targetWidth
                canvas.height = targetHeight
                
                ctx.drawImage(img, 0.0, 0.0, targetWidth.toDouble(), targetHeight.toDouble())
                
                // Use jpeg with 0.8 quality to reduce memory footprint
                val dataUrl = canvas.toDataURL("image/jpeg", 0.8)
                continuation.resume(dataUrl)
            } catch (e: Throwable) {
                console.error("Failed to scale image: $src", e)
                continuation.resume(src) // Fallback to original
            }
        }
        img.onerror = { _, _, _, _, _ ->
            console.warn("Failed to load image for scaling: $src")
            continuation.resume(src) // Fallback to original
        }
        img.crossOrigin = "anonymous"
        img.src = src
    }

    /**
     * Prepares an image for pixel art rendering by scaling it to a fixed height (e.g., 128px)
     * using a multi-step process for cleaner results, and applying posterization.
     */
    suspend fun preparePixelArt(
        src: String,
        targetHeight: Int = 128,
    ): String = suspendCoroutine { continuation ->
        val img = Image()
        img.onload = {
            try {
                val canvas = document.createElement("canvas") as HTMLCanvasElement
                val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
                val aspect = img.width.toDouble() / img.height.toDouble()

                val targetWidth = (targetHeight * aspect).toInt()

                // 1. Smooth downscale to an intermediate size (2x target) to preserve more detail
                val intermediateCanvas = document.createElement("canvas") as HTMLCanvasElement
                intermediateCanvas.width = targetWidth * 2
                intermediateCanvas.height = targetHeight * 2
                val ictx = intermediateCanvas.getContext("2d") as CanvasRenderingContext2D
                ictx.imageSmoothingEnabled = true
                ictx.drawImage(
                    img,
                    0.0,
                    0.0,
                    intermediateCanvas.width.toDouble(),
                    intermediateCanvas.height.toDouble(),
                )

                // 2. Color Boost & Sharpen
                val intermediateData = ictx.getImageData(
                    0.0,
                    0.0,
                    intermediateCanvas.width.toDouble(),
                    intermediateCanvas.height.toDouble(),
                )
                val id = intermediateData.data.asDynamic()
                applyColorBoost(id)
                applySharpen(
                    id,
                    intermediateCanvas.width,
                    intermediateCanvas.height,
                    0.125,
                )
                ictx.putImageData(
                    intermediateData,
                    0.0,
                    0.0,
                )

                // 3. Nearest neighbor downscale to final size for sharp pixels
                canvas.width = targetWidth
                canvas.height = targetHeight
                ctx.imageSmoothingEnabled = false
                ctx.drawImage(
                    intermediateCanvas,
                    0.0,
                    0.0,
                    targetWidth.toDouble(),
                    targetHeight.toDouble(),
                )

                // 4. Post-processing: Posterization, Alpha Thresholding, and Despeckle
                val imageData = ctx.getImageData(
                    0.0,
                    0.0,
                    targetWidth.toDouble(),
                    targetHeight.toDouble(),
                )
                val d = imageData.data.asDynamic()
                
                applyQuantization(d)
                applyAlphaThreshold(d)
                applyDespeckle(
                    d,
                    targetWidth,
                    targetHeight,
                )

                ctx.putImageData(
                    imageData,
                    0.0,
                    0.0,
                )
                continuation.resume(canvas.toDataURL("image/png"))
            } catch (e: Throwable) {
                console.error(
                    "Failed to prepare pixel art: $src",
                    e,
                )
                continuation.resume(src)
            }
        }
        img.onerror = { _, _, _, _, _ ->
            console.warn("Failed to load image for pixel art: $src")
            continuation.resume(src) // Fallback to original
        }
        img.crossOrigin = "anonymous"
        img.src = src
    }

    /**
     * Boosts contrast and saturation slightly to make colors pop.
     */
    private fun applyColorBoost(data: dynamic) {
        val len = data.length as Int
        val contrast = 1.4
        val saturation = 1.2

        for (i in 0 until len step 4) {
            for (j in 0..2) {
                var c = (data[i + j] as Int) / 255.0
                // Contrast boost
                c = ((c - 0.5) * contrast + 0.5).coerceIn(0.0, 1.0)
                data[i + j] = (c * 255).toInt()
            }

            // Saturation boost
            val r = data[i] as Int
            val g = data[i + 1] as Int
            val b = data[i + 2] as Int
            val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            data[i] = (lum + (r - lum) * saturation).toInt().coerceIn(0, 255)
            data[i + 1] = (lum + (g - lum) * saturation).toInt().coerceIn(0, 255)
            data[i + 2] = (lum + (b - lum) * saturation).toInt().coerceIn(0, 255)
        }
    }

    /**
     * Simple convolution-based sharpening.
     */
    private fun applySharpen(
        data: dynamic,
        w: Int,
        h: Int,
        amount: Double,
    ) {
        val len = (data.length as Int)
        val copy = js("new Uint8ClampedArray(len)")
        copy.set(data)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = (y * w + x) * 4
                if (copy[i + 3] as Int == 0) continue

                for (j in 0..2) {
                    val current = copy[i + j] as Int
                    val neighbors = (copy[((y - 1) * w + x) * 4 + j] as Int) +
                        (copy[((y + 1) * w + x) * 4 + j] as Int) +
                        (copy[(y * w + (x - 1)) * 4 + j] as Int) +
                        (copy[(y * w + (x + 1)) * 4 + j] as Int)

                    val sharpened = current + (4 * current - neighbors) * amount
                    data[i + j] = sharpened.toInt().coerceIn(0, 255)
                }
            }
        }
    }

    /**
     * Applies posterization (quantization) to color channels.
     */
    private fun applyQuantization(data: dynamic) {
        val len = data.length as Int
        for (i in 0 until len step 4) {
            // Quantize to 16 levels for better detail preservation
            data[i] = ((data[i] as Int / 16).coerceIn(0, 15) * 255 / 15)
            data[i + 1] = ((data[i + 1] as Int / 16).coerceIn(0, 15) * 255 / 15)
            data[i + 2] = ((data[i + 2] as Int / 16).coerceIn(0, 15) * 255 / 15)
        }
    }

    /**
     * Sharp alpha thresholding (binary alpha).
     */
    private fun applyAlphaThreshold(data: dynamic) {
        val len = data.length as Int
        for (i in 0 until len step 4) {
            val a = data[i + 3] as Int
            data[i + 3] = if (a < 128) 0 else 255
        }
    }

    /**
     * Removes isolated pixels that differ significantly from their neighbors.
     * Uses a copy for neighbor checking to avoid directional bleeding.
     */
    private fun applyDespeckle(
        data: dynamic,
        w: Int,
        h: Int,
    ) {
        // Create a copy for reference to avoid in-place modification bleeding
        val len = (data.length as Int)
        val copy = js("new Uint8ClampedArray(len)")
        copy.set(data)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = (y * w + x) * 4
                if (copy[i + 3] as Int == 0) continue

                var different = true
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val ni = ((y + dy) * w + (x + dx)) * 4
                        // If any neighbor has the same color and is visible, it's not isolated
                        if (copy[ni + 3] as Int > 0 &&
                            copy[ni] == copy[i] &&
                            copy[ni + 1] == copy[i + 1] &&
                            copy[ni + 2] == copy[i + 2]
                        ) {
                            different = false
                            break
                        }
                    }
                    if (!different) break
                }

                if (different) {
                    // Check for high contrast before removing. 
                    // High-contrast isolated pixels are likely intentional details (like eyes).
                    val r = copy[i] as Int
                    val g = copy[i + 1] as Int
                    val b = copy[i + 2] as Int
                    val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b

                    var maxLumDiff = 0.0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val ni = ((y + dy) * w + (x + dx)) * 4
                            if (copy[ni + 3] as Int > 0) {
                                val nr = copy[ni] as Int
                                val ng = copy[ni + 1] as Int
                                val nb = copy[ni + 2] as Int
                                val nlum = 0.2126 * nr + 0.7152 * ng + 0.0722 * nb
                                val diff = kotlin.math.abs(lum - nlum)
                                if (diff > maxLumDiff) maxLumDiff = diff
                            }
                        }
                    }

                    // If it's high contrast (diff > 50), preserve it.
                    if (maxLumDiff > 50.0) {
                        different = false
                    }
                }

                if (different) {
                    // Replace with a neighbor's color (just pick the first visible neighbor)
                    var replaced = false
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val ni = ((y + dy) * w + (x + dx)) * 4
                            if (copy[ni + 3] as Int > 0) {
                                data[i] = copy[ni]
                                data[i + 1] = copy[ni + 1]
                                data[i + 2] = copy[ni + 2]
                                replaced = true
                                break
                            }
                        }
                        if (replaced) break
                    }
                    // If no visible neighbor found, make it transparent
                    if (!replaced) {
                        data[i + 3] = 0
                    }
                }
            }
        }
    }

}
