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
import models.ErrorResponse

object AiApi {
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

    suspend fun generateTts(text: String): ByteArray {
        val resp = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/ai/tts") {
            contentType(ContentType.Application.Json)
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

    suspend fun generateTtsStream(text: String, onChunk: suspend (ByteArray) -> Unit) {
        val request = ApiClient.httpClient.preparePost("${ApiConstants.API_BASE_URL}/ai/tts") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(AiTtsRequest(text = text))
        }

        request.execute { response ->
            if (response.status.isSuccess()) {
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(8192)
                    if (!packet.exhausted()) {
                        onChunk(packet.readByteArray())
                    }
                }
            } else {
                val err = runCatching { response.body<ErrorResponse>() }.getOrNull()
                val txt = err?.message ?: runCatching { response.bodyAsText() }.getOrNull()
                throw Error(txt ?: "TTS request failed: HTTP ${response.status.value}")
            }
        }
    }
}
