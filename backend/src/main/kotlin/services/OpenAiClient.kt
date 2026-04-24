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
    private data class AiTextJson(
        val text: String,
    )

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
        val model: String,
        val input: String,
        val voice: String,
        @SerialName("response_format")
        val responseFormat: String,
    )

    @Serializable
    private data class ImageGenerationRequest(
        val model: String = "dall-e-3",
        val prompt: String,
        val n: Int = 1,
        val size: String = "1024x1024",
    )

    @Serializable
    private data class ImageData(
        val url: String,
    )

    @Serializable
    private data class ImageGenerationResponse(
        val created: Long,
        val data: List<ImageData>,
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
            maxTokens = 4096,
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
            var content = resp.choices.firstOrNull()?.message?.content?.trim() ?: ""

            if (content.startsWith("```")) {
                content = content.removePrefix("```")
                content = content.dropWhile { it != '\n' }.drop(1)
                val fenceIdx = content.lastIndexOf("```")
                if (fenceIdx >= 0) content = content.take(fenceIdx)
                content = content.trim()
            }

            val parsedText: String? = if (!json) {
                runCatching {
                    val parsed = appJson.decodeFromString(AiTextJson.serializer(), content)
                    parsed.text.takeIf { it.isNotBlank() }
                }.getOrNull()
            } else null

            val finalText = (parsedText ?: content).trim()
            finalText.ifBlank {
                if (logSession != null) {
                    WebSocketDebug.logMessage(
                        "AI_RESPONSE_BLANK",
                        "OpenAI yielded blank text after parsing. Content preview: ${content.take(200)}",
                        logSession
                    )
                }
                null
            }
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
        format: String,
        onChunk: suspend (ByteArray) -> Unit
    ): Boolean {
        val apiKey = System.getenv("OPENAI_API_KEY") ?: System.getProperty("OPENAI_API_KEY")
        if (apiKey.isNullOrBlank()) return false

        
        val actualFormat = if (format == "pcm") "pcm" else "mp3"

        // Construct body as a map to ensure absolute control over serialization
        val bodyMap = mapOf(
            "model" to "tts-1",
            "input" to text,
            "voice" to "alloy",
            "response_format" to actualFormat,
        )
        val bodyStr = appJson.encodeToString(bodyMap)

        return try {
            val response = http.post("https://api.openai.com/v1/audio/speech") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                val acceptType = if (actualFormat == "pcm") "audio/pcm" else "audio/mpeg"
                header(HttpHeaders.Accept, acceptType)
                contentType(ContentType.Application.Json)
                setBody(bodyStr)
            }

            val contentType = response.contentType()
            println("[DEBUG_LOG] OpenAI TTS Request: model=tts-1, voice=alloy, format=$actualFormat, textLen=${text.length}")
            println("[DEBUG_LOG] OpenAI TTS response status: ${response.status}, contentType: $contentType")

            if (response.status.isSuccess()) {
                val channel = response.bodyAsChannel()
                var first = true
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(8192)
                    if (!packet.exhausted()) {
                        val bytes = packet.readByteArray()
                        if (first && bytes.size >= 4) {
                            val sig = bytes.take(4).joinToString("") { (it.toInt() and 0xFF).toChar().toString() }
                            val hex = bytes.take(4).joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
                            println("[DEBUG_LOG] OpenAI TTS First bytes sig: $sig, hex: $hex, contentType: $contentType")
                            first = false
                        }
                        onChunk(bytes)
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

    override suspend fun tts(text: String, format: String): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val success = ttsStream(text, format) { chunk ->
            out.write(chunk)
        }
        return if (success && out.size() > 0) out.toByteArray() else null
    }

    override suspend fun generateImage(prompt: String, size: String): String? {
        val apiKey = System.getenv("OPENAI_API_KEY") ?: System.getProperty("OPENAI_API_KEY")
        if (apiKey.isNullOrBlank()) return null

        val body = ImageGenerationRequest(prompt = prompt, size = size.replace("*", "x"))

        return try {
            val response: HttpResponse = http.post("https://api.openai.com/v1/images/generations") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(body)
            }

            if (!response.status.isSuccess()) {
                val err = response.bodyAsText()
                println("[DEBUG_LOG] OpenAI Image Generation error: $err")
                return null
            }

            val data = response.body<ImageGenerationResponse>()
            data.data.firstOrNull()?.url
        } catch (e: Throwable) {
            println("[DEBUG_LOG] OpenAI Image Generation exception: ${e.message}")
            null
        }
    }
}
