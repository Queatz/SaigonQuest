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
}
