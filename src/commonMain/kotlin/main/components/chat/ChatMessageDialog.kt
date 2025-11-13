package main.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import main.api.DocApi
import Translations.str
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import main.components.BaseDialog
import main.components.DialogButton
import main.components.Md
import main.components.Translated
import main.components.translated
import main.components.worlddocs.SelectDocDialog
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
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea
import kotlin.time.Duration.Companion.seconds

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
    onDelete: () -> Unit,
    worldId: String,
    canEditWorld: Boolean = false,
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

    // Send to doc state
    var showSelectDocDialog by remember { mutableStateOf(false) }
    var isSendingToDoc by remember { mutableStateOf(false) }
    var lastSendCreatedTitle by remember { mutableStateOf<String?>(null) }
    var lastSendError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(lastSendCreatedTitle, lastSendError) {
        delay(3.seconds)
        lastSendCreatedTitle = null
        lastSendError = null
    }

    fun handleSendToDoc(parentId: String?) {
        isSendingToDoc = true
        lastSendCreatedTitle = null
        lastSendError = null
        val contentText = message.message
        val lines = contentText.split("\n")
        val firstLine = lines.firstOrNull()?.trim() ?: ""
        val title = if (firstLine.isNotBlank()) firstLine else str("Untitled doc")
        val body = if (lines.size <= 1) "" else contentText
        scope.launch {
            try {
                val created = DocApi.createDoc(
                    worldId = worldId,
                    title = title,
                    parentId = parentId,
                )
                if (body.isNotEmpty()) {
                    // Use base version from created doc
                    DocApi.updateDocContent(created.key, body, created.version)
                }
                lastSendCreatedTitle = title
                lastSendError = null
            } catch (t: Throwable) {
                console.error("Failed to create doc from chat message", t)
                lastSendError = t.message ?: str("Failed to create doc")
                lastSendCreatedTitle = null
            } finally {
                isSendingToDoc = false
                showSelectDocDialog = false
            }
        }
    }

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
                    TextArea(
                        value = editedText,
                        attrs = {
                            classes(Styles.inputField)
                            placeholder(editPlaceholder)
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
                // Send to doc result message
                if (lastSendCreatedTitle != null) {
                    P(attrs = { classes(Styles.formSuccessMessage) }) {
                        Text(str("Created doc") + ": " + lastSendCreatedTitle!!)
                    }
                } else if (lastSendError != null) {
                    P(attrs = { classes(Styles.formErrorMessage) }) {
                        Text(lastSendError!!)
                    }
                }
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
                if (canEditWorld) {
                    DialogButton(
                        onClick = { showSelectDocDialog = true }
                    ) {
                        Translated("Send to doc")
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

    // Select doc dialog
    if (showSelectDocDialog && canEditWorld) {
        SelectDocDialog(
            worldId = worldId,
            onSelect = { parent -> handleSendToDoc(parent.key) },
            onClose = { showSelectDocDialog = false },
            onSelectNew = { handleSendToDoc(null) },
        )
    }
}
