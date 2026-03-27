package services

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
}
