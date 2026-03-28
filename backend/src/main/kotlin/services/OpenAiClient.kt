package services

import debug.WebSocketDebug
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import utils.json as appJson

/**
 * OpenAI client implementation for text prompts, transcription, and TTS.
 */
object OpenAiClient : AiClient {
    private val http: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
                isLenient = true
            })
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
        val message: ChatMessage,
        @SerialName("finish_reason")
        val finishReason: String? = null,
    )

    @Serializable
    private data class OpenAiRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double? = null,
        @SerialName("max_tokens")
        val maxTokens: Int? = null,
        @SerialName("response_format")
        val responseFormat: ResponseFormat? = null,
    )

    @Serializable
    private data class ResponseFormat(
        val type: String,
    )

    @Serializable
    private data class OpenAiResponse(
        val choices: List<Choice> = emptyList(),
    )

    @Serializable
    private data class TtsRequest(
        val model: String = "gpt-4o-mini-tts",
        val input: String,
        val voice: String = "coral",
        @SerialName("response_format")
        val responseFormat: String = "pcm",
    )

    override suspend fun promptText(
        systemPrompt: String,
        userPrompt: String,
        logSession: DefaultWebSocketServerSession?,
        temperature: Double?,
        thinking: Boolean?,
        reason: Boolean,
        json: Boolean
    ): String? {
        val apiKey = System.getenv("OPENAI_API_KEY") ?: System.getProperty("OPENAI_API_KEY")
        if (apiKey.isNullOrBlank()) {
            WebSocketDebug.logError("OpenAI API key missing", null, logSession)
            return null
        }

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt)
        )

        val body = OpenAiRequest(
            model = "gpt-4o-mini",
            messages = messages,
            temperature = temperature,
            maxTokens = 1024,
            responseFormat = if (json) ResponseFormat(type = "json_object") else null,
        )

        return try {
            val httpResp: HttpResponse = http.post("https://api.openai.com/v1/chat/completions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            if (!httpResp.status.isSuccess()) {
                val rawBody = runCatching { httpResp.bodyAsText() }.getOrNull()
                WebSocketDebug.logError("OpenAI HTTP ${httpResp.status.value}. Body: ${rawBody?.take(300)}", null, logSession)
                return null
            }

            val resp = httpResp.body<OpenAiResponse>()
            val content = resp.choices.firstOrNull()?.message?.content?.trim()
            content
        } catch (e: Throwable) {
            WebSocketDebug.logError("OpenAI call failed", e, logSession)
            null
        }
    }

    override suspend fun transcribe(audioUrl: String): String? {
        val apiKey = System.getenv("OPENAI_API_KEY") ?: System.getProperty("OPENAI_API_KEY")
        if (apiKey.isNullOrBlank()) return null

        return try {
            // 1. Download the audio file
            val audioBytes = http.get(audioUrl).body<ByteArray>()

            // 2. Upload to OpenAI Whisper
            val httpResp: HttpResponse = http.post("https://api.openai.com/v1/audio/transcriptions") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                val boundary = "WebAppBoundary"
                contentType(ContentType.MultiPart.FormData.withParameter("boundary", boundary))
                setBody(buildString {
                    append("--$boundary\r\n")
                    append("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n")
                    append("Content-Type: audio/wav\r\n\r\n")
                }.toByteArray() + audioBytes + buildString {
                    append("\r\n--$boundary\r\n")
                    append("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
                    append("whisper-1\r\n")
                    append("--$boundary--\r\n")
                }.toByteArray())
            }

            if (!httpResp.status.isSuccess()) {
                val err = httpResp.bodyAsText()
                println("[DEBUG_LOG] OpenAI Transcription error: $err")
                return null
            }

            @Serializable
            data class TranscriptionResponse(val text: String)
            val resp = httpResp.body<TranscriptionResponse>()
            resp.text
        } catch (e: Throwable) {
            println("[DEBUG_LOG] OpenAI Transcription exception: ${e.message}")
            null
        }
    }

    override suspend fun ttsStream(
        text: String,
        onChunk: suspend (ByteArray) -> Unit
    ): Boolean {
        val apiKey = System.getenv("OPENAI_API_KEY") ?: System.getProperty("OPENAI_API_KEY")
        if (apiKey.isNullOrBlank()) return false

        val body = TtsRequest(input = text)

        return try {
            val response = http.post("https://api.openai.com/v1/audio/speech") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            if (response.status.isSuccess()) {
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(8192)
                    if (!packet.exhausted()) {
                        onChunk(packet.readByteArray())
                    }
                }
                true
            } else {
                val err = runCatching { response.bodyAsText() }.getOrNull()
                println("[DEBUG_LOG] OpenAI TTS error: $err")
                false
            }
        } catch (e: Throwable) {
            println("[DEBUG_LOG] OpenAI TTS exception: ${e.message}")
            false
        }
    }

    override suspend fun tts(text: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val success = ttsStream(text) { chunk ->
            out.write(chunk)
        }
        return if (success && out.size() > 0) out.toByteArray() else null
    }
}
