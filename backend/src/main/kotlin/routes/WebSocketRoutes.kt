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
import kotlinx.serialization.json.Json
import models.ChatMessage
import models.PlayerPositionData
import models.Position
import models.UserJoinData
import models.UserLeaveData
import models.WebSocketAction
import repository.ChatRepository
import repository.UserRepository
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
    
    // JSON serializer
    val json = Json { 
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true  // Ensure default values are encoded
        classDiscriminator = "type"  // Use "type" as the discriminator for polymorphic serialization
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
                        
                        when (message.action) {
                            WebSocketAction.SEND_CHAT_MESSAGE -> {
                                WebSocketDebug.logMessage("PROCESSING", "SEND_CHAT_MESSAGE action", this)
                                
                                // Parse chat message
                                val chatMessage = json.decodeFromString<ChatMessage>(message.data)
                                WebSocketDebug.logMessage("CHAT_MESSAGE", "worldId=${chatMessage.worldId}, userId=${chatMessage.userId}, message=${chatMessage.message}", this)
                                
                                // Save message to database
                                WebSocketDebug.logMessage("SAVING", "Saving chat message to database", this)
                                try {
                                    val savedMessage = chatRepository.saveMessage(chatMessage)
                                    WebSocketDebug.logMessage("SAVED", "Message saved with ID: ${savedMessage.key}", this)
                                    
                                    // Broadcast message to all clients in the same world
                                    val broadcastMessage = WebSocketMessage(
                                        action = WebSocketAction.RECEIVE_CHAT_MESSAGE,
                                        data = json.encodeToString(savedMessage)
                                    )
                                    
                                    val broadcastMessageString = json.encodeToString(broadcastMessage)
                                    WebSocketDebug.logMessage("BROADCASTING", "To world: $worldId", this)
                                    WebSocketDebug.logMessage("BROADCAST_CONTENT", broadcastMessageString, this)
                                    
                                    broadcastToWorld(worldId, broadcastMessageString, activeConnections)
                                } catch (e: Exception) {
                                    WebSocketDebug.logError("Error saving chat message to database", e, this)
                                }
                            }
                            WebSocketAction.UPDATE_PLAYER_POSITION -> {
                                // Parse player position data
                                val positionData = json.decodeFromString<PlayerPositionData>(message.data)
                                WebSocketDebug.logMessage("POSITION_UPDATE", "from: ${connection.userId}, for: ${positionData.userId}, world: $worldId", this)
                                
                                // Broadcast position update to all other clients in the same world
                                val broadcastMessage = WebSocketMessage(
                                    action = WebSocketAction.UPDATE_PLAYER_POSITION,
                                    data = message.data
                                )
                                
                                WebSocketDebug.logMessage("BROADCASTING_POSITION", "user: ${positionData.userId}, world: $worldId", this)
                                broadcastToWorld(worldId, json.encodeToString(broadcastMessage), activeConnections)
                            }
                            WebSocketAction.USER_JOIN -> {
                                // Check if this is a heartbeat ping message
                                if (message.data.contains("\"ping\":")) {
                                    // This is a heartbeat ping, just log it and don't broadcast
                                    WebSocketDebug.logMessage("HEARTBEAT_PING", "from: ${connection.userId}", this)
                                } else {
                                    try {
                                        // Parse user join data
                                        val joinData = json.decodeFromString<UserJoinData>(message.data)
                                        WebSocketDebug.logMessage("USER_JOIN", "from: ${connection.userId}, for: ${joinData.userId}, world: ${joinData.worldId}", this)
                                        
                                        // Ensure the worldId in the join data matches the connection's worldId
                                        val correctedJoinData = if (joinData.worldId != worldId) {
                                            WebSocketDebug.logMessage("WORLD_MISMATCH", "message: ${joinData.worldId}, connection: $worldId", this)
                                            joinData.copy(worldId = worldId)
                                        } else {
                                            joinData
                                        }
                                        
                                        // Broadcast join notification to all clients in the same world
                                        val broadcastMessage = WebSocketMessage(
                                            action = WebSocketAction.USER_JOIN,
                                            data = json.encodeToString(correctedJoinData)
                                        )
                                        
                                        WebSocketDebug.logMessage("BROADCASTING_JOIN", "user: ${correctedJoinData.userId}, world: $worldId", this)
                                        broadcastToWorld(worldId, json.encodeToString(broadcastMessage), activeConnections)
                                    } catch (e: Exception) {
                                        WebSocketDebug.logError("Error processing USER_JOIN message", e, this)
                                        WebSocketDebug.logMessage("MALFORMED_DATA", message.data, this)
                                    }
                                }
                            }
                            WebSocketAction.USER_LEAVE -> {
                                // Parse user leave data
                                val leaveData = json.decodeFromString<UserLeaveData>(message.data)
                                WebSocketDebug.logMessage("USER_LEAVE", "from: ${connection.userId}, for: ${leaveData.userId}, world: ${leaveData.worldId}", this)
                                
                                // Ensure the worldId in the leave data matches the connection's worldId
                                val correctedLeaveData = if (leaveData.worldId != worldId) {
                                    WebSocketDebug.logMessage("WORLD_MISMATCH", "message: ${leaveData.worldId}, connection: $worldId", this)
                                    leaveData.copy(worldId = worldId)
                                } else {
                                    leaveData
                                }
                                
                                // Broadcast leave notification to all clients in the same world
                                val broadcastMessage = WebSocketMessage(
                                    action = WebSocketAction.USER_LEAVE,
                                    data = json.encodeToString(correctedLeaveData)
                                )
                                
                                WebSocketDebug.logMessage("BROADCASTING_LEAVE", "user: ${correctedLeaveData.userId}, world: $worldId", this)
                                broadcastToWorld(worldId, json.encodeToString(broadcastMessage), activeConnections)
                            }
                            WebSocketAction.UPDATE_CHAT_MESSAGE -> {
                                WebSocketDebug.logMessage("PROCESSING", "UPDATE_CHAT_MESSAGE action", this)
                                
                                // Parse chat message
                                val chatMessage = json.decodeFromString<ChatMessage>(message.data)
                                WebSocketDebug.logMessage("UPDATE_CHAT_MESSAGE", "key=${chatMessage.key}, userId=${chatMessage.userId}, message=${chatMessage.message}", this)
                                
                                // Update message in database
                                WebSocketDebug.logMessage("UPDATING", "Updating chat message in database", this)
                                try {
                                    val updatedMessage = chatRepository.updateMessage(chatMessage.key, chatMessage)
                                    
                                    if (updatedMessage != null) {
                                        WebSocketDebug.logMessage("UPDATED", "Message updated with ID: ${updatedMessage.key}", this)
                                        
                                        // Broadcast updated message to all clients in the same world
                                        val broadcastMessage = WebSocketMessage(
                                            action = WebSocketAction.UPDATE_CHAT_MESSAGE,
                                            data = json.encodeToString(updatedMessage)
                                        )
                                        
                                        val broadcastMessageString = json.encodeToString(broadcastMessage)
                                        WebSocketDebug.logMessage("BROADCASTING", "To world: $worldId", this)
                                        WebSocketDebug.logMessage("BROADCAST_CONTENT", broadcastMessageString, this)
                                        
                                        broadcastToWorld(worldId, broadcastMessageString, activeConnections)
                                    } else {
                                        WebSocketDebug.logError("Failed to update message: ${chatMessage.key}", null, this)
                                    }
                                } catch (e: Exception) {
                                    WebSocketDebug.logError("Error updating chat message in database", e, this)
                                }
                            }
                            WebSocketAction.DELETE_CHAT_MESSAGE -> {
                                WebSocketDebug.logMessage("PROCESSING", "DELETE_CHAT_MESSAGE action", this)
                                
                                // Parse chat message
                                val chatMessage = json.decodeFromString<ChatMessage>(message.data)
                                WebSocketDebug.logMessage("DELETE_CHAT_MESSAGE", "key=${chatMessage.key}, userId=${chatMessage.userId}", this)
                                
                                // Delete message in database
                                WebSocketDebug.logMessage("DELETING", "Deleting chat message in database", this)
                                try {
                                    val deletedMessage = chatRepository.deleteMessage(chatMessage.key, chatMessage.userId)
                                    
                                    if (deletedMessage != null) {
                                        WebSocketDebug.logMessage("DELETED", "Message deleted with ID: ${deletedMessage.key}", this)
                                        
                                        // Broadcast deleted message to all clients in the same world
                                        val broadcastMessage = WebSocketMessage(
                                            action = WebSocketAction.DELETE_CHAT_MESSAGE,
                                            data = json.encodeToString(deletedMessage)
                                        )
                                        
                                        val broadcastMessageString = json.encodeToString(broadcastMessage)
                                        WebSocketDebug.logMessage("BROADCASTING", "To world: $worldId", this)
                                        WebSocketDebug.logMessage("BROADCAST_CONTENT", broadcastMessageString, this)
                                        
                                        broadcastToWorld(worldId, broadcastMessageString, activeConnections)
                                    } else {
                                        WebSocketDebug.logError("Failed to delete message: ${chatMessage.key}", null, this)
                                    }
                                } catch (e: Exception) {
                                    WebSocketDebug.logError("Error deleting chat message in database", e, this)
                                }
                            }
                            null -> {
                                WebSocketDebug.logError("Received message with null action", null, this)
                            }
                            else -> {
                                // These actions will be implemented as needed
                                WebSocketDebug.logMessage("UNIMPLEMENTED_ACTION", "${message.action.name} not yet implemented on server", this)
                            }
                            // Add other message types here as needed
                        }
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
 */
private suspend fun broadcastToWorld(
    worldId: String,
    message: String,
    activeConnections: ConcurrentHashMap<String, MutableList<WebSocketConnection>>
) {
    val connections = activeConnections[worldId]
    if (connections.isNullOrEmpty()) {
        WebSocketDebug.logBroadcast("NO_CONNECTIONS", "No active connections", worldId)
        return
    }
    
    WebSocketDebug.logBroadcast("BROADCASTING", "To ${connections.size} clients", worldId)
    WebSocketDebug.logBroadcast("MESSAGE_CONTENT", message, worldId)
    var successCount = 0
    
    connections.forEach { connection ->
        try {
            WebSocketDebug.logBroadcast("SENDING_TO", "User: ${connection.userId}", worldId)
            connection.session.send(message)
            successCount++
            WebSocketDebug.logBroadcast("SENT_SUCCESS", "To user: ${connection.userId}", worldId)
        } catch (e: Exception) {
            // Log error but continue with other connections
            WebSocketDebug.logError("Error sending message to client ${connection.userId}", e, connection.session)
        }
    }
    
    WebSocketDebug.logBroadcast("BROADCAST_COMPLETE", "Success: $successCount/${connections.size} clients", worldId)
}
