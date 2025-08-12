package state

import models.PlayerSnapshot
import models.Position
import models.WorldStateSnapshot
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory authoritative world state store.
 * Not persisted; suitable for current development phase.
 */
object WorldStateStore {
    private data class MutablePlayer(
        var userId: String,
        var username: String,
        var isPlayer: Boolean,
        var position: Position,
        var activity: String? = null, // kept as string name for decoupling
        var status: String? = null,
        var isTyping: Boolean? = null,
    )

    private data class MutableWorld(
        val worldId: String,
        val players: ConcurrentHashMap<String, MutablePlayer> = ConcurrentHashMap(),
        val version: AtomicLong = AtomicLong(0),
    )

    private val worlds = ConcurrentHashMap<String, MutableWorld>()

    private fun world(worldId: String): MutableWorld =
        worlds.computeIfAbsent(worldId) { MutableWorld(worldId) }

    fun upsertPlayer(
        worldId: String,
        userId: String,
        username: String,
        isPlayer: Boolean,
        position: Position,
    ) {
        val w = world(worldId)
        val p = w.players.computeIfAbsent(userId) {
            MutablePlayer(userId, username, isPlayer, position)
        }
        p.username = username
        p.isPlayer = isPlayer
        p.position = position
        w.version.incrementAndGet()
    }

    fun updatePlayerPosition(worldId: String, userId: String, position: Position, isPlayer: Boolean? = null) {
        val w = world(worldId)
        val p = w.players[userId]
        if (p != null) {
            p.position = position
            if (isPlayer != null) p.isPlayer = isPlayer
        } else {
            // If unknown, create a minimal entry
            w.players[userId] = MutablePlayer(userId, username = userId, isPlayer = isPlayer ?: false, position = position)
        }
        w.version.incrementAndGet()
    }

    fun updatePlayerDetails(
        worldId: String,
        userId: String,
        name: String? = null,
        isPlayer: Boolean? = null,
        activityName: String? = null,
        status: String? = null,
    ) {
        val w = world(worldId)
        val p = w.players.computeIfAbsent(userId) {
            MutablePlayer(userId, username = name ?: userId, isPlayer = isPlayer ?: false, position = Position())
        }
        if (name != null) p.username = name
        if (isPlayer != null) p.isPlayer = isPlayer
        if (activityName != null) p.activity = activityName
        if (status != null) p.status = status
        w.version.incrementAndGet()
    }

    fun updateTyping(worldId: String, userId: String, isTyping: Boolean) {
        val w = world(worldId)
        val p = w.players.computeIfAbsent(userId) {
            MutablePlayer(userId, username = userId, isPlayer = false, position = Position())
        }
        p.isTyping = isTyping
        w.version.incrementAndGet()
    }

    fun removePlayer(worldId: String, userId: String) {
        val w = world(worldId)
        w.players.remove(userId)
        w.version.incrementAndGet()
        // Optional: cleanup empty worlds later if needed
    }

    fun snapshot(worldId: String): WorldStateSnapshot {
        val w = world(worldId)
        val now = System.currentTimeMillis()
        val players = w.players.values.map { m ->
            PlayerSnapshot(
                userId = m.userId,
                username = m.username,
                isPlayer = m.isPlayer,
                position = m.position,
                activity = null, // optional: serialize enum if available server-side
                status = m.status,
                isTyping = m.isTyping,
            )
        }
        return WorldStateSnapshot(
            worldId = worldId,
            version = w.version.get(),
            serverTime = now,
            players = players,
        )
    }
}
