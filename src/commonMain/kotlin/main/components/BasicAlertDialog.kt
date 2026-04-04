package main.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Text

/**
 * A basic confirmation dialog with Yes and No buttons.
 *
 * @param message The message to display in the dialog
 * @param onConfirm Callback when the user confirms (clicks Yes)
 * @param onClose Callback when the user closes the dialog
 * @param title The title of the dialog (optional)
 */
@Composable
fun BasicAlertDialog(
    message: String,
    onClose: () -> Unit,
    onConfirm: () -> Unit = onClose,
    title: String? = null,
    headerActions: (@Composable () -> Unit)? = null,
) {
    BaseDialog(
        onClose = onClose,
        title = title,
        headerActions = headerActions,
        content = {
            Text(message)
        },
        actions = {
            DialogButton(
                onClick = onConfirm,
                style = DialogButtonStyle.Primary,
            ) {
                Translated("Okay")
            }
        }
    )
}
