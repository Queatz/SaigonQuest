package main.components

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.theme.Styles
import models.Sex
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.RadioInput
import org.jetbrains.compose.web.dom.Text

/**
 * Dialog for visitor users to register or login
 */
@Composable
fun VisitorDialog(
    onClose: () -> Unit,
    onRegister: (username: String, password: String, yearOfBirth: Int?, sex: Sex?) -> Unit,
    onLogin: (username: String, password: String) -> Unit
) {
    var isLoginMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var yearOfBirth by remember { mutableStateOf<Int?>(null) }
    var sex by remember { mutableStateOf<Sex?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Validation function
    fun validateAndSubmit() {
        errorMessage = ""
        
        when {
            username.isBlank() -> {
                errorMessage = str("Username cannot be empty")
                return
            }
            
            password.isBlank() -> {
                errorMessage = str("Password cannot be empty")
                return
            }

            !isLoginMode && password.length < 8 -> {
                errorMessage = str("Password must be at least 8 characters")
                return
            }

            // Confirm password must match during registration
            !isLoginMode && confirmPassword != password -> {
                errorMessage = str("Passwords do not match")
                return
            }
            
            !isLoginMode && yearOfBirth == null -> {
                errorMessage = str("Year of birth is required")
                return
            }
            
            !isLoginMode && sex == null -> {
                errorMessage = str("Sex is required")
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
        title = if (isLoginMode) translated("Log In") else translated("Create Account"),
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
                    Translated("Username")
                }
                Input(
                    type = InputType.Text,
                    attrs = {
                        classes(Styles.inputField)
                        placeholder(str("Enter username"))
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
                    Translated("Password")
                }
                Input(
                    type = InputType.Password,
                    attrs = {
                        classes(Styles.inputField)
                        placeholder(str("Enter password"))
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
                        Translated("Password must be at least 8 characters")
                    }
                }
            }

            // Confirm Password input (only in registration mode)
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
                        Translated("Confirm Password")
                    }
                    Input(
                        type = InputType.Password,
                        attrs = {
                            classes(Styles.inputField)
                            placeholder(str("Re-enter password"))
                            value(confirmPassword)
                            onInput { confirmPassword = it.value }
                        }
                    )
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
                        Translated("Year of Birth")
                    }
                    
                    // Create a select element for year of birth
                    org.jetbrains.compose.web.dom.Select(
                        attrs = {
                            classes(Styles.inputField)
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
                            Translated("Select year")
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
                        Translated("Sex")
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
                            RadioInput(
                                checked = sex == Sex.Boy,
                                attrs = {
                                    attr("name", "sex")
                                    value(Sex.Boy.name)
                                    onChange {
                                        if (it.value) {
                                            sex = Sex.Boy
                                        }
                                    }
                                }
                            )
                            Label(
                                attrs = {
                                    classes(Styles.radioOptionLabel)
                                }
                            ) {
                                Translated("Boy")
                            }
                        }
                        
                        // Girl option
                        Div(
                            attrs = {
                                classes(Styles.radioOptionContainer)
                            }
                        ) {
                            RadioInput(
                                checked = sex == Sex.Girl,
                                attrs = {
                                    attr("name", "sex")
                                    value(Sex.Girl.name)
                                    onChange {
                                        if (it.value) {
                                            sex = Sex.Girl
                                        }
                                    }
                                }
                            )
                            Label(
                                attrs = {
                                    classes(Styles.radioOptionLabel)
                                }
                            ) {
                                Translated("Girl")
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
                    Translated(if (isLoginMode) "Log In" else "Create Account")
                }
                
                // Toggle mode button
                DialogButton(
                    onClick = {
                        isLoginMode = !isLoginMode
                        errorMessage = ""
                        // Reset sensitive fields when switching modes
                        password = ""
                        confirmPassword = ""
                    }
                ) {
                    Translated(if (isLoginMode) "Create Account" else "Log In")
                }
            }
        }
    )
}
