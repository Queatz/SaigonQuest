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
        backgroundColor: String = PlayerUi.STATUS_BG_COLOR,
    ): WorldObjectHandle? {
        return manager.createTextBillboard(
            text = text,
            position = PlayerUi.hudPositionFor(basePosition),
            fontSize = PlayerUi.FONT_SIZE,
            textColor = textColor,
            backgroundColor = backgroundColor,
        )
    }

    fun updateUsernameBillboardPosition(handle: WorldObjectHandle?, basePosition: Vector3) {
        handle?.position = PlayerUi.usernamePositionFor(basePosition)
    }

    fun updateHudBillboardPosition(handle: WorldObjectHandle?, basePosition: Vector3) {
        handle?.position = PlayerUi.hudPositionFor(basePosition)
    }
}
