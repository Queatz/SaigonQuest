package main.api

import app.softwork.routingcompose.Router
import kotlinx.browser.window
import kotlinx.coroutines.launch
import models.DialogueData
import models.PauseData
import models.PlayerPositionData
import models.UpdatePlayerData
import models.UserJoinData
import models.UserLeaveData
import models.WebSocketAction
import main.utils.createCustomEvent

/**
 * Extension functions for WebSocketApi related to listener operations
 */

/**
 * Add a listener for user join events
 * 
 * @param listener A function that takes a UserJoinData and returns Unit
 */
fun WebSocketApi.addUserJoinListener(listener: (UserJoinData) -> Unit): ListenerToken {
    console.log("WebSocketApi: Registering USER_JOIN listener")
    
    // Check if we already have listeners for this action
    val existingListeners = getListenerCount(WebSocketAction.USER_JOIN)
    console.log("WebSocketApi: Currently have $existingListeners USER_JOIN listeners")
    
    val handler: (WebSocketMessage) -> Unit = { message ->
        console.log("WebSocketApi: USER_JOIN listener triggered with message: ${message.data}")
        try {
            val joinData = ApiClient.json.decodeFromString<UserJoinData>(message.data)
            console.log("WebSocketApi: Successfully parsed UserJoinData: userId=${joinData.userId}, username=${joinData.username}")
            listener(joinData)
        } catch (e: Throwable) {
            console.error("WebSocketApi: Error parsing user join data", e)
        }
    }
    addListener(WebSocketAction.USER_JOIN, handler)
    
    // Verify listener was added
    val newListenerCount = getListenerCount(WebSocketAction.USER_JOIN)
    console.log("WebSocketApi: Now have $newListenerCount USER_JOIN listeners")
    return ListenerToken(WebSocketAction.USER_JOIN, handler)
}

/**
 * Add a listener for user leave events
 * 
 * @param listener A function that takes a UserLeaveData and returns Unit
 */
fun WebSocketApi.addUserLeaveListener(listener: (UserLeaveData) -> Unit): ListenerToken {
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            val leaveData = ApiClient.json.decodeFromString<UserLeaveData>(message.data)
            listener(leaveData)
        } catch (e: Throwable) {
            console.error("Error parsing user leave data", e)
        }
    }
    addListener(WebSocketAction.USER_LEAVE, handler)
    return ListenerToken(WebSocketAction.USER_LEAVE, handler)
}

/**
 * Add a listener for player position update events
 * 
 * @param listener A function that takes a PlayerPositionData and returns Unit
 */
fun WebSocketApi.addPlayerPositionListener(listener: (PlayerPositionData) -> Unit): ListenerToken {
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            val positionData = ApiClient.json.decodeFromString<PlayerPositionData>(message.data)
            listener(positionData)
        } catch (e: Throwable) {
            console.error("Error parsing player position data", e)
        }
    }
    addListener(WebSocketAction.UPDATE_PLAYER_POSITION, handler)
    return ListenerToken(WebSocketAction.UPDATE_PLAYER_POSITION, handler)
}

/**
 * Add a listener for player update events
 * 
 * @param listener A function that takes an UpdatePlayerData and returns Unit
 */
fun WebSocketApi.addUpdatePlayerListener(listener: (UpdatePlayerData) -> Unit): ListenerToken {
    console.log("[DEBUG_LOG] WebSocketApi: Adding UPDATE_PLAYER listener")
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            console.log("[DEBUG_LOG] WebSocketApi: Received UPDATE_PLAYER message: ${message.data}")
            val updateData = ApiClient.json.decodeFromString<UpdatePlayerData>(message.data)
            console.log("[DEBUG_LOG] WebSocketApi: Parsed UpdatePlayerData: userId=${updateData.userId}, name=${updateData.name}, status=${updateData.status ?: "null"}")
            listener(updateData)
            console.log("[DEBUG_LOG] WebSocketApi: UPDATE_PLAYER listener called successfully")
        } catch (e: Throwable) {
            console.error("[DEBUG_LOG] WebSocketApi: Error parsing update player data", e)
            console.error("[DEBUG_LOG] WebSocketApi: Raw message data: ${message.data}")
        }
    }
    addListener(WebSocketAction.UPDATE_PLAYER, handler)
    
    // Log the number of listeners
    val listenerCount = getListenerCount(WebSocketAction.UPDATE_PLAYER)
    console.log("[DEBUG_LOG] WebSocketApi: Now have $listenerCount UPDATE_PLAYER listeners")
    return ListenerToken(WebSocketAction.UPDATE_PLAYER, handler)
}

/**
 * Add a listener for teleport player events
 * 
 * @param router The router to use for navigation
 * @param listener A function that takes a Map<String, String> containing teleport data and returns Unit
 */
