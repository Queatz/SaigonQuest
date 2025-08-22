@file:Suppress("UnsafeCastFromDynamic", "unused")

package sw

import js.objects.jso
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import web.notifications.NotificationOptions
import web.push.PushEvent
import web.serviceworker.Client
import web.serviceworker.ClientQueryOptions
import web.serviceworker.ExtendableEvent
import web.serviceworker.ServiceWorkerGlobalScope

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

fun main() {
    // Push event listener
    self.addEventListener("push", { e ->
        val event = e.unsafeCast<PushEvent>()
        try {
            val dataText: String? = event.data?.text()
            console.log("[SW] push event", dataText)
            if (dataText != null) {
                val ws = json.decodeFromString(WsMessage.serializer(), dataText)
                val nd = json.decodeFromString(NotificationData.serializer(), ws.data)

                val options = jso<NotificationOptions> {
                    body = nd.description
                    tag = "saigon-quest-notification"
                    renotify = true
                    icon = "/favicon.png"
                    // store data we need on click
                    asDynamic().data = jso<dynamic> {
                        id = nd.id
                        worldId = nd.worldId
                    }
                }
                // Server may set worldUrl at top level; handle if present
                try {
                    val root = Json.parseToJsonElement(dataText).jsonObject
                    val wu = root["worldUrl"]?.jsonPrimitive?.content
                    if (wu != null && wu.isNotBlank()) options.asDynamic().data.worldUrl = wu
                } catch (_: Throwable) {}

                event.waitUntil(self.registration.showNotification(nd.title, options))
            }
        } catch (e: Throwable) {
            console.error("[SW] Error handling push", e)
        }
    })

    // Notification click handler
    self.addEventListener("notificationclick", { e ->
        val event = e.unsafeCast<ExtendableEvent>()
        val notification = (e.asDynamic().notification)
        try {
            notification.close()
        } catch (_: Throwable) {}

        val data = notification?.data
        val worldUrl: String? = try { data?.worldUrl as String? } catch (_: Throwable) { null }

        val payload = jso<OpenNotificationMessage> {
            type = "OPEN_NOTIFICATION"
            data = jso<OpenNotificationData> {
                if (worldUrl != null && worldUrl.isNotBlank()) this.worldUrl = worldUrl
            }
        }

        val opts = jso<ClientQueryOptions> {
            includeUncontrolled = true
            // type is a string union; assign directly
            asDynamic().type = "window"
        }

        event.waitUntil(
            self.clients.matchAll(opts).then { clientList ->
                console.log("[SW] notificationclick clients", clientList)
                val list = clientList.asDynamic()
                if (list.length > 0) {
                    val client = list[0].unsafeCast<Client>()
                    client.focus()
                    client.postMessage(payload)
                    null
                } else {
                    val idPart: String = try { data?.id as? String ?: "" } catch (_: Throwable) { "" }
                    val openUrl = if (!worldUrl.isNullOrBlank()) "/$worldUrl" else "/"
                    self.clients.openWindow(openUrl)
                }
            }
        )
    })
}
