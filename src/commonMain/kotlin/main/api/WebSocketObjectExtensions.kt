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
fun WebSocketApi.addObject(obj: Object): Boolean {
    if (!isConnected()) {
        console.warn("Cannot add object: WebSocket not connected")
        return false
    }
    
    try {
        val message = WebSocketMessage.fromEnum(WebSocketAction.ADD_OBJECT, ApiClient.json.encodeToString(obj))
        return sendMessage(message)
    } catch (e: Exception) {
        console.error("Error creating add object message", e)
        return false
    }
}

/**
 * Remove an object from the world
 * 
 * @param objectId The ID of the object to remove
 * @return true if the object was removed successfully, false otherwise
 */
fun WebSocketApi.removeObject(objectId: String): Boolean {
    if (!isConnected()) {
        console.warn("Cannot remove object: WebSocket not connected")
        return false
    }
    
    try {
        val message = WebSocketMessage.fromEnum(WebSocketAction.REMOVE_OBJECT, ApiClient.json.encodeToString(objectId))
        return sendMessage(message)
    } catch (e: Exception) {
        console.error("Error creating remove object message", e)
        return false
    }
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
fun WebSocketApi.updateObject(obj: Object): Boolean {
    if (!isConnected()) {
        console.warn("Cannot update object: WebSocket not connected")
        return false
    }
    
    try {
        val message = WebSocketMessage.fromEnum(WebSocketAction.OBJECT_UPDATE, ApiClient.json.encodeToString(obj))
        return sendMessage(message)
    } catch (e: Exception) {
        console.error("Error creating update object message", e)
        return false
    }
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
    } catch (e: Exception) {
        console.error("Error creating trigger event message", e)
        return false
    }
}
