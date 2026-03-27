package services

import debug.WebSocketDebug
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.server.websocket.DefaultWebSocketServerSession
import utils.json as appJson

/**
 * Minimal DeepSeek client utilizing the recommended Structured JSON API.
 *
 * Reads API key from environment variable `DEEPSEEK_API_KEY`.
 */
object DeepSeekClient : AiClient {
    private val http: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(appJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 60_000
            socketTimeoutMillis = 120_000
        }
    }

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class Choice(
        val index: Int? = null,
        val message: ChatMessage,
        @SerialName("finish_reason")
        val finishReason: String? = null,
    )

    @Serializable
    private data class ResponseFormat(
        val type: String? = "text",
    )

    @Serializable
    private data class Thinking(
        val type: String,
    )

    @Serializable
    private data class DeepSeekRequest(
        val model: String = "deepseek-chat",
        val messages: List<ChatMessage>,
        val thinking: Thinking? = null,
        val temperature: Double? = null,
        @SerialName("max_tokens")
        val maxTokens: Int? = 1024,
        @SerialName("response_format")
        val responseFormat: ResponseFormat? = null,
    )

    @Serializable
    private data class DeepSeekResponse(
        val id: String? = null,
        @SerialName("object")
        val obj: String? = null,
        val created: Long? = null,
        val model: String? = null,
        val choices: List<Choice> = emptyList(),
    )

    @Serializable
    private data class AiTextJson(
        val text: String,
    )

    /**
     * Call DeepSeek with the provided system and user prompts and return extracted text.
     * If the provider does not return valid JSON, falls back to raw content.
     */
    override suspend fun promptText(
        systemPrompt: String,
        userPrompt: String,
        logSession: DefaultWebSocketServerSession?,
        temperature: Double?,
        thinking: Boolean?,
        reason: Boolean,
        json: Boolean,
    ): String? {
        val apiKey = System.getenv("DEEPSEEK_API_KEY")
            ?: System.getProperty("DEEPSEEK_API_KEY")

        if (apiKey.isNullOrBlank()) {
            WebSocketDebug.logError("DeepSeek API key missing (DEEPSEEK_API_KEY)", null, logSession)
            return null
        }

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt)
        )

        val body = DeepSeekRequest(
            messages = messages,
            temperature = temperature,
            model = if (reason) "deepseek-reasoner" else "deepseek-chat",
            responseFormat = if (json) ResponseFormat(type = "json_object") else null,
            thinking = thinking?.let { Thinking(type = if (it) "enabled" else "disabled") },
        )

        return try {
            val httpResp: HttpResponse = http.post("https://api.deepseek.com/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            val statusOk = httpResp.status.isSuccess()
            if (!statusOk) {
                val rawBody = runCatching { httpResp.bodyAsText() }.getOrNull()
                WebSocketDebug.logError(
                    "DeepSeek HTTP ${httpResp.status.value}: ${httpResp.status.description}. Body preview: ${
                        rawBody?.take(
                            300
                        )
                    }", null, logSession
                )
                return null
            }

            // Try to deserialize the structured response
            val resp: DeepSeekResponse? = runCatching { httpResp.body<DeepSeekResponse>() }.getOrNull()
            var content = resp?.choices?.firstOrNull()?.message?.content

            if (content.isNullOrBlank()) {
                val rawBody = runCatching { httpResp.bodyAsText() }.getOrNull()
                WebSocketDebug.logMessage(
                    "AI_RESPONSE_PREVIEW",
                    "DeepSeek returned empty content. Raw body preview: ${rawBody?.take(300)}",
                    logSession
                )
                return null
            }

            // Normalize content: strip common code fences and trim
            content = content.trim()
            if (content.startsWith("```")) {
                // Remove triple backtick fences like ```json ... ```
                content = content.removePrefix("```")
                // Drop optional language tag
                content = content.dropWhile { it != '\n' }.drop(1)
                val fenceIdx = content.lastIndexOf("```")
                if (fenceIdx >= 0) content = content.take(fenceIdx)
                content = content.trim()
            }

            // Try to decode structured JSON with `text`
            val parsedText: String? = runCatching {
                appJson.decodeFromString(AiTextJson.serializer(), content).text
            }.getOrNull()

            val finalText = (parsedText ?: content).trim()
            finalText.ifBlank {
                if (logSession != null) {
                    WebSocketDebug.logMessage(
                        "AI_NODE_RESPONSE_PREVIEW",
                        "DeepSeek yielded blank text after parsing. Content preview: ${content.take(200)}",
                        logSession
                    )
                }
                null
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("DeepSeek call failed", e, logSession)
            null
        }
    }

    override suspend fun transcribe(audioUrl: String): String? = null
    
    override suspend fun ttsStream(
        text: String,
        onChunk: suspend (ByteArray) -> Unit,
    ): Boolean = false

    override suspend fun tts(text: String): ByteArray? = null
}
