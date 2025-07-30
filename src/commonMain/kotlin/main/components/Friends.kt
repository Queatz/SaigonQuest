package main.components

import androidx.compose.runtime.Composable

/**
 * A responsive friends component that adapts between a dialog (mobile) and a side panel (desktop).
 * 
 * @param isMobile Whether the current device is mobile
 * @param onClose Callback when the friends panel is closed
 */
@Composable
fun Friends(
    isMobile: Boolean,
    onClose: () -> Unit
) {
    ResponsivePanel(
        title = "Friends",
        isMobile = isMobile,
        onClose = onClose
    ) {
        FriendsContent()
    }
}
