package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.UserApi
import main.api.WebSocketApi
import main.api.updatePlayerStatus
import main.components.player.CharacterRenderer
import main.theme.Styles
import models.User
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput

/**
 * Dialog for player users to logout
 */
@Composable
fun PlayerDialog(
    user: User,
    onClose: () -> Unit,
    onLogout: () -> Unit
) {
    // State to track graphics settings
    val isLowGraphicsMode = remember { mutableStateOf(GraphicsSettings.isLowGraphicsModeEnabled()) }
    val isPrettyGraphics = remember { mutableStateOf(GraphicsSettings.isPrettyGraphicsEnabled()) }

    // State to track current language
    val currentLanguage = remember { mutableStateOf(LanguageSettings.getCurrentLanguage()) }

    // State for user status
    val status = remember { mutableStateOf(user.status) }
    
    // State to control character customization dialog visibility
    val showCustomizationDialog = remember { mutableStateOf(false) }

    // Coroutine scope for async operations that persists beyond dialog closure
    val coroutineScope = remember { MainScope() }

    // Function to update user status
    val updateUserStatus = {
        console.log("[DEBUG_LOG] PlayerDialog: updateUserStatus called")
        console.log("[DEBUG_LOG] PlayerDialog: Current status: '${user.status}', New status: '${status.value}'")

        // Only update if status has changed
        if (user.status != status.value) {
            console.log("[DEBUG_LOG] PlayerDialog: Status changed, updating from '${user.status}' to '${status.value}'")
            user.status = status.value

            // Store the status value to use in the coroutine
            val newStatus = status.value

            // Launch the coroutine before closing the dialog
            // Using MainScope ensures it won't be cancelled when the dialog closes
            coroutineScope.launch {
                try {
                    // Check connection state again inside coroutine
                    val isConnected = WebSocketApi.isConnected()
                    console.log("[DEBUG_LOG] PlayerDialog: WebSocket connection state before update: ${if (isConnected) "CONNECTED" else "DISCONNECTED"}")

                    if (!isConnected) {
                        console.warn("[DEBUG_LOG] PlayerDialog: WebSocket not connected, will retry status update")
                        // Wait a bit and retry if not connected
                        delay(1000)
                        if (!WebSocketApi.isConnected()) {
                            console.error("[DEBUG_LOG] PlayerDialog: WebSocket still not connected after retry, status update failed")
                            return@launch
                        }
                    }

                    // Get current world ID
                    val worldId = WebSocketApi.getCurrentWorldId()
                    console.log("[DEBUG_LOG] PlayerDialog: Current world ID: ${worldId ?: "null"}")

                    if (worldId == null) {
                        console.error("[DEBUG_LOG] PlayerDialog: No world ID available, status update failed")
                        return@launch
                    }

                    // Update user status via UserApi
                    val statusUpdateResult = UserApi.updateUserStatus(newStatus)
                    console.log("[DEBUG_LOG] PlayerDialog: User status updated via API: ${statusUpdateResult.status}")

                    console.log("[DEBUG_LOG] PlayerDialog: Calling WebSocketApi.updatePlayerStatus with status: '$newStatus'")
                    val success = WebSocketApi.updatePlayerStatus(newStatus)
                    if (success) {
                        console.log("[DEBUG_LOG] PlayerDialog: Player status updated successfully: '$newStatus'")
                    } else {
                        console.error("[DEBUG_LOG] PlayerDialog: Failed to update player status")
                        console.error("[DEBUG_LOG] PlayerDialog: Last WebSocket error: ${WebSocketApi.getLastError() ?: "None"}")

                        // If the first attempt failed, try once more after a short delay
                        console.log("[DEBUG_LOG] PlayerDialog: Retrying status update after failure")
                        delay(1000)
                        val retrySuccess = WebSocketApi.updatePlayerStatus(newStatus)
                        if (retrySuccess) {
                            console.log("[DEBUG_LOG] PlayerDialog: Player status updated successfully on retry: '$newStatus'")
                        } else {
                            console.error("[DEBUG_LOG] PlayerDialog: Status update failed even after retry")
                        }
                    }
                } catch (e: Throwable) {
                    console.error("[DEBUG_LOG] PlayerDialog: Error updating player status", e)
                    console.error("[DEBUG_LOG] PlayerDialog: Exception details: ${e.message}")

                    // Try to get more detailed error information
                    val lastError = WebSocketApi.getLastError()
                    if (lastError != null) {
                        console.error("[DEBUG_LOG] PlayerDialog: Last WebSocket error: $lastError")
                    }
                }
            }
        } else {
            console.log("[DEBUG_LOG] PlayerDialog: Status unchanged, not updating")
        }

        // Close the dialog immediately, the coroutine will continue in the background
        console.log("[DEBUG_LOG] PlayerDialog: Calling onClose()")
        onClose()
    }

    // Use the BaseDialog component
    BaseDialog(
        onClose = updateUserStatus,
        title = translated("My Profile"),
        content = {
            // Two-column container for responsive layout
            Div(
                attrs = {
                    classes(Styles.playerDialogTwoColumnContainer)
                }
            ) {
                // Left column: Character renderer, Customize button, Status
                Div(
                    attrs = {
                        classes(Styles.playerDialogLeftColumn)
                    }
                ) {
                    // Character renderer
                    Div(
                        attrs = {
                            style {
                                marginBottom(16.px)
                                display(DisplayStyle.Companion.Flex)
                                alignItems(AlignItems.Companion.Center)
                                justifyContent(JustifyContent.Companion.Center)
                                // Use flex-direction column to stack renderer and button
                                property("flex-direction", "column")
                            }
                        }
                    ) {
                        // Add the character renderer component
                        CharacterRenderer(
                            bodyType = user.bodyType,
                            skinTone = user.skinTone,
                            eyeColor = user.eyeColor,
                            hairStyle = user.hairStyle
                        )
                        
                        // Add customize character button
                        Button(
                            attrs = {
                                classes(Styles.secondaryButton)
                                style {
                                    marginTop(8.px)
                                }
                                onClick { 
                                    showCustomizationDialog.value = true
                                }
                            }
                        ) {
                            Translated("Customize Character")
                        }
                    }
                    
                    // Status input
                    Div(
                        attrs = {
                            classes(Styles.settingsSectionContainer)
                        }
                    ) {
                        // Label
                        Span(
                            attrs = {
                                classes(Styles.settingsSectionLabel)
                            }
                        ) {
                            Translated("Status")
                        }

                        val placeholder = translated("Set your status")

                        // Status input field
                        TextInput(
                            value = status.value,
                            attrs = {
                                classes(Styles.inputField)
                                placeholder(placeholder)
                                onInput { event -> status.value = event.value }
                            }
                        )
                    }
                }
                
                // Right column: All remaining UI
                Div(
                    attrs = {
                        classes(Styles.playerDialogRightColumn)
                    }
                ) {
                    // User info
                    Div(
                        attrs = {
                            classes(Styles.userInfoContainer)
                        }
                    ) {
                        // Username
                        Div(
                            attrs = {
                                classes(Styles.usernameText)
                            }
                        ) {
                            Text(user.username)
                        }

                        // Player status
                        Div(
                            attrs = {
                                classes(Styles.userStatusText)
                            }
                        ) {
                            Translated("Player Account")
                        }
                    }

                    // Language switcher
                    Div(
                        attrs = {
                            classes(Styles.settingsSectionContainer)
                        }
                    ) {
                        // Label
                        Span(
                            attrs = {
                                classes(Styles.settingsSectionLabel)
                            }
                        ) {
                            Translated("Language")
                        }

                        // Language switcher component
                        Div(
                            attrs = {
                                classes(Styles.settingsControlContainer)
                            }
                        ) {
                            LanguageSwitcher(
                                onLanguageChange = { language ->
                                    currentLanguage.value = language
                                    
                                    // Update user language in the database
                                    coroutineScope.launch {
                                        try {
                                            UserApi.updateUserLanguage(language)
                                            console.log("[DEBUG_LOG] PlayerDialog: User language updated to: $language")
                                        } catch (e: Throwable) {
                                            console.error("[DEBUG_LOG] PlayerDialog: Error updating user language", e)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Low graphics mode toggle
                    GraphicsToggle(
                        label = translated("Low Graphics Mode"),
                        checked = isLowGraphicsMode.value,
                        onCheckedChange = { newValue ->
                            // Toggle low graphics mode
                            isLowGraphicsMode.value = GraphicsSettings.toggleLowGraphicsMode()
                        },
                        id = "lowGraphicsToggle"
                    )

                    // Pretty graphics toggle
                    GraphicsToggle(
                        label = translated("Pretty Graphics"),
                        checked = isPrettyGraphics.value,
                        onCheckedChange = { newValue ->
                            // Toggle pretty graphics mode
                            isPrettyGraphics.value = GraphicsSettings.togglePrettyGraphics()
                        },
                        id = "prettyGraphicsToggle"
                    )

                    // Logout button
                    Button(
                        attrs = {
                            classes(Styles.logoutButton)
                            onClick { onLogout() }
                        }
                    ) {
                        Translated("Log Out")
                    }
                }
            }
            
            // Render the character customization dialog when visible
            if (showCustomizationDialog.value) {
                CharacterCustomizationDialog(
                    user = user,
                    onClose = { 
                        showCustomizationDialog.value = false 
                    },
                    onSave = { updatedUser ->
                        // Save the customization changes
                        coroutineScope.launch {
                            try {
                                // Update user on the server
                                UserApi.updateUserCustomization(updatedUser)
                            } catch (e: Throwable) {
                                console.error("Failed to update user customization: ${e.message}")
                            }
                        }
                        showCustomizationDialog.value = false
                    }
                )
            }
        },
        actions = {
            // Close button
            DialogButton(
                onClick = updateUserStatus,
                isPrimary = false
            ) {
                Translated("Close")
            }
        }
    )
}
