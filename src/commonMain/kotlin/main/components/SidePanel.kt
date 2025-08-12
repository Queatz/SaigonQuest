package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import app.softwork.routingcompose.Router
import kotlinx.coroutines.launch
import main.api.WorldApi
import main.components.inventory.InventoryDialog
import main.components.nodeeditor.LogicEditorDialog
import main.components.objects.ObjectsDialog
import main.components.objects.ObjectsPanel
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
    onOpenObjectEditor: (isCreate: Boolean) -> Unit,
    onEditObject: (String) -> Unit,
    onSaveObject: (Object) -> Unit,
    onOpenLogicEditor: (Object) -> Unit
) {
    val router = Router.current
    val scope = rememberCoroutineScope()
    
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
                    
                    // Handle different notification types
                    when (notification.type) {
                        // For object notifications, navigate to the world URL
                        models.NotificationType.OBJECT_NOTIFICATION -> {
                            if (notification.worldId.isNotEmpty()) {
                                // Get the world URL from the repository
                                console.log("Navigating to world: ${notification.worldId}")
                                
                                // Navigate to the world URL
                                scope.launch {
                                    val worldUrl = WorldApi.getWorld(notification.worldId).url
                                    router.navigate("/$worldUrl")
                                }
                                
                                // Close the notifications panel
                                onClose()
                            } else {
                                console.log("Navigating to world failed: missing worldId")
                            }
                        }
                        else -> {
                            // For other notification types, just log
                            console.log("Notification of type ${notification.type} clicked")
                        }
                    }
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
                    objectManager = objectManager,
                    currentWorld = currentWorld
                )
            } else {
                // Desktop: Side panel
                ObjectsPanel(
                    onClose = onClose,
                    onOpenObjectEditor = onOpenObjectEditor,
                    onEditObject = onEditObject,
                    objectManager = objectManager,
                    currentWorld = currentWorld
                )
            }
        }
        
        SidePanelState.ObjectEditor -> {
            ObjectEditorDialog(
                objectModel = currentEditingObject, // Use the current editing object or null for new object
                worldId = currentWorld?.key ?: return,
                onClose = onClose,
                onSave = { obj ->
                    // Handle saved object
                    console.log("Object saved: ${obj.title}")

                    onSaveObject(obj)
                },
                onOpenLogicEditor = onOpenLogicEditor
            )
        }
        
        SidePanelState.LogicEditor -> {
            if (currentEditingObject != null) {
                LogicEditorDialog(
                    objectModel = currentEditingObject,
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
