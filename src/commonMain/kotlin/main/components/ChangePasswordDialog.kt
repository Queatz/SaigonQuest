package main.components

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import main.api.UserApi
import main.theme.Styles
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

@Composable
fun ChangePasswordDialog(
    onClose: () -> Unit
) {
    val current = remember { mutableStateOf("") }
    val next = remember { mutableStateOf("") }
    val repeat = remember { mutableStateOf("") }
    val error = remember { mutableStateOf<String?>(null) }
    val saving = remember { mutableStateOf(false) }

    val scope = remember { MainScope() }

    fun submit() {
        error.value = null
        if (next.value != repeat.value) {
            error.value = str("Passwords do not match")
            return
        }
        if (next.value.length < 8) {
            error.value = str("Password must be at least 8 characters")
            return
        }

        saving.value = true
        scope.launch {
            try {
                val authUser = UserApi.changePassword(current.value, next.value)
                // optional: notify success
                onClose()
            } catch (t: Throwable) {
                // Surface API message if possible and map to translations
                val msg = t.message ?: ""
                error.value = when {
                    msg.contains("incorrect", ignoreCase = true) -> str("Current password is incorrect")
                    msg.contains("least 8", ignoreCase = true) -> str("Password must be at least 8 characters")
                    else -> str("An error occurred")
                }
            } finally {
                saving.value = false
            }
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("Change Password"),
        content = {
            if (error.value != null) {
                P(attrs = { classes(Styles.formErrorMessage) }) { Text(error.value!!) }
            }

            // Current password
            Div(attrs = { classes(Styles.formFieldContainer) }) {
                Input(
                    type = InputType.Password,
                    attrs = {
                        classes(Styles.inputField)
                        placeholder(str("Current Password"))
                        value(current.value)
                        onInput { ev -> current.value = ev.value }
                    }
                )
            }

            // New password
            Div(attrs = { classes(Styles.formFieldContainer) }) {
                Input(
                    type = InputType.Password,
                    attrs = {
                        classes(Styles.inputField)
                        placeholder(str("New Password"))
                        value(next.value)
                        onInput { ev -> next.value = ev.value }
                    }
                )
            }

            // Repeat new password
            Div(attrs = { classes(Styles.formFieldContainer) }) {
                Input(
                    type = InputType.Password,
                    attrs = {
                        classes(Styles.inputField)
                        placeholder(str("Repeat New Password"))
                        value(repeat.value)
                        onInput { ev -> repeat.value = ev.value }
                    }
                )
            }
        },
        actions = {
            DialogButton(onClick = onClose, style = DialogButtonStyle.Secondary) { Translated("Cancel") }
            DialogButton(onClick = { if (!saving.value) submit() }, style = DialogButtonStyle.Primary) {
                Text(if (saving.value) translated("Loading...") else translated("Save"))
            }
        }
    )
}
