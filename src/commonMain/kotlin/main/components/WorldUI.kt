@file:OptIn(ExperimentalTime::class)

package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import main.api.ObjectApi
import main.api.UserApi
import main.theme.Theme
import models.Object
import models.User
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.bottom
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
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.js.Promise
import kotlin.random.Random
import kotlin.time.ExperimentalTime

/**
 * A component that renders a responsive UI overlay for the 3D world scene.
 * Includes UI elements for both mobile (<=960px) and desktop views.
 */
@Composable
fun WorldUI() {
    // State for UI elements
    var isChatOpen by remember { mutableStateOf(false) }
    var isInventoryOpen by remember { mutableStateOf(false) }
    var isProfileOpen by remember { mutableStateOf(false) }
    var isWorldEditOpen by remember { mutableStateOf(false) }
    var isObjectEditorOpen by remember { mutableStateOf(false) }
    var isLogicEditorOpen by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    
    // State for the current object being edited
    var currentEditingObject by remember { mutableStateOf<Object?>(null) }
    
    // State to track if we're on mobile
    val isMobile = remember { mutableStateOf(window.innerWidth <= 960) }
    
    // State for current user
    var currentUser by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Fetch current user on component initialization
    DisposableEffect(Unit) {
        // Use the UserApi to fetch the current user
        isLoading = true
        errorMessage = ""
        
        UserApi.getCurrentUser()
            .then { user: User ->
                currentUser = user
                isLoading = false
                Promise.resolve(Unit)
            }
            .catch { error: dynamic ->
                console.error("Error fetching user", error)
                errorMessage = error.message ?: "Failed to load user"
                isLoading = false
                Promise.resolve(Unit)
            }
        
        onDispose { }
    }
    
    // Update isMobile state on window resize
    DisposableEffect(Unit) {
        val resizeListener: (dynamic) -> Unit = {
            isMobile.value = window.innerWidth <= 960
        }
        
        window.addEventListener("resize", resizeListener)
        
        onDispose {
            window.removeEventListener("resize", resizeListener)
        }
    }
    
    // Function to toggle fullscreen mode
    fun toggleFullscreen() {
        try {
            val doc = document.asDynamic()
            
            if (doc.fullscreenElement == null) {
                // Enter fullscreen mode
                document.documentElement.asDynamic().requestFullscreen()
                // State will be updated by the fullscreenchange event listener
            } else {
                // Exit fullscreen mode
                doc.exitFullscreen()
                // State will be updated by the fullscreenchange event listener
            }
        } catch (e: dynamic) {
            console.error("Fullscreen API error: ", e)
        }
    }
    
    // Update fullscreen state when fullscreen changes
    DisposableEffect(Unit) {
        val fullscreenChangeListener: (dynamic) -> Unit = {
            isFullscreen = document.asDynamic().fullscreenElement != null
        }
        
        document.addEventListener("fullscreenchange", fullscreenChangeListener)
        
        onDispose {
            document.removeEventListener("fullscreenchange", fullscreenChangeListener)
        }
    }
    
    // Main UI overlay container
    Div(
        attrs = {
            style {
                position(Position.Fixed)
                top(0.px)
                left(0.px)
                width(100.percent)
                height(100.vh)
                // Make the div non-interactive for mouse events to allow interaction with the 3D scene
                property("pointer-events", "none")
            }
        }
    ) {
        // World name and subtitle (center top)
        Div(
            attrs = {
                style {
                    position(Position.Absolute)
                    top(20.px)
                    left(50.percent)
                    property("transform", "translateX(-50%)")
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    alignItems(AlignItems.Center)
                    // Make text elements interactive
                    property("pointer-events", "auto")
                }
            }
        ) {
            H2(
                attrs = {
                    style {
                        margin(0.px)
                        color(Color.white)
                        property("text-shadow", "0px 0px 4px rgba(0, 0, 0, 0.5)")
                    }
                }
            ) {
                Text("Jacob's House")
            }
            
            P(
                attrs = {
                    style {
                        margin(0.px)
                        color(Color.white)
                        property("text-shadow", "0px 0px 4px rgba(0, 0, 0, 0.5)")
                    }
                }
            ) {
                Text("Thảo Điền")
            }
        }
        
        // Top right UI elements (notifications, fullscreen, and edit world)
        Div(
            attrs = {
                style {
                    position(Position.Absolute)
                    top(20.px)
                    // Adjust right position when chat is open on desktop
                    if (isChatOpen && !isMobile.value) {
                        right(340.px) // 20px original padding + 320px chat panel width
                    } else {
                        right(20.px)
                    }
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    property("gap", "10px")
                }
            }
        ) {
            // Notifications button
            CircleButton(
                onClick = { /* Handle notifications click */ },
                icon = "🔔"
            )
            
            // Fullscreen button
            CircleButton(
                onClick = { toggleFullscreen() },
                icon = if (isFullscreen) "⤓" else "⤢"
            )
            
            // Edit world button
            CircleButton(
                onClick = { isWorldEditOpen = !isWorldEditOpen },
                icon = "✏️"
            )
            
            // Create object button
            CircleButton(
                onClick = { isObjectEditorOpen = !isObjectEditorOpen },
                icon = "🧩"
            )
        }
        
        // Bottom right UI elements (profile and chat)
        Div(
            attrs = {
                style {
                    position(Position.Absolute)
                    bottom(20.px)
                    // Adjust right position when chat is open on desktop
                    if (isChatOpen && !isMobile.value) {
                        right(340.px) // 20px original padding + 320px chat panel width
                    } else {
                        right(20.px)
                    }
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    property("gap", "10px")
                }
            }
        ) {
            // World Chat button
            CircleButton(
                onClick = { isChatOpen = !isChatOpen },
                icon = "💬"
            )
            
            // My profile button
            CircleButton(
                onClick = { /* Handle profile click */ },
                icon = "👤"
            )
        }
        
        // Inventory tray (center bottom)
        Div(
            attrs = {
                style {
                    position(Position.Absolute)
                    bottom(20.px)
                    left(50.percent)
                    property("transform", "translateX(-50%)")
                    display(DisplayStyle.Flex)
                    property("gap", "4px")
                }
            }
        ) {
            // Create 5 inventory boxes
            repeat(5) { index ->
                InventoryBox(
                    onClick = { 
                        if (index == 4) {
                            // Last box opens inventory dialog
                            isInventoryOpen = true
                        }
                    },
                    content = if (index == 4) "+" else ""
                )
            }
        }
        
        // World Chat panel/dialog
        if (isChatOpen) {
            if (isMobile.value) {
                // Mobile: Dialog
                ChatDialog(onClose = { isChatOpen = false })
            } else {
                // Desktop: Side panel
                ChatPanel(onClose = { isChatOpen = false })
            }
        }
        
        // World Edit panel/dialog
        if (isWorldEditOpen) {
            if (isMobile.value) {
                // Mobile: Dialog
                WorldEditDialog(
                    currentWorld = null, // For now, always create new world
                    onClose = { isWorldEditOpen = false },
                    onSave = { world ->
                        // Handle saved world
                        console.log("World saved: ${world.title}")
                    }
                )
            } else {
                // Desktop: Side panel
                WorldEditPanel(
                    currentWorld = null, // For now, always create new world
                    onClose = { isWorldEditOpen = false },
                    onSave = { world ->
                        // Handle saved world
                        console.log("World saved: ${world.title}")
                    }
                )
            }
        }
        
        // Object Editor dialog
        if (isObjectEditorOpen) {
            // Get current world ID from the URL path
            val currentWorldId = window.location.pathname.substring(1)
            
            ObjectEditorDialog(
                objectModel = null, // For now, always create new object
                worldId = currentWorldId,
                onClose = { isObjectEditorOpen = false },
                onSave = { obj ->
                    // Handle saved object
                    console.log("Object saved: ${obj.title}")
                    isObjectEditorOpen = false
                },
                onOpenLogicEditor = { obj ->
                    // Open the Logic Editor dialog with the object
                    currentEditingObject = obj
                    isLogicEditorOpen = true
                }
            )
        }
        
        // Logic Editor dialog
        if (isLogicEditorOpen && currentEditingObject != null) {
            // Get current world ID from the URL path
            val currentWorldId = window.location.pathname.substring(1)
            
            LogicEditorDialog(
                objectModel = currentEditingObject,
                worldId = currentWorldId,
                onClose = { 
                    isLogicEditorOpen = false 
                    currentEditingObject = null
                },
                onSave = { updatedObj ->
                    // Handle saved object with updated logic
                    console.log("Object logic updated: ${updatedObj.title}")
                    isLogicEditorOpen = false
                    currentEditingObject = null
                }
            )
        }
        
        // Inventory dialog
        if (isInventoryOpen) {
            InventoryDialog(onClose = { isInventoryOpen = false })
        }
        
        // Profile dialog
        if (isProfileOpen && currentUser != null) {
            if (currentUser!!.isPlayer) {
                // Player dialog for logout
                PlayerDialog(
                    user = currentUser!!,
                    onClose = { isProfileOpen = false },
                    onLogout = {
                        // Implement logout functionality
                        // For now, just create a new visitor user
                        currentUser = User(
                            username = "visitor-" + Random.nextInt(1000),
                            isPlayer = false,
                            visitorId = "visitor-" + Random.nextInt(1000)
                        ).apply {
                            key = "visitor-" + Random.nextInt(1000)
                        }
                        isProfileOpen = false
                    }
                )
            } else {
                // Visitor dialog for login/registration
                VisitorDialog(
                    onClose = { isProfileOpen = false },
                    onRegister = { username, password ->
                        // Implement registration functionality
                        // For now, just create a new player user
                        if (username.isNotEmpty() && password.length >= 8) {
                            currentUser = User(
                                username = username,
                                isPlayer = true,
                                visitorId = currentUser!!.visitorId
                            ).apply {
                                key = "player-" + Random.nextInt(1000)
                            }
                            isProfileOpen = false
                        }
                    },
                    onLogin = { username, password ->
                        // Implement login functionality
                        // For now, just create a new player user
                        if (username.isNotEmpty() && password.isNotEmpty()) {
                            currentUser = User(
                                username = username,
                                isPlayer = true,
                                visitorId = currentUser!!.visitorId
                            ).apply {
                                key = "player-" + Random.nextInt(1000)
                            }
                            isProfileOpen = false
                        }
                    }
                )
            }
        }
        
        // Bottom text with 8px font size
        Div(
            attrs = {
                style {
                    position(Position.Absolute)
                    bottom(4.px)
                    left(4.px)
                    fontSize(8.px)
                    color(Color.white)
                    property("text-shadow", "0px 0px 2px rgba(0, 0, 0, 0.7)")
                    // Make text elements interactive
                    property("pointer-events", "auto")
                }
            }
        ) {
            Text("Saigon.Quest v0.1 - © 2025 Saigon.Quest")
        }
    }
}

