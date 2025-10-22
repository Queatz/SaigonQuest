package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import main.theme.Styles
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Text

@Composable
fun SettingsDialog(
    showUI: Boolean,
    onShowUI: (showUI: Boolean) -> Unit,
    onClose: () -> Unit
) {
    val showChangePassword = remember { mutableStateOf(false) }
    val wideAngle = remember { mutableStateOf(GraphicsSettings.isWideAngleEnabled()) }
    val observerMode = remember { mutableStateOf(GraphicsSettings.isObserverModeEnabled()) }
    val alwaysShowQr = remember { mutableStateOf(GraphicsSettings.isAlwaysShowQrEnabled()) }

    BaseDialog(
        onClose = onClose,
        title = translated("Settings"),
        content = {
            // Hide UI button

            Button(
                attrs = {
                    classes(Styles.secondaryButton)
                    onClick {
                        onShowUI(!showUI)
                        onClose()
                    }
                }
            ) {
                if (!showUI) Translated("Show UI") else Translated("Hide UI")
            }

            // Wide Angle
            Button(
                attrs = {
                    classes(Styles.secondaryButton)
                    style {
                        marginTop(16.px)
                    }
                    onClick {
                        wideAngle.value = !wideAngle.value
                        GraphicsSettings.setWideAngle(wideAngle.value)
                    }
                }
            ) {
                Text("${translated("Wide angle")}: ${if (wideAngle.value) translated("On") else translated("Off")}")
            }

            // Observer Mode
            Button(
                attrs = {
                    classes(Styles.secondaryButton)
                    style {
                        marginTop(16.px)
                    }
                    onClick {
                        observerMode.value = !observerMode.value
                        GraphicsSettings.setObserverMode(observerMode.value)
                    }
                }
            ) {
                Text("${translated("Observer mode")}: ${if (observerMode.value) translated("On") else translated("Off")}")
            }

            // Always show QR code
            Button(
                attrs = {
                    classes(Styles.secondaryButton)
                    style { marginTop(16.px) }
                    onClick {
                        alwaysShowQr.value = !alwaysShowQr.value
                        GraphicsSettings.setAlwaysShowQr(alwaysShowQr.value)
                    }
                }
            ) {
                Text("${translated("Always show QR code")}: ${if (alwaysShowQr.value) translated("On") else translated("Off")}")
            }

            // Change Password
            Button(
                attrs = {
                    classes(Styles.secondaryButton)
                    style {
                        marginTop(16.px)
                    }
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
