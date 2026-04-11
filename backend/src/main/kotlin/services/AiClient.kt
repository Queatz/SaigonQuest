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
        return if (!token.isNullOrBlank()) {
            uploadAsset(bytes, "wav", token)
        } else {
            null
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
        val url = generateImage(prompt, size) ?: return null
        if (url.startsWith("http") && !url.contains("/gen/") && !url.contains("/static/")) {
            // Download and upload to content server if token is present
            return try {
                HttpClient(CIO).use { client ->
                    val bytes = client.get(url).body<ByteArray>()
                    if (!token.isNullOrBlank()) {
                        uploadAsset(bytes, "png", token)
                    } else {
                        null
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                null
            } ?: url
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

}
