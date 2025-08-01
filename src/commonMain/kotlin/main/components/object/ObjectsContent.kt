package main.components.`object`

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import main.api.ObjectApi
import main.api.WebSocketApi
import models.Object
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
    onOpenObjectEditor: () -> Unit,
    onEditObject: (String) -> Unit = {},
    objectManager: ObjectManager? = null,
    content: @Composable (@Composable () -> Unit) -> Unit
) {
    // State for the current tab
    var currentTab by remember { mutableStateOf(ObjectsTab.WORLD) }
    
    // State for objects
    var worldObjects by remember { mutableStateOf<List<Object>>(emptyList()) }
    var ownedObjects by remember { mutableStateOf<List<Object>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    
    // State for feedback messages
    var feedbackMessage by remember { mutableStateOf("") }
    var isFeedbackError by remember { mutableStateOf(false) }
    
    // Get current world ID from the URL path
    val currentWorldId = window.location.pathname.substring(1)
    
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
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = ""
        
        try {
            // Load all objects in the world
            val objectList = ObjectApi.getObjectsByWorldId(currentWorldId)
            worldObjects = objectList
            
            // Filter for owned objects (this is a placeholder - in a real app, you'd check ownership)
            // For now, we'll just assume the first half of objects are owned by the current user
            ownedObjects = objectList.take(objectList.size / 2)
            
            isLoading = false
        } catch (error: Exception) {
            console.error("Error loading objects", error)
            errorMessage = error.message ?: "Failed to load objects"
            isLoading = false
        }
    }
    
    // JSON serializer
    val json = Json { 
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }
    
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
                
                // Send the remove object event over WebSocket for real-time updates
                WebSocketApi.removeObject(objectId)
                
                // Update the local state
                worldObjects = worldObjects.filter { it.key != objectId }
                ownedObjects = ownedObjects.filter { it.key != objectId }
                
                // Show success feedback
                showFeedback("'$objectTitle' removed successfully", false)
            } catch (error: Exception) {
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
                if (objectManager is SceneObjectManager) {
                    // Call the locateObject method on the SceneObjectManager
                    val success = (objectManager as SceneObjectManager).locateObject(objectId)
                    if (success) {
                        showFeedback("Locating object: ${obj.title}")
                    } else {
                        showFeedback("Failed to locate object in scene", true)
                    }
                } else {
                    // Basic implementation for other object managers
                    console.log("Locating object: ${obj.title} (${obj.key})")
                    showFeedback("Locating object: ${obj.title}")
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
    
    // Call the content lambda with the tab content as a lambda
    content {
        // Tab navigation
        TabNavigation {
            // World tab
            TabItem(
                label = "World",
                isSelected = currentTab == ObjectsTab.WORLD,
                onClick = { currentTab = ObjectsTab.WORLD }
            )
            
            // Marketplace tab
            TabItem(
                label = "Marketplace",
                isSelected = currentTab == ObjectsTab.MARKETPLACE,
                onClick = { currentTab = ObjectsTab.MARKETPLACE }
            )

            // Create tab
            TabItem(
                label = "Create",
                isSelected = currentTab == ObjectsTab.CREATE,
                onClick = { currentTab = ObjectsTab.CREATE }
            )
        }
        
        // Tab content
        when (currentTab) {
            ObjectsTab.WORLD -> {
                // World tab content - list all objects in the world
                WorldTabContent(
                    objects = worldObjects,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onRemove = { objectId -> removeObject(objectId) },
                    onLocate = { objectId -> locateObject(objectId) },
                    onEdit = { objectId -> editObject(objectId) }
                )
            }
            ObjectsTab.CREATE -> {
                // Owned tab content - list objects created by the user with create button
                OwnedTabContent(
                    objects = ownedObjects,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onCreateObject = onOpenObjectEditor,
                    onDragStart = { event, obj -> handleDragStart(event, obj) }
                )
            }
            ObjectsTab.MARKETPLACE -> {
                // Marketplace tab content - blank for now
                MarketplaceTabContent()
            }
        }
    }
}
