package main.api

import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.client.statement.bodyAsText
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.callbackFlow
import io.ktor.websocket.Frame
import models.Doc
import models.DocTree
import models.ErrorResponse

object DocApi {
    class VersionConflictError(message: String) : Throwable(message)

    // --- Collaboration (WebSocket) API ---
    @kotlinx.serialization.Serializable
    sealed class WsEvent {
        @kotlinx.serialization.Serializable data class Message(val msg: models.WsMsg) : WsEvent()
        @kotlinx.serialization.Serializable data class Closed(val reason: String? = null) : WsEvent()
        @kotlinx.serialization.Serializable data class Failed(val error: String) : WsEvent()
    }

    @kotlinx.serialization.Serializable
    data class CreateDocRequest(
        val title: String,
        val parentId: String? = null,
    )

    @kotlinx.serialization.Serializable
    data class UpdateDocRequest(
        val title: String? = null,
        val parentId: String? = null,
        val orderIndex: Double? = null,
    )

    @kotlinx.serialization.Serializable
    data class MoveDocRequest(
        val newParentId: String?,
        val newOrderIndex: Double,
    )

    @kotlinx.serialization.Serializable
    data class UpdateContentRequest(
        val content: String,
        val baseVersion: Int,
    )

    suspend fun createDoc(
        worldId: String,
        title: String,
        parentId: String? = null,
    ): Doc {
        val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/worlds/$worldId/docs") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(CreateDocRequest(title = title, parentId = parentId))
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val errorResponse = try { response.body<ErrorResponse>() } catch (_: Throwable) { ErrorResponse(message = response.bodyAsText()) }
            throw Error(errorResponse.message)
        }
    }

    suspend fun getDocTree(worldId: String): List<DocTree> {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/worlds/$worldId/docs") {
            ApiClient.addAuthHeader(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val errorResponse = try { response.body<ErrorResponse>() } catch (_: Throwable) { ErrorResponse(message = response.bodyAsText()) }
            throw Error(errorResponse.message)
        }
    }

    suspend fun getDoc(docId: String): Doc {
        val response = ApiClient.httpClient.get("${ApiConstants.API_BASE_URL}/docs/$docId") {
            ApiClient.addAuthHeader(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val errorResponse = try { response.body<ErrorResponse>() } catch (_: Throwable) { ErrorResponse(message = response.bodyAsText()) }
            throw Error(errorResponse.message)
        }
    }

    suspend fun updateDoc(docId: String, update: UpdateDocRequest): Doc {
        val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/docs/$docId") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(update)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val errorResponse = try { response.body<ErrorResponse>() } catch (_: Throwable) { ErrorResponse(message = response.bodyAsText()) }
            throw Error(errorResponse.message)
        }
    }

    suspend fun updateDocContent(docId: String, content: String, baseVersion: Int): Doc {
        val response = ApiClient.httpClient.put("${ApiConstants.API_BASE_URL}/docs/$docId/content") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(UpdateContentRequest(content = content, baseVersion = baseVersion))
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            if (response.status == HttpStatusCode.Conflict) {
                val message = try { response.body<ErrorResponse>().message } catch (_: Throwable) { response.bodyAsText() }
                throw VersionConflictError(message)
            }
            val errorResponse = try { response.body<ErrorResponse>() } catch (_: Throwable) { ErrorResponse(message = response.bodyAsText()) }
            throw Error(errorResponse.message)
        }
    }

    suspend fun deleteDoc(docId: String) {
        val response = ApiClient.httpClient.delete("${ApiConstants.API_BASE_URL}/docs/$docId") {
            ApiClient.addAuthHeader(this)
        }
        if (!response.status.isSuccess()) {
            val errorResponse = try { response.body<ErrorResponse>() } catch (_: Throwable) { ErrorResponse(message = response.bodyAsText()) }
            throw Error(errorResponse.message)
        }
    }

    suspend fun moveDoc(docId: String, newParentId: String?, newOrderIndex: Double): Doc {
        val response = ApiClient.httpClient.post("${ApiConstants.API_BASE_URL}/docs/$docId/move") {
            contentType(ContentType.Application.Json)
            ApiClient.addAuthHeader(this)
            setBody(MoveDocRequest(newParentId = newParentId, newOrderIndex = newOrderIndex))
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val errorResponse = try { response.body<ErrorResponse>() } catch (_: Throwable) { ErrorResponse(message = response.bodyAsText()) }
            throw Error(errorResponse.message)
        }
    }

    suspend fun searchDocs(worldId: String, query: String, limit: Int = 50): List<Doc> {
        val response = ApiClient.httpClient.get {
            ApiClient.addAuthHeader(this)
            url("${ApiConstants.API_BASE_URL}/worlds/$worldId/docs/search")
            parameter("query", query)
            parameter("limit", limit)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val errorResponse = try { response.body<ErrorResponse>() } catch (_: Throwable) { ErrorResponse(message = response.bodyAsText()) }
            throw Error(errorResponse.message)
        }
    }

    // Collaboration connection handle
    class CollaborationConnection(
        val events: kotlinx.coroutines.flow.Flow<WsEvent>,
        val send: suspend (models.WsMsg) -> Unit,
        val close: () -> Unit,
    )

    private fun toWsUrl(httpUrl: String): String = when {
        httpUrl.startsWith("https://") -> httpUrl.replaceFirst("https://", "wss://")
        httpUrl.startsWith("http://") -> httpUrl.replaceFirst("http://", "ws://")
        else -> "wss://$httpUrl"
    }

    fun collaborate(docId: String): CollaborationConnection {
        val url = toWsUrl(ApiConstants.API_BASE_URL) + "/docs/$docId/collaborate"
        val channel = kotlinx.coroutines.channels.Channel<WsEvent>(capacity = kotlinx.coroutines.channels.Channel.BUFFERED)
        val outgoing = kotlinx.coroutines.channels.Channel<models.WsMsg>(capacity = kotlinx.coroutines.channels.Channel.BUFFERED)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)

        // Connection loop with simple auto-reconnect + backoff.
        scope.launch {
            var attempt = 0
            while (!outgoing.isClosedForSend && !channel.isClosedForSend) {
                try {
                    ApiClient.httpClient.webSocket(urlString = url, request = { ApiClient.addAuthHeader(this) }) {
                        attempt = 0 // reset backoff on successful connect
                        val wsSession = this
                        // Sender: forward queued messages to the server
                        val sender = launch {
                            for (msg in outgoing) {
                                try {
                                    val text = utils.json.encodeToString(models.WsMsg.serializer(), msg)
                                    wsSession.send(io.ktor.websocket.Frame.Text(text))
                                } catch (t: Throwable) {
                                    channel.send(WsEvent.Failed(t.message ?: "send failed"))
                                }
                            }
                        }
                        // Reader: forward server messages to the channel
                        try {
                            for (frame in incoming) {
                                val txt = (frame as? io.ktor.websocket.Frame.Text)?.readText() ?: continue
                                val parsed = runCatching { utils.json.decodeFromString(models.WsMsg.serializer(), txt) }.getOrNull()
                                if (parsed != null) channel.send(WsEvent.Message(parsed))
                            }
                        } catch (t: Throwable) {
                            channel.send(WsEvent.Failed(t.message ?: "ws error"))
                        } finally {
                            sender.cancel()
                            // Don't immediately mark Closed; try reconnect unless user explicitly closed
                        }
                    }
                } catch (t: Throwable) {
                    channel.send(WsEvent.Failed(t.message ?: "connect failed"))
                }
                // If we reach here, the connection was closed or failed. Attempt reconnect unless user closed.
                if (outgoing.isClosedForSend || channel.isClosedForSend) break
                attempt += 1
                val delayMs = kotlin.math.min(15000, 500 * (1 shl (attempt.coerceAtMost(5))))
                try { kotlinx.coroutines.delay(delayMs.toLong()) } catch (_: Throwable) { break }
            }
            // Final closed signal
            channel.trySend(WsEvent.Closed())
        }

        val flow = callbackFlow {
            val job = scope.launch {
                for (ev in channel) trySend(ev)
            }
            awaitClose { job.cancel() }
        }

        val sendFn: suspend (models.WsMsg) -> Unit = { msg -> outgoing.send(msg) }
        val closeFn: () -> Unit = {
            try { outgoing.close() } catch (_: Throwable) {}
            try { channel.close() } catch (_: Throwable) {}
        }
        return CollaborationConnection(events = flow, send = sendFn, close = closeFn)
    }
}
