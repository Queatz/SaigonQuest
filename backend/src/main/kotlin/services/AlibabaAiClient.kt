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
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.server.websocket.DefaultWebSocketServerSession
import utils.json as appJson

/**
 * Alibaba AI client utilizing the DashScope OpenAI-compatible API.
 *
 * Region: ap-southeast-1
 * Model: Qwen3.5-Flash
 */
object AlibabaAiClient : AiClient {
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
    private data class AlibabaRequest(
        val model: String = "qwen3.5-flash",
        val messages: List<ChatMessage>,
        val temperature: Double? = null,
        @SerialName("max_tokens")
        val maxTokens: Int? = 1024,
        @SerialName("response_format")
        val responseFormat: ResponseFormat? = null,
    )

    @Serializable
    private data class AlibabaResponse(
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

    override suspend fun promptText(
        systemPrompt: String,
        userPrompt: String,
        logSession: DefaultWebSocketServerSession?,
        temperature: Double?,
        thinking: Boolean?,
        reason: Boolean,
        json: Boolean,
    ): String? {
        val apiKey = System.getenv("ALIBABA_API_KEY")
            ?: System.getProperty("ALIBABA_API_KEY")
            ?: "sk-6c257c3e09b349bf81c631f31ef6679a"

        if (apiKey.isBlank()) {
            WebSocketDebug.logError("Alibaba API key missing", null, logSession)
            return null
        }

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt)
        )

        val body = AlibabaRequest(
            messages = messages,
            temperature = temperature,
            model = "qwen3.5-flash",
            responseFormat = if (json) ResponseFormat(type = "json_object") else null,
        )

        return try {
            val httpResp: HttpResponse = http.post("https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            val statusOk = httpResp.status.isSuccess()
            if (!statusOk) {
                val rawBody = runCatching { httpResp.bodyAsText() }.getOrNull()
                WebSocketDebug.logError(
                    "Alibaba HTTP ${httpResp.status.value}: ${httpResp.status.description}. Body preview: ${
                        rawBody?.take(300)
                    }", null, logSession
                )
                return null
            }

            val resp: AlibabaResponse? = runCatching { httpResp.body<AlibabaResponse>() }.getOrNull()
            var content = resp?.choices?.firstOrNull()?.message?.content

            if (content.isNullOrBlank()) {
                val rawBody = runCatching { httpResp.bodyAsText() }.getOrNull()
                WebSocketDebug.logMessage(
                    "AI_RESPONSE_PREVIEW",
                    "Alibaba returned empty content. Raw body preview: ${rawBody?.take(300)}",
                    logSession
                )
                return null
            }

            content = content.trim()
            if (content.startsWith("```")) {
                content = content.removePrefix("```")
                content = content.dropWhile { it != '\n' }.drop(1)
                val fenceIdx = content.lastIndexOf("```")
                if (fenceIdx >= 0) content = content.take(fenceIdx)
                content = content.trim()
            }

            val parsedText: String? = runCatching {
                appJson.decodeFromString(AiTextJson.serializer(), content).text
            }.getOrNull()

            val finalText = (parsedText ?: content).trim()
            finalText.ifBlank {
                if (logSession != null) {
                    WebSocketDebug.logMessage(
                        "AI_NODE_RESPONSE_PREVIEW",
                        "Alibaba yielded blank text after parsing. Content preview: ${content.take(200)}",
                        logSession
                    )
                }
                null
            }
        } catch (e: Throwable) {
            WebSocketDebug.logError("Alibaba call failed", e, logSession)
            null
        }
    }
}
