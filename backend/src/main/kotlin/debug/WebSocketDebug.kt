package debug

import io.ktor.server.plugins.origin
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import models.WebSocketAction
import org.slf4j.LoggerFactory
import routes.WebSocketMessage
import utils.json
import java.util.concurrent.atomic.AtomicInteger

/**
 * Utility class for debugging WebSocket connections and messages
 */
object WebSocketDebug {
    private val logger = LoggerFactory.getLogger(WebSocketDebug::class.java)
    private val connectionCounter = AtomicInteger(0)
    private val messageCounter = AtomicInteger(0)
    private val broadcastCounter = AtomicInteger(0)
    
    // Enable or disable debug logging
    var enabled = System.getenv("WEBSOCKET_DEBUG")?.equals("true", ignoreCase = true) ?: true
    
    // JSON serializer with pretty printing for debugging
    private val debugJson = json
    
    init {
        logger.info("WebSocketDebug initialized. Debug logging is ${if (enabled) "ENABLED" else "DISABLED"}")
        logger.info("Set WEBSOCKET_DEBUG=true environment variable to enable debug logging")
    }
    
    /**
     * Enable or disable WebSocket debug logging programmatically
     * 
     * @param enable True to enable logging, false to disable
     * @return The previous state of the enabled flag
     */
    fun setEnabled(enable: Boolean): Boolean {
        val previous = enabled
        enabled = enable
        logger.info("WebSocketDebug logging ${if (enable) "ENABLED" else "DISABLED"} programmatically")
        return previous
    }
    
    /**
     * Log a WebSocket connection event
     */
    fun logConnection(event: String, session: DefaultWebSocketServerSession, worldId: String, userId: String) {
        if (!enabled) return
        
        val connectionId = connectionCounter.incrementAndGet()
        logger.debug("=== WEBSOCKET DEBUG [Connection #$connectionId] ===")
        logger.debug("Event: $event")
        logger.debug("Session: $session")
        logger.debug("World ID: $worldId")
        logger.debug("User ID: $userId")
        logger.debug("Remote Address: ${session.call.request.origin.remoteAddress}")
        logger.debug("User Agent: ${session.call.request.headers["User-Agent"]}")
        logger.debug("=== END WEBSOCKET DEBUG [Connection #$connectionId] ===")
    }
    
    /**
     * Log a WebSocket message
     */
    fun logMessage(direction: String, rawMessage: String, session: DefaultWebSocketServerSession) {
        if (!enabled) return
        
        val messageId = messageCounter.incrementAndGet()
        logger.debug("=== WEBSOCKET DEBUG [Message #$messageId] ===")
        logger.debug("Direction: $direction")
        logger.debug("Session: $session")
        logger.debug("Raw Message: $rawMessage")
        
        try {
            // Try to parse and pretty print the message
            val message = debugJson.decodeFromString<WebSocketMessage>(rawMessage)
            logger.debug("Parsed Message:")
            logger.debug("  Action: ${message.action}")
            logger.debug("  Data Length: ${message.data.length}")
            
            // For specific actions, try to parse and log the data
            when (message.action) {
                WebSocketAction.SEND_CHAT_MESSAGE -> {
                    logger.debug("  Message Type: Chat Message")
                    try {
                        val chatMessageJson = debugJson.parseToJsonElement(message.data)
                        logger.debug("  Chat Message Data: ${debugJson.encodeToString(chatMessageJson)}")
                    } catch (e: Exception) {
                        logger.debug("  Failed to parse chat message data: ${e.message}")
                    }
                }
                WebSocketAction.USER_JOIN -> logger.debug("  Message Type: User Join")
                WebSocketAction.USER_LEAVE -> logger.debug("  Message Type: User Leave")
                WebSocketAction.UPDATE_PLAYER_POSITION -> logger.debug("  Message Type: Player Position Update")
                else -> logger.debug("  Message Type: Other")
            }
        } catch (e: Exception) {
            logger.debug("Failed to parse message: ${e.message}")
        }
        
        logger.debug("=== END WEBSOCKET DEBUG [Message #$messageId] ===")
    }
    
    /**
     * Log a WebSocket error
     */
    fun logError(error: String, exception: Exception?, session: DefaultWebSocketServerSession?) {
        if (!enabled) return
        
        logger.error("=== WEBSOCKET DEBUG [ERROR] ===")
        logger.error("Error: $error")
        if (session != null) {
            logger.error("Session: $session")
        }
        if (exception != null) {
            logger.error("Exception: ${exception.javaClass.name}")
            logger.error("Message: ${exception.message}")
            logger.error("Stack Trace:")
            exception.stackTrace.take(10).forEach { logger.error("  $it") }
        }
        logger.error("=== END WEBSOCKET DEBUG [ERROR] ===")
    }
    
    /**
     * Log a WebSocket broadcast operation
     */
    fun logBroadcast(event: String, details: String, worldId: String) {
        if (!enabled) return
        
        val broadcastId = broadcastCounter.incrementAndGet()
        logger.debug("=== WEBSOCKET DEBUG [Broadcast #$broadcastId] ===")
        logger.debug("Event: $event")
        logger.debug("World ID: $worldId")
        logger.debug("Details: $details")
        logger.debug("=== END WEBSOCKET DEBUG [Broadcast #$broadcastId] ===")
    }
}
