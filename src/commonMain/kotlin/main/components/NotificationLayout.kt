package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.window
import main.components.IconRenderer
import main.theme.NotificationStyles
import models.NotificationData
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text

/**
 * A component that displays notification popups at the top center of the screen.
 * 
 * @param notification The notification to display, or null if no notification is active
 * @param currentPanel The current side panel state
 * @param isMobile Whether the device is mobile
 * @param onDismiss Callback when the notification is dismissed
 * @param onClick Callback when the notification is clicked
 */
@Composable
fun NotificationLayout(
    notification: NotificationData?,
    currentPanel: SidePanelState,
    isMobile: Boolean,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    // Only show if we have a notification
    if (notification == null) return
    
    // Auto-dismiss timer
    DisposableEffect(notification) {
        val timeoutId = window.setTimeout({
            onDismiss()
        }, 5000) // Auto-dismiss after 5 seconds
        
        onDispose {
            window.clearTimeout(timeoutId)
        }
    }
    
    // Container for the notification
    Div(
        attrs = {
            classes(NotificationStyles.notificationLayoutContainer)
            
            // Adjust position when side panel is open on desktop
            if (isLayoutAdjustmentNeeded(currentPanel, isMobile)) {
                style {
                    property("transform", "translateX(-180px)") // Half of the panel width (360px)
                }
            }
        }
    ) {
        // Notification card
        Div(
            attrs = {
                classes(NotificationStyles.notificationCard)
                
                onClick {
                    onClick()
                }
            }
        ) {
            // Notification content
            Div(
                attrs = {
                    classes(NotificationStyles.notificationContentContainer)
                }
            ) {
                // Title
                Div(
                    attrs = {
                        classes(NotificationStyles.notificationCardTitle)
                    }
                ) {
                    Text(notification.title)
                }
                
                // Description
                Div(
                    attrs = {
                        classes(NotificationStyles.notificationCardDescription)
                    }
                ) {
                    Text(notification.description)
                }
            }
            
            // Close button
            Div(
                attrs = {
                    classes(NotificationStyles.notificationCloseButton)
                    
                    onClick { 
                        it.stopPropagation()
                        onDismiss() 
                    }
                }
            ) {
                IconRenderer(
                    url = "raw/data/ui/ui.png",
                    index = 20,
                    size = 24,
                )
            }
        }
    }
}
