package main.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.components.BaseDialog
import main.components.DialogButton
import main.theme.Styles
import main.theme.Theme
import models.ChatMessage
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * A dialog for editing or deleting a chat message.
 *
 * @param message The chat message to edit or delete
 * @param onClose Callback when the dialog is closed
 * @param onEdit Callback when the message is edited
 * @param onDelete Callback when the message is deleted
 */
@Composable
fun ChatMessageDialog(
    message: ChatMessage,
    onClose: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit
) {
    // If the message is already deleted, just show a simple dialog
    if (message.deleted) {
        BaseDialog(
            onClose = onClose,
            title = "Message",
            content = {
                P(
                    attrs = {
                        style {
                            margin(0.px)
                            padding(8.px)
                            color(rgba(255, 255, 255, 0.5))
                        }
                    }
                ) {
                    Text("Message was deleted")
                }
            },
            actions = {
                DialogButton(
                    onClick = onClose,
                    isPrimary = true
                ) {
                    Text("Close")
                }
            }
        )
        return
    }
    
    // State for editing mode
    var isEditing by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf(message.message) }
    
    // State for delete confirmation
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    BaseDialog(
        onClose = onClose,
        title = if (isEditing) "Edit Message" else if (showDeleteConfirmation) "Delete Message" else "Message Options",
        content = {
            if (isEditing) {
                // Edit message form
                Div(
                    attrs = {
                        style {
                            padding(8.px)
                        }
                    }
                ) {
                    Input(
                        type = InputType.Text,
                        attrs = {
                            classes(Styles.inputField)
                            placeholder("Edit your message")
                            value(editedText)
                            onInput { editedText = it.value }
                        }
                    )
                }
            } else if (showDeleteConfirmation) {
                // Delete confirmation
                P(
                    attrs = {
                        style {
                            margin(0.px)
                            padding(8.px)
                        }
                    }
                ) {
                    Text("Are you sure you want to delete this message?")
                }
            } else {
                // Message display
                P(
                    attrs = {
                        style {
                            margin(0.px)
                            padding(8.px)
                        }
                    }
                ) {
                    Text(message.message)
                }
            }
        },
        actions = {
            if (isEditing) {
                // Edit mode actions
                DialogButton(
                    onClick = { isEditing = false }
                ) {
                    Text("Cancel")
                }
                DialogButton(
                    onClick = {
                        if (editedText.isNotBlank() && editedText != message.message) {
                            onEdit(editedText)
                        }
                        onClose()
                    },
                    isPrimary = true
                ) {
                    Text("Update")
                }
            } else if (showDeleteConfirmation) {
                // Delete confirmation actions
                DialogButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Text("Cancel")
                }
                DialogButton(
                    onClick = {
                        onDelete()
                        onClose()
                    },
                    isPrimary = true
                ) {
                    Text("Delete")
                }
            } else {
                // Normal mode actions
                DialogButton(
                    onClick = { showDeleteConfirmation = true }
                ) {
                    Text("Delete")
                }
                DialogButton(
                    onClick = { isEditing = true }
                ) {
                    Text("Edit")
                }
                DialogButton(
                    onClick = onClose,
                    isPrimary = true
                ) {
                    Text("Close")
                }
            }
        }
    )
}
