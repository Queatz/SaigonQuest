package main.api

import models.Object
import models.TriggerEventData
import models.WebSocketAction

/**
 * Extension functions for WebSocketApi related to object operations
 */

/**
 * Add an object to the world
 * 
 * @param obj The object to add
 * @return true if the object was added successfully, false otherwise
 */
@Deprecated("Client WS object mutations are disabled. Use ObjectApi.create(...) (REST). Server will broadcast ADD_OBJECT.")
fun WebSocketApi.addObject(obj: Object): Boolean {
    console.warn("WebSocketApi.addObject is deprecated and disabled. Use ObjectApi.create(...) instead.")
    return false
}

/**
 * Remove an object from the world
 * 
 * @param objectId The ID of the object to remove
 * @return true if the object was removed successfully, false otherwise
 */
@Deprecated("Client WS object mutations are disabled. Use ObjectApi.delete(...) (REST). Server will broadcast REMOVE_OBJECT.")
fun WebSocketApi.removeObject(objectId: String): Boolean {
    console.warn("WebSocketApi.removeObject is deprecated and disabled. Use ObjectApi.delete(...) instead.")
    return false
}

/**
 * Update an object in the world
 * 
 * This sends the updated object to all clients in the world.
 * Used primarily for position updates when objects are moved.
 * 
 * @param obj The updated object
 * @return true if the object was updated successfully, false otherwise
 */
@Deprecated("Client WS object mutations are disabled. Use ObjectApi.updateObject(...) (REST). Server will broadcast OBJECT_UPDATE.")
fun WebSocketApi.updateObject(obj: Object): Boolean {
    console.warn("WebSocketApi.updateObject is deprecated and disabled. Use ObjectApi.updateObject(...) instead.")
    return false
}

/**
 * Trigger an event on an object
 * 
 * This is called when an event condition is met, such as a player being nearby.
 * The server will trigger the specified event on the object.
 * 
 * @param objectId The ID of the object
 * @param eventType The type of event to trigger (e.g., "player_nearby", "visitor_nearby")
 * @param userId The ID of the user who triggered the event
 * @return true if the event was triggered successfully, false otherwise
 */
fun WebSocketApi.triggerObjectEvent(objectId: String, eventType: String, userId: String): Boolean {
    if (!isConnected()) {
        console.warn("Cannot trigger object event: WebSocket not connected")
        return false
    }
    
    try {
        val eventData = TriggerEventData(
            objectId = objectId,
            eventType = eventType,
            userId = userId
        )
        
        val message = WebSocketMessage.fromEnum(WebSocketAction.TRIGGER_OBJECT_EVENT, ApiClient.json.encodeToString(eventData))
        return sendMessage(message)
    } catch (e: Throwable) {
        console.error("Error creating trigger event message", e)
        return false
    }
}
