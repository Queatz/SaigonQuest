package main.components

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import main.api.ChatApi
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.components.chat.ChatMessages
import main.components.chat.ChatComposer
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div

/**
 * Content for the chat panel/dialog.
 * This component contains the shared UI elements for both mobile and desktop views.
 *
 * @param worldId The ID of the world to get messages for
 */
@Composable
fun ChatContent(
    worldId: String,
    isDialog: Boolean = false,
    onClose: () -> Unit,
) {
    var isAuthenticated by remember { mutableStateOf(false) }
    var isPlayer by remember { mutableStateOf(false) }
    var isWebSocketConnected by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }

    // Check if user is authenticated
    LaunchedEffect(Unit) {
        try {
            val user = UserApi.getCurrentUser()
            isAuthenticated = user.key.isNotEmpty()
            isPlayer = user.isPlayer
        } catch (e: Throwable) {
            console.error("Error checking authentication status", e)
            isAuthenticated = false
        }
    }

    // Check WebSocket connection status periodically using ConnectionManager
    LaunchedEffect(Unit) {
        while (true) {
            isWebSocketConnected =
                WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED
            lastError = WebSocketApi.getLastError()

            // If not connected and we have a worldId, try to connect using ConnectionManager
            if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.DISCONNECTED &&
                worldId.isNotEmpty()
            ) {
                console.log("WebSocket not connected, attempting to connect to world: $worldId")
                WebSocketConnectionManager.connectToWorld(worldId)
            }

            delay(10000) // Check every 10 seconds
        }
    }

    // Main Layout
    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                height(100.percent)
                width(100.percent)
            }
        }
    ) {
        // Chat messages area
        Div(
            attrs = {
                style {
                    property("flex-grow", "1")
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    height(0.px) // Allow flex-grow to take over
                }
            }
        ) {
            // Mark messages as read when chat opens
            LaunchedEffect(worldId) {
                try {
                    ChatApi.markMessagesAsRead(worldId)
                } catch (t: Throwable) {
                    console.error("Error marking messages as read on open", t)
                }
            }

            // Use the ChatMessages component to display messages
            ChatMessages(
                worldId = worldId,
            )
        }

        // Chat composer (input area)
        ChatComposer(
            worldId = worldId,
            isAuthenticated = isAuthenticated,
            isPlayer = isPlayer,
            isWebSocketConnected = isWebSocketConnected,
            lastError = lastError,
            isDialog = isDialog,
            onClose = onClose,
        )
    }
}
