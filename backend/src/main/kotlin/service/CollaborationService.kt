package service

import io.ktor.server.application.ApplicationCall
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import models.Doc
import models.DocOperation
import models.WsMsg
import repository.DocRepository
import utils.json

/**
 * Minimal collaboration service implementing a basic real-time channel:
 * - On connect: loads current content/version and sends Snapshot
 * - Join: tracks presence and broadcasts it
 * - Op: applies Insert/Delete operations sequentially, persists with optimistic versioning,
 *       updates in-memory state, broadcasts Op and Ack
 * - Format ops are accepted but ignored server-side in this minimal step
 *
 * OT transformation between concurrent ops is not implemented in this step; instead we rely on
 * optimistic versioning backed by the repository. If a version conflict occurs, the sender
 * receives an Error and a fresh Snapshot.
 */
object CollaborationService {
    private val scope = CoroutineScope(Dispatchers.Default)

    private data class Client(var userId: String, val session: DefaultWebSocketServerSession)

    private data class DocState(
        var content: String,
        var version: Int,
    )

    private class Room(
        val docId: String,
        var state: DocState,
    ) {
        val clients = mutableSetOf<Client>()
        val mutex = Mutex()
    }

    // docId -> room
    private val rooms = mutableMapOf<String, Room>()
    private val roomsMutex = Mutex()

    private suspend fun getOrCreateRoom(docId: String, repo: DocRepository): Room {
        roomsMutex.withLock {
            val existing = rooms[docId]
            if (existing != null) return existing
            val doc = repo.getById(docId) ?: Doc(worldId = "", title = "", content = "")
            val room = Room(
                docId = docId,
                state = DocState(content = doc.content, version = doc.version)
            )
            rooms[docId] = room
            return room
        }
    }

