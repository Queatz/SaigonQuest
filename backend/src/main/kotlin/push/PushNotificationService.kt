

package push

import debug.WebSocketDebug
import models.Notification
import models.NotificationData
import models.WebSocketAction
import nl.martijndwars.webpush.PushService
import org.apache.http.HttpResponse
import org.bouncycastle.jce.provider.BouncyCastleProvider
import repository.PushSubscriptionRepository
import repository.UserRepository
import repository.WorldRepository
import routes.WebSocketMessage
import routes.activeConnections
import utils.json
import java.security.Security

import nl.martijndwars.webpush.Notification as WPN


/**
 * Web Push sender using nl.martijndwars:web-push for proper VAPID and payload encryption.
 */
object PushNotificationService {
    private val subsRepo = PushSubscriptionRepository()
    private val userRepo = UserRepository()
    private val worldRepo = WorldRepository()

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private fun hasActiveWebSocket(userId: String): Boolean =
        try { activeConnections.values.any { list -> list.any { it.userId == userId } } } catch (_: Throwable) { false }

    private fun buildPushService(): PushService? {
        return try {
            val publicKey = System.getenv("VAPID_PUBLIC_KEY") ?: ""
            val privateKey = System.getenv("VAPID_PRIVATE_KEY") ?: ""
            if (publicKey.isBlank() || privateKey.isBlank()) {
                WebSocketDebug.logBroadcast("PUSH_SKIP_WS", "Missing keys", "notification")
                null
            } else {
                PushService().apply {
                    setPublicKey(publicKey)
                    setPrivateKey(privateKey)
                    subject = System.getenv("VAPID_SUBJECT") ?: "mailto:admin@saigon.quest"
                }
            }
        } catch (e: Throwable) {
            WebSocketDebug.logBroadcast("PUSH_SKIP_WS", "error: $e", "notification")
            null
        }
    }

    fun sendIfEligible(notification: Notification) {
        // Only push for players and when no active WS exists
        val user = try { userRepo.getById(notification.userId) } catch (_: Throwable) { null }
        if (user == null || !user.isPlayer) return

        if (hasActiveWebSocket(notification.userId)) {
            WebSocketDebug.logBroadcast("PUSH_SKIP_WS", "Skipping push; WS active for ${notification.userId}", "notification")
            return
        }

        val pushService = buildPushService()

        if (pushService == null) {
            WebSocketDebug.logBroadcast("PUSH_NO_KEYS", "VAPID not configured; skipping push", "notification")
            return
        }

        // Build payload delivered to service worker
        val worldUrl = try {
            worldRepo.getById(notification.worldId)?.url ?: ""
        } catch (_: Throwable) {
            ""
        }

        // Log payload summary (no PII beyond ids)
        WebSocketDebug.logBroadcast(
            "PUSH_PAYLOAD",
            "notifId=${notification.key} worldUrl=$worldUrl title='${notification.title.take(40)}'",
            notification.userId
        )

        val data = NotificationData(
            id = notification.key,
            userId = notification.userId,
            title = notification.title,
            description = notification.description,
            isRead = notification.isRead,
            type = notification.type,
            worldId = notification.worldId,
            objectId = notification.objectId,
            otherUserId = notification.otherUserId,
            timestamp = notification.timestamp
        )

        val swMessage = WebSocketMessage(
            action = WebSocketAction.NOTIFICATION,
            data = json.encodeToString(data),
            worldUrl = worldUrl
        ).let {
            json.encodeToString(it)
        }

        val subs = subsRepo.getByUserId(notification.userId)
        WebSocketDebug.logBroadcast("PUSH_TARGETS", "subscriptions=${subs.size}", notification.userId)

        subs.forEach { sub ->
            try {
                val endpoint = sub.endpoint
                val payloadBytes = swMessage.toByteArray(Charsets.UTF_8)
                WebSocketDebug.logBroadcast("PUSH_SEND", "to=$endpoint bytes=${payloadBytes.size}", notification.userId)
                val notif = WPN(endpoint, sub.p256dh, sub.auth, payloadBytes)
                val resp: HttpResponse = pushService.send(notif)
                val status = resp.statusLine.statusCode
                if (status == 404 || status == 410) {
                    subsRepo.deleteByEndpoint(notification.userId, endpoint)
                }
                WebSocketDebug.logBroadcast("PUSH_STATUS", "$status for $endpoint", notification.userId)
            } catch (e: Throwable) {
                WebSocketDebug.logBroadcast("PUSH_ERROR", e.message ?: "error", notification.userId)
            }
        }
    }
}
