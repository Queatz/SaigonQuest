package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import main.api.WebSocketApi
import main.components.player.CharacterRenderer
import main.theme.Styles
import models.User
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Dialog for visitor users to register or login
 */
@Composable
fun VisitorDialog(
    onClose: () -> Unit,
    onRegister: (username: String, password: String, yearOfBirth: Int?, sex: String?) -> Unit,
    onLogin: (username: String, password: String) -> Unit
) {
    var isLoginMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var yearOfBirth by remember { mutableStateOf<Int?>(null) }
    var sex by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Dialog overlay
    Div(
        attrs = {
            classes(Styles.overlayBase)
            onClick { onClose() }
        }
    ) {
        // Dialog content
        Div(
            attrs = {
                classes(Styles.dialogContent)
                onClick { it.stopPropagation() }
            }
        ) {
            // Dialog header
            H3(
                attrs = {
                    classes(Styles.dialogHeader)
                }
            ) {
                Text(if (isLoginMode) "Log In" else "Create Account")
            }
            
            // Error message (if any)
            if (errorMessage.isNotEmpty()) {
                Div(
                    attrs = {
                        classes(Styles.formErrorMessage)
                    }
                ) {
                    Text(errorMessage)
                }
            }
            
            // Username input
            Div(
                attrs = {
                    classes(Styles.formFieldContainer)
                }
            ) {
                Label(
                    attrs = {
                        classes(Styles.formLabel)
                    }
                ) {
                    Text("Username")
                }
                Input(
                    type = InputType.Text,
                    attrs = {
                        classes(Styles.formInput)
                        placeholder("Enter username")
                        value(username)
                        onInput { username = it.value }
                    }
                )
            }
            
            // Password input
            Div(
                attrs = {
                    classes(Styles.formFieldContainer)
                }
            ) {
                Label(
                    attrs = {
                        classes(Styles.formLabel)
                    }
                ) {
                    Text("Password")
                }
                Input(
                    type = InputType.Password,
                    attrs = {
                        classes(Styles.formInput)
                        placeholder("Enter password")
                        value(password)
                        onInput { password = it.value }
                    }
                )
                if (!isLoginMode) {
                    Div(
                        attrs = {
                            classes(Styles.formHelpText)
                        }
                    ) {
                        Text("Password must be at least 8 characters")
                    }
                }
            }
            
            // Year of Birth dropdown (only in registration mode)
            if (!isLoginMode) {
                Div(
                    attrs = {
                        classes(Styles.formFieldContainer)
                    }
                ) {
                    Label(
                        attrs = {
                            classes(Styles.formLabel)
                        }
                    ) {
                        Text("Year of Birth")
                    }
                    
                    // Create a select element for year of birth
                    org.jetbrains.compose.web.dom.Select(
                        attrs = {
                            classes(Styles.formInput)
                            onChange { event ->
                                val selectedValue = event.target.value
                                yearOfBirth = if (selectedValue.isNotEmpty()) {
                                    selectedValue.toInt()
                                } else {
                                    null
                                }
                            }
                        }
                    ) {
                        // Add a default empty option
                        org.jetbrains.compose.web.dom.Option(
                            value = "",
                            attrs = {
                                if (yearOfBirth == null) {
                                    attr("selected", "selected")
                                }
                            }
                        ) {
                            Text("Select year")
                        }
                        
                        // Add options for years (from current year - 100 to current year)
                        val currentYear = 2025 // Hardcoded current year
                        for (year in currentYear - 100..currentYear) {
                            org.jetbrains.compose.web.dom.Option(
                                value = year.toString(),
                                attrs = {
                                    if (yearOfBirth == year) {
                                        attr("selected", "selected")
                                    }
                                }
                            ) {
                                Text(year.toString())
                            }
                        }
                    }
                }
                
                // Sex selection (only in registration mode)
                Div(
                    attrs = {
                        classes(Styles.formFieldContainer)
                    }
                ) {
                    Label(
                        attrs = {
                            classes(Styles.formLabel)
                        }
                    ) {
                        Text("Sex")
                    }
                    
                    // Radio buttons for Boy/Girl
                    Div(
                        attrs = {
                            classes(Styles.radioGroupContainer)
                        }
                    ) {
                        // Boy option
                        Div(
                            attrs = {
                                classes(Styles.radioOptionContainer)
                            }
                        ) {
                            Input(
                                type = InputType.Radio,
                                attrs = {
                                    attr("name", "sex")
                                    value("Boy")
                                    if (sex == "Boy") {
                                        checked(true)
                                    }
                                    onChange {
                                        if (it.value) {
                                            sex = "Boy"
                                        }
                                    }
                                }
                            )
                            Label(
                                attrs = {
                                    classes(Styles.radioOptionLabel)
                                }
                            ) {
                                Text("Boy")
                            }
                        }
                        
                        // Girl option
                        Div(
                            attrs = {
                                classes(Styles.radioOptionContainer)
                            }
                        ) {
                            Input(
                                type = InputType.Radio,
                                attrs = {
                                    attr("name", "sex")
                                    value("Girl")
                                    if (sex == "Girl") {
                                        checked(true)
                                    }
                                    onChange {
                                        if (it.value) {
                                            sex = "Girl"
                                        }
                                    }
                                }
                            )
                            Label(
                                attrs = {
                                    classes(Styles.radioOptionLabel)
                                }
                            ) {
                                Text("Girl")
                            }
                        }
                    }
                }
            }
            
            // Action buttons
            Div(
                attrs = {
                    classes(Styles.formButtonsContainer)
                }
            ) {
                // Primary action button
                Button(
                    attrs = {
                        classes(Styles.primaryButton)
                        onClick {
                            errorMessage = ""
                            
                            if (username.isBlank()) {
                                errorMessage = "Username cannot be empty"
                                return@onClick
                            }
                            
                            if (password.isBlank()) {
                                errorMessage = "Password cannot be empty"
                                return@onClick
                            }
                            
                            if (!isLoginMode && password.length < 8) {
                                errorMessage = "Password must be at least 8 characters"
                                return@onClick
                            }
                            
                            if (!isLoginMode && yearOfBirth == null) {
                                errorMessage = "Year of birth is required"
                                return@onClick
                            }
                            
                            if (!isLoginMode && sex == null) {
                                errorMessage = "Sex is required"
                                return@onClick
                            }
                            
                            if (isLoginMode) {
                                onLogin(username, password)
                            } else {
                                onRegister(username, password, yearOfBirth, sex)
                            }
                        }
                    }
                ) {
                    Text(if (isLoginMode) "Log In" else "Create Account")
                }
                
                // Toggle mode button
                Button(
                    attrs = {
                        classes(Styles.secondaryButton)
                        onClick {
                            isLoginMode = !isLoginMode
                            errorMessage = ""
                        }
                    }
                ) {
                    Text(if (isLoginMode) "Create Account" else "Log In")
                }
            }
        }
    }
}

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
    
    // Coroutine scope for async operations that persists beyond dialog closure
    val coroutineScope = remember { MainScope() }
    
    // Function to update user status
    val updateUserStatus = {
        console.log("[DEBUG_LOG] PlayerDialog: updateUserStatus called")
        console.log("[DEBUG_LOG] PlayerDialog: Current status: '${user.status}', New status: '${status.value}'")
        
        // Check WebSocket connection state
        val isConnected = WebSocketApi.isConnected()
        console.log("[DEBUG_LOG] PlayerDialog: WebSocket connection state: ${if (isConnected) "CONNECTED" else "DISCONNECTED"}")
        
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
                    val isStillConnected = WebSocketApi.isConnected()
                    console.log("[DEBUG_LOG] PlayerDialog: WebSocket connection state before update: ${if (isStillConnected) "CONNECTED" else "DISCONNECTED"}")
                    
                    // Get current world ID
                    val worldId = WebSocketApi.getCurrentWorldId()
                    console.log("[DEBUG_LOG] PlayerDialog: Current world ID: ${worldId ?: "null"}")
                    
                    console.log("[DEBUG_LOG] PlayerDialog: Calling WebSocketApi.updatePlayerStatus with status: '$newStatus'")
                    val success = WebSocketApi.updatePlayerStatus(newStatus)
                    if (success) {
                        console.log("[DEBUG_LOG] PlayerDialog: Player status updated successfully: '$newStatus'")
                    } else {
                        console.error("[DEBUG_LOG] PlayerDialog: Failed to update player status")
                        console.error("[DEBUG_LOG] PlayerDialog: Last WebSocket error: ${WebSocketApi.getLastError() ?: "None"}")
                    }
                } catch (e: Exception) {
                    console.error("[DEBUG_LOG] PlayerDialog: Error updating player status", e)
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
        title = "My Profile",
        content = {
            // Character renderer - add this above the user info
            Div(
                attrs = {
                    style {
                        marginBottom(16.px)
                        display(DisplayStyle.Flex)
                        alignItems(AlignItems.Center)
                        justifyContent(JustifyContent.Center)
                    }
                }
            ) {
                // Add the character renderer component
                CharacterRenderer()
            }
            
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
                        Text("Status")
                    }
                    
                    // Status input field
                    Input(
                        type = InputType.Text,
                        attrs = {
                            classes(Styles.inputField)
                            placeholder("Set your status (displayed above your character)")
                            value(status.value)
                            onInput { event -> status.value = event.value }
                        }
                    )
                }
                
                // Player status
                Div(
                    attrs = {
                        classes(Styles.userStatusText)
                    }
                ) {
                    Text("Player Account")
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
                    Text("Language")
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
                        }
                    )
                }
            }
            
            // Low graphics mode toggle
            GraphicsToggle(
                label = "Low Graphics Mode",
                checked = isLowGraphicsMode.value,
                onCheckedChange = { newValue ->
                    // Toggle low graphics mode
                    isLowGraphicsMode.value = GraphicsSettings.toggleLowGraphicsMode()
                },
                id = "lowGraphicsToggle"
            )
            
            // Pretty graphics toggle
            GraphicsToggle(
                label = "Pretty Graphics",
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
                Text("Log Out")
            }
        },
        actions = {
            // Close button
            DialogButton(
                onClick = updateUserStatus,
                isPrimary = false
            ) {
                Text("Close")
            }
        }
    )
}
