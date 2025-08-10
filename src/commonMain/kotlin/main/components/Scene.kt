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
import io.ktor.client.plugins.ResponseException
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lib.Engine
import lib.Scene
import lib.initializeBabylonJSLoaders
import main.api.ApiClient
import main.api.FriendApi
import main.api.NotificationApi
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.components.camera.CameraController
import main.components.input.InputHandler
import main.components.objects.SceneObjectManager
import main.components.player.FriendshipStatus
import main.components.player.OtherPlayerDialog
import main.components.player.OtherPlayersController
import main.components.player.PlayerController
import main.components.world.WorldLoader
import main.components.world.WorldObjectManager
import main.theme.Styles
import main.utils.DataType
import main.utils.createObjectDropEvent
import models.NotificationType
import models.OpenUrlData
import org.jetbrains.compose.web.css.outline
import org.jetbrains.compose.web.dom.Canvas
import org.w3c.dom.DragEvent
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import kotlin.random.Random

/**
 * Helper function to dispatch an object drop event with the necessary data.
 * 
 * @param x The x coordinate of the drop
 * @param y The y coordinate of the drop
 * @param data The JSON data of the dropped object
 * @param dataType The type of data being dropped (ITEM or OBJECT)
 */
private fun dispatchObjectDropEvent(
    x: Int,
    y: Int,
    data: String,
    dataType: DataType = DataType.OBJECT
) {
    // Create a custom event using the utility function
    val customEvent = createObjectDropEvent(x, y, data, dataType)
    
    // Dispatch the event
    window.dispatchEvent(customEvent)
}

/**
 * A component that renders a BabylonJS scene with a 3D model.
 * Uses Physics-Based Rendering (PBR) Pipeline and PBR Materials.
 * 
 * @param url The URL of the world to load. Empty string is valid for Home World.
 */
