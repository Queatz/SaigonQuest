package main.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.components.BaseDialog
import main.components.DialogButton
import main.components.Md
import main.components.Translated
import main.components.translated
import main.theme.Styles
import models.ChatMessage
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P

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
    isOwner: Boolean,
    isCurrentUser: Boolean,
    onClose: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit
) {
    // If the message is already deleted, just show a simple dialog
    if (message.deleted) {
        BaseDialog(
            onClose = onClose,
            title = translated("Message"),
            maxWidth = "800px",
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
                    Translated("Message was deleted")
                }
            },
            actions = {
                DialogButton(
                    onClick = onClose,
                    isPrimary = true
                ) {
                    Translated("Close")
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

    // Rich editor state
    var showRichEditorDialog by remember { mutableStateOf(false) }
    
    BaseDialog(
        onClose = onClose,
        title = if (isEditing) {
            translated("Edit message")
        } else if (showDeleteConfirmation) {
            translated("Delete message")
        } else {
            translated("Message options")
        },
        content = {
            if (isEditing) {
                // Edit message form
                Div {
                    val editPlaceholder = translated("Edit your message")
                    Input(
                        type = InputType.Text,
                        attrs = {
                            classes(Styles.inputField)
                            placeholder(editPlaceholder)
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
                    Translated("Are you sure you want to delete this message?")
                }
            } else {
                // Message display
                Md(message.message)
            }
        },
        actions = {
            if (isEditing) {
                // Edit mode actions
                DialogButton(
                    onClick = { isEditing = false }
                ) {
                    Translated("Cancel")
                }
                DialogButton(
                    onClick = { showRichEditorDialog = true }
                ) {
                    Translated("Rich editor")
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
                    Translated("Update")
                }
            } else if (showDeleteConfirmation) {
                // Delete confirmation actions
                DialogButton(
                    onClick = { showDeleteConfirmation = false }
                ) {
                    Translated("Cancel")
                }
                DialogButton(
                    onClick = {
                        onDelete()
                        onClose()
                    },
                    isPrimary = true
                ) {
                    Translated("Delete")
                }
            } else {
                // Normal mode actions
                if (isOwner || isCurrentUser) {
                    DialogButton(
                        onClick = { showDeleteConfirmation = true }
                    ) {
                        Translated("Delete")
                    }
                }
                if (isCurrentUser) {
                    DialogButton(
                        onClick = { isEditing = true }
                    ) {
                        Translated("Edit")
                    }
                }
                DialogButton(
                    onClick = onClose,
                    isPrimary = true
                ) {
                    Translated("Close")
                }
            }
        }
    )

    // Child rich text editor dialog (for editing existing message)
    if (showRichEditorDialog) {
        RichTextEditorDialog(
            onClose = { showRichEditorDialog = false },
            onSend = { markdown ->
                editedText = markdown
                showRichEditorDialog = false
            },
            initialText = editedText,
        )
    }
}
