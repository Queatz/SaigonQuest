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
import main.components.world.effect.WaterRippleEffect
import main.components.world.PlayerActivity
import main.components.world.SpriteSheet
import main.components.world.WorldObjectHandle
import main.components.world.WorldObjectManager
import main.components.audio.SpatialSoundManager
import models.ChatMessage
import models.PlayerPositionData
import models.Position
import models.Sex
import models.User
import models.UpdatePlayerData
import models.UserJoinData
import models.UserLeaveData
import models.WebSocketAction

class OtherPlayersCoordinator(private val scene: Scene) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val players = mutableMapOf<String, OtherPlayer>()
    
    // Reusable vectors for performance
    private val tempVector3 = Vector3()
    private val tempVector3_2 = Vector3()
    private val tempVector3_3 = Vector3()

    // Managers – must be set before initialize()
    lateinit var worldObjectManager: WorldObjectManager
    var httpWorldIdOverride: String? = null
    var myUserId: String? = null

    private val hud = OtherPlayersHudManager(
        managerProvider = { worldObjectManager },
        myUserIdProvider = { myUserId },
        positionProvider = { uid -> players[uid]?.handle?.position }
    )

    private var initialized = false
    private val baseMovementSpeed: Float = 3.0f
    private val movementDelta = FrameDeltaTracker()
    private val usernameTextHandles = mutableMapOf<String, WorldObjectHandle>()
    private var worldMeshes: Array<AbstractMesh> = emptyArray()
    private var collisionContext: WorldCollisionContext? = null
    private var frameCount: Int = 0

    // Spawn retry state to ensure other players appear even if world assets are not yet ready
    private val spawnRetryCounts = mutableMapOf<String, Int>()
    private val maxSpawnRetries = 15
    private val spawnRetryInitialDelayMs = 150
    private val spawnRetryMaxDelayMs = 2000
    private val spawnRetryTimeouts = mutableMapOf<String, Int>()

    fun setWorldObjectManager(manager: WorldObjectManager) {
        worldObjectManager = manager
    }

    fun setHttpWorldId(id: String) {
        httpWorldIdOverride = id
    }

    fun setWorldMeshes(meshes: Array<AbstractMesh>) {
        worldMeshes = meshes
        collisionContext = WorldCollisionContext(meshes)
    }

    private fun snapToGround(position: Vector3): Vector3 {
        val context = collisionContext ?: return position
        val snapped = WorldCollisionUtils.findValidYPosition(
            context = context,
            collider = null,
            moveCircle = null,
            position = position,
            yPadding = 0.5f
        )
        return snapped ?: position
    }

    private fun snapToGroundToRef(position: Vector3, result: Vector3) {
        val context = collisionContext
        if (context == null) {
            result.copyFrom(position)
            return
        }
        val success = WorldCollisionUtils.findValidYPositionToRef(
            context = context,
            collider = null,
            moveCircle = null,
            position = position,
            result = result,
            yPadding = 0.5f
        )
        if (!success) result.copyFrom(position)
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
                    if (chat.senderType == models.ChatSenderType.USER) {
                        hud.showChatMessage(chat.senderId, chat.message)
                    }
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
                                    op.shouldSnap = true
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
                                                op.shouldSnap = true
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
        players.values.forEach {
            it.spawnJob?.cancel()
            SpatialSoundManager.safeDispose(it.walkSound)
            it.handle?.remove()
        }
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
                op.targetPos = Vector3(join.position.x, join.position.y, join.position.z)
                op.shouldSnap = true
                attemptSpawnWithUser(op)
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
            dup.spawnJob?.cancel()
            SpatialSoundManager.safeDispose(dup.walkSound)
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
                // Stop any pending spawn retries and jobs
                clearSpawnRetry(leave.userId)
                op.spawnJob?.cancel()
                SpatialSoundManager.safeDispose(op.walkSound)
                op.handle?.remove()
                players.remove(leave.userId)
                hud.removeHud(leave.userId)
                usernameTextHandles.remove(leave.userId)?.remove()
            }
        }
    }

    private fun onPlayerPosition(pos: PlayerPositionData) {
        if (pos.userId == myUserId) return
        val op = ensurePlayer(pos.userId, pos.position)

        // Update basic movement target immediately (update loop handles the rest)
        op.targetPos = Vector3(pos.position.x, pos.position.y, pos.position.z)

        // If isPlayer role changed, we must trigger a respawn (similar to onUpdatePlayer)
        if (op.isPlayer != pos.isPlayer) {
            scope.launch {
                op.mutex.withLock {
                    if (op.isPlayer != pos.isPlayer) {
                        op.isPlayer = pos.isPlayer
                        // Role changed: respawn cleanly
                        SpatialSoundManager.safeDispose(op.walkSound)
                        op.handle?.remove()
                        op.handle = null
                        op.walkSound = null
                        usernameTextHandles.remove(op.userId)?.remove()
                        attemptSpawnWithUser(op)
                    }
                }
            }
            return
        }

        // Only attempt to spawn if we don't already have a handle and aren't already spawning.
        // This avoids visible flashing as textures/materials are repeatedly reset during movement.
        if (op.handle == null && !op.isSpawning) {
            scope.launch {
                op.mutex.withLock {
                    if (op.handle == null && !op.isSpawning) {
                        attemptSpawnWithUser(op)
                    }
                }
            }
        }
    }

    private fun onUpdatePlayer(update: UpdatePlayerData) {
        if (update.userId == myUserId) return
        val op = ensurePlayer(update.userId, null)
        scope.launch {
            op.mutex.withLock {
                val prevIsPlayer = op.isPlayer
                val prevUsername = op.username
                // Basic fields
                op.username = update.name
                op.isPlayer = update.isPlayer

                // Status/HUD updates (do not trigger appearance refresh)
                update.status?.let { s ->
                    op.status = s
                    hud.setDesiredStatus(update.userId, s)
                }

                // Activity updates should only change animation, never appearance
                op.activity = update.activity
                op.handle?.updateActivity(update.activity)

                // Refresh username billboard only if changed
                if (prevUsername != op.username) {
                    refreshUsernameBillboard(op)
                }

                // Only refresh appearance when the update carries no activity or status fields.
                // This pattern is used when customization/worn items change and we need to refetch profile.
                val shouldRefreshAppearance = update.activity == null && update.status == null
                if (shouldRefreshAppearance) {
                    assembleAppearance(op)
                }

                if (prevIsPlayer != op.isPlayer) {
                    // Role changed (visitor <-> player): respawn cleanly to swap base if needed
                    SpatialSoundManager.safeDispose(op.walkSound)
                    op.handle?.remove()
                    op.handle = null
                    op.walkSound = null
                    op.isSpawning = false // Allow fresh spawn
                    usernameTextHandles.remove(op.userId)?.remove()
                    attemptSpawnWithUser(op)
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
        if (op.handle != null) {
            op.isSpawning = false
            return
        }
        val startPos = snapToGround(op.targetPos ?: op.lastPos)
        val baseSprite = SpriteSheet.createPlayerSpriteSheetForBodyType(null, null)
        val handle: WorldObjectHandle? = worldObjectManager.createCharacter(
            spriteSheet = baseSprite,
            direction = op.direction,
            position = startPos,
            additionalSpriteSheets = emptyList(),
            user = null,
        )
        if (handle == null) {
            scheduleSpawnRetry(op, "createCharacter returned null (placeholder)")
            return
        }
        op.handle = handle
        initializeWalkSound(op)
        op.lastPos.copyFrom(startPos)
        op.isSpawning = false
        // Apply any pending activity (e.g., Sit) immediately on spawn
        op.activity?.let { pending ->
            try { handle.updateActivity(pending) } catch (_: Throwable) {}
        }

        // Success: clear retry state
        clearSpawnRetry(op.userId)

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
            op.isSpawning = false
            clearSpawnRetry(op.userId)
            return
        }
        val count = (spawnRetryCounts[op.userId] ?: 0)
        if (count >= maxSpawnRetries) {
            console.warn("OtherPlayersCoordinator: spawn retries exceeded for ${op.userId} (reason=$reason)")
            op.isSpawning = false
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
                still.isSpawning = false
                clearSpawnRetry(op.userId)
                return@setTimeout
            }
            attemptSpawnWithUser(still)
        }, delay)
        spawnRetryTimeouts[op.userId] = id
    }

    private fun clearSpawnRetry(userId: String) {
        spawnRetryCounts.remove(userId)
        spawnRetryTimeouts.remove(userId)?.let { window.clearTimeout(it) }
    }

    private fun attemptSpawnWithUser(op: OtherPlayer) {
        // Cancel any existing spawn job to avoid race conditions and leaked characters
        op.spawnJob?.cancel()
        op.isSpawning = true

        // Fallback: spawn placeholder if user fetch isn't quick
        val fallbackId = window.setTimeout({
            if (op.handle == null) spawnIfNeeded(op)
        }, 150)

        op.spawnJob = scope.launch {
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
                        )
                        if (handle != null) {
                            op.handle = handle
                            initializeWalkSound(op)
                            op.lastPos.copyFrom(startPos)
                            op.targetPos?.copyFrom(startPos) ?: run { op.targetPos = startPos.clone() }
                            // Track the profile so we can detect future base changes
                            op.profile = user
                            
                            // Success: clear retry state
                            spawnRetryCounts.remove(op.userId)
                            
                            // Prefer server status if available
                            if (user.status.isNotBlank()) op.status = user.status
                            if (op.status.isNotBlank()) hud.setDesiredStatus(op.userId, op.status)
                            createOrUpdateUsernameBillboard(op, startPos)
                            // Apply full appearance (hair, eyes, worn) immediately so it doesn't wait for movement
                            try {
                                val sheets = fetchAppearanceSheets(user)
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
                        // Check if appearance actually needs updating to avoid flashing
                        val prev = op.profile
                        val appearanceChanged = prev == null || 
                                prev.hairStyle != user.hairStyle || 
                                prev.eyeColor != user.eyeColor || 
                                prev.skinTone != user.skinTone || 
                                prev.wornItems != user.wornItems
                        
                        if (appearanceChanged) {
                            val sheets = fetchAppearanceSheets(user)
                            op.handle?.updateAppearance(sheets, user.skinTone ?: 0.5f)
                        }
                        
                        op.profile = user
                        if (op.status.isBlank() && user.status.isNotBlank()) {
                            op.status = user.status
                            hud.setDesiredStatus(op.userId, op.status)
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    console.error("OtherPlayersCoordinator: attemptSpawnWithUser failed for ${op.userId}", t)
                }
            } finally {
                op.isSpawning = false
                window.clearTimeout(fallbackId)
            }
        }
    }

    private suspend fun assembleAppearance(op: OtherPlayer) {
        try {
            val prev = op.profile
            val user = UserApi.getUserById(op.userId) ?: return

            // Detect base body change (sex or bodyType) which requires base spritesheet swap
            val baseChanged = (prev == null) || (prev.sex != user.sex) || (prev.bodyType != user.bodyType)

            if (baseChanged) {
                // Preserve current transform/state
                val currentPos = op.handle?.position ?: op.targetPos ?: op.lastPos
                // Remove current mesh and trigger respawn with updated base body
                SpatialSoundManager.safeDispose(op.walkSound)
                op.handle?.remove()
                op.handle = null
                op.walkSound = null
                op.isSpawning = false
                op.targetPos = currentPos
                op.lastPos = currentPos
                op.profile = user
                // Attempt spawn using fresh user so base body type is applied
                attemptSpawnWithUser(op)
                return
            }

            // Detect if appearance layers changed to avoid redundant flashing
            val layersChanged = (prev == null) ||
                    prev.hairStyle != user.hairStyle ||
                    prev.eyeColor != user.eyeColor ||
                    prev.skinTone != user.skinTone ||
                    prev.wornItems != user.wornItems

            op.profile = user
            if (layersChanged) {
                val sheets = fetchAppearanceSheets(user)
                op.additionalSpriteSheets = sheets
                op.skinTone = user.skinTone
                op.handle?.updateAppearance(sheets, op.skinTone)
            }
        } catch (t: Throwable) {
            console.error("OtherPlayersCoordinator: assembleAppearance failed for ${op.userId}", t)
        }
    }

    private suspend fun fetchAppearanceSheets(user: User): List<SpriteSheet> {
        val hair = SpriteSheet.createHairSpriteSheet(user.sex ?: Sex.Boy, user.hairStyle)
        val eyes = SpriteSheet.createEyesSpriteSheet(user.eyeColor, user.sex)
        val itemsById = try {
            val ids = user.wornItems
            if (ids.isEmpty()) emptyMap() else ItemApi.getItemsByIds(ids).associateBy { it.key }
        } catch (_: Throwable) { emptyMap() }
        val worn = WornItemsRenderer.getWornItemSpriteSheetsForUser(user, itemsById)
        // Ensure that additional sprite sheets are correctly sorted (front-to-back)
        return (listOfNotNull(hair, eyes) + worn).sortedByDescending { it.sorting }
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

    private fun initializeWalkSound(op: OtherPlayer) {
        val handle = op.handle ?: return
        SpatialSoundManager.safeDispose(op.walkSound)
        op.walkSound = SpatialSoundManager.createWalkingSound(
            name = "walk_${op.userId}",
            url = "/raw/sounds/walk.wav",
            scene = scene,
            mesh = handle.node.unsafeCast<lib.TransformNode>()
        )
    }

    // Per-frame movement and HUD positioning
    private fun update() {
        frameCount++
        val deltaTime = movementDelta.nextDeltaSeconds()
        val frameMovementSpeed = (baseMovementSpeed * deltaTime).toFloat()
        val now = window.performance.now()

        players.values.forEach { op ->
            val handle = op.handle ?: return@forEach

            // 1) Movement logic
            val targetPos = op.targetPos ?: return@forEach
            snapToGroundToRef(targetPos, tempVector3)
            val snappedTargetPos = tempVector3
            
            val currentPos = handle.position

            // Throttle environment check for other players (every 10 frames)
            if (op.lastEnvCheckFrame == -1 || (frameCount - op.lastEnvCheckFrame) >= 10) {
                val context = collisionContext
                if (context != null) {
                    op.isOnWater = WorldCollisionUtils.getIsOnWater(
                        context = context,
                        collider = null,
                        moveCircle = null,
                        position = currentPos,
                    )
                    op.lastEnvCheckFrame = frameCount
                }
            }
            
            val isOnWater = op.isOnWater
            val speedFactor = if (isOnWater) 0.5f else 1.0f
            
            // Calculate distance to target
            snappedTargetPos.subtractToRef(currentPos, tempVector3_2)
            val direction = tempVector3_2
            val distance = direction.length()
            
            // Calculate XZ distance for activity state
            tempVector3_3.set(snappedTargetPos.x - currentPos.x, 0f, snappedTargetPos.z - currentPos.z)
            val distanceXZ = tempVector3_3.length()

            // 2) Update Activity State (Moving vs Standing)
            // Use a small threshold to avoid jitter, but large enough to detect intent
            val isMovingIntent = distanceXZ > 0.05f

            if (isMovingIntent) {
                op.lastMovingTime = now
            }

            val desiredActivity = if (isMovingIntent && op.activity != PlayerActivity.Sit) {
                PlayerActivity.Moving
            } else if (op.activity == PlayerActivity.Sit) {
                PlayerActivity.Sit
            } else {
                null
            }

            if (op.activity != desiredActivity) {
                op.activity = desiredActivity
                handle.updateActivity(desiredActivity)
            }

            // 3) Handle Movement
            if (!isMovingIntent) {
                // Close enough to snap and be done
                if (distance > 0f) {
                    handle.position.copyFrom(snappedTargetPos)
                    op.lastPos.copyFrom(snappedTargetPos)
                    hud.updateHudPosition(op.userId, snappedTargetPos)
                    PlayerUiUtils.updateUsernameBillboardPosition(usernameTextHandles[op.userId], snappedTargetPos)
                }
                op.shouldSnap = false
                
                // Stop walking sound with a small hysteresis (200ms) to avoid stuttering on minor network updates
                if (now - op.lastMovingTime > 200) {
                    SpatialSoundManager.pause(op.walkSound)
                }
                
                return@forEach
            }

            // If we are very far, snap to avoid long "catch up" sliding
            if (op.shouldSnap || distance > 100f) {
                op.shouldSnap = false
                handle.position.copyFrom(snappedTargetPos)
                op.lastPos.copyFrom(snappedTargetPos)
                hud.updateHudPosition(op.userId, snappedTargetPos)
                PlayerUiUtils.updateUsernameBillboardPosition(usernameTextHandles[op.userId], snappedTargetPos)
                
                // Stop walking sound when snapping
                SpatialSoundManager.pause(op.walkSound)
                
                return@forEach
            }
            
            // Start walking sound
            SpatialSoundManager.play(op.walkSound)

            // Dynamic speed: if far away, move faster to catch up (sprint-like)
            val catchUpMultiplier = when {
                distance > 5.0f -> 5.0f // Catch up fast
                distance > 2.0f -> 2.5f // Catch up moderate
                else -> 1.2f // Slightly faster than base to ensure we actually reach target between updates
            }
            val playerFrameMovementSpeed = frameMovementSpeed * speedFactor * catchUpMultiplier

            // Update facing with hysteresis
            val currentFacing = op.direction
            val newFacing = main.components.world.calculateFacingFromVector(direction, currentFacing)
            if (newFacing != currentFacing) {
                op.direction = newFacing
                handle.updateDirection(newFacing)
            }

            direction.normalize()
            val step = kotlin.math.min(playerFrameMovementSpeed, distance)
            direction.scaleInPlace(step)
            
            currentPos.addToRef(direction, tempVector3_2)
            val moved = tempVector3_2
            snapToGroundToRef(moved, tempVector3_3)
            val newPosition = tempVector3_3
            
            handle.position.copyFrom(newPosition)
            
            // Check for water ripples when moving
            val nowTime = window.performance.now()
            if (nowTime - op.lastRippleTime > 300) {
                trySpawnWaterRipple(newPosition)
                op.lastRippleTime = nowTime
            }

            // Trigger wind boost for other player's movement (convert displacement to velocity)
            direction.scaleToRef(1.0f / deltaTime.toFloat(), tempVector3_2)
            handle.triggerWindBoost(tempVector3_2)
            
            hud.updateHudPosition(op.userId, newPosition)
            PlayerUiUtils.updateUsernameBillboardPosition(usernameTextHandles[op.userId], newPosition)
        }
    }

    private fun trySpawnWaterRipple(position: Vector3) {
        val context = collisionContext ?: return
        val waterPoint = WorldCollisionUtils.getWaterSurfacePoint(context, position) ?: return
        // Only spawn if player is actually in/at the water (within a reasonable distance from surface)
        if (kotlin.math.abs(position.y - waterPoint.y) < 1.0f) {
            WaterRippleEffect.create(scene, waterPoint)
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
            SpatialSoundManager.safeDispose(selfOp.walkSound)
            selfOp.handle?.remove()
            hud.removeHud(selfId)
            usernameTextHandles.remove(selfId)?.remove()
        }
        clearSpawnRetry(selfId)
    }
}
