package routes

import auth.JwtConfig
import com.auth0.jwt.exceptions.JWTVerificationException
import debug.WebSocketDebug
import io.ktor.server.routing.Routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.Serializable
import models.Position
import models.User
import models.UserJoinData
import models.UserLeaveData
import models.WebSocketAction
import repository.ChatRepository
import repository.FriendRepository
import repository.ObjectRepository
import repository.UserRepository
import repository.WorldRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket connection for a specific client
 */
data class WebSocketConnection(
    val session: DefaultWebSocketServerSession,
    val user: User,
) {
    val userId: String
        get() = user.key
}

/**
 * WebSocket message model
 * 
 * This class defines the expected format for all WebSocket messages.
 * - action: The type of action being performed
 * - data: A JSON string containing the serialized data for the action
 */
@Serializable
data class WebSocketMessage(
    val action: WebSocketAction? = null,
    val data: String = "",
    val worldUrl: String? = null
)

/**
 * Global map of active WebSocket connections by worldId
 * 
 * This map is used to track all active WebSocket connections and is accessible
 * from other parts of the application that need to send messages to clients.
 */
val activeConnections = ConcurrentHashMap<String, MutableList<WebSocketConnection>>()

/**
 * Global map of user-level WebSocket connections by userId (not tied to a world)
 */
val globalUserConnections = ConcurrentHashMap<String, MutableList<WebSocketConnection>>()

/**
 * Configure routes for WebSocket operations
 */
