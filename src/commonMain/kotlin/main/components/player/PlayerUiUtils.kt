package main.components.player

import lib.Vector3
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager

/**
 * Small helpers for creating and positioning text billboards for players.
 */
object PlayerUiUtils {
    fun createUsernameBillboard(
        manager: WorldObjectManager,
        username: String,
        basePosition: Vector3,
    ): WorldObjectHandle? {
        return manager.createTextBillboard(
            text = username,
            position = PlayerUi.usernamePositionFor(basePosition),
            fontSize = PlayerUi.FONT_SIZE,
            textColor = "white",
        )
    }

    fun createHudBillboard(
        manager: WorldObjectManager,
        text: String,
        basePosition: Vector3,
        textColor: String = PlayerUi.STATUS_TEXT_COLOR,
        backgroundColor: String? = PlayerUi.STATUS_BG_COLOR,
        fontSize: Int = PlayerUi.FONT_SIZE,
        yOffset: Float = PlayerUi.HUD_Y_OFFSET,
    ): WorldObjectHandle? {
        return manager.createTextBillboard(
            text = text,
            position = PlayerUi.hudPositionFor(basePosition, yOffset),
            fontSize = fontSize,
            textColor = textColor,
            backgroundColor = backgroundColor,
        )
    }

    fun updateUsernameBillboardPosition(handle: WorldObjectHandle?, basePosition: Vector3) {
        handle?.position = PlayerUi.usernamePositionFor(basePosition)
    }

    fun updateHudBillboardPosition(
        handle: WorldObjectHandle?,
        basePosition: Vector3,
        yOffset: Float = PlayerUi.HUD_Y_OFFSET,
    ) {
        handle?.position = PlayerUi.hudPositionFor(basePosition, yOffset)
    }
}
