package main.components

import androidx.compose.runtime.*
import models.User
import main.theme.Theme
import main.theme.Styles
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import main.components.GraphicsSettings
import main.components.LanguageSettings

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
                        style {
                            color(Color.red)
                            marginBottom(16.px)
                            fontSize(14.px)
                            property("text-align", "center")
                        }
                    }
                ) {
                    Text(errorMessage)
                }
            }
            
            // Username input
            Div(
                attrs = {
                    style {
                        marginBottom(16.px)
                    }
                }
            ) {
                Label(
                    attrs = {
                        style {
                            display(DisplayStyle.Block)
                            marginBottom(4.px)
                            fontSize(14.px)
                            color(Theme.colors.onBackground)
                        }
                    }
                ) {
                    Text("Username")
                }
                Input(
                    type = InputType.Text,
                    attrs = {
                        style {
                            width(100.percent)
                            padding(8.px)
                            property("border-radius", "4px")
                            property("border", "1px solid ${Theme.colors.outline}")
                            property("box-sizing", "border-box")
                        }
                        placeholder("Enter username")
                        value(username)
                        onInput { username = it.value }
                    }
                )
            }
            
            // Password input
            Div(
                attrs = {
                    style {
                        marginBottom(16.px)
                    }
                }
            ) {
                Label(
                    attrs = {
                        style {
                            display(DisplayStyle.Block)
                            marginBottom(4.px)
                            fontSize(14.px)
                            color(Theme.colors.onBackground)
                        }
                    }
                ) {
                    Text("Password")
                }
                Input(
                    type = InputType.Password,
                    attrs = {
                        style {
                            width(100.percent)
                            padding(8.px)
                            property("border-radius", "4px")
                            property("border", "1px solid ${Theme.colors.outline}")
                            property("box-sizing", "border-box")
                        }
                        placeholder("Enter password")
                        value(password)
                        onInput { password = it.value }
                    }
                )
                if (!isLoginMode) {
                    Div(
                        attrs = {
                            style {
                                fontSize(12.px)
                                color(Theme.colors.onSurfaceVariant)
                                marginTop(4.px)
                            }
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
                        style {
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Label(
                        attrs = {
                            style {
                                display(DisplayStyle.Block)
                                marginBottom(4.px)
                                fontSize(14.px)
                                color(Theme.colors.onBackground)
                            }
                        }
                    ) {
                        Text("Year of Birth")
                    }
                    
                    // Create a select element for year of birth
                    org.jetbrains.compose.web.dom.Select(
                        attrs = {
                            style {
                                width(100.percent)
                                padding(8.px)
                                property("border-radius", "4px")
                                property("border", "1px solid ${Theme.colors.outline}")
                                property("box-sizing", "border-box")
                            }
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
                        style {
                            marginBottom(16.px)
                        }
                    }
                ) {
                    Label(
                        attrs = {
                            style {
                                display(DisplayStyle.Block)
                                marginBottom(4.px)
                                fontSize(14.px)
                                color(Theme.colors.onBackground)
                            }
                        }
                    ) {
                        Text("Sex")
                    }
                    
                    // Radio buttons for Boy/Girl
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                property("gap", "16px")
                            }
                        }
                    ) {
                        // Boy option
                        Div(
                            attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    alignItems(AlignItems.Center)
                                }
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
                                    style {
                                        marginLeft(4.px)
                                        fontSize(14.px)
                                        color(Theme.colors.onBackground)
                                    }
                                }
                            ) {
                                Text("Boy")
                            }
                        }
                        
                        // Girl option
                        Div(
                            attrs = {
                                style {
                                    display(DisplayStyle.Flex)
                                    alignItems(AlignItems.Center)
                                }
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
                                    style {
                                        marginLeft(4.px)
                                        fontSize(14.px)
                                        color(Theme.colors.onBackground)
                                    }
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
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.SpaceBetween)
                        marginTop(24.px)
                    }
                }
            ) {
                // Primary action button
                Button(
                    attrs = {
                        style {
                            padding(8.px, 16.px)
                            backgroundColor(Theme.colors.primary)
                            color(Theme.colors.onPrimary)
                            property("border", "0")
                            property("border-radius", "4px")
                            property("cursor", "pointer")
                        }
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
                        style {
                            padding(8.px, 16.px)
                            backgroundColor(Color.transparent)
                            color(Theme.colors.primary)
                            property("border", "0")
                            property("cursor", "pointer")
                        }
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
    
    // Use the BaseDialog component
    BaseDialog(
        onClose = onClose,
        title = "My Profile",
        content = {
            // User info
            Div(
                attrs = {
                    style {
                        marginBottom(24.px)
                        property("text-align", "center")
                    }
                }
            ) {
                // Username
                Div(
                    attrs = {
                        style {
                            fontSize(18.px)
                            property("font-weight", "bold")
                            color(Theme.colors.onBackground)
                            marginBottom(8.px)
                        }
                    }
                ) {
                    Text(user.username)
                }
                
                // Player status
                Div(
                    attrs = {
                        style {
                            fontSize(14.px)
                            color(Theme.colors.onSurfaceVariant)
                        }
                    }
                ) {
                    Text("Player Account")
                }
            }
            
            // Language switcher
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.SpaceBetween)
                        alignItems(AlignItems.Center)
                        padding(8.px)
                        marginBottom(16.px)
                        backgroundColor(Theme.colors.surfaceVariant)
                        property("border-radius", "4px")
                    }
                }
            ) {
                // Label
                Span(
                    attrs = {
                        style {
                            padding(8.px)
                            color(Theme.colors.onSurfaceVariant)
                        }
                    }
                ) {
                    Text("Language")
                }
                
                // Language switcher component
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            alignItems(AlignItems.Center)
                            padding(8.px)
                        }
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
                    style {
                        width(100.percent)
                        padding(8.px, 16.px)
                        backgroundColor(Theme.colors.secondary)
                        color(Theme.colors.onPrimary)
                        property("border", "0")
                        property("border-radius", "4px")
                        property("cursor", "pointer")
                        marginTop(16.px)
                    }
                    onClick { onLogout() }
                }
            ) {
                Text("Log Out")
            }
        },
        actions = {
            // Close button
            DialogButton(
                onClick = onClose,
                isPrimary = false
            ) {
                Text("Close")
            }
        }
    )
}
