package main.components.`object`

import androidx.compose.runtime.Composable
import main.components.SimpleSidePanel

/**
 * A panel that displays objects with three tabs: World, Owned, and Marketplace.
 * 
 * @param onClose Callback when the panel is closed
 * @param onOpenObjectEditor Callback to open the object editor dialog
 * @param onEditObject Callback to edit a specific object by ID
 * @param objectManager Reference to the ObjectManager for removing objects
 */
@Composable
fun ObjectsPanel(
    onClose: () -> Unit,
    onOpenObjectEditor: () -> Unit,
    onEditObject: (String) -> Unit = {},
    objectManager: SceneObjectManager? = null
) {
    ObjectsContent(
        onOpenObjectEditor = onOpenObjectEditor,
        onEditObject = onEditObject,
        objectManager = objectManager
    ) { tabContent ->
        SimpleSidePanel(
            title = "Objects",
            onClose = onClose,
            content = { tabContent() }
        )
    }
}
