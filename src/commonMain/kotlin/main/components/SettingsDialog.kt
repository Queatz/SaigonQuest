package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import main.theme.Styles
import org.jetbrains.compose.web.dom.Button

@Composable
fun SettingsDialog(
    onClose: () -> Unit
) {
    val showChangePassword = remember { mutableStateOf(false) }

    BaseDialog(
        onClose = onClose,
        title = translated("Settings"),
        content = {
            // Change Password
            Button(
                attrs = {
                    classes(Styles.secondaryButton)
                    onClick { showChangePassword.value = true }
                }
            ) {
                Translated("Change Password")
            }

            if (showChangePassword.value) {
                ChangePasswordDialog(
                    onClose = { showChangePassword.value = false }
                )
            }
        },
        actions = {
            DialogButton(onClick = onClose, isPrimary = false) {
                Translated("Close")
            }
        }
    )
}

