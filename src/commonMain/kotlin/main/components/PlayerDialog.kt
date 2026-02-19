package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.softwork.routingcompose.Router
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WorldApi
import main.api.updatePlayer
import main.api.updatePlayerStatus
import main.components.player.CharacterRenderer
import main.theme.Styles
import main.components.AudioSettings
import models.User
import models.UserBalance
import main.httpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import main.components.common.formatAmount
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import main.api.ApiConstants
import main.components.SceneManager.getPlayerController
import main.components.world.PlayerActivity
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.P
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
    // State to control Visited Worlds dialog visibility
    val showVisitedWorldsDialog = remember { mutableStateOf(false) }
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
                            Translated("Customize character")
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

                    // Health, mind, spirit bars
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
                            Translated("Health")
                        }

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
                    Button(
                        attrs = {
                            classes(Styles.primaryButton)
                            onClick { showBalanceDialog.value = true }
                        }
                    ) {
                        Translated("Deposit / Withdraw")
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

                    // My worlds button or first-world CTA
                    run {
                        val hasWorlds = myWorldsCountState.value?.let { it > 0 } ?: true
                        if (hasWorlds) {
                            Button(
                                attrs = {
                                    classes(Styles.primaryButton)
                                    onClick { showMyWorldsDialog.value = true }
                                }
                            ) {
                                Translated("My Worlds")
                            }
                        } else {
                            Button(
                                attrs = {
                                    classes(Styles.primaryButton)
                                    style { marginTop(8.px) }
                                    onClick { showMyWorldsDialog.value = true }
                                }
                            ) {
                                Text("🎉 ")
                                Translated("Claim your first world!")
                                Text(" 🎉")
                            }
                        }
                    }

                    // Visited worlds button
                    Button(
                        attrs = {
                            classes(Styles.secondaryButton)
                            style { marginTop(8.px) }
                            onClick { showVisitedWorldsDialog.value = true }
                        }
                    ) {
                        Translated("Visited Worlds")
                    }

                    // Media library button
                    Button(
                        attrs = {
                            classes(Styles.secondaryButton)
                            style { marginTop(8.px) }
                            onClick { showMediaLibraryDialog.value = true }
                        }
                    ) {
                        Translated("Media Library")
                    }


                    // Settings button
                    Button(
                        attrs = {
                            classes(Styles.secondaryButton)
                            style { marginTop(8.px) }
                            onClick { showSettingsDialog.value = true }
                        }
                    ) {
                        Translated("Settings")
                    }

                    // Sit / Stand toggle

                    var playerActivity by remember { mutableStateOf(getPlayerController()?.getPlayerActivity()) }

                    Button(
                        attrs = {
                            classes(Styles.secondaryButton)
                            style { marginTop(8.px) }
                            onClick {
                                try {
                                    getPlayerController()?.toggleSitting()
                                    playerActivity = getPlayerController()?.getPlayerActivity()
                                    onClose()
                                    console.log("PlayerDialog: Toggled sitting/standing from dialog")
                                } catch (e: Throwable) {
                                    console.error("PlayerDialog: Failed to toggle sitting/standing", e)
                                }
                            }
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

            // Render the Visited Worlds dialog when visible
            if (showVisitedWorldsDialog.value) {
                VisitedWorldsDialog(
                    onClose = { showVisitedWorldsDialog.value = false },
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
                    baseUrl = main.api.ApiConstants.API_BASE_URL,
                    bearerToken = main.api.UserApi.getToken() ?: "",
                    onClose = { showBalanceHistoryDialog.value = false }
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
