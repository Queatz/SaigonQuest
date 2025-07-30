package main.components.`object`

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import main.api.ObjectApi
import main.api.WebSocketApi
import main.components.SidePanel
import main.theme.Theme
import models.Object
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginRight
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

/**
 * Enum class representing the tabs in the Objects Panel
 */
enum class ObjectsTab {
    WORLD, OWNED, MARKETPLACE
}

/**
 * A panel that displays objects with three tabs: World, Owned, and Marketplace.
 * 
 * @param onClose Callback when the panel is closed
 * @param onOpenObjectEditor Callback to open the object editor dialog
 * @param objectManager Reference to the ObjectManager for removing objects
 */
@Composable
fun ObjectsPanel(
    onClose: () -> Unit,
    onOpenObjectEditor: () -> Unit,
    objectManager: ObjectManager? = null
) {
    // State for the current tab
    var currentTab by remember { mutableStateOf(ObjectsTab.WORLD) }
    
    // State for objects
    var worldObjects by remember { mutableStateOf<List<Object>>(emptyList()) }
    var ownedObjects by remember { mutableStateOf<List<Object>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Get current world ID from the URL path
    val currentWorldId = window.location.pathname.substring(1)
    
    // Load objects when the panel is opened
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
    
    // Function to remove an object
    fun removeObject(objectId: String) {
        // Send the remove object event over WebSocket
        WebSocketApi.removeObject(objectId)
        
        // Update the local state
        worldObjects = worldObjects.filter { it.key != objectId }
        ownedObjects = ownedObjects.filter { it.key != objectId }
    }
    
    SidePanel(
        title = "Objects",
        onClose = onClose
    ) {
        // Tab navigation
        Div(
            attrs = {
                style {
                    display(DisplayStyle.Flex)
                    width(100.percent)
                    property("border-bottom", "1px solid rgba(255, 255, 255, 0.2)")
                }
            }
        ) {
            // World tab
            Div(
                attrs = {
                    style {
                        padding(12.px, 16.px)
                        cursor("pointer")
                        if (currentTab == ObjectsTab.WORLD) {
                            property("border-bottom", "2px solid ${Theme.colors.primary}")
                            color(Theme.colors.primary)
                        } else {
                            color(Color.white)
                        }
                    }
                    onClick { currentTab = ObjectsTab.WORLD }
                }
            ) {
                Text("World")
            }
            
            // Owned tab
            Div(
                attrs = {
                    style {
                        padding(12.px, 16.px)
                        cursor("pointer")
                        if (currentTab == ObjectsTab.OWNED) {
                            property("border-bottom", "2px solid ${Theme.colors.primary}")
                            color(Theme.colors.primary)
                        } else {
                            color(Color.white)
                        }
                    }
                    onClick { currentTab = ObjectsTab.OWNED }
                }
            ) {
                Text("All")
            }
            
            // Marketplace tab
            Div(
                attrs = {
                    style {
                        padding(12.px, 16.px)
                        cursor("pointer")
                        if (currentTab == ObjectsTab.MARKETPLACE) {
                            property("border-bottom", "2px solid ${Theme.colors.primary}")
                            color(Theme.colors.primary)
                        } else {
                            color(Color.white)
                        }
                    }
                    onClick { currentTab = ObjectsTab.MARKETPLACE }
                }
            ) {
                Text("Marketplace")
            }
        }
        
        // Tab content
        when (currentTab) {
            ObjectsTab.WORLD -> {
                // World tab content - list all objects in the world
                WorldTabContent(
                    objects = worldObjects,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onRemove = { objectId -> removeObject(objectId) }
                )
            }
            ObjectsTab.OWNED -> {
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

/**
 * Content for the World tab - lists all objects in the world with remove functionality.
 */
@Composable
private fun WorldTabContent(
    objects: List<Object>,
    isLoading: Boolean,
    errorMessage: String,
    onRemove: (String) -> Unit
) {
    // Container for the content
    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                height(100.percent)
                padding(16.px)
            }
        }
    ) {
        // Loading or error message
        if (isLoading) {
            P(
                attrs = {
                    style {
                        color(Color.white)
                        property("text-align", "center")
                    }
                }
            ) {
                Text("Loading objects...")
            }
        } else if (errorMessage.isNotEmpty()) {
            P(
                attrs = {
                    style {
                        color(Color.red)
                        property("text-align", "center")
                    }
                }
            ) {
                Text(errorMessage)
            }
        } else if (objects.isEmpty()) {
            P(
                attrs = {
                    style {
                        color(rgba(255, 255, 255, 0.7))
                        property("text-align", "center")
                    }
                }
            ) {
                Text("No objects in this world yet.")
            }
        } else {
            // List of objects
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        property("gap", "8px")
                        property("overflow-y", "auto")
                        property("flex-grow", "1")
                    }
                }
            ) {
                // Display objects
                objects.forEach { obj ->
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                justifyContent(JustifyContent.SpaceBetween)
                                alignItems(AlignItems.Center)
                                padding(8.px)
                                backgroundColor(rgba(255, 255, 255, 0.1))
                                property("border-radius", "4px")
                            }
                        }
                    ) {
                        // Object title
                        Span(
                            attrs = {
                                style {
                                    color(Color.white)
                                }
                            }
                        ) {
                            Text(obj.title.takeIf { it.isNotEmpty() } ?: "Untitled")
                        }
                        
                        // Remove button
                        Button(
                            attrs = {
                                style {
                                    backgroundColor(rgba(255, 0, 0, 0.7))
                                    color(Color.white)
                                    property("border", "0")
                                    property("border-radius", "4px")
                                    padding(4.px, 8.px)
                                    cursor("pointer")
                                    fontSize(12.px)
                                }
                                onClick { onRemove(obj.key) }
                            }
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Content for the Owned tab - lists objects created by the user with create button.
 */
@Composable
private fun OwnedTabContent(
    objects: List<Object>,
    isLoading: Boolean,
    errorMessage: String,
    onCreateObject: () -> Unit,
    onDragStart: (dynamic, Object) -> Unit
) {
    // Container for the content
    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                height(100.percent)
                padding(16.px)
            }
        }
    ) {
        // Create object button
        Button(
            attrs = {
                style {
                    backgroundColor(Theme.colors.primary)
                    color(Theme.colors.onPrimary)
                    property("border", "0")
                    property("border-radius", "4px")
                    padding(8.px, 16.px)
                    marginRight(8.px)
                    cursor("pointer")
                    fontSize(14.px)
                    margin(0.px, 0.px, 16.px, 0.px)
                }
                onClick { onCreateObject() }
            }
        ) {
            Text("+ Create Object")
        }
        
        // Loading or error message
        if (isLoading) {
            P(
                attrs = {
                    style {
                        color(Color.white)
                        property("text-align", "center")
                    }
                }
            ) {
                Text("Loading objects...")
            }
        } else if (errorMessage.isNotEmpty()) {
            P(
                attrs = {
                    style {
                        color(Color.red)
                        property("text-align", "center")
                    }
                }
            ) {
                Text(errorMessage)
            }
        } else if (objects.isEmpty()) {
            P(
                attrs = {
                    style {
                        color(rgba(255, 255, 255, 0.7))
                        property("text-align", "center")
                    }
                }
            ) {
                Text("You don't have any objects yet. Click 'Create Object' to create one.")
            }
        } else {
            // Grid of objects
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Grid)
                        property("grid-template-columns", "repeat(auto-fill, minmax(80px, 1fr))")
                        property("gap", "12px")
                        property("overflow-y", "auto")
                        property("flex-grow", "1")
                        property("align-content", "flex-start")
                    }
                }
            ) {
                // Display objects
                objects.forEach { obj ->
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                flexDirection(FlexDirection.Column)
                                alignItems(AlignItems.Center)
                                property("gap", "4px")
                            }
                        }
                    ) {
                        // Object box (draggable)
                        Div(
                            attrs = {
                                style {
                                    width(64.px)
                                    height(64.px)
                                    backgroundColor(rgba(0, 120, 255, 0.3))
                                    property("border", "1px solid rgba(0, 120, 255, 0.7)")
                                    property("border-radius", "4px")
                                    display(DisplayStyle.Flex)
                                    justifyContent(JustifyContent.Center)
                                    alignItems(AlignItems.Center)
                                    cursor("grab")
                                    // Make box draggable
                                    property("draggable", "true")
                                }
                                // Set up drag events
                                onDragStart { event ->
                                    onDragStart(event.nativeEvent, obj)
                                }
                            }
                        ) {
                            // Display a cube icon
                            Span {
                                Text("□")
                            }
                        }
                        
                        // Object title
                        Span(
                            attrs = {
                                style {
                                    color(Color.white)
                                    fontSize(12.px)
                                    property("text-align", "center")
                                    property("word-break", "break-word")
                                    property("max-width", "80px")
                                }
                            }
                        ) {
                            Text(obj.title.takeIf { it.isNotEmpty() } ?: "Untitled")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Content for the Marketplace tab - blank for now.
 */
@Composable
private fun MarketplaceTabContent() {
    Div(
        attrs = {
            style {
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.Center)
                alignItems(AlignItems.Center)
                height(100.percent)
                padding(16.px)
            }
        }
    ) {
        P(
            attrs = {
                style {
                    color(rgba(255, 255, 255, 0.7))
                    property("text-align", "center")
                }
            }
        ) {
            Text("Marketplace coming soon!")
        }
    }
}

/**
 * A dialog version of the Objects Panel for mobile view.
 */
@Composable
fun ObjectsDialog(
    onClose: () -> Unit,
    onOpenObjectEditor: () -> Unit,
    objectManager: ObjectManager? = null
) {
    // State for the current tab
    var currentTab by remember { mutableStateOf(ObjectsTab.WORLD) }
    
    // State for objects
    var worldObjects by remember { mutableStateOf<List<Object>>(emptyList()) }
    var ownedObjects by remember { mutableStateOf<List<Object>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Get current world ID from the URL path
    val currentWorldId = window.location.pathname.substring(1)
    
    // Load objects when the dialog is opened
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
    
    // Function to remove an object
    fun removeObject(objectId: String) {
        // Send the remove object event over WebSocket
        WebSocketApi.removeObject(objectId)
        
        // Update the local state
        worldObjects = worldObjects.filter { it.key != objectId }
        ownedObjects = ownedObjects.filter { it.key != objectId }
    }
    
    Div(
        attrs = {
            style {
                position(Position.Fixed)
                top(0.px)
                left(0.px)
                width(100.percent)
                height(100.percent)
                backgroundColor(rgba(0, 0, 0, 0.7))
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.Center)
                alignItems(AlignItems.Center)
                // Make dialog interactive
                property("pointer-events", "auto")
            }
        }
    ) {
        Div(
            attrs = {
                style {
                    width(90.percent)
                    property("max-width", "600px")
                    height(80.percent)
                    backgroundColor(rgba(30, 30, 30, 0.95))
                    property("border-radius", "8px")
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                }
            }
        ) {
            // Dialog header
            Div(
                attrs = {
                    style {
                        padding(16.px)
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.SpaceBetween)
                        alignItems(AlignItems.Center)
                        property("border-bottom", "1px solid rgba(255, 255, 255, 0.2)")
                    }
                }
            ) {
                H3(
                    attrs = {
                        style {
                            margin(0.px)
                            color(Color.white)
                        }
                    }
                ) {
                    Text("Objects")
                }
                
                Button(
                    attrs = {
                        style {
                            backgroundColor(Color.transparent)
                            property("border", "0")
                            color(Color.white)
                            cursor("pointer")
                            fontSize(20.px)
                        }
                        onClick { onClose() }
                    }
                ) {
                    Text("×")
                }
            }
            
            // Tab navigation
            Div(
                attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        width(100.percent)
                        property("border-bottom", "1px solid rgba(255, 255, 255, 0.2)")
                    }
                }
            ) {
                // World tab
                Div(
                    attrs = {
                        style {
                            padding(12.px, 16.px)
                            cursor("pointer")
                            if (currentTab == ObjectsTab.WORLD) {
                                property("border-bottom", "2px solid ${Theme.colors.primary}")
                                color(Theme.colors.primary)
                            } else {
                                color(Color.white)
                            }
                        }
                        onClick { currentTab = ObjectsTab.WORLD }
                    }
                ) {
                    Text("World")
                }
                
                // Owned tab
                Div(
                    attrs = {
                        style {
                            padding(12.px, 16.px)
                            cursor("pointer")
                            if (currentTab == ObjectsTab.OWNED) {
                                property("border-bottom", "2px solid ${Theme.colors.primary}")
                                color(Theme.colors.primary)
                            } else {
                                color(Color.white)
                            }
                        }
                        onClick { currentTab = ObjectsTab.OWNED }
                    }
                ) {
                    Text("All")
                }
                
                // Marketplace tab
                Div(
                    attrs = {
                        style {
                            padding(12.px, 16.px)
                            cursor("pointer")
                            if (currentTab == ObjectsTab.MARKETPLACE) {
                                property("border-bottom", "2px solid ${Theme.colors.primary}")
                                color(Theme.colors.primary)
                            } else {
                                color(Color.white)
                            }
                        }
                        onClick { currentTab = ObjectsTab.MARKETPLACE }
                    }
                ) {
                    Text("Marketplace")
                }
            }
            
            // Tab content container
            Div(
                attrs = {
                    style {
                        property("flex-grow", "1")
                        property("overflow", "hidden")
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                    }
                }
            ) {
                // Tab content
                when (currentTab) {
                    ObjectsTab.WORLD -> {
                        // World tab content - list all objects in the world
                        WorldTabContent(
                            objects = worldObjects,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onRemove = { objectId -> removeObject(objectId) }
                        )
                    }
                    ObjectsTab.OWNED -> {
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
    }
}
