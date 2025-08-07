package main.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Text
import main.components.Translated
import main.components.translated

/**
 * A basic confirmation dialog with Yes and No buttons.
 *
 * @param message The message to display in the dialog
 * @param onConfirm Callback when the user confirms (clicks Yes)
 * @param onCancel Callback when the user cancels (clicks No or closes the dialog)
 * @param title The title of the dialog (optional)
 */
@Composable
fun BasicConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    title: String? = translated("Confirm"),
) {
    BaseDialog(
        onClose = onCancel,
        title = title,
        content = {
            Text(message)
        },
        actions = {
            // No button (secondary)
            DialogButton(
                onClick = onCancel,
                isPrimary = false,
            ) {
                Translated("No")
            }
            
            // Yes button (primary)
            DialogButton(
                onClick = onConfirm,
                isPrimary = true,
            ) {
                Translated("Yes")
            }
        }
    )
}
