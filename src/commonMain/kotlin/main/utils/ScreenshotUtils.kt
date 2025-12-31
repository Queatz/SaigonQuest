package main.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import lib.ScreenshotOptions
import lib.Tools
import main.components.SceneManager
import org.w3c.dom.HTMLAnchorElement
import kotlinx.browser.document
import org.w3c.files.File
import org.w3c.files.FilePropertyBag
import kotlinx.coroutines.await
import kotlinx.browser.window
import main.api.ContentApi
import main.api.UserApi
import org.w3c.fetch.RequestInit
import kotlin.coroutines.resume
import kotlin.js.Date

/**
 * Utilities for capturing and downloading BabylonJS scene screenshots.
 */
object ScreenshotUtils {
    /**
     * Captures a PNG screenshot of the current scene using the active camera.
     * Returns a data URL (e.g., "data:image/png;base64,...").
     *
     * Throws when the scene/engine/camera is unavailable.
     */
    suspend fun captureCurrentScenePng(): String = suspendCancellableCoroutine { cont ->
        val scene = SceneManager.getScene()
        if (scene == null) {
            cont.resumeWith(Result.failure(IllegalStateException("Scene not available for screenshot")))
            return@suspendCancellableCoroutine
        }
        val engine = scene.getEngine()
        val camera = scene.activeCamera
        try {
            val options = object : ScreenshotOptions {
                override val width: Int = engine.getRenderWidth()
                override val height: Int = engine.getRenderHeight()
            }
            Tools.CreateScreenshot(
                engine = engine,
                camera = camera,
                options = options,
            ) { data ->
                // Success: resume with data URL
                if (!cont.isCancelled) {
                    cont.resume(data)
                }
            }
        } catch (t: Throwable) {
            if (!cont.isCancelled) cont.resumeWith(Result.failure(t))
        }

        // If coroutine is cancelled, nothing special to clean up here.
    }

    /**
     * Converts a data URL to a File using the browser fetch + blob APIs.
     */
    suspend fun dataUrlToFile(dataUrl: String, filename: String, mimeType: String = "image/png"): File {
        val response = window.fetch(dataUrl, RequestInit()).await()
        val blob = response.blob().await()
        return File(arrayOf(blob), filename, FilePropertyBag(type = mimeType))
    }

    /**
     * Captures the current scene and returns a File (PNG) with a sensible filename.
     */
    suspend fun captureCurrentScenePngFile(filename: String = buildScreenshotFilename(null)): File {
        val dataUrl = captureCurrentScenePng()
        return dataUrlToFile(dataUrl, filename, "image/png")
    }

    /**
     * Captures screenshot and uploads it to the Content API, returning the hosted URL.
     */
    suspend fun captureAndUploadWorldScreenshot(worldTitle: String?): String {
        val filename = buildScreenshotFilename(worldTitle)
        val file = captureCurrentScenePngFile(filename)
        val token = UserApi.getToken() ?: ""
        val record = ContentApi.upload(file, token)
        return ContentApi.imageUrlFor(record.uploadedFilePath)
    }

    /**
     * Downloads a given data URL as a file with the provided filename.
     */
    fun downloadDataUrlAsFile(dataUrl: String, filename: String) {
        val link = document.createElement("a") as HTMLAnchorElement
        link.href = dataUrl
        link.download = sanitizeFilename(filename)
        document.body?.appendChild(link)
        link.click()
        document.body?.removeChild(link)
    }

    /**
     * Builds a nice default filename for screenshots using the world title and the current timestamp.
     */
    fun buildScreenshotFilename(worldTitle: String?, prefix: String = "saigon-quest"): String {
        val date = Date()
        fun pad(n: Int): String = if (n < 10) "0$n" else "$n"
        val timestamp = "${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}_" +
            "${pad(date.getHours())}-${pad(date.getMinutes())}-${pad(date.getSeconds())}"
        val safeWorldName = sanitizeFilename(worldTitle?.trim().orEmpty().ifEmpty { "world" })
        return "$prefix-$safeWorldName-$timestamp.png"
    }

    private fun sanitizeFilename(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "")
            .replace(Regex("\\s+"), "-")
    }
}
