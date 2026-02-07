package logic

import debug.WebSocketDebug
import models.World
import models.canUserEdit
import models.nodeeditor.NodeDataModel
import models.nodeeditor.PropertyKey
import repository.WorldRepository

/**
 * Runner for IsObjectOwner nodes
 * 
 * Checks if the current user is an owner or co-owner of the world.
 */
class IsObjectOwnerNodeRunner(private val context: NodeRunnerContext) : NodeRunner {
    override suspend fun execute(node: NodeDataModel, eventData: MutableMap<String, Any>): Boolean {
        WebSocketDebug.logMessage("IS_OBJECT_OWNER_NODE_EXECUTE", "Executing IsObjectOwner node for user ${context.connection.userId}", context.session)
        
        try {
            val worldRepository = WorldRepository()
            val world = worldRepository.getById(context.worldId)
            
            if (world == null) {
                WebSocketDebug.logError("World not found for id ${context.worldId}", null, context.session)
                return false
            }
            
            val isOwner = world.canUserEdit(context.connection.userId)
            
            WebSocketDebug.logMessage("IS_OBJECT_OWNER_RESULT", "User ${context.connection.userId} is owner/co-owner: $isOwner", context.session)
            
            // Set output property
            eventData["outputProperty"] = if (isOwner) PropertyKey.YES else PropertyKey.NO
            
            return true
        } catch (e: Throwable) {
            WebSocketDebug.logError("Error executing IsObjectOwner node", e, context.session)
            return false
        }
    }
}