/**
 * A circular button with a red dot indicator.
 */
@Composable
fun CircleButton(onClick: () -> Unit, icon: String) {
    Div(
        attrs = {
            style {
                width(48.px)
                height(48.px)
                property("border-radius", "50%")
                backgroundColor(Color.white)
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.Center)
                alignItems(AlignItems.Center)
                position(Position.Relative)
                cursor("pointer")
                // Make button interactive
                property("pointer-events", "auto")
            }
            onClick { onClick() }
        }
    ) {
        // Button content (icon)
        Text(icon)
        
        // Red dot indicator
        Div(
            attrs = {
                style {
                    position(Position.Absolute)
                    top(4.px)
                    right(4.px)
                    width(8.px)
                    height(8.px)
                    property("border-radius", "50%")
                    backgroundColor(Color.red)
                }
            }
        )
    }
}

/**
 * An inventory box.
 */
@Composable
fun InventoryBox(onClick: () -> Unit, content: String = "") {
    Div(
        attrs = {
            style {
                width(48.px)
                height(48.px)
                backgroundColor(rgba(255, 255, 255, 0.2))
                property("border", "1px solid rgba(255, 255, 255, 0.5)")
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.Center)
                alignItems(AlignItems.Center)
                cursor("pointer")
                // Make box interactive
                property("pointer-events", "auto")
            }
            onClick { onClick() }
        }
    ) {
        Text(content)
    }
}

