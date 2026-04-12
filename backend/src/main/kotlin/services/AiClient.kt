package services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
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
        onChunk: suspend (ByteArray) -> Unit,
    ): Boolean

    /**
     * Synthesize speech for the provided text and return the binary audio data.
     */
    suspend fun tts(
        text: String,
    ): ByteArray?

    /**
     * Synthesize speech for the provided text and return the persistent audio URL.
     */
    suspend fun ttsUrl(
        text: String,
        token: String? = null,
    ): String? {
        val bytes = tts(text) ?: return null
        return if (token != null) {
            uploadAsset(bytes, "wav", token)
        } else {
            saveGenFile(bytes, "wav")
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
     * Generate an image from the provided prompt and return a persistent local URL.
     */
    suspend fun generateImageUrl(
        prompt: String,
        size: String = "1024*1024",
        token: String? = null,
    ): String? {
        val styledPrompt = "$prompt, dreamy, watercolor illustrations with soft, vibrant colors, NO TEXT"
        val url = generateImage(styledPrompt, size) ?: return null
        if (url.startsWith("http") && !url.contains("/gen/") && !url.contains("/static/")) {
            // Download and save locally or upload to content server
            return try {
                HttpClient(CIO).use { client ->
                    val bytes = client.get(url).body<ByteArray>()
                    if (token != null) {
                        uploadAsset(bytes, "png", token)
                    } else {
                        saveGenFile(bytes, "png")
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                url // Fallback to temporary URL
            }
        }
        return url
    }

    /**
     * Helper to upload bytes to the content server
     */
    suspend fun uploadAsset(bytes: ByteArray, extension: String, token: String): String? {
        val tempFile = java.io.File.createTempFile("ai_gen_", ".$extension")
        return try {
            tempFile.writeBytes(bytes)
            utils.AssetUploader.upload(tempFile, token)
        } finally {
            try { tempFile.delete() } catch (_: Throwable) {}
        }
    }

    /**
     * Helper to save bytes to gen/ directory and return the URL
     */
    fun saveGenFile(bytes: ByteArray, extension: String): String? {
        val fileName = "${java.util.UUID.randomUUID()}.$extension"
        val dir = java.io.File("gen")
        if (!dir.exists()) dir.mkdirs()
        val file = java.io.File(dir, fileName)
        return try {
            file.writeBytes(bytes)
            val apiBase = (System.getenv("API_SERVER_URL") ?: System.getProperty("API_SERVER_URL") ?: "https://api.saigon.quest").trimEnd('/')
            "$apiBase/gen/$fileName"
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}
