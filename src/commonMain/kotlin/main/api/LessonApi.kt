package main.api

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import models.LessonScene

object LessonApi {
    suspend fun getScenes(): List<LessonScene> {
        val resp = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/lessons/scenes") {
            ApiClient.addAuthHeader(this)
        }
        return if (resp.status.isSuccess()) resp.body() else emptyList()
    }

    suspend fun getScene(id: String): LessonScene {
        val resp = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/lessons/scenes/$id") {
            ApiClient.addAuthHeader(this)
        }
        if (!resp.status.isSuccess()) {
            throw Exception("Failed to load lesson scene: ${resp.status.value}")
        }
        return resp.body()
    }

    suspend fun saveScene(scene: LessonScene): LessonScene {
        val resp = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/lessons/scenes") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(scene)
        }
        if (!resp.status.isSuccess()) {
            throw Exception("Failed to save lesson scene: ${resp.status.value}")
        }
        return resp.body()
    }

    suspend fun deleteScene(id: String) {
        ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/lessons/scenes/$id") {
            ApiClient.addAuthHeader(this)
        }
    }
}
