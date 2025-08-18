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

                    // Re-show local player's status after registration
                    try {
                        if (!user.status.isNullOrBlank()) {
                            SceneManager.getPlayerController()?.displayPlayerStatus(user.status!!)
                        }
                    } catch (e: Throwable) {
                        console.error("VisitorDialogPresenter: Failed to display status after registration", e)
                    }
                    
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

                    // Re-show local player's status after login
                    try {
                        if (!user.status.isNullOrBlank()) {
                            SceneManager.getPlayerController()?.displayPlayerStatus(user.status!!)
                        }
                    } catch (e: Throwable) {
                        console.error("VisitorDialogPresenter: Failed to display status after login", e)
                    }
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
                        } catch (e: Throwable) {
                            console.error("Error updating player state after registration", e)
                        }
                        
                        // Inform other-players system of our new identity to avoid self-duplication
                        try {
                            SceneManager.getOtherPlayersController()?.refreshCurrentUserId()
                        } catch (e: Throwable) {
                            console.error("VisitorDialogPresenter: Failed to refresh current userId in OtherPlayersController (registration retry path)", e)
                        }

                        // Refresh inventory and customizations so the local avatar updates
                        try {
                            InventoryState.refreshInventory(registrationScope)
                        } catch (e: Throwable) {
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
                    } catch (e: Throwable) {
                        console.error("Error updating player state after registration", e)
                    }
                    
                    // Inform other-players system of our new identity to avoid self-duplication
                    try {
                        SceneManager.getOtherPlayersController()?.refreshCurrentUserId()
                    } catch (e: Throwable) {
                        console.error("VisitorDialogPresenter: Failed to refresh current userId in OtherPlayersController (registration)", e)
                    }

                    // Refresh inventory and customizations so the local avatar updates
                    try {
                        InventoryState.refreshInventory(registrationScope)
                    } catch (e: Throwable) {
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
            } catch (e: Throwable) {
                console.error("VisitorDialogPresenter: Error while sending USER_LEAVE before login", e)
            }

            // Step 1: Login (credentials switch from visitor -> player)
            val user = UserApi.login(username, password)
            console.log("User logged in: ${user.username}")

            // Update OtherPlayersController knowledge of our current userId so it ignores our join
            try {
                SceneManager.getOtherPlayersController()?.refreshCurrentUserId()
            } catch (e: Throwable) {
                console.error("VisitorDialogPresenter: Failed to refresh current userId in OtherPlayersController", e)
            }

            // Step 3: Reconnect WebSocket so server binds session to the logged-in user
            try {
                val worldId = WebSocketConnectionManager.currentWorldId.value
                val isConnected = WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED

                if (isConnected && worldId != null) {
                    console.log("VisitorDialogPresenter: Reconnecting WebSocket after login to update server-side user context")

                    // Disconnect current connection to drop the visitor-associated session
                    WebSocketConnectionManager.disconnect()

                    // Small delay to ensure the socket actually closes before reconnect
                    delay(250)

                    // Connect again to the same world using the new token
                    WebSocketConnectionManager.connectToWorld(worldId)

                    // Wait for reconnection (up to 10 attempts, 500ms each)
                    var attempts = 0
                    while (WebSocketConnectionManager.connectionState.value != WebSocketConnectionManager.ConnectionState.CONNECTED && attempts < 10) {
                        delay(500)
                        attempts++
                    }

                    if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED) {
                        // Determine current position
                        val playerPos = SceneManager.getPlayerController()?.getPlayerPosition()
                        val position = if (playerPos != null) Position(playerPos.x, playerPos.y, playerPos.z) else Position(0f, 0f, 0f)

                        console.log("VisitorDialogPresenter: Sending USER_JOIN for player after reconnect at position: $position")
                        WebSocketApi.sendUserJoin(worldId, position, isPlayer = true)

                        // Update player state in WebSocket to notify other clients
                        val updateSuccess = WebSocketApi.updatePlayer(
                            name = user.username,
                            isPlayer = user.isPlayer
                        )
                        if (updateSuccess) {
                            console.log("Successfully updated player state after login (post-reconnect)")
                        } else {
                            console.warn("Failed to update player state after login (post-reconnect)")
                        }
                    } else {
                        console.warn("VisitorDialogPresenter: Reconnect after login did not complete in time; skipping USER_JOIN/updatePlayer")
                    }
                } else {
                    console.warn("VisitorDialogPresenter: Skipping WebSocket reconnect after login - not connected or missing worldId")
                    // Best effort: try to update player state if possible
                    try {
                        val updateSuccess = WebSocketApi.updatePlayer(
                            name = user.username,
                            isPlayer = user.isPlayer
                        )
                        if (updateSuccess) {
                            console.log("Successfully updated player state after login (no reconnect path)")
                        } else {
                            console.warn("Failed to update player state after login (no reconnect path)")
                        }
                    } catch (ignored: Throwable) {
                        console.error("VisitorDialogPresenter: Error updating player state after login (no reconnect path)", ignored)
                    }
                }
            } catch (e: Throwable) {
                console.error("VisitorDialogPresenter: Error during WebSocket reconnect after login", e)
            }
            
            // Refresh inventory and customizations so the local avatar updates
            try {
                InventoryState.refreshInventory(loginScope)
            } catch (e: Throwable) {
                console.error("VisitorDialogPresenter: Failed to refresh inventory after login", e)
            }
            
            // Call the onSuccess callback
            onSuccess(user)
        } catch (error: Throwable) {
            console.error("Login error: ${error.message}")
        }
    }
}
