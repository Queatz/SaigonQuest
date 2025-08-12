package main.components.objects

import androidx.compose.runtime.Composable
import main.components.SimpleSidePanel
import main.components.translated
import models.World

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
    onOpenObjectEditor: (isCreate: Boolean) -> Unit,
    onEditObject: (String) -> Unit = {},
    objectManager: SceneObjectManager? = null,
    currentWorld: World?
) {
    ObjectsContent(
        onOpenObjectEditor = onOpenObjectEditor,
        onEditObject = onEditObject,
        objectManager = objectManager,
        currentWorld = currentWorld
    ) { tabContent ->
        SimpleSidePanel(
            title = translated("Objects"),
            onClose = onClose,
            content = { tabContent() }
        )
    }
}
