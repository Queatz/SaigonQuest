package main.utils

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import web.notifications.Notification
import web.notifications.NotificationOptions
import web.notifications.NotificationPermission
import web.notifications.denied
import web.notifications.granted
import web.notifications.requestPermission
import web.timers.setTimeout

/**
 * Lightweight helper to show regular Browser notifications using typed wrappers
 * and gracefully no-ops if unsupported or denied.
 */
object BrowserNotifications {
    private fun isSupported(): Boolean = try {
        // Access will throw if Notifications are unsupported
        Notification.permission
        true
    } catch (_: Throwable) {
        false
    }

    private fun showNow(title: String, body: String, iconUrl: String?) {
        val options: dynamic = object {
            var body = body.ifBlank { null }
            var icon = iconUrl
            var tag: String? = "saigon-quest-notification"
        }
        val n = Notification(title, options as NotificationOptions)
        setTimeout({ n.close() }, 6000)
    }

    /**
     * Show a Browser notification, requesting permission if needed.
     * Non-suspending; performs permission request in a coroutine if required.
     */
    fun show(title: String, body: String = "", iconUrl: String? = "/favicon.png") {
        if (!isSupported()) return

        when (Notification.permission) {
            NotificationPermission.granted -> showNow(title, body, iconUrl)
            NotificationPermission.denied -> { /* do nothing */ }
            else -> GlobalScope.launch {
                try {
                    val perm = Notification.requestPermission()
                    if (perm == NotificationPermission.granted) {
                        showNow(title, body, iconUrl)
                    }
                } catch (_: Throwable) { /* ignore */ }
            }
        }
    }
}
