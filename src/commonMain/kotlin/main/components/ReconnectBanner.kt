package main.components

import androidx.compose.runtime.Composable
import main.api.WebSocketConnectionManager
import main.theme.Styles
import org.jetbrains.compose.web.dom.Div

@Composable
fun ReconnectBanner() {
    val hasEverConnected = WebSocketConnectionManager.hasEverConnected.value
    val state = WebSocketConnectionManager.connectionState.value
    val isConnected = state == WebSocketConnectionManager.ConnectionState.CONNECTED

    if (hasEverConnected && !isConnected) {
        Div(attrs = { classes(Styles.reconnectingBanner) }) {
            // Replace static ellipsis with animated dots
            Translated("Reconnecting")
            AnimatedDots()
        }
    }
}

