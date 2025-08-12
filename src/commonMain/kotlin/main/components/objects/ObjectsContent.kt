package main.components.objects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.ObjectApi
import models.Object
import models.Position
import models.World
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.Position as CssPosition
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.opacity
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement

/**
 * A shared content component for displaying objects with tabs for World, Marketplace, and Create.
 * This component is used by both ObjectsPanel and ObjectsDialog.
 * 
 * @param onOpenObjectEditor Callback to open the object editor dialog
 * @param onEditObject Callback to edit a specific object by ID
 * @param objectManager Reference to the ObjectManager for removing objects
 * @param content Lambda that receives the tab content composable to render
 */
@Composable
fun ObjectsContent(
    onOpenObjectEditor: (isCreate: Boolean) -> Unit,
    onEditObject: (String) -> Unit = {},
    objectManager: SceneObjectManager? = null,
    currentWorld: World?,
    content: @Composable (@Composable () -> Unit) -> Unit,
) {
    // State for objects
    var worldObjects by remember { mutableStateOf<List<Object>>(emptyList()) }
    var ownedObjects by remember { mutableStateOf<List<Object>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    // State for feedback messages
    var feedbackMessage by remember { mutableStateOf("") }
    var isFeedbackError by remember { mutableStateOf(false) }

    // Get current world ID from the URL path
    val currentWorldId = currentWorld?.key
    
    // Create a coroutine scope for async operations
    val scope = rememberCoroutineScope()

    // Function to show feedback message
    fun showFeedback(message: String, isError: Boolean = false) {
        feedbackMessage = message
        isFeedbackError = isError

        // Clear feedback after 3 seconds
        scope.launch {
            delay(3000)
            feedbackMessage = ""
        }
    }

    // Load objects when the component is mounted
    LaunchedEffect(currentWorldId) {
        isLoading = true
        errorMessage = ""
        
        try {
            // Load all objects in the world
            val objectList = ObjectApi.getObjectsByWorldId(currentWorldId ?: return@LaunchedEffect)
            worldObjects = objectList
            
            // Filter for owned objects (this is a placeholder - in a real app, you'd check ownership)
            // For now, we'll just assume the first half of objects are owned by the current user
            ownedObjects = objectList.take(objectList.size / 2)
            
            isLoading = false
        } catch (error: Throwable) {
            console.error("Error loading objects", error)
            errorMessage = error.message ?: "Failed to load objects"
            isLoading = false
        }
    }
    
    // JSON serializer
    val json = utils.json
    
    // Function to handle drag start
    fun handleDragStart(event: dynamic, obj: Object) {
        // Set the drag data
        event.dataTransfer.setData("application/json", json.encodeToString(Object.serializer(), obj))
        
        // Set a custom drag image
        val dragImage = document.createElement("div") as HTMLElement
        dragImage.style.width = "48px"
        dragImage.style.height = "48px"
        dragImage.style.backgroundColor = "rgba(0, 120, 255, 0.7)"
        dragImage.style.borderRadius = "4px"
        document.body?.appendChild(dragImage)
        event.dataTransfer.setDragImage(dragImage, 24, 24)
        
        // Remove the drag image element after it's been used
        window.setTimeout({ document.body?.removeChild(dragImage) }, 0)
    }
    
    // Function to remove an object with both HTTP and WebSocket persistence
    fun removeObject(objectId: String) {
        // Get the object title for feedback message
        val objectTitle = worldObjects.find { it.key == objectId }?.title ?: "Object"
        
        // Launch a coroutine to delete the object via HTTP
        scope.launch {
            try {
                // Delete the object via HTTP API
                ObjectApi.deleteObject(objectId)

                // Update the local state
                worldObjects = worldObjects.filter { it.key != objectId }
                ownedObjects = ownedObjects.filter { it.key != objectId }
                
                // Show success feedback
                showFeedback("'$objectTitle' removed successfully", false)
            } catch (error: Throwable) {
                console.error("Error deleting object", error)
                errorMessage = error.message ?: "Failed to delete object"
                
                // Show error feedback
                showFeedback("Failed to remove '$objectTitle': ${error.message ?: "Unknown error"}", true)
            }
        }
    }
    
    // Function to locate an object in the 3D view
    fun locateObject(objectId: String) {
        // Find the object in the list
        val obj = worldObjects.find { it.key == objectId }
        if (obj != null) {
            // Use the object manager to locate the object if available
            if (objectManager != null) {
                // Check if the object manager is a SceneObjectManager
                // Call the locateObject method on the SceneObjectManager
                val success = objectManager.locateObject(objectId)
                if (success) {
                    showFeedback("Locating object: ${obj.title}")
                } else {
                    showFeedback("Failed to locate object in scene", true)
                }
            } else {
                console.warn("Object manager not available for locating object")
                showFeedback("Cannot locate object: Object manager not available", true)
            }
        } else {
            console.error("Object not found: $objectId")
            showFeedback("Cannot locate object: Object not found", true)
        }
    }
    
    // Function to edit an object
    fun editObject(objectId: String) {
        // Find the object in the list
        val obj = worldObjects.find { it.key == objectId }
        if (obj != null) {
            // Log the edit action
            console.log("Editing object: ${obj.title} (${obj.key})")
            showFeedback("Opening editor for: ${obj.title}")
            
            // Call the callback with the object ID
            onEditObject(objectId)
        } else {
            console.error("Object not found: $objectId")
            showFeedback("Cannot edit object: Object not found", true)
        }
    }

    // Function to duplicate an object with X offset +1
    fun duplicateObject(objectId: String) {
        val original = worldObjects.find { it.key == objectId }
        if (original == null) {
            console.error("Object not found: $objectId")
            showFeedback("Cannot duplicate object: Object not found", true)
            return
        }

        // Build a new Object instance to ensure a fresh key is generated client-side
        val newObj = Object(
            title = original.title,
            data = original.data,
            worldId = original.worldId,
            file = original.file,
            collection = original.collection,
            position = Position(
                x = (original.position.x + 1f),
                y = original.position.y,
                z = original.position.z,
            ),
            rotation = original.rotation,
            scale = original.scale,
            status = original.status,
        )

        scope.launch {
            try {
                val created = ObjectApi.createObject(newObj)
                // Update local lists
                worldObjects = worldObjects + created
                if (ownedObjects.any { it.key == original.key }) {
                    ownedObjects = ownedObjects + created
                }
                showFeedback("'${original.title}' duplicated")
            } catch (error: Throwable) {
                console.error("Error duplicating object", error)
                showFeedback("Failed to duplicate '${original.title}': ${error.message ?: "Unknown error"}", true)
            }
        }
    }
    
    // Render feedback message if present (similar to InventoryTray)
    if (feedbackMessage.isNotEmpty()) {
        Div(
            attrs = {
                style {
                    position(CssPosition.Absolute)
                    bottom(70.px)
                    left(50.percent)
                    property("transform", "translateX(-50%) translateY(-10px)")
                    padding(8.px, 12.px)
                    backgroundColor(if (isFeedbackError) rgba(255, 0, 0, 0.7) else rgba(0, 150, 0, 0.7))
                    color(Color.white)
                    fontSize(14.px)
                    property("border-radius", "4px")
                    property("pointer-events", "none")
                    opacity(0.9)
                }
            }
        ) {
            Text(feedbackMessage)
        }
    }

    // Call the content lambda with the tab content as a lambda
    content {
        WorldObjectsContent(
            objects = worldObjects,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onCreateObject = { onOpenObjectEditor(true) },
            onRemove = { objectId -> removeObject(objectId) },
            onLocate = { objectId -> locateObject(objectId) },
            onEdit = { objectId -> editObject(objectId) },
            onDuplicate = { objectId -> duplicateObject(objectId) }
        )
    }
}
