package services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.http.*
import io.ktor.server.websocket.DefaultWebSocketServerSession

/**
 * Common interface for AI clients.
 */
interface AiClient {
    /**
     * Call the AI with the provided system and user prompts and return extracted text.
     */
    suspend fun promptText(
        systemPrompt: String,
        userPrompt: String,
        logSession: DefaultWebSocketServerSession? = null,
        temperature: Double? = null,
        thinking: Boolean? = null,
        reason: Boolean = false,
        json: Boolean = false,
    ): String?
    /**
     * Transcribe the provided audio URL and return the text.
     */
    suspend fun transcribe(
        audioUrl: String,
    ): String?
    /**
     * Synthesize speech for the provided text and stream binary audio data to the output.
     */
    suspend fun ttsStream(
        text: String,
        format: String = "pcm",
        onChunk: suspend (ByteArray) -> Unit,
    ): Boolean

    /**
     * Synthesize speech for the provided text and return the binary audio data.
     */
    suspend fun tts(
        text: String,
        format: String = "mp3",
    ): ByteArray?

    /**
     * Synthesize speech for the provided text and return the persistent audio URL.
     */
    suspend fun ttsUrl(
        text: String,
        token: String? = null,
    ): String? {
        val format = "mp3"
        val bytes = tts(text, format) ?: return null
        return if (token != null) {
            uploadAsset(bytes, format, token)
        } else {
            saveGenFile(bytes, format)
        }
    }

    /**
     * Generate an image from the provided prompt and return the image URL.
     */
    suspend fun generateImage(
        prompt: String,
        size: String = "1024*1024",
    ): String?

    /**
     * Remove the background from the provided image URL and return the processed image bytes.
     */
    suspend fun removeBackground(
        imageUrl: String,
    ): ByteArray?

    /**
     * Generate an image from the provided prompt and return a persistent local URL.
     */
    suspend fun generateImageUrl(
        prompt: String,
        size: String = "1024*1024",
        token: String? = null,
        style: String = "In the style of a detailed studio ghibli illustrations with vibrant colors.",
    ): String? {
        val styledPrompt = "$prompt\n\n$style"
        val url = generateImage(styledPrompt, size) ?: return null
        return downloadAndSave(url, token)
    }

    /**
     * Remove the background from the provided image URL and return a persistent local URL.
     */
    suspend fun removeBackgroundUrl(
        imageUrl: String,
        token: String? = null,
    ): String? {
        val bytes = removeBackground(imageUrl) ?: return null
        return if (token != null) {
            uploadAsset(bytes, "png", token)
        } else {
            saveGenFile(bytes, "png")
        }
    }

    /**
     * Helper to get bytes from a URL, either local or remote.
     */
    suspend fun getBytes(url: String): ByteArray? {
        val apiBase = (System.getenv("API_SERVER_URL") ?: System.getProperty("API_SERVER_URL") ?: "http://0.0.0.0:8080").trimEnd('/')
        if (url.startsWith(apiBase + "/gen/")) {
            val fileName = url.substringAfter("/gen/")
            val file = java.io.File("gen", fileName)
            if (file.exists()) {
                return try {
                    file.readBytes()
                } catch (e: Throwable) {
                    e.printStackTrace()
                    null
                }
            }
        }
        
        return try {
            HttpClient(CIO).use { client ->
                val response = client.get(url)
                if (response.status.isSuccess()) {
                    response.body<ByteArray>()
                } else {
                    println("[ERROR] getBytes failed for $url: ${response.status}")
                    null
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Helper to download an image from a URL and save it locally or upload to content server.
     */
    private suspend fun downloadAndSave(url: String, token: String?): String? {
        val contentServerUrl = (System.getenv("CONTENT_SERVER_URL") ?: System.getProperty("CONTENT_SERVER_URL") ?: "http://0.0.0.0:8090").trimEnd('/')
        val isAlreadyOnContent = url.startsWith(contentServerUrl)
        val isGen = url.contains("/gen/")
        val isStatic = url.contains("/static/")

        if (url.startsWith("http") && ((token != null && !isAlreadyOnContent) || (token == null && !isGen && !isStatic))) {
            // Download and save locally or upload to content server
            val bytes = getBytes(url) ?: return url
            return if (token != null) {
                uploadAsset(bytes, "png", token)
            } else {
                saveGenFile(bytes, "png")
            }
        }
        return url
    }

    /**
     * Helper to upload bytes to the content server
     */
    suspend fun uploadAsset(bytes: ByteArray, extension: String, token: String): String? {
        println("[DEBUG_LOG] uploadAsset called with ${bytes.size} bytes")
        if (bytes.isEmpty()) {
            println("[ERROR] uploadAsset called with empty bytes!")
            return null
        }
        val tempFile = java.io.File.createTempFile("ai_gen_", ".$extension")
        return try {
            tempFile.writeBytes(bytes)
            println("[DEBUG_LOG] uploadAsset: Written to temp file ${tempFile.absolutePath}, size: ${tempFile.length()}")
            utils.AssetUploader.upload(tempFile, token)
        } finally {
            try { tempFile.delete() } catch (_: Throwable) {}
        }
    }

    /**
     * Helper to save bytes to gen/ directory and return the URL
     */
    fun saveGenFile(bytes: ByteArray, extension: String): String? {
        println("[DEBUG_LOG] saveGenFile called with ${bytes.size} bytes")
        if (bytes.isEmpty()) {
            println("[ERROR] saveGenFile called with empty bytes!")
            return null
        }
        val fileName = "${java.util.UUID.randomUUID()}.$extension"
        val dir = java.io.File("gen")
        if (!dir.exists()) dir.mkdirs()
        val file = java.io.File(dir, fileName)
        return try {
            file.writeBytes(bytes)
            println("[DEBUG_LOG] saveGenFile: Written to ${file.absolutePath}, size: ${file.length()}")
            val apiBase = (System.getenv("API_SERVER_URL") ?: System.getProperty("API_SERVER_URL") ?: "http://0.0.0.0:8080").trimEnd('/')
            "$apiBase/gen/$fileName"
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}
