package main.components.player

import kotlinx.browser.window
import kotlinx.coroutines.launch
import lib.Vector3
import main.api.ApiClient
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.api.WorldApi
import main.api.addPlayerPositionListener
import main.api.addUpdatePlayerListener
import main.api.addUserJoinListener
import main.api.addUserLeaveListener
import main.api.sendUserJoin
import models.ChatMessage
import models.PlayerPositionData
import models.PlayerTypingData
import models.UpdatePlayerData
import models.UserJoinData
import models.UserLeaveData
import models.WebSocketAction

class OtherPlayersPresence(
    private val state: OtherPlayersState,
    private val factory: OtherPlayersFactory,
    private val hud: OtherPlayersHudManager,
) {
    fun refreshCurrentUserId() {
        state.scope.launch {
            try { state.myUserId = UserApi.getCurrentUser().key } catch (_: Throwable) {}
        }
    }

    private fun getPlayerPosition(): models.Position {
        val playerController = main.components.SceneManager.getPlayerController()
        val pos = playerController?.getPlayerPosition()
        return if (pos != null) models.Position(pos.x, pos.y, pos.z) else models.Position(0f, 0f, 0f)
    }

    fun initialize() {
        state.scope.launch {
            try { state.myUserId = UserApi.getCurrentUser().key } catch (_: Throwable) {}
        }

        // listeners
        state.listenerTokens += WebSocketApi.addUserJoinListener { handleUserJoin(it) }
        state.listenerTokens += WebSocketApi.addUserLeaveListener { handleUserLeave(it) }
        state.listenerTokens += WebSocketApi.addPlayerPositionListener { handlePlayerPosition(it) }
        state.listenerTokens += WebSocketApi.addUpdatePlayerListener { handleUpdatePlayer(it) }
        state.listenerTokens += WebSocketApi.addListenerWithToken(WebSocketAction.RECEIVE_CHAT_MESSAGE) { message ->
            try {
                if (message.isValid()) {
                    val chat = ApiClient.json.decodeFromString<ChatMessage>(message.data)
                    hud.showChatMessage(chat.userId, chat.message)
                }
            } catch (_: Throwable) {}
        }

        // typing
        setupTypingStatusListener()

        // announce join and seed
        state.scope.launch {
            try {
                if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED) {
                    val user = UserApi.getCurrentUser()
                    val wsWorldId = WebSocketConnectionManager.currentWorldId.value
                    val httpWorldId = state.httpWorldIdOverride ?: wsWorldId
                    if (wsWorldId != null) {
                        val playerPosition = getPlayerPosition()
                        WebSocketApi.sendUserJoin(wsWorldId, playerPosition, user.isPlayer)
                        val effectiveWorldId = httpWorldId ?: wsWorldId
                        val snapshot = WorldApi.getWorldState(effectiveWorldId)
                        if (state.myUserId == null) state.myUserId = user.key
                            snapshot.players.forEach { p ->
                            if (p.userId != state.myUserId) {
                                state.playerUsernames[p.userId] = p.username
                                state.playerIsPlayerFlags[p.userId] = p.isPlayer
                                val pos = Vector3(p.position.x, p.position.y, p.position.z)
                                val hasMesh = state.playerNodes.containsKey(p.userId)
                                if (!hasMesh) {
                                    run {
                                        val s = p.status
                                        if (!s.isNullOrBlank()) {
                                            state.pendingStatuses[p.userId] = s
                                            state.playerUpdateData[p.userId] = UpdatePlayerData(
                                                userId = p.userId, name = p.username, isPlayer = p.isPlayer, status = s
                                            )
                                        }
                                    }
                                    factory.createPlayerMesh(
                                        userId = p.userId,
                                        position = pos,
                                        isPlayer = p.isPlayer,
                                        username = p.username,
                                        animateJoin = true
                                    )
                                } else {
                                    state.playerTargetPositions[p.userId] = pos
                                    run {
                                        val s = p.status
                                        if (!s.isNullOrBlank()) {
                                            state.playerUpdateData[p.userId] = UpdatePlayerData(
                                                userId = p.userId, name = p.username, isPlayer = p.isPlayer, status = s
                                            )
                                            hud.setDesiredStatus(p.userId, s)
                                        }
                                    }
                                }
                                if (p.isTyping == true) hud.setTyping(p.userId, true)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                console.error("OtherPlayersPresence: Presence init failed", t)
            }
        }
    }

    private fun scheduleAppearanceRefresh(userId: String, delayMs: Int = 150) {
        if (userId == state.myUserId) return
        state.pendingAppearanceRefreshTimers[userId]?.let { window.clearTimeout(it) }
        val timerId = window.setTimeout({
            state.pendingAppearanceRefreshTimers.remove(userId)
            refreshAppearance(userId)
        }, delayMs)
        state.pendingAppearanceRefreshTimers[userId] = timerId
    }

    private fun refreshAppearance(userId: String) {
        val handle = state.playerHandles[userId] ?: return
        val currentPos = handle.position
        val isPlayer = state.playerIsPlayerFlags[userId] ?: false
        val username = state.playerUsernames[userId]
        factory.createPlayerMesh(userId, currentPos, isPlayer, username, animateJoin = false)
    }

    private fun handleUserJoin(joinData: UserJoinData) {
        if (joinData.userId == state.myUserId) return
        state.playerUsernames[joinData.userId] = joinData.username
        state.playerIsPlayerFlags[joinData.userId] = joinData.isPlayer
        if (state.playerNodes.containsKey(joinData.userId)) {
            state.playerTargetPositions[joinData.userId] = Vector3(
                joinData.position.x, joinData.position.y, joinData.position.z
            )
            return
        }
        factory.createPlayerMesh(
            userId = joinData.userId,
            position = Vector3(joinData.position.x, joinData.position.y, joinData.position.z),
            isPlayer = joinData.isPlayer,
            username = joinData.username,
            animateJoin = true
        )
        scheduleAppearanceRefresh(joinData.userId, 50)
    }

    private fun handleUserLeave(leaveData: UserLeaveData) {
        if (!state.playerNodes.containsKey(leaveData.userId)) return
        state.playerNodes[leaveData.userId]?.let {
            state.playerAnimationTimes[leaveData.userId] = window.performance.now()
            state.playerTargetPositions.remove(leaveData.userId)
            window.setTimeout({
                factory.removePlayerMesh(leaveData.userId)
                state.playerUsernames.remove(leaveData.userId)
                state.playerIsPlayerFlags.remove(leaveData.userId)
            }, (state.joinLeaveDuration * 1000).toInt())
        }
    }

    private fun handlePlayerPosition(positionData: PlayerPositionData) {
        if (positionData.userId == state.myUserId) return
        val newPosition = Vector3(positionData.position.x, positionData.position.y, positionData.position.z)
        state.playerTargetPositions[positionData.userId] = newPosition
        state.playerIsPlayerFlags[positionData.userId] = positionData.isPlayer
        if (!state.playerNodes.containsKey(positionData.userId)) {
            if (!state.playerUsernames.containsKey(positionData.userId)) {
                state.playerUsernames[positionData.userId] = "Player-${positionData.userId}"
            }
            factory.createPlayerMesh(
                userId = positionData.userId,
                position = newPosition,
                isPlayer = positionData.isPlayer,
                username = state.playerUsernames[positionData.userId],
                animateJoin = true
            )
        }
    }

    private fun handleUpdatePlayer(updateData: UpdatePlayerData) {
        if (updateData.userId == state.myUserId) return
        val prev = state.playerIsPlayerFlags[updateData.userId]
        val isPlayerChanged = prev != null && prev != updateData.isPlayer
        state.playerUsernames[updateData.userId] = updateData.name
        state.playerIsPlayerFlags[updateData.userId] = updateData.isPlayer
        state.playerUpdateData[updateData.userId] = updateData

        state.usernameTextHandles[updateData.userId]?.let { old ->
            old.remove()
            state.usernameTextHandles.remove(updateData.userId)
            val position = state.playerHandles[updateData.userId]?.position
            if (position != null) {
                val manager = try { state.worldObjectManager } catch (_: Throwable) { return@let }
                val newTextHandle = PlayerUiUtils.createUsernameBillboard(
                    manager = manager,
                    username = updateData.name,
                    basePosition = position,
                )
                if (newTextHandle != null) state.usernameTextHandles[updateData.userId] = newTextHandle
            }
        }
        if (updateData.activity == null && !isPlayerChanged) {
            scheduleAppearanceRefresh(updateData.userId)
        }
        updateData.status?.let { status ->
            if (status.isNotBlank()) hud.setDesiredStatus(updateData.userId, status) else hud.setDesiredStatus(updateData.userId, "")
        }
    }

    private fun setupTypingStatusListener() {
        state.listenerTokens += WebSocketApi.addListenerWithToken(WebSocketAction.PLAYER_TYPING) { message ->
            try {
                if (message.isValid()) {
                    val data = ApiClient.json.decodeFromString(PlayerTypingData.serializer(), message.data)
                    if (data.userId.isNotEmpty()) hud.setTyping(data.userId, data.isTyping)
                }
            } catch (_: Throwable) {}
        }
    }

    fun dispose() {
        state.listenerTokens.forEach { WebSocketApi.removeListener(it) }
        state.listenerTokens.clear()
    }
}
