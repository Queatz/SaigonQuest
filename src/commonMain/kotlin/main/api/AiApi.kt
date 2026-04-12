package main.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.url
import io.ktor.client.request.parameter
import io.ktor.client.request.preparePost
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import models.AiPromptHistory
import models.AiPromptRequest
import models.AiTtsRequest
import models.AiImageRequest
import models.AiImageResponse
import models.ErrorResponse
import models.MinigameGenerateRequest
import models.MinigameItem
import models.MinigameItemsResponse
import models.MinigameType
import utils.json

object AiApi {
    suspend fun generateImage(prompt: String, size: String = "1024*1024"): String {
        val resp = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/ai/image") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(AiImageRequest(prompt = prompt, size = size))
        }
        if (resp.status.isSuccess()) {
            return resp.body<AiImageResponse>().imageUrl
        } else {
            val err = runCatching { resp.body<ErrorResponse>() }.getOrNull()
            val txt = err?.message ?: runCatching { resp.bodyAsText() }.getOrNull()
            throw Error(txt ?: "Image generation failed: HTTP ${resp.status.value}")
        }
    }
    suspend fun sendPrompt(
        prompt: String,
        docContents: String,
        caretStart: Int,
        caretEnd: Int,
        docId: String? = null,
        docName: String? = null,
    ): String {
        val resp = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/ai/prompt") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(AiPromptRequest(
                prompt = prompt,
                docContents = docContents,
                caretStart = caretStart,
                caretEnd = caretEnd,
                docId = docId,
                docName = docName,
            ))
        }
        if (resp.status.isSuccess()) {
            return resp.bodyAsText()
        } else {
            val err = runCatching { resp.body<ErrorResponse>() }.getOrNull()
            val txt = err?.message ?: runCatching { resp.bodyAsText() }.getOrNull()
            throw Error(txt ?: "AI request failed: HTTP ${resp.status.value}")
        }
    }

    suspend fun getHistory(limit: Int = 20): List<AiPromptHistory> {
        val resp = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/ai/history") {
            ApiClient.addAuthHeader(this)
            parameter("limit", limit)
        }
        if (resp.status.isSuccess()) {
            return resp.body()
        } else {
            val txt = runCatching { resp.bodyAsText() }.getOrNull()
            throw Error(txt ?: "Failed to load AI history: HTTP ${resp.status.value}")
        }
    }

    suspend fun generateTts(text: String, format: String = "mp3"): ByteArray {
        val resp = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/ai/tts") {
            contentType(ContentType.Application.Json)
            parameter("format", format)
            ApiClient.addAuthHeader(this)
            setBody(AiTtsRequest(text = text))
        }
        if (resp.status.isSuccess()) {
            return resp.body()
        } else {
            val err = runCatching { resp.body<ErrorResponse>() }.getOrNull()
            val txt = err?.message ?: runCatching { resp.bodyAsText() }.getOrNull()
            throw Error(txt ?: "TTS request failed: HTTP ${resp.status.value}")
        }
    }

    suspend fun generateTtsStream(text: String, format: String = "pcm", onChunk: suspend (ByteArray) -> Unit) {
        val request = ApiClient.httpClient.preparePost("${ApiConstants.API_BASE_URL}/ai/tts") {
            contentType(ContentType.Application.Json)
            parameter("format", format)
            ApiClient.addAuthHeader(this)
            setBody(AiTtsRequest(text = text))
        }

        request.execute { response ->
            if (response.status.isSuccess()) {
                val channel = response.bodyAsChannel()
                var leftover: Byte? = null
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(8192)
                    if (!packet.exhausted()) {
                        val rawBytes = packet.readByteArray()
                        val bytes = if (leftover != null) {
                            val combined = ByteArray(rawBytes.size + 1)
                            combined[0] = leftover
                            rawBytes.copyInto(combined, 1)
                            combined
                        } else {
                            rawBytes
                        }

                        if (bytes.size % 2 != 0) {
                            leftover = bytes[bytes.size - 1]
                            val evenBytes = bytes.copyOfRange(0, bytes.size - 1)
                            if (evenBytes.isNotEmpty()) {
                                onChunk(evenBytes)
                            }
                        } else {
                            leftover = null
                            if (bytes.isNotEmpty()) {
                                onChunk(bytes)
                            }
                        }
                    }
                }
            } else {
                val err = runCatching { response.body<ErrorResponse>() }.getOrNull()
                val txt = err?.message ?: runCatching { response.bodyAsText() }.getOrNull()
                throw Error(txt ?: "TTS request failed: HTTP ${response.status.value}")
            }
        }
    }

    suspend fun generateMinigameItems(type: MinigameType, prompt: String): List<MinigameItem> {
        val resp = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/ai/minigame/generate") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(MinigameGenerateRequest(type = type, prompt = prompt))
        }
        if (resp.status.isSuccess()) {
            val raw = resp.bodyAsText()
            return try {
                json.decodeFromString<MinigameItemsResponse>(raw).items
            } catch (e: Throwable) {
                json.decodeFromString<List<MinigameItem>>(raw)
            }
        } else {
            val err = runCatching { resp.body<ErrorResponse>() }.getOrNull()
            val txt = err?.message ?: runCatching { resp.bodyAsText() }.getOrNull()
            throw Error(txt ?: "Minigame generation failed: HTTP ${resp.status.value}")
        }
    }
}
