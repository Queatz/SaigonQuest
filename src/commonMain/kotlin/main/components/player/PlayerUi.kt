package main.components.player

import lib.Vector3

/**
 * Shared UI constants and helpers for player-related billboards and overlays.
 */
object PlayerUi {
    // Vertical offsets relative to the base position of a character
    const val USERNAME_Y_OFFSET: Float = 0.8f
    const val HUD_Y_OFFSET: Float = 1.1f

    // Common font size used for billboards
    const val FONT_SIZE: Int = 18

    // Colors for different HUD types
    const val CHAT_TEXT_COLOR: String = "black"
    const val CHAT_BG_COLOR: String = "white"
    const val STATUS_TEXT_COLOR: String = "white"
    const val STATUS_BG_COLOR: String = "#00000080"

    fun usernamePositionFor(base: Vector3): Vector3 =
        Vector3(base.x, base.y + USERNAME_Y_OFFSET, base.z)

    fun hudPositionFor(base: Vector3): Vector3 =
        Vector3(base.x, base.y + HUD_Y_OFFSET, base.z)

    fun truncateChat(message: String, max: Int = 16): String =
        if (message.length > max) message.take(max) + "…" else message
}
