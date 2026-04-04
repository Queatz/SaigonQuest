package main.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.dom.Text

/**
 * Dialog component for prompting the user to open a URL
 *
 * This dialog is shown when an OPEN_URL WebSocket message is received.
 * It displays the URL and provides buttons to cancel or open the URL.
 *
 * @param url The URL to open
 * @param onConfirm Callback when the user confirms and wants to open the URL
 * @param onCancel Callback when the user cancels
 */
@Composable
fun OpenUrlDialog(
    url: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    BaseDialog(
        title = translated("Open URL?"),
        onClose = onCancel,
        content = {
            // Display the URL
            Text(url)
        },
        actions = {
            // Cancel button
            DialogButton(
                onClick = onCancel,
                style = DialogButtonStyle.Secondary
            ) {
                Translated("Cancel")
            }
            
            // Open URL button
            DialogButton(
                onClick = onConfirm,
                style = DialogButtonStyle.Primary
            ) {
                Translated("Open URL")
            }
        }
    )
}