    suspend fun handle(
        call: ApplicationCall,
        ws: DefaultWebSocketServerSession,
        userId: String,
        docId: String,
        repo: DocRepository,
        canEdit: Boolean,
    ) {
        val room = getOrCreateRoom(docId, repo)

        // Send initial snapshot
        try {
            val data = json.encodeToString(WsMsg.serializer(), WsMsg.Snapshot(content = room.state.content, version = room.state.version))
            ws.send(Frame.Text(data))
        } catch (_: Throwable) { /* ignore */ }

        val client = Client(userId, ws)
        // Track presence and broadcast join presence
        scope.launch {
            room.mutex.withLock {
                room.clients.add(client)
            }
            broadcast(room, WsMsg.Presence(userId = client.userId, cursor = 0))
        }

        try {
            for (frame in ws.incoming) {
                val text = when (frame) {
                    is Frame.Text -> frame.readText()
                    else -> continue
                }
                val msg = runCatching { json.decodeFromString(WsMsg.serializer(), text) }.getOrNull() ?: continue
                when (msg) {
                    is WsMsg.Join -> {
                        // Update client identity if server inferred Anonymous and client provided a userId
                        room.mutex.withLock {
                            if (client.userId == "Anonymous" && msg.userId.isNotBlank()) {
                                client.userId = msg.userId
                            }
                        }
                        // Echo presence with the resolved client userId
                        broadcast(room, WsMsg.Presence(userId = client.userId, cursor = msg.cursor))
                    }
                    is WsMsg.Presence -> {
                        // Re-broadcast presence updates (cursor changes) with resolved client userId
                        broadcast(room, WsMsg.Presence(userId = client.userId, cursor = msg.cursor))
                    }
                    is WsMsg.Op -> {
                        // Enforce permissions: read-only clients cannot send ops
                        if (!canEdit) {
                            runCatching { val data = json.encodeToString(WsMsg.serializer(), WsMsg.Error(message = "Cannot edit")); ws.send(Frame.Text(data)) }
                            runCatching { val snap = json.encodeToString(WsMsg.serializer(), WsMsg.Snapshot(content = room.state.content, version = room.state.version)); ws.send(Frame.Text(snap)) }
                            continue
                        }
                        // Apply operations with optimistic versioning
                        room.mutex.withLock {
                            val currentVersion = room.state.version
                            if (msg.baseVersion != currentVersion) {
                                // Version mismatch: send fresh snapshot
                                runCatching {
                                    val err = json.encodeToString(WsMsg.serializer(), WsMsg.Error(message = "Version mismatch"))
                                    ws.send(Frame.Text(err))
                                    val snap = json.encodeToString(WsMsg.serializer(), WsMsg.Snapshot(content = room.state.content, version = room.state.version))
                                    ws.send(Frame.Text(snap))
                                }
                                continue
                            }
                            val newContent = applyOps(room.state.content, msg.ops)
                            try {
                                val updated = repo.saveContentAndVersion(docId, newContent, baseVersion = currentVersion)
                                    ?: run {
                                        runCatching { val data = json.encodeToString(WsMsg.serializer(), WsMsg.Error(message = "Doc not found")); ws.send(Frame.Text(data)) }
                                        continue
                                    }
                                // Update in-memory state to authoritative values
                                room.state.content = updated.content
                                room.state.version = updated.version

                                // Broadcast op to other clients (do not echo back to sender)
                                broadcast(room, WsMsg.Op(userId = client.userId, baseVersion = currentVersion, ops = msg.ops), except = client)
                                // Ack new version to sender
                                runCatching { val data = json.encodeToString(WsMsg.serializer(), WsMsg.Ack(version = updated.version)); ws.send(Frame.Text(data)) }
                            } catch (e: IllegalStateException) {
                                runCatching {
                                    val err = json.encodeToString(WsMsg.serializer(), WsMsg.Error(message = e.message ?: "Version conflict"))
                                    ws.send(Frame.Text(err))
                                    val snap = json.encodeToString(WsMsg.serializer(), WsMsg.Snapshot(content = room.state.content, version = room.state.version))
                                    ws.send(Frame.Text(snap))
                                }
                            } catch (t: Throwable) {
                                runCatching { val data = json.encodeToString(WsMsg.serializer(), WsMsg.Error(message = t.message ?: "Error")); ws.send(Frame.Text(data)) }
                            }
                        }
                    }
                    is WsMsg.Snapshot -> {
                        // Client should not send snapshots; ignore
                    }
                    is WsMsg.Ack -> {
                        // Acks are client-local; ignore on server
                    }
                    is WsMsg.Error -> {
                        // Client error message; ignore
                    }
                }
            }
        } catch (_: ClosedReceiveChannelException) {
            // normal close
        } catch (t: Throwable) {
            runCatching {
                val err = json.encodeToString(WsMsg.serializer(), WsMsg.Error(message = t.message ?: "Error"))
                ws.send(Frame.Text(err))
            }
        } finally {
            // Remove client and broadcast presence leave (using cursor -1 to indicate offline)
            scope.launch {
                room.mutex.withLock { room.clients.remove(client) }
                broadcast(room, WsMsg.Presence(userId = client.userId, cursor = -1))
            }
            runCatching { ws.close(CloseReason(CloseReason.Codes.NORMAL, "Bye")) }
        }
    }

    private fun applyOps(base: String, ops: List<DocOperation>): String {
        var content = base
        ops.forEach { op ->
            when (op) {
                is DocOperation.Insert -> {
                    val pos = op.position.coerceIn(0, content.length)
                    content = content.substring(0, pos) + op.text + content.substring(pos)
                }
                is DocOperation.Delete -> {
                    if (op.length <= 0) return@forEach
                    val start = op.position.coerceIn(0, content.length)
                    val end = (start + op.length).coerceAtMost(content.length)
                    if (start < end) {
                        content = content.removeRange(start, end)
                    }
                }
                is DocOperation.Format -> {
                    // Formatting is ignored in this minimal backend pass; kept for protocol parity
                }
            }
        }
        return content
    }

    private fun broadcast(room: Room, message: WsMsg, except: Client? = null) {
        scope.launch {
            val data = runCatching { json.encodeToString(WsMsg.serializer(), message) }.getOrNull()
            if (data == null) return@launch
            val clientsCopy = room.mutex.withLock { room.clients.toList() }
            clientsCopy.forEach { client ->
                if (except != null && client === except) return@forEach
                runCatching { client.session.send(Frame.Text(data)) }
            }
        }
    }
}
