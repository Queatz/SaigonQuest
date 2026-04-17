package main.components

import androidx.compose.runtime.Composable
import main.components.translated
import Translations.str
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import main.components.chat.ChatBellButton
import main.components.DialogButton
import main.components.DialogButtonStyle
import org.jetbrains.compose.web.dom.Text

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
    var showInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(worldId) {
        try {
            val w = main.api.WorldApi.getWorld(worldId)
            longevityState.value = w.chatLongevity
        } catch (_: Throwable) {
            longevityState.value = null
        }
    }

    val retentionText: String? = run {
        val days = longevityState.value
        if (days != null) {
            if (days <= 0.0) {
                translated("⏲ Forever")
            } else {
                val rounded = kotlin.math.round((days * 100.0)) / 100.0
                val numberText = if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
                "${"⏲"} $numberText ${if (rounded == 1.0) translated("day") else translated("days")}"
            }
        } else null
    }

    ResponsivePanel(
        title = translated("Chat"),
        isMobile = isMobile,
        onClose = onClose,
        headerActions = {
            ChatBellButton(worldId = worldId)
        },
        headerRight = {
            val text = retentionText
            if (text != null) {
                DialogButton(
                    onClick = { showInfoDialog = true },
                    style = DialogButtonStyle.Secondary,
                    attrs = {
                        title(text)
                    }
                ) {
                    Text("⏳")
                }
            }
        },
        onTitleClick = {
            showInfoDialog = true
        },
    ) {
        ChatContent(
            worldId = worldId,
            isDialog = isMobile,
            onClose = onClose,
        )
    }

    if (showInfoDialog) {
        BasicAlertDialog(
            title = translated("Chat"),
            message = "${translated("Messages are kept for")} $retentionText",
            onClose = { showInfoDialog = false }
        )
    }
}
