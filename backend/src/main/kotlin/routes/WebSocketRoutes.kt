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
import models.ChatMessage
import models.PlayerPositionData
import models.Position
import models.UserJoinData
import models.UserLeaveData
import models.WebSocketAction
import repository.ChatRepository
import repository.UserRepository
import routes.handlers.WebSocketHandler
import utils.json
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket connection for a specific client
 */
data class WebSocketConnection(
    val session: DefaultWebSocketServerSession,
    val userId: String,
    val worldId: String
)

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
    val data: String = ""
)

/**
 * Configure routes for WebSocket operations
 */
fun Routing.configureWebSocketRoutes() {
    val chatRepository = ChatRepository()
    val userRepository = UserRepository()
    
    // Store active connections by worldId -> list of connections
    val activeConnections = ConcurrentHashMap<String, MutableList<WebSocketConnection>>()
    
    // Create handler factory
    val handlerFactory = routes.handlers.WebSocketHandlerFactory(chatRepository, userRepository)
    
    // JSON serializer
    val json = utils.json
    
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
        WebSocketDebug.logConnection("TOKEN", this, worldId, "unknown")
        
        // Validate token and get userId
        val userId = if (token.isNotBlank()) {
            try {
                // Use JwtConfig.verifier to verify the token
                WebSocketDebug.logConnection("TOKEN_VALIDATION", this, worldId, "unknown")
                val decodedJWT = JwtConfig.verifier.verify(token)
                val extractedUserId = decodedJWT.getClaim("userId").asString()
                WebSocketDebug.logConnection("TOKEN_VALID", this, worldId, extractedUserId)
                extractedUserId
            } catch (e: JWTVerificationException) {
                WebSocketDebug.logError("Error validating token", e, this)
                WebSocketDebug.logConnection("ANONYMOUS", this, worldId, "unknown")
                null
            }
        } else {
            WebSocketDebug.logConnection("NO_TOKEN", this, worldId, "unknown")
            null
        }
        
        // Create connection
        val finalUserId = userId ?: "anonymous-${System.currentTimeMillis()}"
        val connection = WebSocketConnection(
            session = this,
            userId = finalUserId,
            worldId = worldId
        )
        WebSocketDebug.logConnection("CREATED", this, worldId, finalUserId)
        
        // Log connection with WebSocketDebug
        WebSocketDebug.logConnection("CONNECT", this, worldId, finalUserId)
        
        try {
            // Add connection to active connections
            activeConnections.getOrPut(worldId) { mutableListOf() }.add(connection)
            
            // Log active connections for debugging
            WebSocketDebug.logConnection("ACTIVE_WORLDS", this, "all", "system")
            activeConnections.forEach { (wId, connections) ->
                WebSocketDebug.logConnection("ACTIVE_CONNECTIONS", this, wId, "system")
            }
            
            // Send user join notification to all clients in the world
            val isPlayer = connection.userId.startsWith("anonymous-").not()
            val username = if (isPlayer) connection.userId else "Visitor"
            
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
            
            // Send information about all existing users to the new connection
            WebSocketDebug.logMessage("SENDING_EXISTING_USERS", "", this)
            val existingConnections = activeConnections[worldId] ?: emptyList()
            WebSocketDebug.logMessage("EXISTING_CONNECTIONS", "count: ${existingConnections.size}", this)
            
            // Make a copy of the list to avoid concurrent modification issues
            val existingConnectionsCopy = ArrayList(existingConnections)
            
            existingConnectionsCopy.forEach { existingConnection ->
                if (existingConnection.userId != connection.userId) {
                    WebSocketDebug.logMessage("PROCESSING_USER", existingConnection.userId, this)
                    val existingUserJoinData = UserJoinData(
                        userId = existingConnection.userId,
                        username = if (existingConnection.userId.startsWith("anonymous-")) "Visitor" else existingConnection.userId,
                        isPlayer = existingConnection.userId.startsWith("anonymous-").not(),
                        position = Position(0f, 0f, 0f), // Default position, will be updated by client
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
                    } catch (e: Exception) {
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
                        handler.handle(message, connection, worldId, activeConnections, json, this)
                    } catch (e: Exception) {
                        // Log error but don't close connection
                        WebSocketDebug.logError("Error processing WebSocket message", e, this)
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            // Normal close
            WebSocketDebug.logConnection("CLOSE_NORMAL", this, worldId, connection.userId)
        } catch (e: Exception) {
            // Unexpected error
            WebSocketDebug.logError("Error in WebSocket connection", e, this)
        } finally {
            // Send user leave notification to all clients in the world
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
