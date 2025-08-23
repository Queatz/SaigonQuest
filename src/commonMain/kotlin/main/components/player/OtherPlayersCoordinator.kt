package main.components.player

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import lib.AbstractMesh
import lib.Scene
import lib.Vector3
import main.api.ApiClient
import main.api.UserApi
import main.api.ItemApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.api.WorldApi
import main.api.addPlayerPositionListener
import main.api.addUpdatePlayerListener
import main.api.addUserJoinListener
import main.api.addUserLeaveListener
import main.api.sendUserJoin
import main.components.shadow.ShadowManager
import main.components.world.PlayerActivity
import main.components.world.SpriteSheet
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager
import models.ChatMessage
import models.PlayerPositionData
import models.Position
import models.UpdatePlayerData
import models.UserJoinData
import models.UserLeaveData
import models.WebSocketAction
import kotlin.random.Random

class OtherPlayersCoordinator(private val scene: Scene) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val players = mutableMapOf<String, OtherPlayer>()

    // Managers – must be set before initialize()
    lateinit var worldObjectManager: WorldObjectManager
    var shadowManager: ShadowManager? = null
    var httpWorldIdOverride: String? = null
    var myUserId: String? = null

    private val hud = OtherPlayersHudManager(
        managerProvider = { worldObjectManager },
        myUserIdProvider = { myUserId },
        positionProvider = { uid -> players[uid]?.handle?.position }
    )

    private var initialized = false
    private val baseMovementSpeed: Float = 3.0f
    private val joinLeaveDuration: Double = 0.6
    private val movementDelta = FrameDeltaTracker()
    private val usernameTextHandles = mutableMapOf<String, WorldObjectHandle>()
    private var worldMeshes: Array<AbstractMesh> = emptyArray()

    // Spawn retry state to ensure other players appear even if world assets are not yet ready
    private val spawnRetryCounts = mutableMapOf<String, Int>()
    private val maxSpawnRetries = 15
    private val spawnRetryInitialDelayMs = 150
    private val spawnRetryMaxDelayMs = 2000
    private val spawnRetryTimeouts = mutableMapOf<String, Int>()

    // Alpha flicker ranges between 0.5 and 1.0 (0.5 base + 0.0-0.5 random)
    private fun computeFlickerAlpha(): Float {
        val base = 0.25
        val jitter = Random.nextDouble() * (1.0 - base)
        return (base + jitter).coerceIn(0.0, 1.0).toFloat()
    }

    fun setWorldObjectManager(manager: WorldObjectManager) {
        worldObjectManager = manager
    }

    fun setShadowManager(manager: ShadowManager) {
        shadowManager = manager
    }

    fun setHttpWorldId(id: String) {
        httpWorldIdOverride = id
    }

    fun setWorldMeshes(meshes: Array<AbstractMesh>) {
        worldMeshes = meshes
    }

    private fun snapToGround(position: Vector3): Vector3 {
        if (worldMeshes.isEmpty()) return position
        val snapped = WorldCollisionUtils.findValidYPosition(
            worldMeshes = worldMeshes,
            collider = null,
            moveCircle = null,
            position = position,
            yPadding = 0.5f
        )
        return snapped ?: position
    }

    fun initialize() {
        if (!this::worldObjectManager.isInitialized) {
            console.error("OtherPlayersCoordinator: worldObjectManager must be set before initialize()")
            return
        }
        if (initialized) return
        initialized = true

        // Identify current user and prune any accidental self-spawn
        scope.launch {
            try {
                myUserId = UserApi.getCurrentUser().key
                pruneSelfIfPresent()
            } catch (_: Throwable) {
            }
        }

        // Register frame update
        scene.registerBeforeRender { update() }

        // Listeners
        WebSocketApi.addUserJoinListener { onUserJoin(it) }
        WebSocketApi.addUserLeaveListener { onUserLeave(it) }
        WebSocketApi.addPlayerPositionListener { onPlayerPosition(it) }
        WebSocketApi.addUpdatePlayerListener { onUpdatePlayer(it) }
        WebSocketApi.addListenerWithToken(WebSocketAction.RECEIVE_CHAT_MESSAGE) { message ->
            try {
                if (message.isValid()) {
                    val chat = ApiClient.json.decodeFromString<ChatMessage>(message.data)
                    hud.showChatMessage(chat.userId, chat.message)
                }
            } catch (_: Throwable) {
            }
        }

        // Typing
        WebSocketApi.addListener(WebSocketAction.PLAYER_TYPING) { message ->
            try {
                if (message.isValid()) {
                    val data = ApiClient.json.decodeFromString(models.PlayerTypingData.serializer(), message.data)
                    if (data.userId.isNotEmpty()) hud.setTyping(data.userId, data.isTyping)
                }
            } catch (_: Throwable) {
            }
        }

        // Announce join and seed snapshot
        scope.launch {
            try {
                if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED) {
                    val user = UserApi.getCurrentUser()
                    val wsWorldId = WebSocketConnectionManager.currentWorldId.value
                    val httpWorldId = httpWorldIdOverride ?: wsWorldId
                    if (wsWorldId != null) {
                        val effectiveWorldId = httpWorldId ?: wsWorldId
                        val snapshot = WorldApi.getWorldState(effectiveWorldId)
                        if (myUserId == null) myUserId = user.key
                        // Ensure we never keep a self entry from any earlier race
                        pruneSelfIfPresent()
                        snapshot.players.forEach { p ->
                            if (p.userId != myUserId) {
                                ensurePlayer(p.userId, p.position).also { op ->
                                    op.username = p.username
                                    op.isPlayer = p.isPlayer
                                    op.targetPos = Vector3(p.position.x, p.position.y, p.position.z)
                                    if (p.isPlayer) removePotentialVisitorDuplicates(
                                        newUserId = p.userId,
                                        newUsername = p.username,
                                        newPosition = Vector3(p.position.x, p.position.y, p.position.z)
                                    )
                                    attemptSpawnWithUser(op,)
                                    if (!p.status.isNullOrBlank()) {
                                        op.status = p.status.orEmpty()
                                        hud.setDesiredStatus(p.userId, p.status.orEmpty())
                                    }
                                    if (p.isTyping == true) hud.setTyping(p.userId, true)
                                }
                            }
                        }
                        // Fallback: if snapshot had players but none spawned yet (due to timing), retry shortly
                        if (snapshot.players.isNotEmpty()) {
                            val nonSelf = snapshot.players.filter { it.userId != myUserId }
                            if (nonSelf.isNotEmpty()) {
                                window.setTimeout({
                                    nonSelf.forEach { p ->
                                        ensurePlayer(p.userId, p.position).also { op ->
                                            if (op.handle == null) {
                                                op.username = p.username
                                                op.isPlayer = p.isPlayer
                                                op.targetPos = Vector3(p.position.x, p.position.y, p.position.z)
                                                attemptSpawnWithUser(op,)
                                            }
                                        }
                                    }
                                }, 200)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                console.error("OtherPlayersCoordinator: Presence init failed", t)
            }
        }
    }

    fun dispose() {
        // Remove meshes and HUD
        players.values.forEach { it.handle?.remove() }
        players.clear()
        hud.disposeAll()
        usernameTextHandles.values.forEach { it.remove() }
        usernameTextHandles.clear()
        // Clear any pending spawn retries
        spawnRetryTimeouts.values.forEach { window.clearTimeout(it) }
        spawnRetryTimeouts.clear()
        spawnRetryCounts.clear()
    }

    // Queries
    fun getUserIdForMesh(mesh: dynamic): String? =
        players.entries.find { (_, p) ->
            p.handle?.node?.getChildMeshes()?.any { it === mesh } == true
        }?.key

    fun getPlayerData(userId: String): Pair<String, Boolean>? {
        val p = players[userId] ?: return null
        return Pair(p.username, p.isPlayer)
    }

    fun getUserIdForUsername(username: String): String? =
        players.entries.find { it.value.username == username }?.key

    fun getPlayerMeshes(): Array<AbstractMesh> =
        players.values.mapNotNull { it.handle?.node }
            .map { it.getChildMeshes() }
            .toTypedArray()
            .flatten()
            .toTypedArray()

    // Event handlers
    private fun onUserJoin(join: UserJoinData) {
        if (join.userId == myUserId) return
        val op = ensurePlayer(join.userId, join.position)
        scope.launch {
            op.mutex.withLock {
                // If a visitor likely corresponds to this player, remove it to avoid duplicates
                if (join.isPlayer) removePotentialVisitorDuplicates(
                    newUserId = join.userId,
                    newUsername = join.username,
                    newPosition = Vector3(join.position.x, join.position.y, join.position.z)
                )
                op.username = join.username
                op.isPlayer = join.isPlayer
                op.targetPos = snapToGround(Vector3(join.position.x, join.position.y, join.position.z))
                attemptSpawnWithUser(op,)
            }
        }
    }

    private fun removePotentialVisitorDuplicates(newUserId: String, newUsername: String, newPosition: Vector3) {
        val radius = 0.6f
        val candidates = players.values.filter { existing ->
            existing.userId != newUserId && !existing.isPlayer &&
                    run {
                        val pos = existing.targetPos ?: existing.handle?.position
                        if (pos == null) false else pos.subtract(newPosition).length() <= radius
                    }
        }
        // Also consider same-username visitors anywhere (brittle but catches common case)
        val sameName = players.values.filter { it.userId != newUserId && !it.isPlayer && it.username == newUsername }
        (candidates + sameName).distinct().forEach { dup ->
            dup.handle?.remove()
            players.remove(dup.userId)
            hud.removeHud(dup.userId)
            usernameTextHandles.remove(dup.userId)?.remove()
            clearSpawnRetry(dup.userId)
        }
    }

    private fun onUserLeave(leave: UserLeaveData) {
        val op = players[leave.userId] ?: return
        scope.launch {
            op.mutex.withLock {
                // Begin scale-out animation
                op.leaveStartMs = window.performance.now()
                // Stop any pending spawn retries
                clearSpawnRetry(leave.userId)
                window.setTimeout({
                    op.handle?.remove()
                    players.remove(leave.userId)
                    hud.removeHud(leave.userId)
                    usernameTextHandles.remove(leave.userId)?.remove()
                }, (joinLeaveDuration * 1000).toInt())
            }
        }
    }

    private fun onPlayerPosition(pos: PlayerPositionData) {
        if (pos.userId == myUserId) return
        val op = ensurePlayer(pos.userId, pos.position)
        op.isPlayer = pos.isPlayer
        op.targetPos = snapToGround(Vector3(pos.position.x, pos.position.y, pos.position.z))
        attemptSpawnWithUser(op,)
    }

    private fun onUpdatePlayer(update: UpdatePlayerData) {
        if (update.userId == myUserId) return
        val op = ensurePlayer(update.userId, null)
        scope.launch {
            op.mutex.withLock {
                val prevIsPlayer = op.isPlayer
                op.username = update.name
                op.isPlayer = update.isPlayer
                update.status?.let { s -> op.status = s; hud.setDesiredStatus(update.userId, s) }
                // Track explicit activity if server provides it
                op.activity = update.activity
                // If username changed, refresh username billboard
                refreshUsernameBillboard(op)
                // If appearance-related changes exist server-side, we can bump revision and re-assemble
                assembleAppearance(op, scale = false)
                if (prevIsPlayer != op.isPlayer) {
                    // Remove old mesh and respawn to reflect new role cleanly
                    op.handle?.remove(); op.handle = null
                    usernameTextHandles.remove(op.userId)?.remove()
                    attemptSpawnWithUser(op,)
                }
            }
        }
    }

    // Spawning and appearance
    private fun ensurePlayer(userId: String, position: Position?): OtherPlayer =
        players[userId] ?: OtherPlayer(
            userId = userId,
            targetPos = Vector3(position?.x ?: 0f, position?.y ?: 0f, position?.z ?: 0f),
            lastPos = Vector3(position?.x ?: 0f, position?.y ?: 0f, position?.z ?: 0f)
        ).also { players[userId] = it }

    private fun spawnIfNeeded(op: OtherPlayer) {
        if (op.handle != null) return
        val startPos = snapToGround(op.targetPos ?: op.lastPos)
        val baseSprite = SpriteSheet.createPlayerSpriteSheetForBodyType(null, null)
        val handle: WorldObjectHandle? = worldObjectManager.createCharacter(
            spriteSheet = baseSprite,
            direction = op.direction,
            position = startPos,
            additionalSpriteSheets = emptyList(),
            user = null,
            initialScale = .5f
        )
        if (handle == null) {
            scheduleSpawnRetry(op, "createCharacter returned null (placeholder)")
            return
        }
        // Start with scale 0 for join animation
        handle.scale = Vector3(.5f, .5f, .5f)
        op.handle = handle
        op.lastPos = startPos
        op.joinStartMs = window.performance.now()

        // Success: clear retry state
        clearSpawnRetry(op.userId)

        // Shadows
        shadowManager?.let { mgr ->
            handle.node.getChildMeshes().forEach { mesh ->
                mgr.addShadowCaster(mesh)
                mgr.configureShadowReceiver(mesh)
            }
        }

        // HUD (only apply non-blank status to avoid clearing an earlier set value)
        if (op.status.isNotBlank()) hud.setDesiredStatus(op.userId, op.status)
        // Username billboard
        createOrUpdateUsernameBillboard(op, startPos)

        // Assemble appearance asynchronously
        scope.launch { assembleAppearance(op) }
    }

    private fun scheduleSpawnRetry(op: OtherPlayer, reason: String) {
        // If already spawned, no retry needed
        if (op.handle != null) {
            clearSpawnRetry(op.userId)
            return
        }
        val count = (spawnRetryCounts[op.userId] ?: 0)
        if (count >= maxSpawnRetries) {
            console.warn("OtherPlayersCoordinator: spawn retries exceeded for ${op.userId} (reason=$reason)")
            clearSpawnRetry(op.userId)
            return
        }
        spawnRetryCounts[op.userId] = count + 1
        val prev = spawnRetryTimeouts.remove(op.userId)
        if (prev != null) window.clearTimeout(prev)
        val delay = kotlin.math.min(spawnRetryInitialDelayMs * (1 shl count), spawnRetryMaxDelayMs)
        console.log("OtherPlayersCoordinator: scheduling spawn retry #${count + 1} for ${op.userId} in ${delay}ms (reason=$reason)")
        val id = window.setTimeout({
            val still = players[op.userId]
            if (still == null) {
                clearSpawnRetry(op.userId)
                return@setTimeout
            }
            if (still.handle != null) {
                clearSpawnRetry(op.userId)
                return@setTimeout
            }
            attemptSpawnWithUser(still,)
        }, delay)
        spawnRetryTimeouts[op.userId] = id
    }

    private fun clearSpawnRetry(userId: String) {
        spawnRetryCounts.remove(userId)
        spawnRetryTimeouts.remove(userId)?.let { window.clearTimeout(it) }
    }

    private fun attemptSpawnWithUser(op: OtherPlayer, scale: Boolean = true) {
        // Fallback: spawn placeholder if user fetch isn't quick
        val fallbackId = window.setTimeout({ if (op.handle == null) spawnIfNeeded(op) }, 150)
        scope.launch {
            try {
                val user = UserApi.getUserById(op.userId)
                if (user != null) {
                    window.clearTimeout(fallbackId)
                    if (op.handle == null) {
                        val startPos = snapToGround(op.targetPos ?: op.lastPos)
                        val baseSprite = SpriteSheet.createPlayerSpriteSheetForBodyType(user.sex, user.bodyType)
                        val handle: WorldObjectHandle? = worldObjectManager.createCharacter(
                            spriteSheet = baseSprite,
                            direction = op.direction,
                            position = startPos,
                            additionalSpriteSheets = emptyList(),
                            user = user,
                            initialScale = if (scale) .5f else 1f
                        )
                        if (handle != null) {
                            op.handle = handle
                            op.lastPos = startPos
                            op.targetPos = startPos
                            if (scale) {
                                op.joinStartMs = window.performance.now()
                            }
                            // Track the profile so we can detect future base changes
                            op.profile = user
                            // Success: clear retry state
                            spawnRetryCounts.remove(op.userId)
                            shadowManager?.let { mgr ->
                                handle.node.getChildMeshes().forEach { mesh ->
                                    mgr.addShadowCaster(mesh)
                                    mgr.configureShadowReceiver(mesh)
                                }
                            }
                            // Prefer server status if available
                            if (user.status.isNotBlank()) op.status = user.status
                            if (op.status.isNotBlank()) hud.setDesiredStatus(op.userId, op.status)
                            createOrUpdateUsernameBillboard(op, startPos)
                            // Apply full appearance (hair, eyes, worn) immediately so it doesn't wait for movement
                            try {
                                val hair = SpriteSheet.createHairSpriteSheet(user.hairStyle)
                                val eyes = SpriteSheet.createEyesSpriteSheet(user.eyeColor, user.sex)
                                val itemsById = try {
                                    val ids = user.wornItems
                                    if (ids.isEmpty()) emptyMap() else ItemApi.getItemsByIds(ids).associateBy { it.key }
                                } catch (_: Throwable) { emptyMap() }
                                val worn = WornItemsRenderer.getWornItemSpriteSheetsForUser(user, itemsById)
                                val sheets = listOfNotNull(hair, eyes) + worn
                                op.additionalSpriteSheets = sheets
                                op.skinTone = user.skinTone
                                handle.updateAppearance(sheets, user.skinTone)
                            } catch (t: Throwable) {
                                console.error("OtherPlayersCoordinator: initial appearance apply failed for ${op.userId}", t)
                            }
                        } else {
                            scheduleSpawnRetry(op, "createCharacter returned null (with user)")
                        }
                    } else {
                        val hair = SpriteSheet.createHairSpriteSheet(user.hairStyle)
                        val eyes = SpriteSheet.createEyesSpriteSheet(user.eyeColor, user.sex)
                        val itemsById = try {
                            val ids = user.wornItems
                            if (ids.isEmpty()) emptyMap() else ItemApi.getItemsByIds(ids).associateBy { it.key }
                        } catch (_: Throwable) { emptyMap() }
                        val worn = WornItemsRenderer.getWornItemSpriteSheetsForUser(user, itemsById)
                        val sheets = listOfNotNull(hair, eyes) + worn
                        op.handle?.updateAppearance(sheets, user.skinTone ?: 0.5f)
                        if (op.status.isBlank() && user.status.isNotBlank()) {
                            op.status = user.status
                            hud.setDesiredStatus(op.userId, op.status)
                        }
                    }
                }
            } catch (t: Throwable) {
                console.error("OtherPlayersCoordinator: attemptSpawnWithUser failed for ${op.userId}", t)
            }
        }
    }

    private suspend fun assembleAppearance(op: OtherPlayer, scale: Boolean = true) {
        try {
            val prev = op.profile
            val user = UserApi.getUserById(op.userId) ?: return

            // Detect base body change (sex or bodyType) which requires base spritesheet swap
            val baseChanged = (prev == null) || (prev.sex != user.sex) || (prev.bodyType != user.bodyType)

            if (baseChanged) {
                // Preserve current transform/state
                val currentPos = op.handle?.position ?: op.targetPos ?: op.lastPos
                val currentDir = op.direction
                // Remove current mesh and trigger respawn with updated base body
                op.handle?.remove()
                op.handle = null
                op.targetPos = currentPos
                op.lastPos = currentPos
                op.profile = user
                // Attempt spawn using fresh user so base body type is applied
                attemptSpawnWithUser(op, scale = scale)
                return
            }

            // No base change: update only additional layers and skin tone
            op.profile = user
            val hair = SpriteSheet.createHairSpriteSheet(user.hairStyle)
            val eyes = SpriteSheet.createEyesSpriteSheet(user.eyeColor, user.sex)
            val itemsById = try {
                val ids = user.wornItems
                if (ids.isEmpty()) emptyMap() else ItemApi.getItemsByIds(ids).associateBy { it.key }
            } catch (_: Throwable) { emptyMap() }
            val worn = WornItemsRenderer.getWornItemSpriteSheetsForUser(user, itemsById)
            val sheets = listOfNotNull(hair, eyes) + worn
            op.additionalSpriteSheets = sheets
            op.skinTone = user.skinTone
            op.handle?.updateAppearance(sheets, op.skinTone)
        } catch (t: Throwable) {
            console.error("OtherPlayersCoordinator: assembleAppearance failed for ${op.userId}", t)
        }
    }

    fun sendJoinEvent(pos: Vector3) {
        scope.launch {
            val user = UserApi.getCurrentUser()
            val wsWorldId = WebSocketConnectionManager.currentWorldId.value
            if (wsWorldId != null) {
                WebSocketApi.sendUserJoin(
                    worldId = wsWorldId,
                    position = Position(pos.x, pos.y, pos.z),
                    isPlayer = user.isPlayer
                )
            }
        }
    }

    // Per-frame movement and HUD positioning
    private fun update() {
        val deltaTime = movementDelta.nextDeltaSeconds()
        val frameMovementSpeed = (baseMovementSpeed * deltaTime).toFloat()
        val now = window.performance.now()
        val initialJoinScale = 0.5f

        players.values.forEach { op ->
            val handle = op.handle ?: return@forEach
            // Join scale-in animation (with alpha flicker)
            op.joinStartMs?.let { start ->
                val elapsed = (now - start) / 1000.0
                if (elapsed < joinLeaveDuration) {
                    val progress = (elapsed / joinLeaveDuration).coerceIn(0.0, 1.0)
                    val s = (initialJoinScale + (1f - initialJoinScale) * progress.toFloat()).coerceIn(0f, 1f)
                    handle.scale = Vector3(0.5f + s * 0.5f, 0.5f + s * 0.5f, 0.5f + s * 0.5f)
                    // Apply random alpha flicker while joining
                    val alpha = computeFlickerAlpha()
                    handle.node.getChildMeshes().forEach { mesh ->
                        try {
                            mesh.material.alpha = alpha
                        } catch (_: Throwable) {
                        }
                    }
                } else {
                    handle.scale = Vector3(1f, 1f, 1f)
                    op.joinStartMs = null
                    // Restore opaque alpha after animation completes
                    handle.node.getChildMeshes().forEach { mesh ->
                        try {
                            mesh.material.alpha = 1f
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            // Leave scale-out animation (with alpha flicker)
            op.leaveStartMs?.let { start ->
                val elapsed = (now - start) / 1000.0
                if (elapsed < joinLeaveDuration) {
                    val progress = 1.0 - (elapsed / joinLeaveDuration)
                    val s = progress.toFloat().coerceIn(0f, 1f)
                    handle.scale = Vector3(0.5f + s * 0.5f, 0.5f + s * 0.5f, 0.5f + s * 0.5f)
                    // Apply random alpha flicker while leaving
                    val alpha = computeFlickerAlpha()
                    handle.node.getChildMeshes().forEach { mesh ->
                        try {
                            mesh.material.alpha = alpha
                        } catch (_: Throwable) {
                        }
                    }
                } else {
                    handle.scale = Vector3(0.5f, 0.5f, 0.5f)
                    // Ensure fully transparent at the end of leave animation
                    handle.node.getChildMeshes().forEach { mesh ->
                        try {
                            mesh.material.alpha = 0f
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
            val targetPos = op.targetPos?.let { snapToGround(it) } ?: return@forEach
            val currentPos = handle.position
            val direction = targetPos.subtract(currentPos)
            val distance = direction.length()
            if (distance < 0.001f) {
                // Close enough
                handle.position = targetPos
                hud.updateHudPosition(op.userId, targetPos)
                if (op.activity == PlayerActivity.Moving) {
                    handle.updateActivity(null)
                    op.activity = null
                }
                // Keep username billboard in sync
                PlayerUiUtils.updateUsernameBillboardPosition(usernameTextHandles[op.userId], targetPos)
                return@forEach
            }

            // Update facing with hysteresis
            val currentFacing = op.direction
            val newFacing = main.components.world.calculateFacingFromVector(direction, currentFacing)
            if (newFacing != currentFacing) {
                op.direction = newFacing
                handle.updateDirection(newFacing)
            }

            direction.normalize()
            val step = kotlin.math.min(frameMovementSpeed, distance)
            direction.scaleInPlace(step)
            val moved = currentPos.add(direction)
            val newPosition = snapToGround(moved)
            handle.position = newPosition
            console.log("[OTHER] id=${op.userId} newPosition=$newPosition")
            hud.updateHudPosition(op.userId, newPosition)
            PlayerUiUtils.updateUsernameBillboardPosition(usernameTextHandles[op.userId], newPosition)
            if (op.activity != PlayerActivity.Sit) {
                if (op.activity != PlayerActivity.Moving) {
                    handle.updateActivity(PlayerActivity.Moving)
                }
                op.activity = PlayerActivity.Moving
            }
        }
    }

    private fun createOrUpdateUsernameBillboard(op: OtherPlayer, basePosition: Vector3) {
        // Remove old
        usernameTextHandles.remove(op.userId)?.remove()
        val handle = PlayerUiUtils.createUsernameBillboard(
            manager = worldObjectManager,
            username = op.username,
            basePosition = basePosition
        )
        if (handle != null) usernameTextHandles[op.userId] = handle
    }

    private fun refreshUsernameBillboard(op: OtherPlayer) {
        val pos = op.handle?.position ?: return
        createOrUpdateUsernameBillboard(op, pos)
    }

    // Public: refresh current user id and prune any mistakenly-spawned self entry
    fun refreshCurrentUserId() {
        scope.launch {
            try {
                myUserId = UserApi.getCurrentUser().key
                pruneSelfIfPresent()
            } catch (t: Throwable) {
                console.error("OtherPlayersCoordinator: refreshCurrentUserId failed", t)
            }
        }
    }

    // Remove any OtherPlayer entry that matches myUserId to avoid self-duplication
    private fun pruneSelfIfPresent() {
        val selfId = myUserId ?: return
        players.remove(selfId)?.let { selfOp ->
            selfOp.handle?.remove()
            hud.removeHud(selfId)
            usernameTextHandles.remove(selfId)?.remove()
        }
        clearSpawnRetry(selfId)
    }
}
