package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.UserApi
import main.api.WebSocketApi
import models.User

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
    
    // Coroutine scope for async operations
    val scope = rememberCoroutineScope()
    
    // Show the VisitorDialog
    VisitorDialog(
        onClose = onClose,
        onRegister = { username, password, yearOfBirth, sex ->
            handleRegistration(
                username = username,
                password = password,
                yearOfBirth = yearOfBirth,
                sex = sex,
                scope = scope,
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
                scope = scope,
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
    sex: String?,
    scope: CoroutineScope,
    onSuccess: (User) -> Unit
) {
    // Create a coroutine scope if one is not provided
    val coroutineScope = scope ?: CoroutineScope(Dispatchers.Main)
    
    // Handle registration using coroutines
    coroutineScope.launch {
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
                        
                        // Call the onSuccess callback
                        onSuccess(user)
                    } catch (error: Exception) {
                        console.error("Registration error after retry: ${error.message}")
                    }
                } catch (error: Exception) {
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
                    
                    // Call the onSuccess callback
                    onSuccess(user)
                } catch (error: Exception) {
                    console.error("Registration error: ${error.message}")
                }
            }
        } catch (error: Exception) {
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
    scope: CoroutineScope,
    onSuccess: (User) -> Unit
) {
    // Create a coroutine scope if one is not provided
    val coroutineScope = scope ?: CoroutineScope(Dispatchers.Main)
    
    // Handle login
    coroutineScope.launch {
        try {
            val user = UserApi.login(username, password)
            console.log("User logged in: ${user.username}")
            
            // Update player state in WebSocket to notify other clients
            try {
                val updateSuccess = WebSocketApi.updatePlayer(
                    name = user.username,
                    isPlayer = true
                )
                if (updateSuccess) {
                    console.log("Successfully updated player state after login")
                } else {
                    console.warn("Failed to update player state after login")
                }
            } catch (e: Exception) {
                console.error("Error updating player state after login", e)
            }
            
            // Call the onSuccess callback
            onSuccess(user)
        } catch (error: Exception) {
            console.error("Login error: ${error.message}")
        }
    }
}