fun Routing.configureWebSocketRoutes() {
    val chatRepository = ChatRepository()
    val userRepository = UserRepository()
    val objectRepository = ObjectRepository()
    val worldRepository = WorldRepository()
    val friendRepository = FriendRepository()
    
    // Create handler factory
    val handlerFactory = routes.handlers.WebSocketHandlerFactory(chatRepository, userRepository, objectRepository)
    
    // JSON serializer
    val json = utils.json

    suspend fun notifyFriendsStatus(
        userId: String,
        isOnline: Boolean,
        worldId: String? = null,
        worldName: String? = null,
        position: Position? = null,
    ) {
        try {
            val friends = friendRepository.getByUserId(userId)
            if (friends.isEmpty()) return
            val payload = models.FriendStatusChanged(
                userId = userId,
                isOnline = isOnline,
                worldId = worldId,
                worldName = worldName,
                position = position,
            )
            val message = WebSocketMessage(
                action = WebSocketAction.FRIEND_STATUS_CHANGED,
                data = json.encodeToString(payload)
            )
            val messageString = json.encodeToString(message)
            friends.forEach { f ->
                val sessions = globalUserConnections[f.friendId]
                sessions?.toList()?.forEach { conn ->
                    try {
                        conn.session.send(messageString)
                    } catch (_: Throwable) { }
                }
            }
        } catch (_: Throwable) { }
    }
    
    // WebSocket route
    webSocket("/ws") {
        WebSocketDebug.logConnection("ATTEMPT", this, "unknown", "unknown")
        
        // Extract query parameters
        val worldId = call.parameters["worldId"] ?: run {
            WebSocketDebug.logError("WebSocket connection rejected: Missing worldId parameter", null, this)
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing worldId parameter"))
            return@webSocket
        }
        
        WebSocketDebug.logConnection("WORLD_ID", this, worldId, "unknown")
        
        // Extract token from query parameters
        val token = call.parameters["token"] ?: ""
        val visitorId = call.parameters["visitorId"] ?: ""
        WebSocketDebug.logConnection("TOKEN", this, worldId, "unknown")
        
        // Validate token and get userId
        val user = if (token.isNotBlank()) {
            try {
                // Use JwtConfig.verifier to verify the token
                WebSocketDebug.logConnection("TOKEN_VALIDATION", this, worldId, "unknown")
                val decodedJWT = JwtConfig.verifier.verify(token)
                val extractedUserId = decodedJWT.getClaim("userId").asString()
                WebSocketDebug.logConnection("TOKEN_VALID", this, worldId, extractedUserId)
                userRepository.getById(extractedUserId) ?: error("User not found (Player)")
            } catch (e: JWTVerificationException) {
                WebSocketDebug.logError("Error validating token", e, this)
                WebSocketDebug.logConnection("ANONYMOUS", this, worldId, "unknown")
                null
            }
        } else if (visitorId.isNotBlank()) {
            userRepository.getByVisitorId(visitorId) ?: error("User not found (Visitor)")
        } else {
            WebSocketDebug.logConnection("NO_TOKEN", this, worldId, "unknown")
            null
        }

        if (user == null) {
            error("User not found")
        }

        // Extract language from query parameters, default to "en" if not provided
        WebSocketDebug.logConnection("LANGUAGE", this, worldId, user.key)
        
        val connection = WebSocketConnection(
            session = this,
            user = user
        )
        WebSocketDebug.logConnection("CREATED", this, worldId, user.key)
        
        // Log connection with WebSocketDebug
        WebSocketDebug.logConnection("CONNECT", this, worldId, user.key)
        
        try {
            // Add connection to active connections
            activeConnections.getOrPut(worldId) { mutableListOf() }.add(connection)

            // After registering this connection, notify and pause any other active sessions
            // for the same user across all worlds
            try {
                // Collect targets first to avoid concurrent modification
                val targets = activeConnections.entries.flatMap { (wId, conns) ->
                    // Create a snapshot list for safe iteration
                    val copy = ArrayList(conns)
                    copy.filter { it.userId == connection.userId && it.session != connection.session }
                        .map { it to wId }
                }

                // Resolve world info for each target and send PAUSE
                targets.forEach { (otherConn, otherWorldId) ->
                    val world = worldRepository.getById(otherWorldId)
                    val pauseData = models.PauseData(
                        userId = otherConn.userId,
                        worldUrl = world?.url ?: otherWorldId,
                        worldName = world?.title ?: "World"
                    )
                    val pauseMessage = WebSocketMessage(
                        action = WebSocketAction.PAUSE,
                        data = json.encodeToString(pauseData)
                    )
                    val payload = json.encodeToString(pauseMessage)
                    WebSocketDebug.logMessage("PAUSE_SEND", payload, this)
                    try {
                        otherConn.session.send(payload)
                    } catch (e: Throwable) {
                        WebSocketDebug.logError("Error sending PAUSE message", e, this)
                    }
                }
            } catch (e: Throwable) {
                WebSocketDebug.logError("Error while processing PAUSE notifications", e, this)
            }
            
            // Log active connections for debugging
            WebSocketDebug.logConnection("ACTIVE_WORLDS", this, "all", "system")
            activeConnections.forEach { (wId, connections) ->
                WebSocketDebug.logConnection("ACTIVE_CONNECTIONS", this, wId, "system")
            }

            // If this is an authenticated connection and we still have a visitor connection from the same browser (visitorId),
            // broadcast USER_LEAVE for the visitor and remove it from the world state and active connections to avoid duplicates
            try {
                if (token.isNotBlank() && visitorId.isNotBlank()) {
                    val visitorUser = userRepository.getByVisitorId(visitorId)
                    if (visitorUser != null && visitorUser.key != connection.userId) {
                        // Remove from world state
                        state.WorldStateStore.removePlayer(worldId, visitorUser.key)

                        // Broadcast USER_LEAVE for the visitor
                        val leaveData = UserLeaveData(userId = visitorUser.key, worldId = worldId)
                        val leaveMessage = WebSocketMessage(
                            action = WebSocketAction.USER_LEAVE,
                            data = json.encodeToString(leaveData)
                        )
                        val leavePayload = json.encodeToString(leaveMessage)
                        WebSocketDebug.logMessage("BROADCAST_VISITOR_LEAVE", leavePayload, this)
                        broadcastToWorld(worldId, leavePayload, activeConnections)

                        // Close and remove any active visitor connections in this world
                        activeConnections[worldId]?.toList()?.forEach { otherConn ->
                            if (otherConn.userId == visitorUser.key) {
                                try { otherConn.session.close(CloseReason(CloseReason.Codes.NORMAL, "Upgraded to player")) } catch (_: Throwable) {}
                                activeConnections[worldId]?.remove(otherConn)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                WebSocketDebug.logError("Error handling visitor->player deduplication", e, this)
            }
            
            // Send user join notification to all clients in the world
            val isPlayer = connection.user.isPlayer
            val username = if (isPlayer) connection.user.username else "Visitor"
            
            val joinData = UserJoinData(
                userId = connection.userId,
                username = username,
                isPlayer = isPlayer,
                position = Position(0f, 0f, 0f), // Default position, will be updated by client
                worldId = worldId
            )
            
            val joinMessage = WebSocketMessage(
                action = WebSocketAction.USER_JOIN,
                data = json.encodeToString(joinData)
            )
            
            val messageString = json.encodeToString(joinMessage)
            WebSocketDebug.logMessage("BROADCAST", messageString, this)
            broadcastToWorld(worldId, messageString, activeConnections)
            WebSocketDebug.logConnection("USER_JOINED", this, worldId, connection.userId)

            // Notify friends globally about this user's online status/world
            try {
                val worldName = worldRepository.getById(worldId)?.title
                notifyFriendsStatus(
                    userId = connection.userId,
                    isOnline = true,
                    worldId = worldId,
                    worldName = worldName,
                    position = null,
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            
            // Send information about all existing users to the new connection
            WebSocketDebug.logMessage("SENDING_EXISTING_USERS", "", this)
            val existingConnections = activeConnections[worldId] ?: emptyList()
            WebSocketDebug.logMessage("EXISTING_CONNECTIONS", "count: ${existingConnections.size}", this)
            
            // Make a copy of the list to avoid concurrent modification issues
            val existingConnectionsCopy = ArrayList(existingConnections)
            // Get snapshot once to provide accurate positions/status for existing users
            val snapshot = state.WorldStateStore.snapshot(worldId)
            val playerById = snapshot.players.associateBy { it.userId }
            
            existingConnectionsCopy.forEach { existingConnection ->
                if (existingConnection.userId != connection.userId) {
                    WebSocketDebug.logMessage("PROCESSING_USER", existingConnection.userId, this)
                    val snap = playerById[existingConnection.userId]
                    val pos = snap?.position ?: Position(0f, 0f, 0f)
                    val existingUserJoinData = UserJoinData(
                        userId = existingConnection.userId,
                        username = existingConnection.user.username.ifBlank { "Visitor" },
                        isPlayer = existingConnection.user.isPlayer,
                        position = pos,
                        worldId = worldId
                    )
                    
                    val existingUserJoinMessage = WebSocketMessage(
                        action = WebSocketAction.USER_JOIN,
                        data = json.encodeToString(existingUserJoinData)
                    )
                    
                    val existingUserMessageString = json.encodeToString(existingUserJoinMessage)
                    WebSocketDebug.logMessage("SENDING_USER_INFO", "from: ${existingConnection.userId}, to: ${connection.userId}", this)
                    
                    try {
                        connection.session.send(existingUserMessageString)
                        WebSocketDebug.logMessage("SENT_USER_INFO", "from: ${existingConnection.userId}, to: ${connection.userId}", this)
                    } catch (e: Throwable) {
                        WebSocketDebug.logError("Error sending existing user info", e, this)
                    }
                }
            }
            
            // Process incoming messages
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    try {
                        // Log message with WebSocketDebug
                        WebSocketDebug.logMessage("RECEIVED", text, this)
                        
                        val message = json.decodeFromString<WebSocketMessage>(text)
                        WebSocketDebug.logMessage("PARSED", "action=${message.action}, data length=${message.data.length}", this)
                        
                        // Get the appropriate handler for this action
                        val handler = handlerFactory.getHandler(message.action)
                        
                        // Handle the message
                        handler.handle(message, connection, worldId, activeConnections, this)
                    } catch (e: Throwable) {
                        // Log error but don't close connection
                        WebSocketDebug.logError("Error processing WebSocket message", e, this)
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            // Normal close
            WebSocketDebug.logConnection("CLOSE_NORMAL", this, worldId, connection.userId)
        } catch (e: Throwable) {
            // Unexpected error
            WebSocketDebug.logError("Error in WebSocket connection", e, this)
        } finally {
            // Send user leave notification to all clients in the world
            // Update authoritative world state first
            try {
                state.WorldStateStore.removePlayer(worldId, connection.userId)
            } catch (_: Throwable) {}
            val leaveData = UserLeaveData(
                userId = connection.userId,
                worldId = worldId
            )
            
            val leaveMessage = WebSocketMessage(
                action = WebSocketAction.USER_LEAVE,
                data = json.encodeToString(leaveData)
            )
            
            broadcastToWorld(worldId, json.encodeToString(leaveMessage), activeConnections)
            WebSocketDebug.logConnection("USER_LEFT", this, worldId, connection.userId)

            // Notify friends that this user went offline (world left)
            try {
                notifyFriendsStatus(
                    userId = connection.userId,
                    isOnline = false,
                    worldId = null,
                    worldName = null,
                    position = null,
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            
            // Remove connection from active connections
            WebSocketDebug.logConnection("REMOVING_CONNECTION", this, worldId, connection.userId)
            val removed = activeConnections[worldId]?.remove(connection)
            WebSocketDebug.logConnection("CONNECTION_REMOVED", this, worldId, connection.userId)
            
            // If this was the last connection for this world, remove the world entry
            if (activeConnections[worldId]?.isEmpty() == true) {
                WebSocketDebug.logConnection("REMOVING_WORLD", this, worldId, "system")
                activeConnections.remove(worldId)
            }
            
            // Log remaining connections
            WebSocketDebug.logConnection("ACTIVE_WORLDS_AFTER_REMOVAL", this, "all", "system")
            activeConnections.forEach { (wId, connections) ->
                WebSocketDebug.logConnection("REMAINING_CONNECTIONS", this, wId, "system")
            }
        }
    }

    // Global user-level WebSocket (not tied to a world)
    webSocket("/ws/global") {
        // Validate token and get user
        val token = call.parameters["token"] ?: ""
        val user = try {
            val decodedJWT = JwtConfig.verifier.verify(token)
            val extractedUserId = decodedJWT.getClaim("userId").asString()
            userRepository.getById(extractedUserId)
        } catch (_: Throwable) {
            null
        }
        if (user == null) {
            try { close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token")) } catch (_: Throwable) {}
            return@webSocket
        }

        val connection = WebSocketConnection(this, user)
        try {
            globalUserConnections.getOrPut(user.key) { mutableListOf() }.add(connection)
            // Consume frames (no-op)
            incoming.consumeEach { _ -> }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            globalUserConnections[user.key]?.remove(connection)
            if (globalUserConnections[user.key]?.isEmpty() == true) {
                globalUserConnections.remove(user.key)
            }
        }
    }
}

/**
 * Broadcast a message to all clients in a world
 * 
 * This function is kept for backward compatibility with existing code.
 * It delegates to WebSocketUtils.broadcastToWorld.
 */
private suspend fun broadcastToWorld(
    worldId: String,
    message: String,
    activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>
) {
    // Get the first connection's session for logging purposes
    val session = activeConnections[worldId]?.firstOrNull()?.session
    
    if (session != null) {
        routes.handlers.WebSocketUtils.broadcastToWorld(worldId, message, activeConnections, session)
    } else {
        // No active connections, log and return
        WebSocketDebug.logBroadcast("NO_CONNECTIONS", "No active connections", worldId)
    }
}
