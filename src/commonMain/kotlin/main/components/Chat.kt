package main.components

import androidx.compose.runtime.Composable
import main.components.translated
import Translations.str
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * A responsive chat component that adapts between a dialog (mobile) and a side panel (desktop).
 * 
 * @param worldId The ID of the world to get messages for
 * @param isMobile Whether the current device is mobile
 * @param onClose Callback when the chat is closed
 */
@Composable
fun Chat(
    worldId: String,
    isMobile: Boolean,
    onClose: () -> Unit
) {
    // Fetch world to get chat longevity
    val longevityState = remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(worldId) {
        try {
            val w = main.api.WorldApi.getWorld(worldId)
            longevityState.value = w.chatLongevity
        } catch (_: Throwable) {
            longevityState.value = null
        }
    }

    ResponsivePanel(
        title = translated("World Chat"),
        isMobile = isMobile,
        onClose = onClose,
        headerRight = {
            val days = longevityState.value
            if (days != null) {
                val text = if (days <= 0.0) {
                    translated("Kept forever")
                } else {
                    val rounded = kotlin.math.round((days * 100.0)) / 100.0
                    val numberText = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
                    "${translated("Kept for")} $numberText ${if (rounded == 1.0) translated("day") else translated("days")}"
                }
                org.jetbrains.compose.web.dom.Text(text)
            }
        }
    ) {
        ChatContent(worldId = worldId)
    }
}
