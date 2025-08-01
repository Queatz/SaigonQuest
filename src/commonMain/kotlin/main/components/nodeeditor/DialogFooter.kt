package main.components.nodeeditor

import androidx.compose.runtime.Composable
import main.theme.NodeEditorStyles
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Footer component for the logic editor dialog.
 * 
 * @param errorMessage Error message to display, if any
 * @param isSubmitting Whether the form is currently being submitted
 * @param onCancel Callback when the cancel button is clicked
 * @param onSave Callback when the save button is clicked
 */
@Composable
fun DialogFooter(
    errorMessage: String = "",
    isSubmitting: Boolean = false,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Div(
        attrs = {
            classes(NodeEditorStyles.dialogFooter)
        }
    ) {
        // Error message
        if (errorMessage.isNotEmpty()) {
            Span(
                attrs = {
                    classes(NodeEditorStyles.errorMessage)
                }
            ) {
                Text(errorMessage)
            }
        }
        
        // Cancel button
        Button(
            attrs = {
                classes(NodeEditorStyles.cancelButton)
                onClick { onCancel() }
            }
        ) {
            Text("Cancel")
        }
        
        // Save button
        Button(
            attrs = {
                classes(NodeEditorStyles.saveButton)
                classes(if (isSubmitting) NodeEditorStyles.saveButtonDisabled else NodeEditorStyles.saveButtonEnabled)
                // Only allow clicking if not submitting
                onClick { if (!isSubmitting) onSave() }
            }
        ) {
            Text(if (isSubmitting) "Saving..." else "Save")
        }
    }
}
