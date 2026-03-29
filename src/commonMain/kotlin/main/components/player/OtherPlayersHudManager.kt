package main.components.player

import kotlinx.browser.window
import lib.Vector3
import main.components.stripMarkdown
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager

/**
 * HUD manager for other players: typing/chat/status with precedence.
 */
class OtherPlayersHudManager(
    private val managerProvider: () -> WorldObjectManager,
    private val myUserIdProvider: () -> String?,
    private val positionProvider: (String) -> Vector3?,
) {
    private data class HudState(
        var desiredStatus: String = "",
        var chatMessage: String? = null,
        var chatExpiry: Double? = null,
        var isTyping: Boolean = false,
    )
    private enum class HudKind { Typing, Chat, Status, None }

    private val hudStateByUser = mutableMapOf<String, HudState>()
    private val lastAppliedHud = mutableMapOf<String, Pair<HudKind, String?>>()
    private val statusTextHandles = mutableMapOf<String, WorldObjectHandle>()
    private val chatMessageTimeouts = mutableMapOf<String, Int>()

    fun disposeAll() {
        statusTextHandles.values.forEach { it.remove() }
        statusTextHandles.clear()
        lastAppliedHud.clear()
        hudStateByUser.clear()
        chatMessageTimeouts.forEach { (_, id) -> window.clearTimeout(id) }
        chatMessageTimeouts.clear()
    }

    fun removeHud(userId: String) {
        statusTextHandles[userId]?.remove()
        statusTextHandles.remove(userId)
        lastAppliedHud.remove(userId)
        hudStateByUser.remove(userId)
        val t = chatMessageTimeouts.remove(userId)
        if (t != null) window.clearTimeout(t)
    }

    fun updateHudPosition(userId: String, basePosition: Vector3) {
        PlayerUiUtils.updateHudBillboardPosition(statusTextHandles[userId], basePosition)
    }

    private fun getHud(userId: String): HudState =
        hudStateByUser[userId] ?: HudState().also { hudStateByUser[userId] = it }

    private fun applyHud(userId: String) {
        if (userId == myUserIdProvider()) return
        // WorldObjectManager is non-null, but may not be initialized yet in state.
        val manager = try { managerProvider() } catch (_: Throwable) {
            // Retry shortly if manager not ready
            window.setTimeout({ applyHud(userId) }, 50); return
        }
        val position = positionProvider(userId)
        if (position == null) {
            window.setTimeout({ applyHud(userId) }, 50); return
        }

        // expire chat if needed
        val hud = getHud(userId)
        val now = window.performance.now()
        hud.chatExpiry?.let { if (now >= it) { hud.chatMessage = null; hud.chatExpiry = null } }

        val (kind, text) = when {
            hud.isTyping -> HudKind.Typing to "\uD83D\uDCAC"
            hud.chatMessage != null -> HudKind.Chat to hud.chatMessage
            hud.desiredStatus.isNotBlank() -> HudKind.Status to hud.desiredStatus
            else -> HudKind.None to null
        }
        val last = lastAppliedHud[userId]
        if (last != null && last.first == kind && last.second == text) return

        statusTextHandles[userId]?.remove()
        statusTextHandles.remove(userId)
        if (kind == HudKind.None) { lastAppliedHud[userId] = HudKind.None to null; return }

        val (textColor, bg) = when (kind) {
            HudKind.Typing -> "white" to PlayerUi.STATUS_BG_COLOR
            HudKind.Chat -> PlayerUi.CHAT_TEXT_COLOR to PlayerUi.CHAT_BG_COLOR
            HudKind.Status -> PlayerUi.STATUS_TEXT_COLOR to PlayerUi.STATUS_BG_COLOR
            else -> PlayerUi.STATUS_TEXT_COLOR to PlayerUi.STATUS_BG_COLOR
        }
        val handle = PlayerUiUtils.createHudBillboard(
            manager, text ?: "", position, textColor, bg
        )
        if (handle != null) statusTextHandles[userId] = handle
        lastAppliedHud[userId] = kind to text
    }

    fun setDesiredStatus(userId: String, status: String) {
        val hud = getHud(userId)
        hud.desiredStatus = status.trim()
        applyHud(userId)
    }

    fun setTyping(userId: String, typing: Boolean) {
        val hud = getHud(userId)
        hud.isTyping = typing
        applyHud(userId)
    }

    fun showChatMessage(userId: String, message: String) {
        if (userId == myUserIdProvider()) return
        val plainText = stripMarkdown(message)
        if (plainText.isBlank()) return
        val hud = getHud(userId)
        val truncated = PlayerUi.truncateChat(plainText)
        chatMessageTimeouts[userId]?.let { window.clearTimeout(it) }
        hud.chatMessage = truncated
        val timeoutId = window.setTimeout({
            val h = getHud(userId)
            h.chatMessage = null
            h.chatExpiry = null
            applyHud(userId)
            chatMessageTimeouts.remove(userId)
        }, 2000)
        chatMessageTimeouts[userId] = timeoutId
        hud.chatExpiry = window.performance.now() + 2000
        applyHud(userId)
    }
}
