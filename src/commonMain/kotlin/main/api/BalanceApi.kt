package main.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.utils.EmptyContent.status
import io.ktor.http.ContentType
import io.ktor.http.contentType
import models.CreateBalanceChangeRequest
import models.UserBalance
import models.UserBalanceChangeRequest
import models.UserBalanceChangeStatus
import models.UserBalanceHistory

/**
 * API client for balance operations
 */
object BalanceApi {
    /**
     * Fetch current user's balance
     */
    suspend fun getBalance(): UserBalance {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/balance") {
            ApiClient.addAuthHeader(this)
        }
        return response.body()
    }

    /**
     * Fetch recent balance history
     */
    suspend fun getRecent(limit: Int = 5): List<UserBalanceHistory> {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/balance/history") {
            ApiClient.addAuthHeader(this)
            parameter("limit", limit)
        }
        return response.body()
    }

    /**
     * Fetch balance change requests by status (default: Received)
     */
    suspend fun getRequests(status: UserBalanceChangeStatus = UserBalanceChangeStatus.Received): List<UserBalanceChangeRequest> {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/balance/requests") {
            ApiClient.addAuthHeader(this)
            parameter("status", status)
        }
        return response.body()
    }

    /**
     * Fetch count of balance change requests by status (default: Received)
     */
    suspend fun getRequestsCount(status: UserBalanceChangeStatus = UserBalanceChangeStatus.Received): Int {
        return try {
            getRequests(status).size
        } catch (t: Throwable) {
            0
        }
    }

    /**
     * Submit a new balance change request
     */
    suspend fun submitRequest(request: CreateBalanceChangeRequest) {
        ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/balance/requests") {
            ApiClient.addAuthHeader(this)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    /**
     * Cancel an existing request by key
     */
    suspend fun cancelRequest(key: String) {
        ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/balance/requests/$key/cancel") {
            ApiClient.addAuthHeader(this)
        }
    }
}
