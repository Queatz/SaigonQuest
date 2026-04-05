package main.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import models.UserEmojiHistory
import models.RecordEmojiRequest

/**
 * API client for user emoji history operations
 */
object UserEmojiHistoryApi {
    /**
     * Fetches the emoji history for the current user
     */
    suspend fun getHistory(): UserEmojiHistory {
        return try {
            val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/user/emoji-history") {
                ApiClient.addAuthHeader(this)
            }
            if (response.status.isSuccess()) {
                response.body<UserEmojiHistory>()
            } else {
                throw Error("Failed to fetch emoji history: ${response.status}")
            }
        } catch (e: Throwable) {
            console.error("Error fetching emoji history", e)
            throw e
        }
    }

    /**
     * Records an emoji to the user's history
     */
    suspend fun recordEmoji(emoji: String) {
        try {
            val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/user/record-emoji") {
                contentType(ContentType.Application.Json)
                ApiClient.addAuthHeader(this)
                setBody(RecordEmojiRequest(emoji))
            }
            if (!response.status.isSuccess()) {
                console.error("Failed to record emoji: ${response.status}")
            }
        } catch (e: Throwable) {
            console.error("Error recording emoji", e)
        }
    }
}
