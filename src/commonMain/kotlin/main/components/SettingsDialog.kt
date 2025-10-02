package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import main.theme.Styles
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.dom.Button

@Composable
fun SettingsDialog(
    showUI: Boolean,
    onShowUI: (showUI: Boolean) -> Unit,
    onClose: () -> Unit
) {
    val showChangePassword = remember { mutableStateOf(false) }
    val wideAngle = remember { mutableStateOf(GraphicsSettings.isWideAngleEnabled()) }
    val observerMode = remember { mutableStateOf(GraphicsSettings.isObserverModeEnabled()) }

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
                Translated("Wide angle: ${if (wideAngle.value) translated("On") else translated("Off")}")
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
                Translated("Observer mode: ${if (observerMode.value) translated("On") else translated("Off")}")
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

