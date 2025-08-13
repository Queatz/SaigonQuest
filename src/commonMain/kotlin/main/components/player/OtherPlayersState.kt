package main.components.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import lib.Node
import lib.Vector3
import main.api.ListenerToken
import main.components.shadow.ShadowManager
import main.components.world.CharacterDirection
import main.components.world.PlayerActivity
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager
import models.UpdatePlayerData

class OtherPlayersState {
    var httpWorldIdOverride: String? = null

    val playerHandles = mutableMapOf<String, WorldObjectHandle>()
    val playerNodes = mutableMapOf<String, Node>()
    val playerTargetPositions = mutableMapOf<String, Vector3>()
    val playerAnimationTimes = mutableMapOf<String, Double>()
    val usernameTextHandles = mutableMapOf<String, WorldObjectHandle>()

    val pendingStatuses = mutableMapOf<String, String>()
    val pendingAppearanceRefreshTimers = mutableMapOf<String, Int>()

    val playerUpdateData = mutableMapOf<String, UpdatePlayerData>()
    val playerUsernames = mutableMapOf<String, String>()
    val playerIsPlayerFlags = mutableMapOf<String, Boolean>()
    val playerDirections = mutableMapOf<String, CharacterDirection>()
    val playerExplicitActivity = mutableMapOf<String, PlayerActivity?>()

    lateinit var worldObjectManager: WorldObjectManager
    lateinit var shadowManager: ShadowManager

    fun hasWorldObjectManager(): Boolean = this::worldObjectManager.isInitialized
    fun hasShadowManager(): Boolean = this::shadowManager.isInitialized

    val movementDelta = FrameDeltaTracker()
    val baseMovementSpeed: Float = 3.0f
    val joinLeaveDuration: Double = 0.25

    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    var myUserId: String? = null

    val listenerTokens = mutableListOf<ListenerToken>()
}
