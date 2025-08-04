package main.components

import androidx.compose.runtime.Composable
import app.softwork.routingcompose.Router
import kotlinx.coroutines.launch
import main.api.ObjectApi
import main.api.WebSocketApi
import main.components.`object`.ObjectsDialog
import main.components.`object`.ObjectsPanel
import main.components.inventory.InventoryDialog
import main.components.nodeeditor.LogicEditorDialog
import main.utils.PathUtils
import models.Object
import models.World

/**
 * A component that displays the side panel based on the current panel state.
 * The side panel can show different content such as Chat, WorldEdit, Notifications, etc.
 */
@Composable
fun SidePanel(
    currentPanel: SidePanelState,
    isMobile: androidx.compose.runtime.State<Boolean>,
    currentWorld: World?,
    currentEditingObject: Object?,
    onClose: () -> Unit,
    onOpenObjectEditor: () -> Unit,
    onEditObject: (String) -> Unit,
    onSaveObject: (Object) -> Unit,
    onOpenLogicEditor: (Object) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val router = Router.current
    
    when (currentPanel) {
        SidePanelState.Chat -> {
            // Get current world ID from the URL path or use the current world's ID
            val chatWorldId = currentWorld?.key ?: PathUtils.getCurrentPath(router)
            
            // Responsive chat component that adapts between mobile and desktop
            Chat(
                worldId = chatWorldId,
                isMobile = isMobile.value,
                onClose = onClose
            )
        }
        
        SidePanelState.WorldEdit -> {
            // Responsive world edit component that adapts between mobile and desktop
            WorldEdit(
                currentWorld = currentWorld,
                isMobile = isMobile.value,
                onClose = onClose,
                onSave = { world ->
                    // Handle saved world
                    console.log("World saved: ${world.title}")
                }
            )
        }
        
        SidePanelState.Notifications -> {
            // Responsive notifications component that adapts between mobile and desktop
            Notifications(
                isMobile = isMobile.value,
                onClose = onClose,
                onNotificationClick = { notification ->
                    // Handle notification click
                    console.log("Notification clicked: ${notification.title}")
                    // Mark notification as read or perform other actions
                }
            )
        }
        
        SidePanelState.Friends -> {
            // Responsive friends component that adapts between mobile and desktop
            Friends(
                isMobile = isMobile.value,
                onClose = onClose
            )
        }
        
        SidePanelState.ObjectsPanel -> {
            // Get the ObjectManager from SceneManager
            val objectManager = SceneManager.getObjectManager()
            
            if (isMobile.value) {
                // Mobile: Dialog
                ObjectsDialog(
                    onClose = onClose,
                    onOpenObjectEditor = onOpenObjectEditor,
                    onEditObject = onEditObject,
                    objectManager = objectManager
                )
            } else {
                // Desktop: Side panel
                ObjectsPanel(
                    onClose = onClose,
                    onOpenObjectEditor = onOpenObjectEditor,
                    onEditObject = onEditObject,
                    objectManager = objectManager
                )
            }
        }
        
        SidePanelState.ObjectEditor -> {
            // Get current world ID from the URL path
            val currentWorldId = PathUtils.getCurrentPath(router)
            
            ObjectEditorDialog(
                objectModel = currentEditingObject, // Use the current editing object or null for new object
                worldId = currentWorldId,
                onClose = onClose,
                onSave = { obj ->
                    // Handle saved object
                    console.log("Object saved: ${obj.title}")
                    
                    // Add the object to the scene via WebSocket
                    val success = WebSocketApi.addObject(obj)
                    if (success) {
                        console.log("Object added to scene: ${obj.title}")
                    } else {
                        console.error("Failed to add object to scene: ${obj.title}")
                    }
                    
                    onSaveObject(obj)
                },
                onOpenLogicEditor = onOpenLogicEditor
            )
        }
        
        SidePanelState.LogicEditor -> {
            if (currentEditingObject != null) {
                // Get current world ID from the URL path
                val currentWorldId = PathUtils.getCurrentPath(router)
                
                LogicEditorDialog(
                    objectModel = currentEditingObject,
                    worldId = currentWorldId,
                    onClose = onClose,
                    onSave = { updatedObj ->
                        // Handle saved object with updated logic
                        console.log("Object logic updated: ${updatedObj.title}")
                        onSaveObject(updatedObj)
                    }
                )
            }
        }
        
        SidePanelState.Inventory -> {
            InventoryDialog(onClose = onClose)
        }
        
        else -> {} // No panel to render
    }
}
