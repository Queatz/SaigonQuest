package main.components

import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.*
import main.components.SceneManager.getPlayerController
import main.components.common.formatAmount
import main.components.player.CharacterRenderer
import main.components.world.PlayerActivity
import main.httpClient
import main.theme.Styles
import models.User
import models.UserBalance
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Dialog for player users to logout
 * nguyễn thị minh khai
 */
@Composable
fun PlayerDialog(
    user: User,
    showUI: Boolean,
    onShowUI: (showUI: Boolean) -> Unit,
    onClose: () -> Unit,
    onLogout: () -> Unit
) {
    // State to track graphics settings
    val isLowGraphicsMode = remember { mutableStateOf(GraphicsSettings.isLowGraphicsModeEnabled()) }
    val isPrettyGraphics = remember { mutableStateOf(GraphicsSettings.isPrettyGraphicsEnabled()) }
    val isMusicEnabled = remember { mutableStateOf(AudioSettings.isMusicEnabled()) }
    val musicVolume = remember { mutableStateOf(AudioSettings.musicVolume()) }
    val isSpeakDialogueEnabled = remember { mutableStateOf(AudioSettings.isSpeakDialogueEnabled()) }

    // State to track current language
    val currentLanguage = remember { mutableStateOf(LanguageSettings.getCurrentLanguage()) }

    // State for user status
    val status = remember { mutableStateOf(user.status) }

    // State for user bars
    val healthState = remember { mutableStateOf(user.health.coerceIn(0, 10)) }
    val mindState = remember { mutableStateOf(user.mind.coerceIn(0, 10)) }
    val spiritState = remember { mutableStateOf(user.spirit.coerceIn(0, 10)) }

    // State to control character customization dialog visibility
    val showCustomizationDialog = remember { mutableStateOf(false) }
    // State to control My Worlds dialog visibility
    val showMyWorldsDialog = remember { mutableStateOf(false) }
    // State to control Media Library dialog visibility
    val showMediaLibraryDialog = remember { mutableStateOf(false) }
    // State to control Settings dialog visibility
    val showSettingsDialog = remember { mutableStateOf(false) }
    // State to control Balance dialogs visibility
    val showBalanceDialog = remember { mutableStateOf(false) }
    val showBalanceHistoryDialog = remember { mutableStateOf(false) }

    // Current balance for summary button
    val balanceState = remember { mutableStateOf<UserBalance?>(null) }

    // Track number of worlds owned by the user (null while loading)
    val myWorldsCountState = remember { mutableStateOf<Int?>(null) }

    // Coroutine scope for async operations that persists beyond dialog closure
    val coroutineScope = remember { MainScope() }
    val router = Router.current

    // Load current balance for summary button
    LaunchedEffect(Unit) {
        val token = UserApi.getToken()
        if (!token.isNullOrEmpty()) {
            try {
                val b: UserBalance = httpClient.get("${ApiConstants.API_BASE_URL}/balance") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }.body()
                balanceState.value = b
            } catch (t: Throwable) {
                console.error("[DEBUG_LOG] PlayerDialog: Failed to load balance: ${t.message}")
            }
        }
    }

    // Load count of user's worlds for CTA logic
    LaunchedEffect(Unit) {
        try {
            val worlds = WorldApi.getWorlds()
            myWorldsCountState.value = worlds.size
        } catch (t: Throwable) {
            console.error("[DEBUG_LOG] PlayerDialog: Failed to load worlds: ${t.message}")
            // Assume zero on failure to avoid blocking CTA
            myWorldsCountState.value = 0
        }
    }

    // Function to update user status
    val updateUserStatus = {
        // Only update if status has changed
        if (user.status != status.value) {
            user.status = status.value

            // Store the status value to use in the coroutine
            val newStatus = status.value

            // Launch the coroutine before closing the dialog
            // Using MainScope ensures it won't be cancelled when the dialog closes
            coroutineScope.launch {
                try {
                    // Check connection state again inside coroutine
                    val isConnected = WebSocketApi.isConnected()

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
        title = translated("My profile"),
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
                        classes(Styles.playerDialogColumn)
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
                        DialogButton(
                            onClick = {
                                showCustomizationDialog.value = true
                            },
                            style = DialogButtonStyle.Secondary,
                            attrs = {
                                style {
                                    marginTop(8.px)
                                }
                            }
                        ) {
                            Translated("Customize character")
                        }
                    }
                    
                    // Status input
                    SettingsSection(label = "Status") {
                        val placeholder = translated("Set your status")

                        // Status input field
                        DialogInput(
                            value = status.value,
                            onInput = { status.value = it },
                            placeholder = placeholder,
                        )
                    }

                    // Health, mind, spirit bars
                    SettingsSection(label = "Health") {
                        UserStatusBars(
                            health = healthState.value,
                            mind = mindState.value,
                            spirit = spiritState.value,
                            readOnly = false,
                            onChange = { h: Int, m: Int, s: Int ->
                                healthState.value = h
                                mindState.value = m
                                spiritState.value = s
                                // Persist via API
                                coroutineScope.launch {
                                    try {
                                        val updated = UserApi.updateUserStatusBars(
                                            health = h,
                                            mind = m,
                                            spirit = s,
                                        )
                                        // Update local user object as well
                                        user.health = updated.health
                                        user.mind = updated.mind
                                        user.spirit = updated.spirit
                                    } catch (e: Throwable) {
                                        console.error("Failed to update status bars: ${e.message}")
                                    }
                                }
                            },
                            size = 24,
                        )
                    }
                }
                
                // Right column: All remaining UI
                Div(
                    attrs = {
                        classes(Styles.playerDialogColumn)
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
                    }
                    // Balance summary (clickable)
                    Div(
                        attrs = {
                            classes(Styles.balanceSummaryBox, Styles.clickable)
                            onClick { showBalanceDialog.value = true }
                        }
                    ) {
                        val b = balanceState.value
                        if (b == null) {
                            P { Translated("Loading...") }
                        } else {
                            Div(
                                attrs = { classes(Styles.balanceAmountText) }
                            ) {
                                Text("${formatAmount(b.balance)} ${translated("VND")}")
                            }
                            Div(
                                attrs = { classes(Styles.balanceSubtext) }
                            ) {
                                Translated("Current balance")
                            }
                        }
                    }
                    DialogButton(
                        onClick = { showBalanceDialog.value = true },
                        style = DialogButtonStyle.Primary
                    ) {
                        Translated("Deposit / Withdraw")
                    }

                    // Language switcher
                    SettingsSection(label = "Language") {
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
                    SettingsToggle(
                        label = translated("Low Graphics Mode"),
                        checked = isLowGraphicsMode.value,
                        onCheckedChange = { newValue ->
                            // Toggle low graphics mode
                            isLowGraphicsMode.value = GraphicsSettings.toggleLowGraphicsMode()
                        },
                        id = "lowGraphicsToggle"
                    )

                    // Pretty graphics toggle
                    SettingsToggle(
                        label = translated("Pretty Graphics"),
                        checked = isPrettyGraphics.value,
                        onCheckedChange = { newValue ->
                            // Toggle pretty graphics mode
                            isPrettyGraphics.value = GraphicsSettings.togglePrettyGraphics()
                        },
                        id = "prettyGraphicsToggle",
                    )

                    // Music toggle (global)
                    SettingsToggle(
                        label = translated("Music"),
                        checked = isMusicEnabled.value,
                        onCheckedChange = { newValue ->
                            AudioSettings.setMusicEnabled(newValue)
                            isMusicEnabled.value = newValue
                        },
                        id = "musicToggle",
                    )

                    if (isMusicEnabled.value) {
                        // Music volume slider
                        SettingsSlider(
                            label = translated("Music volume"),
                            value = musicVolume.value,
                            onValueChange = { newValue ->
                                AudioSettings.setMusicVolume(newValue)
                                musicVolume.value = newValue
                            },
                            disabled = !isMusicEnabled.value,
                            id = "musicVolumeSlider",
                        )
                    }

                    // Speak dialogues toggle
                    SettingsToggle(
                        label = translated("Speak dialogues"),
                        checked = isSpeakDialogueEnabled.value,
                        onCheckedChange = { newValue ->
                            AudioSettings.setSpeakDialogueEnabled(newValue)
                            isSpeakDialogueEnabled.value = newValue
                        },
                        id = "speakDialogueToggle",
                    )

                    // My worlds button or first-world CTA
                    run {
                        val hasWorlds = myWorldsCountState.value?.let { it > 0 } ?: true
                        if (hasWorlds) {
                            DialogButton(
                                onClick = { showMyWorldsDialog.value = true },
                                style = DialogButtonStyle.Primary
                            ) {
                                Translated("My Worlds")
                            }
                        } else {
                            DialogButton(
                                onClick = { showMyWorldsDialog.value = true },
                                style = DialogButtonStyle.Primary,
                                attrs = {
                                    style { marginTop(8.px) }
                                }
                            ) {
                                Text("🎉 ")
                                Translated("Claim your first world!")
                                Text(" 🎉")
                            }
                        }
                    }

                    // Media library button
                    DialogButton(
                        onClick = { showMediaLibraryDialog.value = true },
                        style = DialogButtonStyle.Secondary,
                        attrs = {
                            style { marginTop(8.px) }
                        }
                    ) {
                        Translated("Media Library")
                    }

                    // Settings button
                    DialogButton(
                        onClick = { showSettingsDialog.value = true },
                        style = DialogButtonStyle.Secondary,
                        attrs = {
                            style { marginTop(8.px) }
                        }
                    ) {
                        Translated("Settings")
                    }

                    // Sit / Stand toggle

                    var playerActivity by remember { mutableStateOf(getPlayerController()?.getPlayerActivity()) }

                    DialogButton(
                        onClick = {
                            try {
                                getPlayerController()?.toggleSitting()
                                playerActivity = getPlayerController()?.getPlayerActivity()
                                onClose()
                                console.log("PlayerDialog: Toggled sitting/standing from dialog")
                            } catch (e: Throwable) {
                                console.error("PlayerDialog: Failed to toggle sitting/standing", e)
                            }
                        },
                        style = DialogButtonStyle.Secondary,
                        attrs = {
                            style { marginTop(8.px) }
                        }
                    ) {
                        Translated(if (playerActivity == PlayerActivity.Sit) "Stand" else "Sit")
                        Text(" (Z)")
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
                                val newUser = UserApi.updateUserCustomization(updatedUser)
                                // Update local state to reflect changes immediately
                                main.components.inventory.InventoryState.currentUser = newUser
                                // Refresh the local avatar to apply customization (skin, hair, eyes, body type)
                                try {
                                    SceneManager.getPlayerController()?.refreshWornItems()
                                } catch (e: Throwable) {
                                    console.error("Failed to refresh local avatar after customization: ${e.message}")
                                }
                                // Broadcast an appearance update so other clients refresh this avatar
                                try {
                                    WebSocketApi.updatePlayer(
                                        name = newUser.username,
                                        isPlayer = newUser.isPlayer,
                                        status = newUser.status
                                    )
                                } catch (e: Throwable) {
                                    console.error("Failed to broadcast customization update: ${e.message}")
                                }
                            } catch (e: Throwable) {
                                console.error("Failed to update user customization: ${e.message}")
                            }
                        }
                        showCustomizationDialog.value = false
                    }
                )
            }

            // Render the My Worlds dialog when visible
            if (showMyWorldsDialog.value) {
                MyWorldsDialog(
                    onClose = { showMyWorldsDialog.value = false },
                    onNavigate = { path -> router.navigate(path) }
                )
            }

            // Render the Media Library dialog when visible
            if (showMediaLibraryDialog.value) {
                main.components.media.MediaLibraryDialog(
                    onClose = { showMediaLibraryDialog.value = false }
                )
            }

            // Render the Settings dialog when visible
            if (showSettingsDialog.value) {
                SettingsDialog(
                    showUI = showUI,
                    onShowUI = onShowUI,
                    onLogout = onLogout,
                    onClose = { showSettingsDialog.value = false }
                )
            }

            // Render Balance dialogs
            if (showBalanceDialog.value) {
                BalanceDialog(
                    onClose = { showBalanceDialog.value = false },
                    onViewAll = {
                        showBalanceDialog.value = false
                        showBalanceHistoryDialog.value = true
                    }
                )
            }

            if (showBalanceHistoryDialog.value) {
                BalanceHistoryDialog(
                    baseUrl = ApiConstants.API_BASE_URL,
                    bearerToken = UserApi.getToken() ?: "",
                    onClose = { showBalanceHistoryDialog.value = false }
                )
            }
        },
        actions = {
            // Close button
            DialogButton(
                onClick = updateUserStatus,
                style = DialogButtonStyle.Secondary
            ) {
                Translated("Close")
            }
        }
    )
}
