package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import main.theme.Styles
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Text

@Composable
fun SettingsDialog(
    showUI: Boolean,
    onShowUI: (showUI: Boolean) -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit,
) {
    val showChangePassword = remember { mutableStateOf(false) }
    val wideAngle = remember { mutableStateOf(GraphicsSettings.isWideAngleEnabled()) }
    val observerMode = remember { mutableStateOf(GraphicsSettings.isObserverModeEnabled()) }
    val alwaysShowQr = remember { mutableStateOf(GraphicsSettings.isAlwaysShowQrEnabled()) }
    val showLogoutDialog = remember { mutableStateOf(false) }

    BaseDialog(
        onClose = onClose,
        title = translated("Settings"),
        content = {
            // Hide UI button

            DialogButton(
                onClick = {
                    onShowUI(!showUI)
                    onClose()
                },
                style = DialogButtonStyle.Primary,
            ) {
                if (!showUI) Translated("Show UI") else Translated("Hide UI")
            }

            // Wide Angle
            DialogButton(
                onClick = {
                    wideAngle.value = !wideAngle.value
                    GraphicsSettings.setWideAngle(wideAngle.value)
                },
                style = DialogButtonStyle.Primary,
                attrs = {
                    style {
                        marginTop(16.px)
                    }
                }
            ) {
                Text("${translated("Wide angle")}: ${if (wideAngle.value) translated("On") else translated("Off")}")
            }

            // Observer Mode
            DialogButton(
                onClick = {
                    observerMode.value = !observerMode.value
                    GraphicsSettings.setObserverMode(observerMode.value)
                },
                style = DialogButtonStyle.Primary,
                attrs = {
                    style {
                        marginTop(16.px)
                    }
                }
            ) {
                Text("${translated("Observer mode")}: ${if (observerMode.value) translated("On") else translated("Off")}")
            }

            // Always show QR code
            DialogButton(
                onClick = {
                    alwaysShowQr.value = !alwaysShowQr.value
                    GraphicsSettings.setAlwaysShowQr(alwaysShowQr.value)
                },
                style = DialogButtonStyle.Primary,
                attrs = {
                    style { marginTop(16.px) }
                }
            ) {
                Text("${translated("Always show QR code")}: ${if (alwaysShowQr.value) translated("On") else translated("Off")}")
            }

            // Change Password
            DialogButton(
                onClick = { showChangePassword.value = true },
                style = DialogButtonStyle.Primary,
                attrs = {
                    style {
                        marginTop(16.px)
                    }
                }
            ) {
                Translated("Change Password")
            }

            // Logout button
            DialogButton(
                onClick = {
                    showLogoutDialog.value = true
                },
                style = DialogButtonStyle.Alt,
                attrs = {
                    style {
                        marginTop(16.px)
                    }
                }
            ) {
                Translated("Log Out")
            }

            if (showChangePassword.value) {
                ChangePasswordDialog(
                    onClose = { showChangePassword.value = false }
                )
            }

            if (showLogoutDialog.value) {
                BasicConfirmDialog(
                    title = translated("Log Out"),
                    message = translated("Are you sure you want to log out?"),
                    onCancel = { showLogoutDialog.value = false },
                    onConfirm = {
                        showLogoutDialog.value = false
                        onLogout()
                    }
                )
            }
        },
        actions = {
            DialogButton(onClick = onClose, style = DialogButtonStyle.Secondary) {
                Translated("Close")
            }
        }
    )
}
