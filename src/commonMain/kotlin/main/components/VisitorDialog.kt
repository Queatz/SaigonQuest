package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.theme.Styles
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.style
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
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
    
    // Validation function
    fun validateAndSubmit() {
        errorMessage = ""
        
        when {
            username.isBlank() -> {
                errorMessage = "Username cannot be empty"
                return
            }
            
            password.isBlank() -> {
                errorMessage = "Password cannot be empty"
                return
            }
            
            !isLoginMode && password.length < 8 -> {
                errorMessage = "Password must be at least 8 characters"
                return
            }
            
            !isLoginMode && yearOfBirth == null -> {
                errorMessage = "Year of birth is required"
                return
            }
            
            !isLoginMode && sex == null -> {
                errorMessage = "Sex is required"
                return
            }
            
            else -> {
                if (isLoginMode) {
                    onLogin(username, password)
                } else {
                    onRegister(username, password, yearOfBirth, sex)
                }
            }
        }
    }
    
    BaseDialog(
        onClose = onClose,
        title = if (isLoginMode) "Log In" else "Create Account",
        content = {
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
        },
        actions = {
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        gap(8.px)
                    }
                }
            ) {
                // Primary action button
                DialogButton(
                    onClick = { validateAndSubmit() },
                    isPrimary = true
                ) {
                    Text(if (isLoginMode) "Log In" else "Create Account")
                }
                
                // Toggle mode button
                DialogButton(
                    onClick = {
                        isLoginMode = !isLoginMode
                        errorMessage = ""
                    }
                ) {
                    Text(if (isLoginMode) "Create Account" else "Log In")
                }
            }
        }
    )
}

