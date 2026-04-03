package main.components.player

import kotlinx.browser.window
import lib.Vector3
import main.components.stripMarkdown
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager

/**
 * Manages username, status and chat billboards for the local player.
 */
class SelfHudManager {
    private var manager: WorldObjectManager? = null

    private var usernameTextHandle: WorldObjectHandle? = null
    private var statusTextHandle: WorldObjectHandle? = null
    private var chatMessageTextHandle: WorldObjectHandle? = null
    private var chatMessageTimeoutId: Int? = null
    private var chatYOffset: Float = PlayerUi.HUD_Y_OFFSET

    private var onChatCleared: (() -> Unit)? = null

    fun setManager(m: WorldObjectManager?) { manager = m }

    fun setOnChatCleared(callback: (() -> Unit)?) { onChatCleared = callback }

    fun updateUsername(basePosition: Vector3, username: String) {
        usernameTextHandle?.remove()
        val m = manager ?: return
        usernameTextHandle = PlayerUiUtils.createUsernameBillboard(m, username, basePosition)
    }

    fun showStatus(basePosition: Vector3, status: String) {
        if (status.isBlank()) { clearStatus(); return }
        if (chatMessageTextHandle != null) return
        clearStatus()
        val m = manager ?: return
        statusTextHandle = PlayerUiUtils.createHudBillboard(
            manager = m,
            text = status,
            basePosition = basePosition,
            textColor = PlayerUi.STATUS_TEXT_COLOR,
            backgroundColor = PlayerUi.STATUS_BG_COLOR,
        )
    }

    fun clearStatus() {
        statusTextHandle?.remove()
        statusTextHandle = null
    }

    fun showChat(basePosition: Vector3, message: String) {
        val plainText = stripMarkdown(message)
        if (plainText.isBlank()) return

        // remove existing chat and temporarily hide status
        clearChat()
        clearStatus()

        val m = manager ?: return
        val text = PlayerUi.truncateChat(plainText)
        val isEmoji = PlayerUi.isEmojiOnly(text)
        chatYOffset = if (isEmoji) PlayerUi.EMOJI_HUD_Y_OFFSET else PlayerUi.HUD_Y_OFFSET

        chatMessageTextHandle = PlayerUiUtils.createHudBillboard(
            manager = m,
            text = text,
            basePosition = basePosition,
            textColor = if (isEmoji) "white" else PlayerUi.CHAT_TEXT_COLOR,
            backgroundColor = if (isEmoji) null else PlayerUi.CHAT_BG_COLOR,
            fontSize = if (isEmoji) PlayerUi.EMOJI_ONLY_FONT_SIZE else PlayerUi.FONT_SIZE,
            yOffset = chatYOffset,
        )

        chatMessageTimeoutId = window.setTimeout({
            clearChat()
            onChatCleared?.invoke()
        }, 2000)
    }

    fun clearChat() {
        chatMessageTimeoutId?.let { window.clearTimeout(it) }
        chatMessageTimeoutId = null
        chatMessageTextHandle?.remove()
        chatMessageTextHandle = null
    }

    fun updateAllPositions(basePosition: Vector3) {
        PlayerUiUtils.updateUsernameBillboardPosition(usernameTextHandle, basePosition)
        PlayerUiUtils.updateHudBillboardPosition(statusTextHandle, basePosition)
        PlayerUiUtils.updateHudBillboardPosition(chatMessageTextHandle, basePosition, chatYOffset)
    }

    fun dispose() {
        clearChat()
        clearStatus()
        usernameTextHandle?.remove()
        usernameTextHandle = null
        manager = null
        onChatCleared = null
    }
}
