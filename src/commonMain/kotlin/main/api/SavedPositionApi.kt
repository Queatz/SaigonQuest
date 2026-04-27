package main.api

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import models.SavedPosition

object SavedPositionApi {
    suspend fun getByWorldId(worldId: String): List<SavedPosition> {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/saved-position") {
            url {
                parameters.append("worldId", worldId)
            }
            ApiClient.addAuthHeader(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        }
        throw Error("Failed to fetch saved positions")
    }

    suspend fun create(pos: SavedPosition): SavedPosition {
        val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/saved-position") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(pos)
        }
        if (response.status.isSuccess()) {
            return response.body()
        }
        throw Error("Failed to create saved position")
    }

    suspend fun update(id: String, pos: SavedPosition): SavedPosition {
        val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/saved-position/$id") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(pos)
        }
        if (response.status.isSuccess()) {
            return response.body()
        }
        throw Error("Failed to update saved position")
    }

    suspend fun delete(id: String) {
        val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/saved-position/$id") {
            ApiClient.addAuthHeader(this)
        }
        if (!response.status.isSuccess()) {
            throw Error("Failed to delete saved position")
        }
    }
}
