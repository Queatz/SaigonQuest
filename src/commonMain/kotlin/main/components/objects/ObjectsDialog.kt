package main.components.objects

import androidx.compose.runtime.Composable
import main.components.BaseDialog
import main.components.translated
import models.World
import org.jetbrains.compose.web.dom.Div

/**
 * A dialog version of the Objects Panel for mobile view.
 * 
 * @param onClose Callback when the dialog is closed
 * @param onOpenObjectEditor Callback to open the object editor dialog
 * @param onEditObject Callback to edit a specific object by ID
 * @param objectManager Reference to the ObjectManager for removing objects
 */
@Composable
fun ObjectsDialog(
    onClose: () -> Unit,
    onOpenObjectEditor: (isCreate: Boolean) -> Unit,
    onEditObject: (String) -> Unit = {},
    objectManager: SceneObjectManager? = null,
    currentWorld: World?
) {
    ObjectsContent(
        onOpenObjectEditor = onOpenObjectEditor,
        onEditObject = onEditObject,
        objectManager = objectManager,
        currentWorld = currentWorld,
        isDialog = true,
        content = { tabContent ->
            // Use BaseDialog for consistent styling and behavior
            BaseDialog(
                onClose = onClose,
                title = translated("Objects"),
                isFullHeight = true,
                isFullWidth = true,
                content = {
                    // Tab content
                    tabContent()
                }
            )
        },
    )
}
