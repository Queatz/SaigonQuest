package main.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import models.AdminDecisionRequest
import models.ErrorResponse
import models.UserBalance
import models.UserBalanceChangeRequest
import models.UserBalanceChangeStatus

/**
 * API client for admin balance requests operations
 */
object AdminBalanceApi {

    private suspend inline fun <reified T> parseOrThrow(resp: io.ktor.client.statement.HttpResponse, fallbackMsg: String): T {
        return if (resp.status.value in 200..299) {
            resp.body()
        } else {
            val msg = try { resp.body<ErrorResponse>().message } catch (_: Throwable) { fallbackMsg + " (" + resp.status.value + ")" }
            throw Throwable(msg)
        }
    }

    /**
     * Fetch admin balance requests with optional filters.
     * If no filters are provided, backend returns the pending list by default.
     */
    suspend fun getRequests(
        resolved: Boolean? = null,
        limit: Int? = null,
        statuses: Set<UserBalanceChangeStatus>? = null,
    ): List<UserBalanceChangeRequest> {
        val resp = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/admin/balance-requests") {
            ApiClient.addAuthHeader(this)
            if (resolved != null) parameter("resolved", resolved)
            if (limit != null) parameter("limit", limit)
            statuses?.let { set ->
                if (set.isNotEmpty()) parameter("status", set.joinToString(",") { it.name })
            }
        }
        return parseOrThrow(resp, "Failed to load balance requests")
    }

    /**
     * Convenience method to get pending requests count.
     */
    suspend fun getPendingCount(): Int {
        return try {
            // No filters -> pending list per backend default
            getRequests().size
        } catch (_: Throwable) {
            0
        }
    }

    /**
     * Fetch a user's current balance (admin helper endpoint)
     */
    suspend fun getUserBalance(userId: String): UserBalance {
        val resp = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/admin/balance-requests/balance/$userId") {
            ApiClient.addAuthHeader(this)
        }
        return parseOrThrow(resp, "Failed to load user balance")
    }

    /**
     * Approve a balance change request
     */
    suspend fun approveRequest(id: String, note: String? = null) {
        val resp = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/admin/balance-requests/$id/approve") {
            ApiClient.addAuthHeader(this)
            contentType(ContentType.Application.Json)
            setBody(AdminDecisionRequest(note))
        }
        if (resp.status.value !in 200..299) {
            val msg = try { resp.body<ErrorResponse>().message } catch (_: Throwable) { "Failed to approve request (" + resp.status.value + ")" }
            throw Throwable(msg)
        }
    }

    /**
     * Deny a balance change request
     */
    suspend fun denyRequest(id: String, note: String? = null) {
        val resp = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/admin/balance-requests/$id/deny") {
            ApiClient.addAuthHeader(this)
            contentType(ContentType.Application.Json)
            setBody(AdminDecisionRequest(note))
        }
        if (resp.status.value !in 200..299) {
            val msg = try { resp.body<ErrorResponse>().message } catch (_: Throwable) { "Failed to deny request (" + resp.status.value + ")" }
            throw Throwable(msg)
        }
    }
}
