package main.components

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import main.api.UserApi
import main.theme.Styles
import models.Sex
import models.isValidUsername
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.selected
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Div
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
    onLogin: (username: String, password: String, onError: (String) -> Unit) -> Unit
) {
    var isLoginMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var yearOfBirth by remember { mutableStateOf<Int?>(null) }
    var sex by remember { mutableStateOf<Sex?>(null) }
    var errorMessage by remember { mutableStateOf("") }
    var currentLanguage by remember { mutableStateOf(LanguageSettings.getCurrentLanguage()) }
    val coroutineScope = remember { MainScope() }
    
    // Validation function
    fun validateAndSubmit() {
        errorMessage = ""
        
        when {
            username.isBlank() -> {
                errorMessage = str("Username cannot be empty")
                return
            }

            !isLoginMode && !isValidUsername(username) -> {
                errorMessage = str("Username must not contain spaces or symbols")
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
                    onLogin(username, password) { msg ->
                        errorMessage = if (msg.isNotBlank()) msg else str("Log in failed")
                    }
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
                            currentLanguage = language
                            
                            // Update user language in the database
                            coroutineScope.launch {
                                try {
                                    UserApi.updateUserLanguage(language)
                                    console.log("[DEBUG_LOG] VisitorDialog: User language updated to: $language")
                                } catch (e: Throwable) {
                                    console.error("[DEBUG_LOG] VisitorDialog: Error updating user language", e)
                                }
                            }
                        }
                    )
                }
            }

            // Error message (if any)
            if (errorMessage.isNotEmpty()) {
                Div(
                    attrs = {
                        classes(Styles.formErrorBanner)
                    }
                ) {
                    Text(errorMessage)
                }
            }
            
            // Username input
            DialogInput(
                label = "Username",
                value = username,
                onInput = { username = it },
                placeholder = str("Enter username"),
                attrs = {
                    onKeyUp { event ->
                        if (event.key == "Enter") {
                            validateAndSubmit()
                        }
                    }
                }
            )
            
            // Password input
            DialogInput(
                label = "Password",
                value = password,
                onInput = { password = it },
                type = InputType.Password,
                placeholder = str("Enter password"),
                attrs = {
                    onKeyUp { event ->
                        if (event.key == "Enter") {
                            validateAndSubmit()
                        }
                    }
                },
                content = {
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
            )

            // Confirm Password input (only in registration mode)
            if (!isLoginMode) {
                DialogInput(
                    label = "Confirm Password",
                    value = confirmPassword,
                    onInput = { confirmPassword = it },
                    type = InputType.Password,
                    placeholder = str("Re-enter password"),
                )
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
                                    selected()
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
                                        selected()
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
                    style = DialogButtonStyle.Primary
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
                    },
                    style = DialogButtonStyle.Secondary
                ) {
                    Translated(if (isLoginMode) "Create Account" else "Log In")
                }
            }
        }
    )
}
