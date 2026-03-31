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
import org.jetbrains.compose.web.dom.Button
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
            Button(
                attrs = {
                    classes(NotificationStyles.acceptButton)
                    onClick {
                        it.stopPropagation()
                        if (isProcessing) return@onClick
                        isProcessing = true
                        scope.launch {
                            acceptWorldInvitation(router, notification)
                            localIsRead = true
                            onStatusChange()
                            isProcessing = false
                        }
                    }
                    if (isProcessing) disabled()
                }
            ) {
                Translated("Accept")
            }

            Button(
                attrs = {
                    classes(NotificationStyles.rejectButton)
                    onClick {
                        it.stopPropagation()
                        if (isProcessing) return@onClick
                        isProcessing = true
                        scope.launch {
                            NotificationApi.markAsRead(notification.key)
                            localIsRead = true
                            onStatusChange()
                            isProcessing = false
                        }
                    }
                    if (isProcessing) disabled()
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
        val randomX = (kotlin.random.Random.nextFloat() * 2f + 1f) * (if (kotlin.random.Random.nextBoolean()) 1f else -1f)
        val randomZ = (kotlin.random.Random.nextFloat() * 2f + 1f) * (if (kotlin.random.Random.nextBoolean()) 1f else -1f)
        
        val targetPos = Vector3(
            notification.x + randomX,
            notification.y,
            notification.z + randomZ
        )
        
        // Navigate to the world
        router.navigate("/${targetWorld.url}")
        
        // Polling loop to wait for player controller to be ready
        CoroutineScope(Dispatchers.Default).launch {
            var attempts = 0
            while (attempts < 80) {
                val pc = SceneManager.getPlayerController()
                val handle = pc?.getPlayerHandle()
                val scene = SceneManager.getScene()
                if (pc != null && handle != null && scene != null) {
                    val worldMeshes = pc.getWorldMeshes()
                    val collider = pc.getCollider()
                    
                    val validPos = WorldCollisionUtils.findValidYPosition(
                        worldMeshes = worldMeshes,
                        collider = collider,
                        moveCircle = null,
                        position = targetPos
                    )
                    
                    pc.teleportTo(validPos ?: targetPos)
                    break
                }
                delay(100)
                attempts++
            }
        }
    } catch (e: Throwable) {
        console.error("Error accepting world invitation", e)
    }
}
