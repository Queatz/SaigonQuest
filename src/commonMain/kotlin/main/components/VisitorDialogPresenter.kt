package main.components

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.api.sendUserJoin
import main.api.sendUserLeave
import main.api.updatePlayer
import main.components.SceneManager
import main.components.inventory.InventoryState
import models.Sex
import models.User
import models.Position

/**
 * Presenter component for VisitorDialog
 * 
 * This component encapsulates the common functionality for presenting the VisitorDialog
 * from different parts of the application (Scene or WorldUI).
 */
@Composable
fun VisitorDialogPresenter(
    isVisible: Boolean,
    onClose: () -> Unit,
    onSuccess: (User) -> Unit,
    updatePlayerName: (String) -> Unit = {},
    showWorldEditPanel: Boolean = false,
    onShowWorldEditPanel: () -> Unit = {}
) {
    // Only show the dialog if it's visible
    if (!isVisible) return
    
    // Show the VisitorDialog
    VisitorDialog(
        onClose = onClose,
        onRegister = { username, password, yearOfBirth, sex ->
            handleRegistration(
                username = username,
                password = password,
                yearOfBirth = yearOfBirth,
                sex = sex,
                onSuccess = { user ->
                    // Update player name in the scene
                    updatePlayerName(user.username)
                    
                    // Call the onSuccess callback
                    onSuccess(user)
                    
                    // Show world edit panel if requested
                    if (showWorldEditPanel) {
                        onShowWorldEditPanel()
                    }
                }
            )
        },
        onLogin = { username, password ->
            handleLogin(
                username = username,
                password = password,
                onSuccess = { user ->
                    // Update player name in the scene
                    updatePlayerName(user.username)
                    
                    // Call the onSuccess callback
                    onSuccess(user)
                }
            )
        }
    )
}

/**
 * Handles user registration
 */
private fun handleRegistration(
    username: String,
    password: String,
    yearOfBirth: Int?,
    sex: Sex?,
    onSuccess: (User) -> Unit
) {
    // Create a new coroutine scope that won't be cancelled when the composable is recomposed
    val registrationScope = CoroutineScope(Dispatchers.Default)
    
    // Handle registration using the independent scope
    registrationScope.launch {
        try {
            // Check if we have a token before proceeding
            val token = UserApi.getToken()
            if (token == null || token.isEmpty()) {
                console.error("Registration error: No valid token available")
                
                try {
                    // Try to create a visitor again to get a token
                    val visitor = UserApi.createOrGetVisitor("")
                    console.log("Created new visitor: ${visitor.visitorId}")
                    
                    // Add a small delay to ensure token is saved
                    delay(500) // 500ms delay
                    
                    try {
                        // Now upgrade the visitor to a player
                        val user = UserApi.upgradeToPlayer(username, password, yearOfBirth, sex)
                        console.log("User registered: ${user.username}")
                        
                        // Update player state in WebSocket to notify other clients
                        try {
                            val updateSuccess = WebSocketApi.updatePlayer(
                                name = user.username,
                                isPlayer = true
                            )
                            if (updateSuccess) {
                                console.log("Successfully updated player state after registration")
                            } else {
                                console.warn("Failed to update player state after registration")
                            }
                        } catch (e: Exception) {
                            console.error("Error updating player state after registration", e)
                        }
                        
                        // Refresh inventory and customizations so the local avatar updates
                        try {
                            InventoryState.refreshInventory(registrationScope)
                        } catch (e: Exception) {
                            console.error("VisitorDialogPresenter: Failed to refresh inventory after registration", e)
                        }
                        
                        // Call the onSuccess callback
                        onSuccess(user)
                    } catch (error: Throwable) {
                        console.error("Registration error after retry: ${error.message}")
                    }
                } catch (error: Throwable) {
                    console.error("Error creating visitor: ${error.message}")
                }
            } else {
                // We have a token, proceed with upgrade
                try {
                    val user = UserApi.upgradeToPlayer(username, password, yearOfBirth, sex)
                    console.log("User registered: ${user.username}")
                    
                    // Update player state in WebSocket to notify other clients
                    try {
                        val updateSuccess = WebSocketApi.updatePlayer(
                            name = user.username,
                            isPlayer = true
                        )
                        if (updateSuccess) {
                            console.log("Successfully updated player state after registration")
                        } else {
                            console.warn("Failed to update player state after registration")
                        }
                    } catch (e: Exception) {
                        console.error("Error updating player state after registration", e)
                    }
                    
                    // Refresh inventory and customizations so the local avatar updates
                    try {
                        InventoryState.refreshInventory(registrationScope)
                    } catch (e: Exception) {
                        console.error("VisitorDialogPresenter: Failed to refresh inventory after registration", e)
                    }
                    
                    // Call the onSuccess callback
                    onSuccess(user)
                } catch (error: Throwable) {
                    console.error("Registration error: ${error.message}")
                }
            }
        } catch (error: Throwable) {
            console.error("Error getting current user: ${error.message}")
        }
    }
}

