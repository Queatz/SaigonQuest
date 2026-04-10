package main.components

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import kotlinx.coroutines.*
import lib.Vector3
import main.api.NotificationApi
import main.api.WorldApi
import main.components.player.WorldCollisionUtils
import main.components.world.WorldLoader
import main.theme.NotificationStyles
import main.theme.Styles
import models.Notification
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.attributes.disabled
import Translations.str

@Composable
fun WorldInvitationNotificationContent(
    notification: Notification,
    onStatusChange: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val router = Router.current
    var isProcessing by remember { mutableStateOf(false) }
    var localIsRead by remember { mutableStateOf(notification.isRead) }

    LaunchedEffect(notification.isRead) {
        localIsRead = notification.isRead
    }

    Div(
        attrs = {
            classes(NotificationStyles.worldInvitationActions)
        }
    ) {
        if (!localIsRead) {
            DialogButton(
                style = DialogButtonStyle.Good,
                isDisabled = isProcessing,
                onClick = {
                    if (isProcessing) return@DialogButton
                    isProcessing = true
                    scope.launch {
                        acceptWorldInvitation(router, notification)
                        localIsRead = true
                        onStatusChange()
                        isProcessing = false
                    }
                }
            ) {
                Translated("Accept")
            }

            DialogButton(
                style = DialogButtonStyle.Secondary,
                isDisabled = isProcessing,
                onClick = {
                    if (isProcessing) return@DialogButton
                    isProcessing = true
                    scope.launch {
                        NotificationApi.markAsRead(notification.key)
                        localIsRead = true
                        onStatusChange()
                        isProcessing = false
                    }
                }
            ) {
                Translated("Dismiss")
            }
        }
    }
}

private suspend fun acceptWorldInvitation(router: Router, notification: Notification) {
    try {
        // Mark as read first
        NotificationApi.markAsRead(notification.key)
        
        // Get target world
        val targetWorld = WorldApi.getWorld(notification.worldId)
        
        // Generate random offset 1-3 units away in X and Z
        val randomX = (kotlin.random.Random.nextFloat() * .2f + .1f) * (if (kotlin.random.Random.nextBoolean()) 1f else -1f)
        val randomZ = (kotlin.random.Random.nextFloat() * .2f + .1f) * (if (kotlin.random.Random.nextBoolean()) 1f else -1f)
        
        val targetPos = Vector3(
            notification.x + randomX,
            notification.y,
            notification.z + randomZ
        )
        
        val currentWorld = WorldLoader.getActiveWorld()
        if (currentWorld?.key == notification.worldId) {
            // Already in the target world, teleport immediately
            val pc = SceneManager.getPlayerController()
            if (pc != null) {
                val validPos = pc.findValidYPosition(targetPos) ?: return
                pc.teleportTo(validPos)
                SceneManager.getCameraController()?.setInitialTargetPosition(validPos)
            }
        } else {
            // Store target position in SceneManager for the new scene to pick up
            SceneManager.setPendingTeleport(targetPos)
            // Navigate to the world
            router.navigate("/${targetWorld.url}")
        }
    } catch (e: Throwable) {
        console.error("Error accepting world invitation", e)
    }
}
