package services

import debug.WebSocketDebug
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.get
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
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.delay
import utils.json as appJson

/**
 * Alibaba AI client utilizing the DashScope OpenAI-compatible API.
 *
 * Region: ap-southeast-1
 * Model: Qwen3.5-Flash
 */
object AlibabaAiClient : AiClient {
    private val http: HttpClient = HttpClient(CIO) {
        install(ClientWebSockets)
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
        val role: String = "",
        val content: String = "",
    )

    @Serializable
    private data class Choice(
        val index: Int? = null,
        val message: ChatMessage = ChatMessage(),
        @SerialName("finish_reason")
        val finishReason: String? = null,
    )

    @Serializable
    private data class ResponseFormat(
        val type: String? = "text",
    )

    @Serializable
    private data class AlibabaRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Float? = null,
        @SerialName("max_tokens")
        val maxTokens: Int? = null,
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
    private data class AsrInput(
        @SerialName("file_urls")
        val fileUrls: List<String>,
        @SerialName("audio_urls")
        val audioUrls: List<String>,
    )

    @Serializable
    private data class AsrRequest(
        val model: String,
        val input: AsrInput,
        val parameters: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class AsrResultItem(
        @SerialName("file_url")
        val fileUrl: String? = null,
        @SerialName("transcription_url")
        val transcriptionUrl: String? = null,
        @SerialName("subtask_status")
        val subtaskStatus: String? = null,
        val transcription: String? = null,
        val text: String? = null,
    )

    @Serializable
    private data class AsrTaskOutput(
        @SerialName("task_id")
        val taskId: String? = null,
        @SerialName("task_status")
        val taskStatus: String? = null,
        val results: List<AsrResultItem>? = null,
        val code: String? = null,
        val message: String? = null,
        @SerialName("error_code")
        val errorCode: String? = null,
        @SerialName("error_message")
        val errorMessage: String? = null,
    )

    @Serializable
    private data class TranscriptionFileResult(
        val text: String? = null,
        val transcription: String? = null,
        val sentences: List<TranscriptionSentence>? = null,
        val transcripts: List<TranscriptionTranscript>? = null,
    )

    @Serializable
    private data class TranscriptionTranscript(
        val text: String? = null,
        val sentences: List<TranscriptionSentence>? = null,
    )

    @Serializable
    private data class TranscriptionSentence(
        val text: String? = null,
        val transcription: String? = null,
    )

    @Serializable
    private data class AsrTaskResponse(
        val output: AsrTaskOutput? = null,
        @SerialName("request_id")
        val requestId: String? = null,
        val message: String? = null,
        val code: String? = null,
    )

    @Serializable
    private data class AiTextJson(
        val text: String = "",
    )

    @Serializable
    private data class TtsInput(
        val text: String,
    )

    @Serializable
    private data class TtsParameters(
        val voice: String = "longxiaoxia_v3",
        val format: String = "mp3",
        @SerialName("sample_rate")
        val sampleRate: Int = 22050,
        @SerialName("text_type")
        val textType: String = "PlainText",
        val volume: Int = 50,
        val rate: Double = 1.0,
        val pitch: Double = 1.0,
    )

    @Serializable
    private data class WsHeader(
        val action: String,
        @SerialName("task_id")
        val taskId: String,
        @SerialName("message_id")
        val messageId: String,
        val streaming: String = "duplex",
    )

    @Serializable
    private data class WsPayload(
        val model: String,
        val input: TtsInput,
        val parameters: TtsParameters,
        @SerialName("task_group")
        val taskGroup: String = "audio",
        val task: String = "tts",
        val function: String = "SpeechSynthesizer",
    )

    @Serializable
    private data class WsRequest(
        val header: WsHeader,
        val payload: WsPayload,
    )

    @Serializable
    private data class WsFinishPayload(
        val input: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class WsFinishRequest(
        val header: WsHeader,
        val payload: WsFinishPayload = WsFinishPayload(),
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
            ?: "sk-6c257c3e09b349bf81c631f31ef6679a" // CN: sk-ac963bed6f3e41acb2491a9e40549082

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
            temperature = temperature?.toFloat(),
            model = "qwen3.5-flash",
            maxTokens = 1024,
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

    override suspend fun transcribe(audioUrl: String): String? {
        println("[DEBUG_LOG] AlibabaAiClient.transcribe called with URL: $audioUrl")
        
        val apiKey = System.getenv("ALIBABA_API_KEY")
            ?: System.getProperty("ALIBABA_API_KEY")
            ?: "sk-6c257c3e09b349bf81c631f31ef6679a"

        if (apiKey.isBlank()) {
            println("[DEBUG_LOG] Alibaba API Key is blank, returning null")
            return null
        }
        
        val body = AsrRequest(
            model = "fun-asr",
            input = AsrInput(
                fileUrls = listOf(audioUrl),
                audioUrls = listOf(audioUrl)
            ),
            parameters = emptyMap()
        )

        return try {
            // 1. Submit transcription task
            println("[DEBUG_LOG] Submitting transcription task to DashScope...")
            val submitResp: HttpResponse = http.post("https://dashscope-intl.aliyuncs.com/api/v1/services/audio/asr/transcription") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header("X-DashScope-Async", "enable")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            
            if (!submitResp.status.isSuccess()) {
                val errorBody = runCatching { submitResp.bodyAsText() }.getOrNull()
                println("[DEBUG_LOG] Alibaba ASR submit HTTP error: ${submitResp.status} - $errorBody")
                return null
            }

            val submitData = submitResp.body<AsrTaskResponse>()
            val taskId = submitData.output?.taskId
            if (taskId.isNullOrBlank()) {
                println("[DEBUG_LOG] Alibaba ASR submit failed to get taskId. Code: ${submitData.code}, Message: ${submitData.message}")
                return null
            }
            
            println("[DEBUG_LOG] Alibaba ASR task submitted successfully. taskId: $taskId")

            // 2. Poll for the result (max 60 seconds)
            var transcript: String? = null
            var attempts = 0
            while (attempts < 60) {
                delay(1000)
                if (attempts % 10 == 0) {
                    println("[DEBUG_LOG] Polling for task result ($taskId), attempt $attempts/60...")
                }
                
                val pollResp: HttpResponse = http.get("https://dashscope-intl.aliyuncs.com/api/v1/tasks/$taskId") {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                }
                
                if (pollResp.status.isSuccess()) {
                    val pollRaw = pollResp.bodyAsText()
                    val pollData = try {
                        appJson.decodeFromString<AsrTaskResponse>(pollRaw)
                    } catch (e: Throwable) {
                        println("[DEBUG_LOG] Alibaba ASR poll JSON parse error: ${e.message}. Raw: $pollRaw")
                        null
                    }
                    
                    if (pollData != null) {
                        val status = pollData.output?.taskStatus
                        if (status == "SUCCEEDED") {
                            println("[DEBUG_LOG] Alibaba ASR task SUCCEEDED. Parsing results...")
                            val res = pollData.output.results?.firstOrNull()
                            transcript = res?.transcription ?: res?.text

                            if (transcript == null && !res?.transcriptionUrl.isNullOrBlank()) {
                                val url = res.transcriptionUrl
                                println("[DEBUG_LOG] Fetching transcription from URL: $url")
                                try {
                                    val fileResp: HttpResponse = http.get(url)
                                    if (fileResp.status.isSuccess()) {
                                        val fileRaw = fileResp.bodyAsText()
                                        println("[DEBUG_LOG] Transcription file content: ${fileRaw.take(500)}")

                                        val items = try {
                                            appJson.decodeFromString<List<TranscriptionFileResult>>(fileRaw)
                                        } catch (e: Throwable) {
                                            null
                                        }

                                        if (!items.isNullOrEmpty()) {
                                            transcript = items.mapNotNull { 
                                                it.text ?: it.transcription ?: it.transcripts?.mapNotNull { t -> t.text }?.joinToString(" ") 
                                            }.joinToString("\n").trim()
                                        } else {
                                            val singleItem = try {
                                                appJson.decodeFromString<TranscriptionFileResult>(fileRaw)
                                            } catch (e: Throwable) {
                                                null
                                            }
                                            transcript = singleItem?.text ?: singleItem?.transcription ?: singleItem?.transcripts?.mapNotNull { it.text }?.joinToString(" ")
                                        }
                                    } else {
                                        println("[DEBUG_LOG] Failed to fetch transcription file. Status: ${fileResp.status}")
                                    }
                                } catch (e: Throwable) {
                                    println("[DEBUG_LOG] Error fetching/parsing transcription file: ${e.message}")
                                }
                            }

                            if (transcript.isNullOrBlank()) {
                                println("[DEBUG_LOG] Alibaba ASR task SUCCEEDED but no transcription found in results. Full: $pollRaw")
                            }
                            break
                        } else if (status == "FAILED" || status == "CANCELLED") {
                            val errCode = pollData.output.errorCode ?: pollData.output.code ?: pollData.code
                            val errMsg = pollData.output.errorMessage ?: pollData.output.message ?: pollData.message
                            println("[DEBUG_LOG] Alibaba ASR task $status. Code: $errCode, Message: $errMsg. Full: $pollRaw")
                            break
                        } else {
                            // Still processing or in queue
                        }
                    }
                } else {
                    val pollError = runCatching { pollResp.bodyAsText() }.getOrNull()
                    println("[DEBUG_LOG] Alibaba ASR poll HTTP error: ${pollResp.status} - $pollError")
                }
                attempts++
            }
            
            if (transcript == null && attempts >= 60) {
                println("[DEBUG_LOG] Alibaba ASR polling timed out after 60 seconds for taskId: $taskId")
            }
            
            transcript?.trim()
        } catch (e: Throwable) {
            println("[DEBUG_LOG] Alibaba ASR exception: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override suspend fun tts(text: String): ByteArray? {
        val apiKey = System.getenv("ALIBABA_API_KEY")
            ?: System.getProperty("ALIBABA_API_KEY")
            ?: "sk-6c257c3e09b349bf81c631f31ef6679a"

        if (apiKey.isBlank()) {
            return null
        }

        val taskId = java.util.UUID.randomUUID().toString().replace("-", "")
        val messageId = java.util.UUID.randomUUID().toString().replace("-", "")
        
        // We use cosyvoice-v3-flash and dashscope-intl as we are outside of China Mainland.
        val request = WsRequest(
            header = WsHeader(
                action = "run-task",
                taskId = taskId,
                messageId = messageId
            ),
            payload = WsPayload(
                model = "cosyvoice-v3-flash",
                input = TtsInput(text = text),
                parameters = TtsParameters()
            )
        )

        val out = java.io.ByteArrayOutputStream()
        var success = false

        return try {
            http.webSocket(
                urlString = "wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference/",
                request = {
                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                }
            ) {
                send(Frame.Text(appJson.encodeToString(request)))

                // After sending run-task, we must send finish-task to notify the server that input is complete
                // when using streaming: duplex.
                val finishMessageId = java.util.UUID.randomUUID().toString().replace("-", "")
                val finishRequest = WsFinishRequest(
                    header = WsHeader(
                        action = "finish-task",
                        taskId = taskId,
                        messageId = finishMessageId
                    )
                )
                send(Frame.Text(appJson.encodeToString(finishRequest)))

                for (frame in incoming) {
                    when (frame) {
                        is Frame.Binary -> {
                            out.write(frame.readBytes())
                        }
                        is Frame.Text -> {
                            val textMsg = frame.readText()
                            if (textMsg.contains("task-finished")) {
                                success = true
                                break
                            }
                            if (textMsg.contains("task-failed") || textMsg.contains("error")) {
                                println("[DEBUG_LOG] Alibaba TTS Ws error: $textMsg")
                                break
                            }
                        }
                        else -> {}
                    }
                }
            }
            if (success && out.size() > 0) out.toByteArray() else null
        } catch (e: Throwable) {
            println("[DEBUG_LOG] Alibaba TTS Ws exception: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
