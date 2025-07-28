package main.components

import androidx.compose.runtime.*
import models.User
import main.theme.Theme
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder

/**
 * Dialog for visitor users to register or login
 */
@Composable
fun VisitorDialog(
    onClose: () -> Unit,
    onRegister: (username: String, password: String) -> Unit,
    onLogin: (username: String, password: String) -> Unit
) {
    var isLoginMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    
    // Dialog overlay
    Div(
        attrs = {
            style {
                position(Position.Fixed)
                top(0.px)
                left(0.px)
                width(100.percent)
                height(100.percent)
                backgroundColor(rgba(0, 0, 0, 0.5))
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.Center)
                alignItems(AlignItems.Center)
                property("z-index", "1000")
                // Make dialog interactive
                property("pointer-events", "auto")
            }
            onClick { onClose() }
        }
    ) {
        // Dialog content
        Div(
            attrs = {
                style {
                    width(320.px)
                    backgroundColor(Theme.colors.background)
                    property("border-radius", "8px")
                    padding(16.px)
                    property("box-shadow", "0 4px 8px rgba(0, 0, 0, 0.2)")
                }
                onClick { it.stopPropagation() }
            }
        ) {
            // Dialog header
            H3(
                attrs = {
                    style {
                        margin(0.px)
                        marginBottom(16.px)
                        color(Theme.colors.primary)
                        property("text-align", "center")
                    }
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
                                onRegister(username, password)
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
    // Dialog overlay
    Div(
        attrs = {
            style {
                position(Position.Fixed)
                top(0.px)
                left(0.px)
                width(100.percent)
                height(100.percent)
                backgroundColor(rgba(0, 0, 0, 0.5))
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.Center)
                alignItems(AlignItems.Center)
                property("z-index", "1000")
                // Make dialog interactive
                property("pointer-events", "auto")
            }
            onClick { onClose() }
        }
    ) {
        // Dialog content
        Div(
            attrs = {
                style {
                    width(320.px)
                    backgroundColor(Theme.colors.background)
                    property("border-radius", "8px")
                    padding(16.px)
                    property("box-shadow", "0 4px 8px rgba(0, 0, 0, 0.2)")
                }
                onClick { it.stopPropagation() }
            }
        ) {
            // Dialog header
            H3(
                attrs = {
                    style {
                        margin(0.px)
                        marginBottom(16.px)
                        color(Theme.colors.primary)
                        property("text-align", "center")
                    }
                }
            ) {
                Text("My Profile")
            }
            
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
                    }
                    onClick { onLogout() }
                }
            ) {
                Text("Log Out")
            }
            
            // Close button
            Button(
                attrs = {
                    style {
                        width(100.percent)
                        padding(8.px, 16.px)
                        backgroundColor(Color.transparent)
                        color(Theme.colors.primary)
                        property("border", "0")
                        marginTop(8.px)
                        property("cursor", "pointer")
                    }
                    onClick { onClose() }
                }
            ) {
                Text("Close")
            }
        }
    }
}