/**
 * A chat panel for desktop view.
 */
@Composable
fun ChatPanel(onClose: () -> Unit) {
    Div(
        attrs = {
            style {
                position(Position.Absolute)
                top(0.px)
                right(0.px)
                width(320.px)
                height(100.vh)
                backgroundColor(rgba(0, 0, 0, 0.7))
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                // Make panel interactive
                property("pointer-events", "auto")
            }
        }
    ) {
        // Chat header
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
                Text("World Chat")
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
        
        // Chat messages (currently empty)
        Div(
            attrs = {
                style {
                    property("flex-grow", "1")
                    padding(16.px)
                    property("overflow-y", "auto")
                }
            }
        ) {
            // Empty chat message placeholder
            P(
                attrs = {
                    style {
                        color(rgba(255, 255, 255, 0.5))
                        property("text-align", "center")
                    }
                }
            ) {
                Text("No messages yet")
            }
        }
        
        // Chat input
        Div(
            attrs = {
                style {
                    padding(16.px)
                    display(DisplayStyle.Flex)
                    property("gap", "8px")
                    property("border-top", "1px solid rgba(255, 255, 255, 0.2)")
                }
            }
        ) {
            // Emote button
            Div(
                attrs = {
                    style {
                        width(40.px)
                        height(40.px)
                        backgroundColor(rgba(255, 255, 255, 0.1))
                        property("border-radius", "4px")
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.Center)
                        alignItems(AlignItems.Center)
                        cursor("pointer")
                    }
                }
            ) {
                Text("😀")
            }
            
            // Text input
            Input(
                type = InputType.Text,
                attrs = {
                    style {
                        property("flex-grow", "1")
                        padding(8.px)
                        backgroundColor(rgba(255, 255, 255, 0.1))
                        property("border", "0")
                        property("border-radius", "4px")
                        color(Color.white)
                    }
                    placeholder("Type a message...")
                }
            )
            
            // Send button
            Button(
                attrs = {
                    style {
                        padding(8.px, 16.px)
                        backgroundColor(Theme.colors.primary)
                        color(Theme.colors.onPrimary)
                        property("border", "0")
                        property("border-radius", "4px")
                        cursor("pointer")
                    }
                }
            ) {
                Text("Send")
            }
        }
        
        // Emote picker (hidden by default)
        Div(
            attrs = {
                style {
                    display(DisplayStyle.None)
                    position(Position.Absolute)
                    bottom(80.px)
                    left(16.px)
                    backgroundColor(rgba(0, 0, 0, 0.9))
                    padding(8.px)
                    property("border-radius", "4px")
                    display(DisplayStyle.Grid)
                    property("grid-template-columns", "repeat(5, 1fr)")
                    property("gap", "8px")
                }
            }
        ) {
            // Sample emojis
            listOf("😀", "😂", "😍", "🎮", "👍").forEach { emoji ->
                Div(
                    attrs = {
                        style {
                            padding(8.px)
                            cursor("pointer")
                            property("text-align", "center")
                        }
                    }
                ) {
                    Text(emoji)
                }
            }
        }
    }
}

