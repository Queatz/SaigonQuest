package main.components

import androidx.compose.runtime.Composable

/**
 * A responsive chat component that adapts between a dialog (mobile) and a side panel (desktop).
 * 
 * @param isMobile Whether the current device is mobile
 * @param onClose Callback when the chat is closed
 */
@Composable
fun Chat(
    isMobile: Boolean,
    onClose: () -> Unit
) {
    ResponsivePanel(
        title = "World Chat",
        isMobile = isMobile,
        onClose = onClose
    ) {
        ChatContent()
    }
}
