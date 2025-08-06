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
import app.softwork.routingcompose.Router
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.ChatApi
import main.api.NotificationApi
import main.api.ObjectApi
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WorldApi
import main.components.inventory.InventoryTray
import main.theme.Styles
import main.utils.PathUtils
import main.utils.rememberButtonPositionTracker
import main.utils.toggleFullscreen
import models.Object
import models.User
import models.World
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.right
import org.jetbrains.compose.web.dom.Div
import kotlin.random.Random
import kotlin.time.ExperimentalTime


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
    
    // State for print dialog
    var showPrintDialog by remember { mutableStateOf(false) }
    var printImageUrl by remember { mutableStateOf("") }
    
    // State for unread notification count
    var unreadNotificationCount by remember { mutableStateOf(0) }
    
    // State for unread chat count
    var unreadChatCount by remember { mutableStateOf(0) }
    
    // Coroutine scope for launching async operations
    val scope = rememberCoroutineScope()
    val router = Router.current

    // State for the current object being edited
    var currentEditingObject by remember { mutableStateOf<Object?>(null) }

    // State to track if we're on mobile
    val isMobile = remember { mutableStateOf(window.innerWidth <= 960) }

    // State for current user
    var currentUser by remember { mutableStateOf<User?>(null) }

    // Tutorial manager
    val tutorialManager = remember { TutorialManager() }

    // Button positions are now tracked by ButtonPositionTracker in the TutorialBubbleManager

    // State for current world
    var currentWorld by remember { mutableStateOf<World?>(null) }
    var isWorldLoading by remember { mutableStateOf(true) }
    var worldErrorMessage by remember { mutableStateOf("") }
    
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
    
    // Function to fetch unread chat count
    fun fetchUnreadChatCount() {
        currentWorld?.let { world ->
            scope.launch {
                try {
                    val count = ChatApi.getUnreadMessageCount(world.key)
                    unreadChatCount = count
                } catch (e: Exception) {
                    console.error("Error fetching unread chat count for world ${world.key}", e)
                }
            }
        }
    }
    
    // Fetch unread counts when the component is first rendered
    LaunchedEffect(Unit) {
        fetchUnreadNotificationCount()
        fetchUnreadChatCount()
        
        // Periodically refresh the unread counts (every 30 seconds)
        while (true) {
            delay(30000) // 30 seconds
            fetchUnreadNotificationCount()
            fetchUnreadChatCount()
        }
    }
    
    // Refresh unread counts when panels are closed
    LaunchedEffect(currentPanel) {
        if (currentPanel != SidePanelState.Notifications) {
            // Small delay to allow any "mark as read" operations to complete
            delay(500)
            fetchUnreadNotificationCount()
        }
        
        if (currentPanel != SidePanelState.Chat) {
            // Small delay to allow any "mark as read" operations to complete
            delay(500)
            fetchUnreadChatCount()
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
    
    // Fetch current user on component initialization
    LaunchedEffect(Unit) {
        try {
            val user = UserApi.getCurrentUser()
            currentUser = user
        } catch (error: Exception) {
            console.error("Error fetching user", error)
        }
    }
    
    // Track the current URL path for world loading
    var currentPath by remember { mutableStateOf(PathUtils.getCurrentPath(router)) }
    
    // Update currentPath when URL changes
    DisposableEffect(Unit) {
        val popStateListener: (dynamic) -> Unit = {
            currentPath = PathUtils.getCurrentPath(router)
        }
        
        window.addEventListener("popstate", popStateListener)
        
        onDispose {
            window.removeEventListener("popstate", popStateListener)
        }
    }
    
    // Fetch current world when path changes
    LaunchedEffect(currentPath) {
        // Get current world URL from the pathname
        val worldUrl = currentPath.trimStart('/')
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
    
    // Fetch unread chat count when currentWorld changes
    LaunchedEffect(currentWorld) {
        fetchUnreadChatCount()
    }
    
    // Update isMobile state on window resize
    DisposableEffect(Unit) {
        val resizeListener: (dynamic) -> Unit = {
            isMobile.value = window.innerWidth <= 960
            // Button positions are now tracked by ButtonPositionTracker
        }
        
        // Handle object double-click events
        val objectDoubleClickListener: (dynamic) -> Unit = { event ->
            // Get the object ID from the event detail
            val objectId = event.detail.objectId as String
            console.log("[OBJECT_DOUBLE_CLICK] Received event for object: $objectId")
            
            // Open the object editor dialog
            scope.launch {
                try {
                    // Fetch the object by ID
                    val obj = ObjectApi.getObject(objectId)
                    
                    // Set the current editing object and update panel state
                    currentEditingObject = obj
                    updatePanelState(SidePanelState.ObjectEditor)
                    
                    console.log("[OBJECT_DOUBLE_CLICK] Opened editor for object: ${obj.title}")
                } catch (error: Exception) {
                    console.error("[OBJECT_DOUBLE_CLICK] Error fetching object", error)
                }
            }
        }
        
        window.addEventListener("resize", resizeListener)
        window.addEventListener("objectDoubleClick", objectDoubleClickListener)
        
        // Button positions are now tracked by ButtonPositionTracker
        
        onDispose {
            window.removeEventListener("resize", resizeListener)
            window.removeEventListener("objectDoubleClick", objectDoubleClickListener)
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
    WorldUIContainer {
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
                iconIndex = if (isFullscreen) 1 else 0, // 0: enter fullscreen, 1: exit fullscreen
                badgeCount = 0
            )

            if (currentUser?.isPlayer == true) {
                // Notifications button
                CircleButton(
                    onClick = { togglePanel(SidePanelState.Notifications) },
                    iconIndex = 2, // 2: notifications
                    badgeCount = unreadNotificationCount
                )

                // Friends button
                CircleButton(
                    onClick = { togglePanel(SidePanelState.Friends) },
                    iconIndex = 3, // 3: friends
                    badgeCount = 0
                )
            }

            // Edit world button - only visible if the current user is the owner of the world
            if (currentUser?.key == currentWorld?.userId) {
                CircleButton(
                    onClick = { togglePanel(SidePanelState.WorldEdit) },
                    iconIndex = 4, // 4: edit world
                    badgeCount = 0
                )
                
                // Objects button - only visible if the current user is the owner of the world
                CircleButton(
                    onClick = { togglePanel(SidePanelState.ObjectsPanel) },
                    iconIndex = 5, // 5: edit objects
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
                    if (isLayoutAdjustmentNeeded(currentPanel, isMobile)) {
                        right(340.px) // 20px original padding + 320px panel width
                    } else {
                        right(20.px)
                    }
                }
            }
        ) {
            // QR code button (not part of the panel system)
            CircleButton(
                onClick = { 
                    isQrDialogOpen = true
                    // If this is the current tutorial step, advance to the next step
                    if (tutorialManager.currentStep is TutorialStep.ShareWorld) {
                        tutorialManager.advanceToNextStep()
                    }
                },
                iconIndex = 6, // 6: share / qr code
                badgeCount = 0,
                id = "qrButton"
            )

            // World Chat button
            CircleButton(
                onClick = { 
                    togglePanel(SidePanelState.Chat)
                    // Reset unread count when opening chat
                    if (currentPanel != SidePanelState.Chat) {
                        unreadChatCount = 0
                    }
                    // If this is the current tutorial step, advance to the next step
                    if (tutorialManager.currentStep is TutorialStep.OpenChat) {
                        tutorialManager.advanceToNextStep()
                    }
                },
                iconIndex = 7, // 7: chat
                badgeCount = unreadChatCount,
                isChat = true,
                id = "chatButton"
            )
            
            // My profile button
            CircleButton(
                onClick = { 
                    // If this is the current tutorial step, advance to the next step
                    if (tutorialManager.currentStep is TutorialStep.CreatePlayer) {
                        tutorialManager.advanceToNextStep()
                    }
                    togglePanel(SidePanelState.Profile) 
                },
                iconIndex = 8, // 8: my profile
                badgeCount = 0,
                id = "profileButton"
            )
        }
        
        // Inventory tray (center bottom)
        if (currentUser?.isPlayer == true) {
            InventoryTray(
                onOpenInventory = {
                    updatePanelState(SidePanelState.Inventory)
                },
                currentPanel = currentPanel,
                isMobile = isMobile
            )
        }
        
        // Side Panel
        SidePanel(
            currentPanel = currentPanel,
            isMobile = isMobile,
            currentWorld = currentWorld,
            currentEditingObject = currentEditingObject,
            onClose = { updatePanelState(SidePanelState.None) },
            onOpenObjectEditor = { updatePanelState(SidePanelState.ObjectEditor) },
            onEditObject = { objectId ->
                // Get the object by ID and open the editor with it
                scope.launch {
                    try {
                        val obj = ObjectApi.getObject(objectId)
                        currentEditingObject = obj
                        updatePanelState(SidePanelState.ObjectEditor)
                    } catch (error: Exception) {
                        console.error("Error fetching object", error)
                    }
                }
            },
            onSaveObject = { obj ->
                // Handle saved object
                if (currentPanel == SidePanelState.ObjectEditor) {
                    updatePanelState(SidePanelState.None)
                    currentEditingObject = null // Reset current editing object when saving
                } else if (currentPanel == SidePanelState.LogicEditor) {
                    // Update the current editing object with the updated logic
                    currentEditingObject = obj
                    // Return to the object editor panel
                    updatePanelState(SidePanelState.ObjectEditor)
                }
            },
            onOpenLogicEditor = { obj ->
                // Open the Logic Editor dialog with the object
                currentEditingObject = obj
                updatePanelState(SidePanelState.LogicEditor)
            },
            scope = scope
        )
        
        // QR code dialog - not part of the main panel system, kept separate
        if (isQrDialogOpen) {
            // Get player position from SceneManager if available
            val playerPosition = SceneManager.getPlayerController()?.getPlayerPosition()
            
            QrDialog(
                onClose = { isQrDialogOpen = false },
                onPrint = { imageUrl ->
                    printImageUrl = imageUrl
                    showPrintDialog = true
                },
                playerPosition = playerPosition,
                worldTitle = currentWorld?.title ?: "Saigon.Quest",
                worldSubtitle = currentWorld?.subtitle ?: "Local worlds",
                worldDescription = currentWorld?.description ?: ""
            )
        }
        
        // Print dialog - shown when print button is clicked in QR dialog
        if (showPrintDialog) {
            PrintDialog(
                imageUrl = printImageUrl,
                onClose = { showPrintDialog = false }
            )
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
                            
                            // Notify other clients that this visitor is now a player
                            scope.launch {
                                try {
                                    WebSocketApi.updatePlayer(username, true)
                                    
                                    // Update the player's own username text
                                    SceneManager.getPlayerController()?.updatePlayerName(username)
                                } catch (e: Exception) {
                                    console.error("Error updating player status", e)
                                }
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
                            
                            // Notify other clients that this visitor is now a player
                            scope.launch {
                                try {
                                    WebSocketApi.updatePlayer(username, true)
                                    
                                    // Update the player's own username text
                                    SceneManager.getPlayerController()?.updatePlayerName(username)
                                } catch (e: Exception) {
                                    console.error("Error updating player status", e)
                                }
                            }
                            
                            updatePanelState(SidePanelState.None)
                        }
                    }
                )
            }
        }
        
        // Bottom text with copyright information
        BottomText()
        
        // Tutorial bubbles for non-player users - using the TutorialBubbleManager for more reusable code
        if (currentUser != null && !currentUser!!.isPlayer) {
            // Create a list of button IDs to track
            val buttonIds = remember { listOf("profileButton", "chatButton", "qrButton") }
            
            // Create a button position tracker
            val buttonPositionTracker = rememberButtonPositionTracker(buttonIds)
            
            // Render tutorial bubbles using the TutorialBubbleManager
            TutorialBubbleManager(
                tutorialManager = tutorialManager,
                buttonPositionTracker = buttonPositionTracker,
                isVisible = true,
                defaultPosition = BubblePosition.LEFT,
                horizontalOffset = -150,
                verticalOffset = -15
            )
        }
    }
}