/**
 * A chat dialog for mobile view.
 */
@Composable
fun ChatDialog(onClose: () -> Unit) {
    Div(
        attrs = {
            style {
                position(Position.Fixed)
                top(0.px)
                left(0.px)
                width(100.percent)
                height(100.vh)
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
                    property("max-width", "400px")
                    height(80.vh)
                    backgroundColor(rgba(30, 30, 30, 0.95))
                    property("border-radius", "8px")
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                }
            }
        ) {
            // Chat header
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
                    Text("World Chat")
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
            
            // Chat messages (currently empty)
            Div(
                attrs = {
                    style {
                        property("flex-grow", "1")
                        padding(16.px)
                        property("overflow-y", "auto")
                    }
                }
            ) {
                // Empty chat message placeholder
                P(
                    attrs = {
                        style {
                            color(rgba(255, 255, 255, 0.5))
                            property("text-align", "center")
                        }
                    }
                ) {
                    Text("No messages yet")
                }
            }
            
            // Chat input
            Div(
                attrs = {
                    style {
                        padding(16.px)
                        display(DisplayStyle.Flex)
                        property("gap", "8px")
                        property("border-top", "1px solid rgba(255, 255, 255, 0.2)")
                    }
                }
            ) {
                // Emote button
                Div(
                    attrs = {
                        style {
                            width(40.px)
                            height(40.px)
                            backgroundColor(rgba(255, 255, 255, 0.1))
                            property("border-radius", "4px")
                            display(DisplayStyle.Flex)
                            justifyContent(JustifyContent.Center)
                            alignItems(AlignItems.Center)
                            cursor("pointer")
                        }
                    }
                ) {
                    Text("😀")
                }
                
                // Text input
                Input(
                    type = InputType.Text,
                    attrs = {
                        style {
                            property("flex-grow", "1")
                            padding(8.px)
                            backgroundColor(rgba(255, 255, 255, 0.1))
                            property("border", "0")
                            property("border-radius", "4px")
                            color(Color.white)
                        }
                        placeholder("Type a message...")
                    }
                )
                
                // Send button
                Button(
                    attrs = {
                        style {
                            padding(8.px, 16.px)
                            backgroundColor(Theme.colors.primary)
                            color(Theme.colors.onPrimary)
                            property("border", "0")
                            property("border-radius", "4px")
                            cursor("pointer")
                        }
                    }
                ) {
                    Text("Send")
                }
            }
        }
    }
}

