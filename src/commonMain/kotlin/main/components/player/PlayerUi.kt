package main.components.player

import lib.Vector3

/**
 * Shared UI constants and helpers for player-related billboards and overlays.
 */
object PlayerUi {
    // Vertical offsets relative to the base position of a character
    const val USERNAME_Y_OFFSET: Float = 0.8f
    const val HUD_Y_OFFSET: Float = 1.125f
    const val EMOJI_HUD_Y_OFFSET: Float = 1.25f

    // Common font size used for billboards
    const val FONT_SIZE: Int = 32
    const val EMOJI_ONLY_FONT_SIZE: Int = 64

    // Colors for different HUD types
    const val CHAT_TEXT_COLOR: String = "black"
    const val CHAT_BG_COLOR: String = "white"
    const val STATUS_TEXT_COLOR: String = "white"
    const val STATUS_BG_COLOR: String = "#000000F0"

    fun usernamePositionFor(base: Vector3): Vector3 =
        Vector3(base.x, base.y + USERNAME_Y_OFFSET, base.z)

    fun hudPositionFor(base: Vector3, yOffset: Float = HUD_Y_OFFSET): Vector3 =
        Vector3(base.x, base.y + yOffset, base.z)

    fun truncateChat(message: String, max: Int = 16): String {
        if (message.length <= max) return message
        // Ensure we don't cut a surrogate pair in half
        val takeCount = if (message[max - 1].isHighSurrogate()) max - 1 else max
        return message.take(takeCount) + "…"
    }

    fun isEmojiOnly(text: String): Boolean = PlayerUiJs.isEmojiOnly(text)
}