@Composable
fun SceneComponent(url: String) {
    // Create a unique ID for the canvas
    val canvasId = remember { "babylon-canvas-${Random.nextInt(10000)}" }
    var canvasRef by remember { mutableStateOf(null as HTMLCanvasElement?) }

    // State to track if the world is loaded
    val isLoaded = remember { mutableStateOf(false) }
    
    // State to track if world loading failed
    val loadingFailed = remember { mutableStateOf(false) }
    
    // State to store error message when loading fails
    val errorMessage = remember { mutableStateOf("No world found") }
    
    // State to store the current world
    val currentWorld = remember { mutableStateOf<models.World?>(null) }
    
    // State to track if the world edit panel is visible
    val showWorldEditPanel = remember { mutableStateOf(false) }
    
    // State to track if the login dialog is visible
    val showLoginDialog = remember { mutableStateOf(false) }
    
    // State to track if we're on mobile
    val isMobile = remember { mutableStateOf(window.innerWidth <= 960) }
    
    // State for other player dialog
    val showOtherPlayerDialog = remember { mutableStateOf(false) }
    val otherPlayerUsername = remember { mutableStateOf("") }
    val otherPlayerIsPlayer = remember { mutableStateOf(false) }
    val isCurrentUserPlayer = remember { mutableStateOf(true) } // Assume the current user is a player for now
    val otherPlayerFriendshipStatus = remember { mutableStateOf(FriendshipStatus.UNKNOWN) }
    val otherPlayerIsLoading = remember { mutableStateOf(false) }
    val otherPlayerErrorMessage = remember { mutableStateOf<String?>(null) }
    val otherPlayerId = remember { mutableStateOf("") }
    
    // State for open URL dialog
    val showOpenUrlDialog = remember { mutableStateOf(false) }
    val openUrlValue = remember { mutableStateOf("") }
    val openUrlCallback = remember { mutableStateOf<(() -> Unit)?>(null) }
    
    // Coroutine scope for friendship-related operations
    val scope = rememberCoroutineScope()
    
    // State to track low graphics mode
    val isLowGraphicsMode = remember { GraphicsSettings._isLowGraphicsModeEnabled }
    
    // Get the router for navigation
    val router = Router.current
    
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
    
    // Check if the current user is a player
    LaunchedEffect(Unit) {
        try {
            val currentUser = UserApi.getCurrentUser()
            isCurrentUserPlayer.value = currentUser.isPlayer
            console.log("Current user is a ${if (currentUser.isPlayer) "Player" else "Visitor"} account")
        } catch (e: Exception) {
            console.error("Error checking current user type: ${e.message}")
            isCurrentUserPlayer.value = false // Default to false if there's an error
        }
    }

    // Set up the canvas with CSS
    // Use isLowGraphicsMode.value as a key to ensure the style is updated when the mode changes
    val currentGraphicsMode = isLowGraphicsMode.value
    
    Canvas(
        attrs = {
            id(canvasId)
            
            // Apply base canvas styles
            classes(Styles.canvasBase)

            style {
                outline("none")
            }
            
            // Apply low graphics mode styles if enabled
            if (currentGraphicsMode) {
                classes(Styles.canvasLowGraphics)
                console.log("Applied pixelated rendering to canvas (low graphics mode)")
            } else {
                console.log("Using standard rendering for canvas (normal graphics mode)")
            }

            ref {
                canvasRef = it
                it.focus()

                onDispose {
                    canvasRef = null
                }
            }

            // Add drag and drop event listeners
            onDragOver { event -> 
                event.preventDefault()
            }
            onDrop { event ->
                event.preventDefault()
                
                // Get the necessary data from the event
                val drag = (event.nativeEvent as DragEvent)
                drag.dataTransfer ?: return@onDrop

                val clientX = drag.clientX
                val clientY = drag.clientY
                val data = drag.dataTransfer!!.getData("application/json")
                
                // Get the data type from the event
                val dataTypeString = drag.dataTransfer!!.getData("application/x-data-type")

                console.log("Data type from event: $dataTypeString")

                // Convert the string to a DataType enum
                val dataType = dataTypeString.let { DataType.valueOf(it) }

                // Use the helper function to dispatch the custom event with the data type
                dispatchObjectDropEvent(clientX, clientY, data, dataType)
            }
        }
    )
    
    // Loading screen or error message
    LoadingScreen(
        isLoaded = isLoaded.value,
        loadingFailed = loadingFailed.value,
        errorMessage = errorMessage.value,
        showWorldEditPanel = showWorldEditPanel.value,
        showLoginDialog = showLoginDialog.value,
        onShowWorldEditPanel = { showWorldEditPanel.value = true },
        onShowLoginDialog = { showLoginDialog.value = true },
        onNavigateHome = { router.navigate("/") }
    )
    
    // Show world edit panel if requested
    if (showWorldEditPanel.value) {
        WorldEdit(
            currentWorld = currentWorld.value,
            isMobile = isMobile.value,
            onClose = { showWorldEditPanel.value = false },
            onSave = { newWorld ->
                // After saving, reload the page to see the new world
                window.location.href = "/${newWorld.url}"
            }
        )
    }
    
    // Show login dialog if requested
    if (showLoginDialog.value) {
        VisitorDialogPresenter(
            isVisible = true,
            onClose = {
                showLoginDialog.value = false
            },
            onSuccess = { user ->
                showLoginDialog.value = false
                // After successful login/registration, show the world edit panel
                showWorldEditPanel.value = true
            },
            updatePlayerName = { username ->
                // Update the player's own username text
                SceneManager.getPlayerController()?.updatePlayerName(username)
            },
            showWorldEditPanel = true
        )
    }
    
    // Add the UI overlay when the world is loaded
    if (isLoaded.value) {
        WorldUI()
    }

    LaunchedEffect(isLoaded.value, canvasRef) {
        delay(100)
        canvasRef?.focus()
    }

    // Friendship-related functions
    
    /**
     * Checks the friendship status with another player
     */
    fun checkFriendshipStatus(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            try {
                otherPlayerIsLoading.value = true
                
                // Check if they are already friends
                val isFriend = FriendApi.checkFriendship(otherPlayerId.value)
                if (isFriend) {
                    otherPlayerFriendshipStatus.value = FriendshipStatus.FRIENDS
                    otherPlayerIsLoading.value = false
                    return@launch
                }
                
                // Check if there's a pending friend request from the other player
                val friendRequests = NotificationApi.getNotificationsByType(NotificationType.FRIEND_REQUEST)
                val requestFromFriend = friendRequests.find { notification -> notification.otherUserId == otherPlayerId.value }
                
                if (requestFromFriend != null) {
                    otherPlayerFriendshipStatus.value = FriendshipStatus.REQUEST_RECEIVED
                } else {
                    otherPlayerFriendshipStatus.value = FriendshipStatus.NOT_FRIENDS
                }
            } catch (e: Exception) {
                console.error("Error checking friendship status", e)
                otherPlayerFriendshipStatus.value = FriendshipStatus.NOT_FRIENDS
            } finally {
                otherPlayerIsLoading.value = false
            }
        }
    }
    
    /**
     * Sends a friend request to another player
     */
    fun sendFriendRequest() {
        if (otherPlayerId.value.isEmpty()) {
            otherPlayerErrorMessage.value = "Could not identify player"
            return
        }
        
        otherPlayerIsLoading.value = true
        otherPlayerErrorMessage.value = null
        
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        coroutineScope.launch {
            try {
                val notification = FriendApi.sendFriendRequest(otherPlayerId.value)
                
                if (notification != null) {
                    otherPlayerFriendshipStatus.value = FriendshipStatus.REQUEST_SENT
                } else {
                    otherPlayerErrorMessage.value = "Failed to send friend request"
                }
            } catch (e: Exception) {
                console.error("Error sending friend request", e)
                otherPlayerErrorMessage.value = "Error: ${e.message}"
            } finally {
                otherPlayerIsLoading.value = false
            }
        }
    }
    
    /**
     * Accepts a friend request from another player
     */
    fun acceptFriendRequest() {
        if (otherPlayerId.value.isEmpty()) {
            otherPlayerErrorMessage.value = "Could not identify player"
            return
        }
        
        otherPlayerIsLoading.value = true
        otherPlayerErrorMessage.value = null
        
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        coroutineScope.launch {
            try {
                // Find the notification for this friend request
                val friendRequests = NotificationApi.getNotificationsByType(NotificationType.FRIEND_REQUEST)
                val requestFromFriend = friendRequests.find { notification -> notification.otherUserId == otherPlayerId.value }
                
                if (requestFromFriend != null) {
                    // Accept the friend request
                    val friend = NotificationApi.acceptFriendRequest(requestFromFriend)
                    
                    if (friend != null) {
                        otherPlayerFriendshipStatus.value = FriendshipStatus.FRIENDS
                    } else {
                        otherPlayerErrorMessage.value = "Failed to accept friend request"
                    }
                } else {
                    otherPlayerErrorMessage.value = "Friend request not found"
                }
            } catch (e: Exception) {
                console.error("Error accepting friend request", e)
                otherPlayerErrorMessage.value = "Error: ${e.message}"
            } finally {
                otherPlayerIsLoading.value = false
            }
        }
    }
    
    /**
     * Removes a friend
     */
    fun removeFriend() {
        if (otherPlayerId.value.isEmpty()) {
            otherPlayerErrorMessage.value = "Could not identify player"
            return
        }
        
        otherPlayerIsLoading.value = true
        otherPlayerErrorMessage.value = null
        
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        coroutineScope.launch {
            try {
                val success = FriendApi.removeFriend(otherPlayerId.value)
                
                if (success) {
                    otherPlayerFriendshipStatus.value = FriendshipStatus.NOT_FRIENDS
                    // Close the dialog
                    showOtherPlayerDialog.value = false
                } else {
                    otherPlayerErrorMessage.value = "Failed to remove friend"
                }
            } catch (e: Exception) {
                console.error("Error removing friend", e)
                otherPlayerErrorMessage.value = "Error: ${e.message}"
            } finally {
                otherPlayerIsLoading.value = false
            }
        }
    }

    // Show other player dialog if requested
    if (showOtherPlayerDialog.value) {
        OtherPlayerDialog(
            username = otherPlayerUsername.value,
            isPlayer = otherPlayerIsPlayer.value,
            isCurrentUserPlayer = isCurrentUserPlayer.value,
            friendshipStatus = otherPlayerFriendshipStatus.value,
            isLoading = otherPlayerIsLoading.value,
            errorMessage = otherPlayerErrorMessage.value,
            onAddFriend = {
                scope.launch {
                    when (otherPlayerFriendshipStatus.value) {
                        FriendshipStatus.NOT_FRIENDS -> sendFriendRequest()
                        FriendshipStatus.REQUEST_RECEIVED -> acceptFriendRequest()
                        else -> { /* Do nothing for other states */
                        }
                    }
                }
            },
            onRemoveFriend = {
                removeFriend()
            },
            onClose = {
                // Close the dialog
                showOtherPlayerDialog.value = false
            }
        )
    }

    // Add DialogueBox component for displaying dialogue messages
    DialogueBox()
    
    // Show OpenUrlDialog if requested
    if (showOpenUrlDialog.value) {
        OpenUrlDialog(
            url = openUrlValue.value,
            onConfirm = {
                // Call the callback function if it exists
                openUrlCallback.value?.invoke()
                // Reset the dialog state
                showOpenUrlDialog.value = false
                openUrlCallback.value = null
            },
            onCancel = {
                // Reset the dialog state
                showOpenUrlDialog.value = false
                openUrlCallback.value = null
            }
        )
    }

    // Set up BabylonJS scene
    DisposableEffect(canvasId) {
        // Create a coroutine scope for async operations
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        
        // Get the canvas element
        val canvas = document.getElementById(canvasId) as HTMLCanvasElement

        // Create the engine
        val engine = Engine(canvas, true)
        
        // Set hardware scaling level based on graphics mode
        engine.setHardwareScalingLevel(GraphicsSettings.getEngineScalingFactor())
        
        // Create an observer for graphics mode changes
        val graphicsModeObserver: () -> Unit = {
            // Update hardware scaling level when graphics mode changes
            engine.setHardwareScalingLevel(GraphicsSettings.getEngineScalingFactor())
            console.log("Graphics mode changed. Low graphics mode: ${GraphicsSettings.isLowGraphicsModeEnabled()}, Scaling factor: ${GraphicsSettings.getEngineScalingFactor()}")
        }
        
        // Register the observer with GraphicsSettings
        GraphicsSettings.addObserver(graphicsModeObserver)
        
        // Handle window resize
        val resizeHandler = { _: Event ->
            engine.resize()
        }
        window.addEventListener("resize", resizeHandler)

        // Create the scene
        val scene = Scene(engine)
        
        // Register scene with SceneManager for global access
        SceneManager.setScene(scene)
        
        // Initialize BabylonJS loaders before loading the 3D model
        initializeBabylonJSLoaders()
        
        // Create controllers
        val cameraController = CameraController(scene)
        // Register camera controller with SceneManager for global access
        SceneManager.setCameraController(cameraController)
        
        val playerController = PlayerController(scene)
        // Register player controller with SceneManager for global access
        SceneManager.setPlayerController(playerController)
        
        // Create other players controller for multiplayer
        val otherPlayersController = OtherPlayersController(scene)
        // Register other players controller with SceneManager for global access
        SceneManager.setOtherPlayersController(otherPlayersController)

        val worldLoader = WorldLoader(scene)
        val worldObjectManager = WorldObjectManager(scene)
        val objectManager = SceneObjectManager(
            scene = scene,
            cameraController = cameraController,
            worldObjectManager = worldObjectManager
        )
        
        // Set up shadow manager in the world object manager
        // This needs to be done after world loading since the shadow manager is created during world loading
        WorldLoader.onWorldLoaded {
            // Get the shadow manager from the world loader and set it on the world object manager
            worldLoader.getShadowManager()?.let { shadowManager ->
                worldObjectManager.setShadowManager(shadowManager)
                objectManager.setShadowManager(shadowManager)
                println("[DEBUG] Set shadow manager on world object manager")
            }
        }
        
        // Register object manager with SceneManager for global access
        SceneManager.setObjectManager(objectManager)
        
        // Create input handler after object manager is registered
        val inputHandler = InputHandler(
            scene = scene,
            playerController = playerController,
            cameraController = cameraController,
            onShowOtherPlayerDialog = { username, isPlayer ->
                // Update dialog state
                otherPlayerUsername.value = username
                otherPlayerIsPlayer.value = isPlayer
                otherPlayerFriendshipStatus.value = FriendshipStatus.UNKNOWN
                otherPlayerIsLoading.value = false
                otherPlayerErrorMessage.value = null

                // Get the player ID from the OtherPlayersController
                val otherPlayers = SceneManager.getOtherPlayersController()
                otherPlayerId.value = otherPlayers?.getUserIdForUsername(username) ?: ""

                // Check friendship status if we have a player ID
                if (otherPlayerId.value.isNotEmpty()) {
                    // Check friendship status
                    checkFriendshipStatus(coroutineScope)
                } else {
                    otherPlayerFriendshipStatus.value = FriendshipStatus.NOT_FRIENDS
                }

                // Show the dialog
                showOtherPlayerDialog.value = true
            }
        )

        // Set up object drop handler
        val objectDropHandler: (Event) -> Unit = { event ->
            val detail = event.asDynamic().detail
            val x = detail.x as Int
            val y = detail.y as Int
            val data = detail.data as String
            val dataTypeString = detail.dataType as? String

            // Convert the string to a DataType enum
            val dataType = dataTypeString?.let { DataType.valueOf(it) }

            console.log("[DROP] Dropped a $dataTypeString", event, detail)

            // Get the current world ID
            val worldId = currentWorld.value?.key ?: url
            
            // Use the object manager to handle the drop based on data type
            if (dataType == DataType.ITEM) {
                console.log("Handling inventory item drop")
                objectManager.handleInventoryItemDrop(x, y, data, worldId)
            } else {
                console.log("Handling object drop")
                objectManager.handleObjectDrop(x, y, data, worldId)
            }
        }
        
        // Add event listener for object drop
        window.addEventListener("objectDrop", objectDropHandler)
        
        // Load the world using coroutines
        coroutineScope.launch {
            try {
                // Call the suspend function
                val worldMeshes = worldLoader.loadWorld(url)
                
                // Store the current world
                currentWorld.value = worldLoader.getCurrentWorld() ?: return@launch Unit.also {
                    loadingFailed.value = true
                    errorMessage.value = "Error loading world: World not found or server error"
                }

                inputHandler.currentWorld = currentWorld.value

                // Check if this is a fallback world due to an API error
                if (worldLoader.isApiErrorFallback()) {
                    // Set loading failed to true to show error message
                    loadingFailed.value = true
                    errorMessage.value = "Error loading world: World not found or server error"
                }
                
                // Set world meshes in controllers
                playerController.setWorldMeshes(worldMeshes)
                objectManager.setWorldMeshes(worldMeshes)
                
                // Find a valid starting position for the player
                val startPosition = playerController.findValidStartPosition()
                
                // Create the player using the WorldObjectManager
                playerController.createPlayer(startPosition, worldObjectManager)
                
                // Initialize chat message listener
                console.log("[DEBUG_LOG] Scene: Initializing chat message listener")
                playerController.initializeChatMessageListener()
                
                // Initialize player status display
                console.log("[DEBUG_LOG] Scene: Initializing player status display")
                playerController.initializePlayerStatus()
                console.log("[DEBUG_LOG] Scene: Player status initialization completed")
                
                // Initialize teleport listener
                console.log("[DEBUG_LOG] Scene: Initializing teleport listener")
                playerController.initializeTeleportListener(router)
                console.log("[DEBUG_LOG] Scene: Teleport listener initialization completed")
                
                // Register player mesh as shadow caster and receiver
                playerController.getPlayerHandle()?.node?.let { playerMesh ->
                    worldLoader.getShadowManager()?.let { shadowManager ->
                        playerMesh.getChildMeshes().forEach {
                            shadowManager.addShadowCaster(it)
                            shadowManager.configureShadowReceiver(it)
                        }
                        println("[DEBUG] Player mesh registered as shadow caster and receiver")
                    }
                }
                
                // Set player mesh in object manager to exclude from collision detection
                objectManager.setPlayerMesh(playerController.getCollider())
                objectManager.setMoveCircleMesh(playerController.getMoveCircle())
                
                // Set initial camera target position
                cameraController.setInitialTargetPosition(startPosition)

                // Set up input handling
                inputHandler.setupKeyboardHandling()
                inputHandler.setupPointerHandling()
                
                // Set up WebSocket listeners for objects
                objectManager.setupWebSocketListeners(currentWorld.value!!.key)
                
                // Set up WebSocket listeners for world objects
                worldObjectManager.setupWebSocketListeners()
                
                // Connect to WebSocket for real-time updates using the ConnectionManager
                console.log("Connecting to WebSocket using ConnectionManager...")
                WebSocketConnectionManager.connectToWorld(url)
                
                // Wait for WebSocket connection to be established
                var connectionAttempts = 0
                while (WebSocketConnectionManager.connectionState.value != WebSocketConnectionManager.ConnectionState.CONNECTED && connectionAttempts < 10) {
                    console.log("Waiting for WebSocket connection to establish... Attempt ${connectionAttempts + 1}/10")
                    delay(500) // Wait 500ms between checks
                    connectionAttempts++
                }
                
                if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED) {
                    console.log("WebSocket connection established successfully!")
                    
                    // Set up OPEN_URL listener
                    WebSocketApi.addListener(models.WebSocketAction.OPEN_URL) { message ->
                        try {
                            // Parse the message data
                            val openUrlData = ApiClient.json.decodeFromString<OpenUrlData>(message.data)
                            
                            // Update the dialog state
                            openUrlValue.value = openUrlData.url
                            openUrlCallback.value = {
                                // Open the URL in a new window when confirmed
                                window.open(openUrlData.url, "_blank")
                            }
                            
                            // Show the dialog
                            showOpenUrlDialog.value = true
                        } catch (e: Exception) {
                            console.error("Error handling OPEN_URL message", e)
                        }
                    }
                } else {
                    console.warn("WebSocket connection not established after multiple attempts. Player movement may be limited.")
                }
                
                console.log("Setting WorldObjectManager in other players controller...")
                
                // Set the WorldObjectManager in the OtherPlayersController for username display
                otherPlayersController.setWorldObjectManager(worldObjectManager)

                // Provide HTTP worldId override (DB key) for snapshot fetches
                currentWorld.value?.key?.let { dbKey ->
                    otherPlayersController.setHttpWorldId(dbKey)
                }
                
                console.log("Initializing other players controller...")
                
                // Initialize other players controller for multiplayer
                otherPlayersController.initialize()
                
                // Set the shadow manager in the OtherPlayersController
                worldLoader.getShadowManager()?.let { shadowManager ->
                    otherPlayersController.setShadowManager(shadowManager)
                    println("[DEBUG] Shadow manager set in OtherPlayersController")
                }
                
                // Only mark as loaded if world loaded successfully (no API fallback/error)
                if (!loadingFailed.value) {
                    isLoaded.value = true
                } else {
                    console.warn("World loaded with API error fallback; keeping loading screen visible to allow claiming world")
                }
            } catch (error: Throwable) {
                console.error("Error loading world: ${error.message}")
                console.error("Error", error)
                console.error("Error TYPE ", error is ResponseException)
                console.error("Error STATUS ", (error as? ResponseException)?.response?.status)
                loadingFailed.value = true
                
                // Set specific error message based on the error
                if (error.message?.contains("World not found") == true) {
                    errorMessage.value = "No world found at saigon.quest/${if (url.isNotEmpty()) url else ""}"
                } else if (error.message?.contains("Failed to fetch") == true || 
                           error.message?.contains("NetworkError") == true) {
                    errorMessage.value = "Network error: Unable to connect to the server. Please check your internet connection."
                } else if (error.message?.contains("500") == true) {
                    errorMessage.value = "Server error: Something went wrong on our end. Please try again later."
                } else {
                    errorMessage.value = "Error loading world: ${error.message ?: "Unknown error"}"
                }
            }
        }
        
        // Set up the render loop
        scene.registerBeforeRender {
            // Calculate movement direction from input
            val moveDirection = inputHandler.calculateMovementDirection()

            // Update player position
            val newPosition = playerController.updatePlayerPosition(moveDirection)

            // Update camera target if player moved
            if (newPosition != null) {
                cameraController.updateCameraTarget(newPosition)
            }
            
            // Update camera animation
            cameraController.updateAnimation()
            
            // Update world objects
            worldObjectManager.update(scene.deltaTime)
            
            // Update occlusion fading effect
            worldLoader.getOcclusionManager()?.update()
        }
        
        // Start the render loop
        engine.runRenderLoop {
            scene.render()
        }

        // Clean up when the component is disposed
        onDispose {
            // Cancel the coroutine scope
            coroutineScope.cancel()
            
            window.removeEventListener("resize", resizeHandler)
            window.removeEventListener("objectDrop", objectDropHandler)
            
            // Unregister the graphics mode observer
            GraphicsSettings.removeObserver(graphicsModeObserver)
            
            // Disconnect from WebSocket
            WebSocketConnectionManager.disconnectFromWorld(url)
            
            // Dispose controllers
            cameraController.dispose()
            playerController.dispose()
            otherPlayersController.dispose()
            objectManager.dispose()
            worldObjectManager.dispose()
            worldLoader.dispose()
            
            // Clear SceneManager references
            SceneManager.clear()
            
            // Dispose scene and engine
            scene.dispose()
            engine.dispose()
        }
    }
}