/**
 * An inventory dialog that displays the user's objects and allows them to be dragged into the world.
 */
@Composable
fun InventoryDialog(onClose: () -> Unit) {
    // State for objects
    var objects by remember { mutableStateOf<List<Object>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Get current world ID from the URL path
    val currentWorldId = window.location.pathname.substring(1)
    
    // Load objects when the dialog is opened
    DisposableEffect(Unit) {
        isLoading = true
        errorMessage = ""
        
        ObjectApi.getObjectsByWorldId(currentWorldId)
            .then { objectList ->
                objects = objectList
                isLoading = false
                Promise.resolve(Unit)
            }
            .catch { error ->
                console.error("Error loading objects", error)
                errorMessage = error.message ?: "Failed to load objects"
                isLoading = false
                Promise.resolve(Unit)
            }
        
        onDispose { }
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
        event.dataTransfer.setData("application/json", json.encodeToString(obj))
        // Set a custom drag image (optional)
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
    
    Div(
        attrs = {
            style {
                position(Position.Fixed)
                top(0.px)
                left(0.px)
                width(100.percent)
                height(100.vh)
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
                    height(80.vh)
                    backgroundColor(rgba(30, 30, 30, 0.95))
                    property("border-radius", "8px")
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                }
            }
        ) {
            // Inventory header
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
                
                // Create new object button
                Button(
                    attrs = {
                        style {
                            backgroundColor(rgba(0, 120, 255, 0.7))
                            color(Color.white)
                            property("border", "0")
                            property("border-radius", "4px")
                            padding(4.px, 8.px)
                            marginRight(8.px)
                            cursor("pointer")
                            fontSize(14.px)
                        }
                        onClick { 
                            // Open object editor dialog
                            window.dispatchEvent(Event("openObjectEditor"))
                            onClose()
                        }
                    }
                ) {
                    Text("+ New")
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
            
            // Loading or error message
            if (isLoading) {
                Div(
                    attrs = {
                        style {
                            padding(16.px)
                            color(Color.white)
                            property("text-align", "center")
                        }
                    }
                ) {
                    Text("Loading objects...")
                }
            } else if (errorMessage.isNotEmpty()) {
                Div(
                    attrs = {
                        style {
                            padding(16.px)
                            color(Color.red)
                            property("text-align", "center")
                        }
                    }
                ) {
                    Text(errorMessage)
                }
            } else if (objects.isEmpty()) {
                Div(
                    attrs = {
                        style {
                            padding(16.px)
                            color(rgba(255, 255, 255, 0.7))
                            property("text-align", "center")
                        }
                    }
                ) {
                    Text("No objects yet. Click '+ New' to create one.")
                }
            } else {
                // Inventory grid
                Div(
                    attrs = {
                        style {
                            padding(16.px)
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
                                        // Make box interactive
                                        property("pointer-events", "auto")
                                        // Make box draggable
                                        property("draggable", "true")
                                    }
                                    // Set up drag events
                                    onDragStart { event ->
                                        // Use the handleDragStart function
                                        handleDragStart(event.nativeEvent, obj)
                                    }
                                    onClick { 
                                        // Open object editor dialog with this object
                                        window.asDynamic().currentEditingObject = obj
                                        window.dispatchEvent(Event("openObjectEditor"))
                                        onClose()
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
}
