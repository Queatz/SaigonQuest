package main.api

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import models.MediaType
import models.UserMedia

/**
 * API client for user media library
 */
object MediaApi {
    suspend fun list(type: MediaType? = null, limit: Int = 10, offset: Int = 0): List<UserMedia> {
        val typeParam = if (type != null) "&type=${type.name.lowercase()}" else ""
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/media?limit=$limit&offset=$offset$typeParam") {
            ApiClient.addAuthHeader(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            throw Error(response.bodyAsText())
        }
    }

    suspend fun create(
        type: MediaType,
        path: String,
        originalFileName: String,
    ): UserMedia {
        val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/media") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(CreateMediaRequest(type = type.name.lowercase(), path = path, originalFileName = originalFileName))
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            throw Error(response.bodyAsText())
        }
    }

    suspend fun favorite(id: String): UserMedia {
        val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/media/$id/favorite") {
            ApiClient.addAuthHeader(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            throw Error(response.bodyAsText())
        }
    }

    suspend fun unfavorite(id: String): UserMedia {
        val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/media/$id/favorite") {
            ApiClient.addAuthHeader(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            throw Error(response.bodyAsText())
        }
    }

    suspend fun delete(id: String) {
        val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/media/$id") {
            ApiClient.addAuthHeader(this)
        }
        if (!response.status.isSuccess()) {
            throw Error("Failed to delete media: ${response.status}")
        }
    }

    suspend fun rename(id: String, newName: String): UserMedia {
        val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/media/$id/rename") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(RenameMediaRequest(newName = newName))
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            throw Error(response.bodyAsText())
        }
    }
}

@Serializable
private data class CreateMediaRequest(
    val type: String,
    val path: String,
    val originalFileName: String,
)

@Serializable
private data class RenameMediaRequest(
    val newName: String,
)
