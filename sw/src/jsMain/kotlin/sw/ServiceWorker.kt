@file:Suppress("UnsafeCastFromDynamic", "unused")

package sw

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import web.events.EventHandler
import web.notifications.NotificationOptions
import web.push.PushEvent
import web.serviceworker.ClientQueryOptions
import web.serviceworker.NotificationEvent
import web.serviceworker.ServiceWorkerGlobalScope
import web.serviceworker.WindowClient
import web.serviceworker.focus
import web.serviceworker.matchAll
import web.serviceworker.openWindow
import web.serviceworker.showNotification

@Serializable
data class WsMessage(val action: String? = null, val data: String = "")

@Serializable
data class NotificationData(
    val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val isRead: Boolean,
    val type: String,
    val worldId: String,
    val objectId: String,
    val otherUserId: String,
    val timestamp: String
)

private val json = Json { ignoreUnknownKeys = true }

// Typed access to global self
external val self: ServiceWorkerGlobalScope

// Message payload back to the app
external interface OpenNotificationData { var worldUrl: String? }

external interface OpenNotificationMessage {
    var type: String
    var data: OpenNotificationData
}

// Data stored in NotificationOptions.data for click handling
external interface SwNotificationClickData {
    var id: String?
    var worldId: String?
    var worldUrl: String?
}

private inline fun <T> jsObject(builder: T.() -> Unit = {}): T =
    (js("({})") as T).apply(builder)

fun main() {
    // Push event listener
    self.onpush = EventHandler { event: PushEvent ->
        try {
            val dataText: String? = event.data?.text()
            console.log("[SW] push event", dataText)
            if (dataText != null) {
                val ws = json.decodeFromString(WsMessage.serializer(), dataText)
                val nd = json.decodeFromString(NotificationData.serializer(), ws.data)

                val options = jsObject<NotificationOptions> {
                    body = nd.description
                    tag = "saigon-quest-notification"
                    icon = "/favicon.png"
                    // store data we need on click
                    data = jsObject<SwNotificationClickData> {
                        id = nd.id
                        worldId = nd.worldId
                        worldUrl = null
                    }
                }
                // Server may set worldUrl at top level; handle if present
                try {
                    val root = Json.parseToJsonElement(dataText).jsonObject
                    val wu = root["worldUrl"]?.jsonPrimitive?.content
                    if (wu != null && wu.isNotBlank()) {
                        val d = options.data.unsafeCast<SwNotificationClickData>()
                        d.worldUrl = wu
                    }
                } catch (_: Throwable) {}

                event.waitUntil(
                    GlobalScope.promise {
                        self.registration.showNotification(nd.title, options)
                    }.unsafeCast<js.promise.Promise<*>>()
                )
            }
        } catch (e: Throwable) {
            console.error("[SW] Error handling push", e)
        }
    }

    // Notification click handler
    self.onnotificationclick = EventHandler  { e: NotificationEvent ->
        try {
            e.notification.close()
        } catch (_: Throwable) {}

        val clickData = try { e.notification.data.unsafeCast<SwNotificationClickData?>() } catch (_: Throwable) { null }
        val worldUrl: String? = clickData?.worldUrl

        val payload = jsObject<OpenNotificationMessage> {
            type = "OPEN_NOTIFICATION"
            data = jsObject<OpenNotificationData> {
                if (worldUrl != null && worldUrl.isNotBlank()) this.worldUrl = worldUrl
            }
        }

        e.waitUntil(
            GlobalScope.promise {
                val clientList = self.clients.matchAll<ClientQueryOptions>()
                if (clientList.isNotEmpty()) {
                    val client = clientList[0].unsafeCast<WindowClient>()
                    val focused = try { client.focus() } catch (_: Throwable) { client }
                    focused.postMessage(payload)
                    null
                } else {
                    val openUrl = if (!worldUrl.isNullOrBlank()) "/$worldUrl" else "/"
                    self.clients.openWindow(openUrl)
                }
            }.unsafeCast<js.promise.Promise<*>>()
        )
    }
}