fun WebSocketApi.addTeleportPlayerListener(router: Router, listener: (Map<String, String>) -> Unit): ListenerToken {
    console.log("WebSocketApi: Adding TELEPORT_PLAYER listener")
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            console.log("WebSocketApi: Received TELEPORT_PLAYER message: ${message.data}")
            val teleportData = ApiClient.json.decodeFromString<Map<String, String>>(message.data)
            console.log("WebSocketApi: Parsed teleport data: ${teleportData["targetWorldUrl"]}")
            
            // Use a coroutine scope to handle the suspend function call
            apiScope.launch {
                try {
                    // Check if the teleport is for the current user
                    val currentUser = UserApi.getCurrentUser()
                    if (teleportData["userId"] == currentUser.key) {
                        // If targetWorldUrl is provided, navigate to it
                        val targetWorldUrl = teleportData["targetWorldUrl"]
                        if (targetWorldUrl != null) {
                            // Normalize paths and compare with current world
                            val target = targetWorldUrl.trim().trim('/')
                            val current = router.currentPath.path.trim().trim('/')

                            if (current == target) {
                                console.log("WebSocketApi: Teleport requested to current world ($current); skipping navigation")
                            } else {
                                console.log("WebSocketApi: Teleporting with fade to world URL: $target")
                                // Dispatch a global event so Scene can fade out and navigate
                                val detail: dynamic = object {}
                                detail.path = "/$target"
                                detail.hardReload = false
                                val evt = createCustomEvent("navigateWithFade", detail)
                                window.dispatchEvent(evt)
                            }
                        } else {
                            console.error("WebSocketApi: Missing targetWorldUrl in teleport data")
                        }
                    }
                } catch (e: Throwable) {
                    console.error("WebSocketApi: Error checking current user for teleport", e)
                }
            }
            
            listener(teleportData)
        } catch (e: Throwable) {
            console.error("WebSocketApi: Error parsing teleport player data", e)
            console.error("WebSocketApi: Raw message data: ${message.data}")
        }
    }
    addListener(WebSocketAction.TELEPORT_PLAYER, handler)
    
    // Log the number of listeners
    val listenerCount = getListenerCount(WebSocketAction.TELEPORT_PLAYER)
    console.log("WebSocketApi: Now have $listenerCount TELEPORT_PLAYER listeners")
    return ListenerToken(WebSocketAction.TELEPORT_PLAYER, handler)
}

/**
 * Add a listener for dialogue events
 * 
 * @param listener A function that takes a DialogueData and returns Unit
 */
fun WebSocketApi.addDialogueListener(listener: (DialogueData) -> Unit): ListenerToken {
    console.log("WebSocketApi: Adding SHOW_DIALOGUE listener")
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            console.log("WebSocketApi: Received SHOW_DIALOGUE message: ${message.data}")
            val dialogueData = ApiClient.json.decodeFromString<DialogueData>(message.data)
            console.log("WebSocketApi: Parsed DialogueData: dialogueId=${dialogueData.dialogueId}, text=${dialogueData.text}")
            listener(dialogueData)
        } catch (e: Throwable) {
            console.error("WebSocketApi: SHOW_DIALOGUE Error parsing dialogue data", e)
            console.error("WebSocketApi: SHOW_DIALOGUE Raw message data: ${message.data}")
        }
    }
    addListener(WebSocketAction.SHOW_DIALOGUE, handler)
    
    // Log the number of listeners
    val listenerCount = getListenerCount(WebSocketAction.SHOW_DIALOGUE)
    console.log("WebSocketApi: Now have $listenerCount SHOW_DIALOGUE listeners")
    return ListenerToken(WebSocketAction.SHOW_DIALOGUE, handler)
}

/**
 * Add a listener for pause events (single-device enforcement)
 *
 * On receiving PAUSE, navigate to "/paused" and disconnect WebSocket.
 */
fun WebSocketApi.addPauseListener(router: Router, listener: (PauseData) -> Unit = {}): ListenerToken {
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            val pauseData = ApiClient.json.decodeFromString<PauseData>(message.data)
            // Store for resume UI
            main.PausedState.set(pauseData.worldUrl, pauseData.worldName)
            // Navigate to paused screen
            router.navigate("/paused")
            // Kill the WebSocket connection completely
            WebSocketConnectionManager.disconnect()
            listener(pauseData)
        } catch (e: Throwable) {
            console.error("WebSocketApi: Error parsing PAUSE data", e)
        }
    }
    addListener(WebSocketAction.PAUSE, handler)
    return ListenerToken(WebSocketAction.PAUSE, handler)
}

/**
 * Add a listener for take-items prompts
 */
fun WebSocketApi.addTakeItemsListener(listener: (models.TakeItemsPrompt) -> Unit): ListenerToken {
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            val data = ApiClient.json.decodeFromString<models.TakeItemsPrompt>(message.data)
            listener(data)
        } catch (e: Throwable) {
            console.error("WebSocketApi: Error parsing SHOW_TAKE_ITEMS data", e)
        }
    }
    addListener(models.WebSocketAction.SHOW_TAKE_ITEMS, handler)
    return ListenerToken(models.WebSocketAction.SHOW_TAKE_ITEMS, handler)
}

/**
 * Add a listener for inventory changed notifications.
 */
fun WebSocketApi.addInventoryChangedListener(listener: (models.InventoryChanged) -> Unit): ListenerToken {
    val handler: (WebSocketMessage) -> Unit = { message ->
        try {
            val data = ApiClient.json.decodeFromString<models.InventoryChanged>(message.data)
            listener(data)
        } catch (e: Throwable) {
            console.error("WebSocketApi: Error parsing INVENTORY_CHANGED data", e)
        }
    }
    addListener(models.WebSocketAction.INVENTORY_CHANGED, handler)
    return ListenerToken(models.WebSocketAction.INVENTORY_CHANGED, handler)
}
