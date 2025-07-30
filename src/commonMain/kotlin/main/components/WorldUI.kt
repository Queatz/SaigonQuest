@file:OptIn(ExperimentalTime::class)

package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import main.api.NotificationApi
import main.api.UserApi
import main.api.WorldApi
import main.components.inventory.InventoryDialog
import main.components.nodeeditor.LogicEditorDialog
import main.components.`object`.ObjectsDialog
import main.components.`object`.ObjectsPanel
import main.theme.Styles
import main.components.WorldEdit
import models.Object
import models.User
import models.World
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.css.bottom
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import main.components.WorldTitle


/**
 * A component that renders a responsive UI overlay for the 3D world scene.
 * Includes UI elements for both mobile (<=960px) and desktop views.
 */
@Composable
fun WorldUI() {
    // State for UI elements
    var currentPanel by remember { mutableStateOf<SidePanelState>(SidePanelState.None) }
    
    // State for UI elements that are not part of the panel system
    var isFullscreen by remember { mutableStateOf(false) }
    var isQrDialogOpen by remember { mutableStateOf(false) }
    
    // State for unread notification count
    var unreadNotificationCount by remember { mutableStateOf(0) }
    
    // Coroutine scope for launching async operations
    val scope = rememberCoroutineScope()
    
    // Function to fetch unread notification count
    fun fetchUnreadNotificationCount() {
        scope.launch {
            try {
                val count = NotificationApi.getUnreadCount()
                unreadNotificationCount = count
            } catch (e: Exception) {
                console.error("Error fetching unread notification count", e)
            }
        }
    }
    
    // Fetch unread notification count when the component is first rendered
    LaunchedEffect(Unit) {
        fetchUnreadNotificationCount()
        
        // Periodically refresh the unread count (every 30 seconds)
        while (true) {
            delay(30000) // 30 seconds
            fetchUnreadNotificationCount()
        }
    }
    
    // Refresh unread count when notifications panel is closed
    LaunchedEffect(currentPanel) {
        if (currentPanel != SidePanelState.Notifications) {
            // Small delay to allow any "mark as read" operations to complete
            delay(500)
            fetchUnreadNotificationCount()
        }
    }
    
    // Helper function to update the panel state
    fun updatePanelState(newState: SidePanelState) {
        // Update the currentPanel state
        currentPanel = newState
    }
    
    // Helper function to toggle a panel
    fun togglePanel(panelState: SidePanelState) {
        if (currentPanel == panelState) {
            // If the panel is already open, close it
            updatePanelState(SidePanelState.None)
        } else {
            // Otherwise, open the requested panel
            updatePanelState(panelState)
        }
    }
    
    
    // State for the current object being edited
    var currentEditingObject by remember { mutableStateOf<Object?>(null) }
    
    // State to track if we're on mobile
    val isMobile = remember { mutableStateOf(window.innerWidth <= 960) }
    
    // State for current user
    var currentUser by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    
    // State for current world
    var currentWorld by remember { mutableStateOf<World?>(null) }
    var isWorldLoading by remember { mutableStateOf(true) }
    var worldErrorMessage by remember { mutableStateOf("") }
    
    // Fetch current user on component initialization
    LaunchedEffect(Unit) {
        // Use the UserApi to fetch the current user
        isLoading = true
        errorMessage = ""
        
        try {
            val user = UserApi.getCurrentUser()
            currentUser = user
            isLoading = false
        } catch (error: Exception) {
            console.error("Error fetching user", error)
            errorMessage = error.message ?: "Failed to load user"
            isLoading = false
        }
    }
    
    // Track the current URL path for world loading
    var currentPath by remember { mutableStateOf(window.location.pathname) }
    
    // Update currentPath when URL changes
    DisposableEffect(Unit) {
        val popStateListener: (dynamic) -> Unit = {
            currentPath = window.location.pathname
        }
        
        window.addEventListener("popstate", popStateListener)
        
        onDispose {
            window.removeEventListener("popstate", popStateListener)
        }
    }
    
    // Fetch current world when path changes
    LaunchedEffect(currentPath) {
        // Get current world URL from the pathname
        val worldUrl = currentPath.substring(1)
        isWorldLoading = true
        worldErrorMessage = ""
        
        try {
            val world = WorldApi.getWorldByUrl(worldUrl)
            currentWorld = world
            isWorldLoading = false
        } catch (error: Exception) {
            console.error("Error fetching world", error)
            worldErrorMessage = error.message ?: "Failed to load world"
            isWorldLoading = false
        }
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
            val doc = document
            
            if (doc.fullscreenElement == null) {
                // Enter fullscreen mode
                document.documentElement?.requestFullscreen()
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
            isFullscreen = document.fullscreenElement != null
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
        WorldTitle(
            currentWorld = currentWorld,
            isWorldLoading = isWorldLoading,
            worldErrorMessage = worldErrorMessage,
            currentPanel = currentPanel,
            isMobile = isMobile
        )
        
        // Top right UI elements (notifications, fullscreen, and edit world)
        Div(
            attrs = {
                classes(Styles.topRightContainer)
                style {
                    // Adjust right position when any side panel is open on desktop
                    if ((currentPanel == SidePanelState.Chat || 
                         currentPanel == SidePanelState.WorldEdit || 
                         currentPanel == SidePanelState.Notifications || 
                         currentPanel == SidePanelState.ObjectsPanel ||
                         currentPanel == SidePanelState.Friends) && !isMobile.value) {
                        right(340.px) // 20px original padding + 320px panel width
                    } else {
                        right(20.px)
                    }
                }
            }
        ) {
            // Fullscreen button
            CircleButton(
                onClick = { toggleFullscreen() },
                icon = if (isFullscreen) "⤓" else "⤢",
                badgeCount = 0
            )
            
            // Notifications button
            CircleButton(
                onClick = { togglePanel(SidePanelState.Notifications) },
                icon = "🔔",
                badgeCount = unreadNotificationCount
            )
            
            // Friends button
            CircleButton(
                onClick = { togglePanel(SidePanelState.Friends) },
                icon = "👥",
                badgeCount = 0
            )
            
            // QR code button (not part of the panel system)
            CircleButton(
                onClick = { isQrDialogOpen = true },
                icon = "📱",
                badgeCount = 0
            )
            
            // Edit world button - only visible if the current user is the owner of the world
            if (currentUser?.key == currentWorld?.userId) {
                CircleButton(
                    onClick = { togglePanel(SidePanelState.WorldEdit) },
                    icon = "✏️",
                    badgeCount = 0
                )
                
                // Objects button - only visible if the current user is the owner of the world
                CircleButton(
                    onClick = { togglePanel(SidePanelState.ObjectsPanel) },
                    icon = "🧩",
                    badgeCount = 0
                )
            }
        }
        
        // Bottom right UI elements (profile and chat)
        Div(
            attrs = {
                classes(Styles.bottomRightContainer)
                style {
                    // Adjust right position when any side panel is open on desktop
                    if ((currentPanel == SidePanelState.Chat || 
                         currentPanel == SidePanelState.WorldEdit || 
                         currentPanel == SidePanelState.Notifications || 
                         currentPanel == SidePanelState.ObjectsPanel ||
                         currentPanel == SidePanelState.Friends) && !isMobile.value) {
                        right(340.px) // 20px original padding + 320px panel width
                    } else {
                        right(20.px)
                    }
                }
            }
        ) {
            // World Chat button
            CircleButton(
                onClick = { togglePanel(SidePanelState.Chat) },
                icon = "💬",
                badgeCount = 3,
                isChat = true
            )
            
            // My profile button
            CircleButton(
                onClick = { togglePanel(SidePanelState.Profile) },
                icon = "👤",
                badgeCount = 0
            )
        }
        
        // Inventory tray (center bottom)
        InventoryTray(
            onOpenInventory = { 
                updatePanelState(SidePanelState.Inventory) 
            },
            currentPanel = currentPanel,
            isMobile = isMobile
        )
        
        // Panel rendering based on currentPanel state
        when (currentPanel) {
            SidePanelState.Chat -> {
                // Responsive chat component that adapts between mobile and desktop
                Chat(
                    isMobile = isMobile.value,
                    onClose = { updatePanelState(SidePanelState.None) }
                )
            }
            
            SidePanelState.WorldEdit -> {
                // Responsive world edit component that adapts between mobile and desktop
                WorldEdit(
                    currentWorld = null, // For now, always create new world
                    isMobile = isMobile.value,
                    onClose = { updatePanelState(SidePanelState.None) },
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
                    onClose = { updatePanelState(SidePanelState.None) },
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
                    onClose = { updatePanelState(SidePanelState.None) }
                )
            }
            
            SidePanelState.ObjectsPanel -> {
                if (isMobile.value) {
                    // Mobile: Dialog
                    ObjectsDialog(
                        onClose = { updatePanelState(SidePanelState.None) },
                        onOpenObjectEditor = { 
                            updatePanelState(SidePanelState.ObjectEditor)
                        },
                        objectManager = null // We'll pass the actual ObjectManager in a real implementation
                    )
                } else {
                    // Desktop: Side panel
                    ObjectsPanel(
                        onClose = { updatePanelState(SidePanelState.None) },
                        onOpenObjectEditor = { 
                            updatePanelState(SidePanelState.ObjectEditor)
                        },
                        objectManager = null // We'll pass the actual ObjectManager in a real implementation
                    )
                }
            }
            
            else -> {} // No panel to render
        }
        
        // Object Editor dialog
        if (currentPanel == SidePanelState.ObjectEditor) {
            // Get current world ID from the URL path
            val currentWorldId = window.location.pathname.substring(1)
            
            ObjectEditorDialog(
                objectModel = null, // For now, always create new object
                worldId = currentWorldId,
                onClose = { updatePanelState(SidePanelState.None) },
                onSave = { obj ->
                    // Handle saved object
                    console.log("Object saved: ${obj.title}")
                    updatePanelState(SidePanelState.None)
                },
                onOpenLogicEditor = { obj ->
                    // Open the Logic Editor dialog with the object
                    currentEditingObject = obj
                    updatePanelState(SidePanelState.LogicEditor)
                }
            )
        }
        
        // Logic Editor dialog
        if (currentPanel == SidePanelState.LogicEditor && currentEditingObject != null) {
            // Get current world ID from the URL path
            val currentWorldId = window.location.pathname.substring(1)
            
            LogicEditorDialog(
                objectModel = currentEditingObject,
                worldId = currentWorldId,
                onClose = { 
                    updatePanelState(SidePanelState.None)
                    currentEditingObject = null
                },
                onSave = { updatedObj ->
                    // Handle saved object with updated logic
                    console.log("Object logic updated: ${updatedObj.title}")
                    updatePanelState(SidePanelState.None)
                    currentEditingObject = null
                }
            )
        }
        
        // Inventory dialog
        if (currentPanel == SidePanelState.Inventory) {
            InventoryDialog(onClose = { updatePanelState(SidePanelState.None) })
        }
        
        // QR code dialog - not part of the main panel system, kept separate
        if (isQrDialogOpen) {
            QrDialog(onClose = { isQrDialogOpen = false })
        }
        
        // Profile dialog
        if (currentPanel == SidePanelState.Profile && currentUser != null) {
            if (currentUser!!.isPlayer) {
                // Player dialog for logout
                PlayerDialog(
                    user = currentUser!!,
                    onClose = { updatePanelState(SidePanelState.None) },
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
                        updatePanelState(SidePanelState.None)
                    }
                )
            } else {
                // Visitor dialog for login/registration
                VisitorDialog(
                    onClose = { updatePanelState(SidePanelState.None) },
                    onRegister = { username, password, yearOfBirth, sex ->
                        // Implement registration functionality
                        // For now, just create a new player user
                        if (username.isNotEmpty() && password.length >= 8) {
                            currentUser = User(
                                username = username,
                                isPlayer = true,
                                visitorId = currentUser!!.visitorId,
                                yearOfBirth = yearOfBirth,
                                sex = sex
                            ).apply {
                                key = "player-" + Random.nextInt(1000)
                            }
                            updatePanelState(SidePanelState.None)
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
                            updatePanelState(SidePanelState.None)
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



// SidePanel component has been moved to its own file: SidePanel.kt





