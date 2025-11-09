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
import kotlinx.serialization.Serializable
import models.AiPromptHistory
import models.ErrorResponse

object AiApi {
    @Serializable
    data class AiPromptRequest(
        val prompt: String,
        val docContents: String,
        val caretStart: Int,
        val caretEnd: Int,
        val docId: String? = null,
    )

    @Serializable
    data class AiPromptResponse(
        val text: String,
    )

    suspend fun sendPrompt(
        prompt: String,
        docContents: String,
        caretStart: Int,
        caretEnd: Int,
        docId: String? = null,
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
            ))
        }
        if (resp.status.isSuccess()) {
            return resp.body<AiPromptResponse>().text
        } else {
            val err = runCatching { resp.body<ErrorResponse>() }.getOrNull()
            val txt = err?.message ?: runCatching { resp.bodyAsText() }.getOrNull()
            throw Error(txt ?: "AI request failed: HTTP ${resp.status.value}")
        }
    }

    suspend fun getHistory(limit: Int = 50): List<AiPromptHistory> {
        val resp = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/ai/history") {
            ApiClient.addAuthHeader(this)
            parameter("limit", limit)
        }
        if (resp.status.isSuccess()) {
            return resp.body()
        } else {
            val txt = runCatching { resp.bodyAsText() }.getOrNull()
            throw Error(txt ?: "Failed to load AI history: HTTP ${'$'}{resp.status.value}")
        }
    }
}
