package main.components

import Translations.str
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.softwork.routingcompose.Router
import io.ktor.http.HttpStatusCode
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import lib.Engine
import lib.Scene
import lib.initializeBabylonJSLoaders
import main.api.ApiClient
import main.api.FriendApi
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WebSocketConnectionManager
import main.components.attrs.onTouchDrop
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
import models.ErrorResponse
import models.OpenUrlData
import org.jetbrains.compose.web.css.outline
import org.jetbrains.compose.web.dom.Canvas
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.DragEvent
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import utils.json
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
    val errorMessage = remember { mutableStateOf(str("No world found")) }
    
    // State to store the current world
    val currentWorld = remember { mutableStateOf<models.World?>(null) }
    
    // State to track if the world edit panel is visible
    val showWorldEditPanel = remember { mutableStateOf(false) }
    
    // State to track if the login dialog is visible
    val showLoginDialog = remember { mutableStateOf(false) }
    
    // State to indicate whether we should show the Claim option (only on 404 world not found)
    val showClaimOption = remember { mutableStateOf(false) }

    // State to indicate the world exists but the user has no access to it
    val isNoAccess = remember { mutableStateOf(false) }

    // State to track if we're on mobile
    val isMobile = remember { mutableStateOf(window.innerWidth <= 960) }

    // Fade overlay state (start black with blur)
    var showFadeOverlay by remember { mutableStateOf(true) }
    var fadeOpacity by remember { mutableStateOf(1.0) }
    var fadeBlurPx by remember { mutableStateOf(8) }
    
    // State for other player dialog
    val showOtherPlayerDialog = remember { mutableStateOf(false) }
    val otherPlayerUsername = remember { mutableStateOf("") }
    val otherPlayerIsPlayer = remember { mutableStateOf(false) }
    val isCurrentUserPlayer = remember { mutableStateOf(true) } // Assume the current user is a player for now
    val otherPlayerFriendshipStatus = remember { mutableStateOf(FriendshipStatus.UNKNOWN) }
    val otherPlayerIsLoading = remember { mutableStateOf(false) }
    val otherPlayerErrorMessage = remember { mutableStateOf<String?>(null) }
    val otherPlayerId = remember { mutableStateOf("") }
    val otherPlayerRequestId = remember { mutableStateOf<String?>(null) }
    
    // State for open URL dialog
    val showOpenUrlDialog = remember { mutableStateOf(false) }
    val openUrlValue = remember { mutableStateOf("") }
    val openUrlCallback = remember { mutableStateOf<(() -> Unit)?>(null) }

    // State for simple message dialog (replacing window.alert)
    val showMessageDialog = remember { mutableStateOf(false) }
    val messageDialogText = remember { mutableStateOf("") }
    
    // State for drop quantity dialog for inventory item drops
    data class PendingDrop(val x: Int, val y: Int, val data: String, val worldId: String, val maxQuantity: Int)
    val showDropQuantityDialog = remember { mutableStateOf(false) }
    val pendingDrop = remember { mutableStateOf<PendingDrop?>(null) }

    val requestPanel = remember { MutableSharedFlow<SidePanelState>() }

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
        } catch (e: Throwable) {
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

            // Touch drop mirrors mouse drop
            onTouchDrop { clientX, clientY, data, dataType ->
                dispatchObjectDropEvent(clientX.toInt(), clientY.toInt(), data, dataType)
            }

            onMouseLeave {
                SceneManager.getObjectManager()?.onMouseLeave()
            }
        }
    )
    
    // Helper to perform navigation with 250ms fade-to-black
    val navigateWithFade: (String, Boolean) -> Unit = { path, hardReload ->
        scope.launch {
            showFadeOverlay = true
            // If overlay was hidden, start from transparent/no-blur
            fadeOpacity = 0.0
            fadeBlurPx = 0
            // Next frame, animate to black + blur
            delay(10)
            fadeOpacity = 1.0
            fadeBlurPx = 8
            delay(260)
            if (hardReload) {
                window.location.href = path
            } else {
                router.navigate(path)
            }
        }
    }

    // Listen for external fade navigation requests
    DisposableEffect(Unit) {
        val handler: (dynamic) -> Unit = { e ->
            val detail = e.detail
            val path = detail?.path as? String

            path?.let { path ->
                val hard = (detail.hardReload as? Boolean) ?: false
                navigateWithFade(path, hard)
            }
        }
        window.addEventListener("navigateWithFade", handler)
        onDispose { window.removeEventListener("navigateWithFade", handler) }
    }

    // Fade from black only after the world and objects are ready
    LaunchedEffect(isLoaded.value, loadingFailed.value) {
        if (isLoaded.value && !loadingFailed.value) {
            showFadeOverlay = true
            // Animate to transparent and remove blur
            delay(10)
            fadeOpacity = 0.0
            fadeBlurPx = 0
            delay(260)
            showFadeOverlay = false
        } else if (loadingFailed.value) {
            // Reveal the error/loading UI immediately
            fadeOpacity = 0.0
            fadeBlurPx = 0
            showFadeOverlay = false
        }
    }

    // Loading screen or error message
    LoadingScreen(
        isLoaded = isLoaded.value,
        loadingFailed = loadingFailed.value,
        errorMessage = errorMessage.value,
        showWorldEditPanel = showWorldEditPanel.value,
        showLoginDialog = showLoginDialog.value,
        onShowWorldEditPanel = { showWorldEditPanel.value = true },
        onShowLoginDialog = { showLoginDialog.value = true },
        onNavigateHome = { navigateWithFade("/", false) },
        isNoAccess = isNoAccess.value,
        showClaimOption = showClaimOption.value,
        onRetry = { navigateWithFade(window.location.pathname, true) },
    )
    
    // Show world edit panel if requested
    if (showWorldEditPanel.value) {
        WorldEdit(
            currentWorld = currentWorld.value,
            isMobile = isMobile.value,
            onClose = { showWorldEditPanel.value = false },
            onSave = { newWorld ->
                // After saving, fade to black then hard reload to the new world
                navigateWithFade("/${newWorld.url}", true)
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
        WorldUI(
            requestPanel = requestPanel,
            onRequestCanvasFocus = { canvasRef?.focus() },
        )
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

                val dto = FriendApi.getFriendshipStatusDto(otherPlayerId.value)
                if (dto != null) {
                    otherPlayerRequestId.value = dto.requestId
                    otherPlayerFriendshipStatus.value = when (dto.status) {
                        models.FriendshipState.Friends -> FriendshipStatus.FRIENDS
                        models.FriendshipState.IncomingRequest -> FriendshipStatus.REQUEST_RECEIVED
                        models.FriendshipState.OutgoingRequest -> FriendshipStatus.REQUEST_SENT
                        models.FriendshipState.NotFriends -> FriendshipStatus.NOT_FRIENDS
                    }
                } else {
                    otherPlayerRequestId.value = null
                    otherPlayerFriendshipStatus.value = FriendshipStatus.UNKNOWN
                }
            } catch (e: Throwable) {
                console.error("Error checking friendship status", e)
                otherPlayerRequestId.value = null
                otherPlayerFriendshipStatus.value = FriendshipStatus.UNKNOWN
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
            } catch (e: Throwable) {
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
                var requestId = otherPlayerRequestId.value
                if (requestId == null) {
                    val dto = FriendApi.getFriendshipStatusDto(otherPlayerId.value)
                    if (dto?.status == models.FriendshipState.IncomingRequest) {
                        requestId = dto.requestId
                        otherPlayerRequestId.value = requestId
                    } else {
                        otherPlayerErrorMessage.value = "No pending friend request found"
                        return@launch
                    }
                }

                if (requestId == null) {
                    otherPlayerErrorMessage.value = "No pending friend request found"
                    return@launch
                }

                val friend = FriendApi.acceptRequest(requestId)
                if (friend != null) {
                    otherPlayerFriendshipStatus.value = FriendshipStatus.FRIENDS
                } else {
                    otherPlayerErrorMessage.value = "Failed to accept friend request"
                }
            } catch (e: Throwable) {
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
            } catch (e: Throwable) {
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
                        else -> { /* Do nothing for other states */ }
                    }
                }
            },
            onRemoveFriend = {
                removeFriend()
            },
            onClose = {
                // Close the dialog
                showOtherPlayerDialog.value = false
            },
            otherUserId = otherPlayerId.value,
        )
    }

    // Add DialogueBox component for displaying dialogue messages
    DialogueBox()

    // Add TakeItems dialog for item transfer prompts
    TakeItemsDialog()

    // Simple message dialog host
    if (showMessageDialog.value) {
        BaseDialog(
            onClose ={ showMessageDialog.value = false },
            title = translated("Collect item"),
            content = {
                Text(messageDialogText.value)
            },
            actions = {
                DialogButton(
                    onClick = { showMessageDialog.value = false },
                    isPrimary = false,
                ) {
                    Translated("Close")
                }

                DialogButton(
                    onClick = {
                        showMessageDialog.value = false
                        scope.launch {
                            requestPanel.emit(SidePanelState.Profile)
                        }
                    },
                    isPrimary = true,
                ) {
                    Translated("Log in")
                }
            }
        )
    }

    // Quantity prompt dialog for inventory item drop
    if (showDropQuantityDialog.value && pendingDrop.value != null) {
        val pd = pendingDrop.value!!
        BasicPromptDialog(
            title = translated("Drop quantity"),
            message = translated("Drop quantity"),
            defaultValue = "1",
            isNumber = true,
            min = 1,
            max = pd.maxQuantity,
            onSubmit = { value ->
                val objectManager = SceneManager.getObjectManager()
                val requested = value.toIntOrNull()
                if (requested == null || requested <= 0) {
                    objectManager?.onError?.invoke(str("Invalid quantity"))
                } else {
                    val qty = if (requested > pd.maxQuantity) {
                        pd.maxQuantity
                    } else {
                        requested
                    }
                    objectManager?.handleInventoryItemDrop(pd.x, pd.y, pd.data, pd.worldId, quantityOverride = qty)
                }
                showDropQuantityDialog.value = false
                pendingDrop.value = null
            },
            onCancel = {
                showDropQuantityDialog.value = false
                pendingDrop.value = null
            },
        )
    }
    
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
        val scene = Scene(engine).apply {
            imageProcessingConfiguration.isEnabled = true
            imageProcessingConfiguration.applyByPostProcess = true
            imageProcessingConfiguration.gamma = 1.8f
        }
        
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
            },
            onShowMessageDialog = { message ->
                messageDialogText.value = message
                showMessageDialog.value = true
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
            if (dataType == DataType.INVENTORY_ITEM) {
                console.log("Handling inventory item drop")
                try {
                    val payload = json.decodeFromString(main.components.inventory.InventoryDragPayload.serializer(), data)
                    val maxQuantity = if (payload.quantity > 0) payload.quantity else 1
                    if (maxQuantity > 1) {
                        pendingDrop.value = PendingDrop(x = x, y = y, data = data, worldId = worldId, maxQuantity = maxQuantity)
                        showDropQuantityDialog.value = true
                    } else {
                        objectManager.handleInventoryItemDrop(x, y, data, worldId, quantityOverride = 1)
                    }
                } catch (e: Throwable) {
                    console.error("Failed to parse inventory drop payload", e)
                    objectManager.onError?.invoke(str("Invalid quantity"))
                }
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
                currentWorld.value = worldLoader.getCurrentWorld()
                if (currentWorld.value == null) {
                    loadingFailed.value = true
                    if (worldLoader.isApiErrorFallback()) {
                        showClaimOption.value = true
                        errorMessage.value = str("No world found at") + " saigon.quest/${url.ifEmpty { "" }}"
                    } else {
                        showClaimOption.value = false
                        errorMessage.value = str("Error loading world") + ": " + str("World not found or server error")
                    }
                }

                inputHandler.currentWorld = currentWorld.value

                // Check if this is a fallback world due to an API error
                if (worldLoader.isApiErrorFallback()) {
                    // Set loading failed to true to show error message and enable claim option
                    loadingFailed.value = true
                    showClaimOption.value = true
                    errorMessage.value = str("No world found at") + " saigon.quest/${url.ifEmpty { "" }}"
                }
                
                // Set world meshes in controllers
                playerController.setWorldMeshes(worldMeshes)
                objectManager.setWorldMeshes(worldMeshes)
                otherPlayersController.setWorldMeshes(worldMeshes)
                
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

                // Initialize pause listener (single-device enforcement)
                console.log("[DEBUG_LOG] Scene: Initializing pause listener")
                playerController.initializePauseListener(router)
                console.log("[DEBUG_LOG] Scene: Pause listener initialization completed")
                
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
                objectManager.setupWebSocketListeners(currentWorld.value?.key ?: url)
                
                // Set up WebSocket listeners for world objects
                worldObjectManager.setupWebSocketListeners()
                
                // Connect to WebSocket for real-time updates using the ConnectionManager
                console.log("Connecting to WebSocket using ConnectionManager...")
                // Use canonical world ID (DB key) when available to match server broadcasts
                WebSocketConnectionManager.connectToWorld(currentWorld.value?.key ?: url)
                
                // Wait for WebSocket connection to be established
                var connectionAttempts = 0
                while (WebSocketConnectionManager.connectionState.value != WebSocketConnectionManager.ConnectionState.CONNECTED && connectionAttempts < 10) {
                    console.log("Waiting for WebSocket connection to establish... Attempt ${connectionAttempts + 1}/10")
                    delay(500) // Wait 500ms between checks
                    connectionAttempts++
                }
                
                if (WebSocketConnectionManager.connectionState.value == WebSocketConnectionManager.ConnectionState.CONNECTED) {
                    console.log("WebSocket connection established successfully!")
                    // Send USER_JOIN now that WebSocket is connected to ensure world state upsert even if player doesn't move
                    otherPlayersController.sendJoinEvent(startPosition)
                    
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
                        } catch (e: Throwable) {
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
                console.error("Error TYPE ", error is ErrorResponse)
                console.error("Error STATUS ", (error as? ErrorResponse)?.code)

                loadingFailed.value = true
                showClaimOption.value = (error as? ErrorResponse)?.code == HttpStatusCode.NotFound.value
                isNoAccess.value = (error as? ErrorResponse)?.code == HttpStatusCode.Forbidden.value

                when (error) {
                    is ErrorResponse -> {
                        errorMessage.value = error.message
                    }
                    else -> {
                        // Prefer server-provided message (e.g., friendly access-denied text) without prefix
                        errorMessage.value = error.message ?: str("Unknown error")
                    }
                }
            }
        }
        
        // ------- Graphics auto-leveling state -------
        // Stop auto-leveling if we already are at the lowest settings on load
        var graphicsAutoLevelingActive = !(GraphicsSettings.isLowGraphicsModeEnabled() && !GraphicsSettings.isPrettyGraphicsEnabled())
        // Accumulates milliseconds during which FPS is continuously < 30
        var lowFpsAccumulatedMs = 0.0

        // Set up the render loop
        scene.registerBeforeRender {
            // Graphics auto-leveling: track FPS and adjust settings if needed
            if (graphicsAutoLevelingActive) {
                val dtMs = scene.deltaTime.toDouble()
                val fps = if (dtMs > 0.0) 1000.0 / dtMs else 0.0

                if (fps < 45.0) {
                    lowFpsAccumulatedMs += dtMs
                } else {
                    // Reset if performance recovers
                    lowFpsAccumulatedMs = 0.0
                }

                // If FPS has been < 30 for 5 seconds, degrade graphics
                if (lowFpsAccumulatedMs >= 5_000.0) {
                    // First step: disable pretty graphics if still enabled
                    if (GraphicsSettings.isPrettyGraphicsEnabled()) {
                        console.warn("[AutoLevel] Disabling pretty graphics due to sustained <30 FPS")
                        GraphicsSettings.setPrettyGraphics(false)
                        // Reset counter for potential next step
                        lowFpsAccumulatedMs = 0.0
                    } else if (!GraphicsSettings.isLowGraphicsModeEnabled()) {
                        // Second step: enable low graphics if pretty is already disabled
                        console.warn("[AutoLevel] Enabling low graphics mode due to sustained <30 FPS")
                        GraphicsSettings.setLowGraphicsMode(true)
                        // Reset counter after change
                        lowFpsAccumulatedMs = 0.0
                    }

                    // If both low mode is on and pretty is off, stop auto-leveling until reload
                    if (!GraphicsSettings.isPrettyGraphicsEnabled() && GraphicsSettings.isLowGraphicsModeEnabled()) {
                        console.warn("[AutoLevel] Reached minimum graphics. Auto-leveling disabled until reload.")
                        graphicsAutoLevelingActive = false
                    }
                }
            }

            // Calculate movement direction from input
            val moveDirection = inputHandler.calculateMovementDirection()

            // Update player position
            val newPosition = playerController.updatePlayerPosition(moveDirection)
            if (newPosition == null) {
                // No movement this frame; zero out instantaneous velocity for sway
                SceneManager.setPlayerVelocity(lib.Vector3(0f, 0f, 0f))
            }

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

    // Global fade overlay rendered above everything via portal
    Portal(key = url) {
        if (showFadeOverlay) {
            Div(
                attrs = {
                    classes(main.theme.OverlayStyles.fadeOverlay)
                    style {
                        property("opacity", fadeOpacity.toString())
                        property("backdrop-filter", "blur(${fadeBlurPx}px)")
                        property("-webkit-backdrop-filter", "blur(${fadeBlurPx}px)")
                        property("pointer-events", "auto")
                    }
                }
            ) {}
        }
    }
}
