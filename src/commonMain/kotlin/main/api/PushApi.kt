package main.api

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
data class PublicKeyResponse(val publicKey: String)

@Serializable
data class PushSubscriptionRequest(
    val endpoint: String,
    val keys: PushKeys,
    val ua: String,
)

@Serializable
data class PushKeys(
    val p256dh: String,
    val auth: String,
)

object PushApi {
    suspend fun getPublicKey(): String {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/notification/push/public-key") {
            ApiClient.addAuthHeader(this)
        }
        if (response.status.isSuccess()) {
            return response.body<PublicKeyResponse>().publicKey
        } else {
            throw Error("Failed to fetch public key: ${response.status}")
        }
    }

    suspend fun subscribe(endpoint: String, p256dh: String, auth: String, ua: String) {
        val payload = PushSubscriptionRequest(
            endpoint = endpoint,
            keys = PushKeys(p256dh = p256dh, auth = auth),
            ua = ua
        )
        val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/notification/push/subscribe") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw Error("Failed to subscribe push: ${response.status}")
        }
    }

    suspend fun unsubscribe(endpoint: String) {
        val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/notification/push/subscribe") {
            ApiClient.addAuthHeader(this)
            url { parameters.append("endpoint", endpoint) }
        }
        if (!response.status.isSuccess()) {
            throw Error("Failed to unsubscribe push: ${response.status}")
        }
    }
}
