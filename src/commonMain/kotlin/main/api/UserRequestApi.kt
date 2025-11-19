package main.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.contentType
import models.CreateUserRequestDto
import models.UserRequest
import models.UserRequestStatus
import models.UpdateUserRequestStatusDto
import models.AddUserRequestNoteDto

object UserRequestApi {
    suspend fun getMyRequests(): List<UserRequest> {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/user-requests") {
            ApiClient.addAuthHeader(this)
        }
        return response.body()
    }

    suspend fun create(dto: CreateUserRequestDto): UserRequest {
        val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/user-requests") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(dto)
        }
        return response.body()
    }

    // Admin: list all user requests by status (e.g., Open for pending)
    suspend fun getAll(status: UserRequestStatus? = null): List<UserRequest> {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/admin/user-requests") {
            ApiClient.addAuthHeader(this)
            if (status != null) parameter("status", status.name)
        }
        return response.body()
    }

    // Admin: update status with optional note
    suspend fun updateStatus(
        id: String,
        status: UserRequestStatus,
        note: String = "",
    ): UserRequest {
        val response = ApiClient.httpClient.patch("${ApiConstants.API_BASE_URL}/admin/user-requests/$id/status") {
            ApiClient.addAuthHeader(this)
            contentType(ContentType.Application.Json)
            setBody(UpdateUserRequestStatusDto(status = status, note = note))
        }
        return response.body()
    }

    // Admin: add note
    suspend fun addNote(
        id: String,
        note: String,
    ): UserRequest {
        val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/admin/user-requests/$id/notes") {
            ApiClient.addAuthHeader(this)
            contentType(ContentType.Application.Json)
            setBody(AddUserRequestNoteDto(note = note))
        }
        return response.body()
    }
}
