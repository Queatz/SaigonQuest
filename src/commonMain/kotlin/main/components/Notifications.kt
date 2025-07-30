package main.components

import androidx.compose.runtime.Composable
import models.Notification

/**
 * A responsive notifications component that adapts between a dialog (mobile) and a side panel (desktop).
 * 
 * @param isMobile Whether the current device is mobile
 * @param onClose Callback when the notifications panel is closed
 * @param onNotificationClick Callback when a notification is clicked
 */
@Composable
fun Notifications(
    isMobile: Boolean,
    onClose: () -> Unit,
    onNotificationClick: (Notification) -> Unit = {}
) {
    ResponsivePanel(
        title = "Notifications",
        isMobile = isMobile,
        onClose = onClose
    ) {
        NotificationsContent(
            onNotificationClick = onNotificationClick
        )
    }
}