/**
 * Handles user login
 */
private fun handleLogin(
    username: String,
    password: String,
    onSuccess: (User) -> Unit
) {
    // Create a new coroutine scope that won't be cancelled when the composable is recomposed
    val loginScope = CoroutineScope(Dispatchers.Default)
    
    // Handle login using the independent scope
    loginScope.launch {
        try {
            // Step 2: Send USER_LEAVE as the visitor (before credentials switch)
            try {
                val worldId = WebSocketConnectionManager.currentWorldId.value
                if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED && worldId != null) {
                    console.log("VisitorDialogPresenter: Sending USER_LEAVE for visitor before login")
                    WebSocketApi.sendUserLeave(worldId)
                } else {
                    console.warn("VisitorDialogPresenter: Skipping USER_LEAVE before login - not connected or missing worldId")
                }
            } catch (e: Exception) {
                console.error("VisitorDialogPresenter: Error while sending USER_LEAVE before login", e)
            }

            // Step 1: Login (credentials switch from visitor -> player)
            val user = UserApi.login(username, password)
            console.log("User logged in: ${user.username}")

            // Update OtherPlayersController knowledge of our current userId so it ignores our join
            try {
                SceneManager.getOtherPlayersController()?.let { controller ->
                    controller.refreshCurrentUserId()
                }
            } catch (e: Exception) {
                console.error("VisitorDialogPresenter: Failed to refresh current userId in OtherPlayersController", e)
            }

            // Step 3: Send USER_JOIN for the new player with current position
            try {
                val worldId = WebSocketConnectionManager.currentWorldId.value
                if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED && worldId != null) {
                    // Determine current position
                    val playerPos = SceneManager.getPlayerController()?.getPlayerPosition()
                    val position = if (playerPos != null) Position(playerPos.x, playerPos.y, playerPos.z) else Position(0f, 0f, 0f)

                    console.log("VisitorDialogPresenter: Sending USER_JOIN for player after login at position: ${'$'}position")
                    WebSocketApi.sendUserJoin(worldId, position, isPlayer = true)
                } else {
                    console.warn("VisitorDialogPresenter: Skipping USER_JOIN after login - not connected or missing worldId")
                }
            } catch (e: Exception) {
                console.error("VisitorDialogPresenter: Error while sending USER_JOIN after login", e)
            }
            
            // Update player state in WebSocket to notify other clients
            try {
                val updateSuccess = WebSocketApi.updatePlayer(
                    name = user.username,
                    isPlayer = user.isPlayer
                )
                if (updateSuccess) {
                    console.log("Successfully updated player state after login")
                } else {
                    console.warn("Failed to update player state after login")
                }
            } catch (e: Exception) {
                console.error("Error updating player state after login", e)
            }
            
            // Refresh inventory and customizations so the local avatar updates
            try {
                InventoryState.refreshInventory(loginScope)
            } catch (e: Exception) {
                console.error("VisitorDialogPresenter: Failed to refresh inventory after login", e)
            }
            
            // Call the onSuccess callback
            onSuccess(user)
        } catch (error: Throwable) {
            console.error("Login error: ${error.message}")
        }
    }
}
