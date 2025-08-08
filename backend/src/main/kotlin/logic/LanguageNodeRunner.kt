package logic

import debug.WebSocketDebug
import models.nodeeditor.NodeDataModel
import repository.UserRepository

/**
 * Runner for language nodes
 *
 * This node runner handles checking the user's language and directing the flow accordingly.
 * It sets a buttonIndex in the eventData based on the user's language:
 * - 0 for English
 * - 1 for Vietnamese
 */
class LanguageNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        val userRepository = UserRepository()

        WebSocketDebug.logMessage("LANGUAGE_NODE_EXECUTE", "Executing language node with data: ${node.data}", context.session)
        
        // Get the user's language from the connection
        val userLanguage = userRepository.getById(context.connection.userId)?.language ?: "en"
        WebSocketDebug.logMessage("USER_LANGUAGE", "User language: $userLanguage", context.session)
        
        // Set the buttonIndex based on the language
        val buttonIndex = when (userLanguage.lowercase()) {
            "vi" -> 1  // Vietnamese
            else -> 0  // Default to English (index 0)
        }
        
        WebSocketDebug.logMessage("LANGUAGE_BUTTON_INDEX", "Setting button index to $buttonIndex for language: $userLanguage", context.session)
        
        // Set the buttonIndex in the eventData
        eventData["buttonIndex"] = buttonIndex
        
        WebSocketDebug.logMessage("LANGUAGE_NODE_SUCCESS", "Language node execution completed successfully", context.session)
        return true
    }
}
