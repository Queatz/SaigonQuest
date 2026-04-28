package main.components

import Translations.str
import androidx.compose.runtime.*
import app.softwork.routingcompose.Router
import io.ktor.http.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import lib.AudioEngine
import lib.Engine
import lib.Node
import lib.Scene
import lib.TransformNode
import lib.configureBabylonDracoDecoder
import lib.initializeBabylonJSLoaders
import main.api.*
import main.components.audio.MusicManager
import main.components.audio.SoundManager
import main.components.attrs.onTouchDrop
import main.components.camera.CameraController
import main.components.input.InputHandler
import main.components.minigame.MinigameOverlay
import main.components.objects.IdleObjectHighlighter
import main.components.objects.SceneObjectManager
import main.components.objects.SavedPositionManager
import main.components.objects.handleInventoryItemDrop
import main.components.objects.handleObjectDrop
import main.components.objects.update
import main.components.player.FriendshipStatus
import main.components.player.OtherPlayerDialog
import main.components.player.OtherPlayersCoordinator
import main.components.player.PlayerController
import main.components.world.DepthPicking
import main.components.world.WorldLoader
import main.components.world.WorldObjectManager
import main.theme.Styles
import main.utils.DataType
import main.utils.createObjectDropEvent
import models.ErrorResponse
import models.OpenUrlData
import models.QuestStep
import org.jetbrains.compose.web.dom.Canvas
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.DragEvent
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import utils.json
import web.animations.awaitAnimationFrame
import web.navigator.navigator
import web.wakelock.WakeLockSentinel
import web.wakelock.release
import web.wakelock.request
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
 * Helper to remove the 'at' parameter from the URL without triggering a reload.
 */
private fun removeAtParamFromUrl() {
    try {
        val currentUrl = window.location.href
        if (currentUrl.contains("at=")) {
            // Remove 'at=x,y,z' from the URL. Handles both ?at= and &at=
            val newUrl = currentUrl
                .replace(Regex("[?&]at=[^&]*"), "")
                .replace("?&", "?")
                .trimEnd('?', '&')
            
            if (newUrl != currentUrl) {
                window.history.replaceState(null, "", newUrl)
                console.log("[SCENE] Removed 'at' parameter from URL")
            }
        }
    } catch (e: Throwable) {
        console.error("Failed to cleanup URL", e)
    }
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
    var canvasRef by remember { mutableStateOf<HTMLCanvasElement?>(null) }

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
    val isMobile = rememberIsMobile()

    // Fade overlay state (start black with blur)
    var showFadeOverlay by remember { mutableStateOf(true) }
    var fadeOpacity by remember { mutableStateOf(1.0) }
    var fadeBlurPx by remember { mutableStateOf(8) }


    // State for other player dialog
    val showOtherPlayerDialog = remember { mutableStateOf(false) }
    val otherPlayerUsername = remember { mutableStateOf("") }
    val otherPlayerIsPlayer = remember { mutableStateOf(false) }
    val isCurrentUserPlayer = remember { mutableStateOf<Boolean?>(null) } // Assume the current user is unknown for now
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
    
    var lastQuestUpdate by remember { mutableStateOf(0L) }
    
    fun refreshQuestIndicators() {
        lastQuestUpdate = window.performance.now().toLong()
    }

    var hasFocus by remember { mutableStateOf(true) }

    var engineRef by remember { mutableStateOf<Engine?>(null) }

    // todo: Why doesn't 'focusin' work
    LaunchedEffect(canvasRef) {
        while (true) {
            delay(1_000)
            awaitAnimationFrame()
            // Only steal focus if no specific element is focused and no text is selected by the user.
            // This prevents focus jumping and losing text selections in the Chat or other UI elements.
            val selection = window.asDynamic().getSelection()?.toString()
            if (document.activeElement == document.body && selection.isNullOrEmpty()) {
                canvasRef?.focus()
            }
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

    // Refocus canvas when local dialogs are closed
    LaunchedEffect(
        showLoginDialog.value,
        showWorldEditPanel.value,
        showOtherPlayerDialog.value,
        showMessageDialog.value,
        showDropQuantityDialog.value,
        showOpenUrlDialog.value,
    ) {
        if (!showLoginDialog.value &&
            !showWorldEditPanel.value &&
            !showOtherPlayerDialog.value &&
            !showMessageDialog.value &&
            !showDropQuantityDialog.value &&
            !showOpenUrlDialog.value
        ) {
            awaitAnimationFrame()
            canvasRef?.focus()
        }
    }

    // Handle 'at' query parameter for initial position or teleport
    var handledAt by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(router.currentPath, isLoaded.value) {
        val atParam = router.currentPath.parameters?.map?.get("at")?.firstOrNull()
        if (atParam == null) {
            handledAt = null
        } else if (atParam != handledAt) {
            console.log("[SCENE] Found 'at' parameter: $atParam (isLoaded: ${isLoaded.value})")
            val parts = atParam.split(",")
            if (parts.size == 3) {
                val x = parts[0].toFloatOrNull()
                val y = parts[1].toFloatOrNull()
                val z = parts[2].toFloatOrNull()
                if (x != null && y != null && z != null) {
                    val pos = lib.Vector3(x, y, z)
                    val pc = SceneManager.getPlayerController()
                    
                    if (isLoaded.value && pc != null) {
                        handledAt = atParam
                        console.log("[SCENE] Teleporting existing player to $pos")
                        pc.teleportTo(pos)
                        
                        // Remove from URL after processing
                        removeAtParamFromUrl()
                    } else if (!isLoaded.value) {
                        handledAt = atParam
                        console.log("[SCENE] Setting pending teleport to $pos")
                        SceneManager.setPendingTeleport(pos)
                        
                        // Remove from URL after processing
                        removeAtParamFromUrl()
                    }
                }
            }
        }
    }

    // Quest indicators effect
    LaunchedEffect(isCurrentUserPlayer.value, currentWorld.value, isLoaded.value, lastQuestUpdate) {
        if (isCurrentUserPlayer.value != true || currentWorld.value == null || !isLoaded.value) return@LaunchedEffect
        
        try {
            val progress = QuestApi.getAllPlayerQuestProgress()
            val activeProgress = progress.filter { !it.completed }
            
            val questIndicators = mutableMapOf<String, QuestStep>()
            activeProgress.forEach { p ->
                val quest = try { QuestApi.getQuest(p.questId) } catch (_: Throwable) { null }
                val step = quest?.steps?.getOrNull(p.currentStepIndex)
                if (step != null && step.objectId != null && (step.worldId.isNullOrBlank() || step.worldId == currentWorld.value!!.key)) {
                    questIndicators[step.objectId!!] = step
                }
            }
            
            main.components.SceneManager.getObjectManager()?.updateActiveQuests(questIndicators)
        } catch (e: Throwable) {
            console.error("Failed to fetch quests for indicators", e)
        }
        
        while (isActive) {
            delay(30_000)
            try {
                val progress = QuestApi.getAllPlayerQuestProgress()
                val activeProgress = progress.filter { !it.completed }
                
                val questIndicators = mutableMapOf<String, QuestStep>()
                activeProgress.forEach { p ->
                    val quest = try { QuestApi.getQuest(p.questId) } catch (_: Throwable) { null }
                    val step = quest?.steps?.getOrNull(p.currentStepIndex)
                    if (step != null && step.objectId != null && (step.worldId.isNullOrBlank() || step.worldId == currentWorld.value!!.key)) {
                        questIndicators[step.objectId!!] = step
                    }
                }
                
                main.components.SceneManager.getObjectManager()?.updateActiveQuests(questIndicators)
            } catch (e: Throwable) {
                console.error("Failed to fetch quests for indicators (loop)", e)
            }
        }
    }

    DisposableEffect(Unit) {
        val token = WebSocketApi.addQuestProgressListener {
            refreshQuestIndicators()
        }
        onDispose {
            WebSocketApi.removeListener(token)
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

                // Adjust client coordinates to be relative to the canvas
                val rect = canvasRef?.getBoundingClientRect()
                val x = (drag.clientX - (rect?.left ?: 0.0)).toInt()
                val y = (drag.clientY - (rect?.top ?: 0.0)).toInt()
                
                val data = drag.dataTransfer!!.getData("application/json")

                // Get the data type from the event
                val dataTypeString = drag.dataTransfer!!.getData("application/x-data-type")

                console.log("Data type from event: $dataTypeString")

                // Convert the string to a DataType enum
                val dataType = dataTypeString.let { DataType.valueOf(it) }

                // Use the helper function to dispatch the custom event with the data type
                dispatchObjectDropEvent(x, y, data, dataType)
            }

            onTouchDrop { clientX, clientY, data, dataType ->
                val rect = canvasRef?.getBoundingClientRect()
                val x = (clientX - (rect?.left ?: 0.0)).toInt()
                val y = (clientY - (rect?.top ?: 0.0)).toInt()
                dispatchObjectDropEvent(x, y, data, dataType)
            }

            onMouseDown {
                // Always focus the canvas itself on interaction
                // Using relatedTarget here could re-focus the prior element (e.g., a dialog button)
                // which can swallow the first drag after a dialog closes.
                canvasRef?.focus()
            }

            onMouseLeave {
                SceneManager.getObjectManager()?.onMouseLeave()
            }

            onFocus { hasFocus = true }

            onBlur { hasFocus = false }

            onMouseMove {
                hasFocus = true
                canvasRef?.focus()
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
        showGoHome = currentWorld.value?.url?.isNotEmpty() ?: false,
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
        onRetry = { navigateWithFade(router.currentPath.path, true) },
        worldName = currentWorld.value?.title,
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
            onSuccess = { _ ->
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
        val canvas = document.getElementById(canvasId) as HTMLCanvasElement
        canvas.focus()
    }

    // Keep the screen awake while the 3D canvas exists
    DisposableEffect(canvasRef) {
        var sentinel: WakeLockSentinel? = null

        val acquireJob = scope.launch {
            try {
                if (canvasRef != null) {
                    val wl = navigator.wakeLock
                    sentinel = wl.request()
                    console.log("[WakeLock] acquired")
                }
            } catch (t: Throwable) {
                console.warn("[WakeLock] acquire failed: ${t.message}")
            }
        }

        onDispose {
            acquireJob.cancel()
            scope.launch {
                try {
                    sentinel?.release()
                    console.log("[WakeLock] released (dispose)")
                } catch (t: Throwable) {
                    console.warn("[WakeLock] release failed: ${t.message}")
                } finally {
                    sentinel = null
                }
            }
        }
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
                val request = FriendApi.sendFriendRequestNew(otherPlayerId.value)

                if (request != null) {
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
            isCurrentUserPlayer = isCurrentUserPlayer.value == true,
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

    val initialMaxFps = 60

    LaunchedEffect(hasFocus) {
        val engine = engineRef ?: return@LaunchedEffect
        if (hasFocus) {
            engine.maxFPS = initialMaxFps
            console.log("[FPS] Canvas focused again; restoring maxFPS to $initialMaxFps")
        } else {
            delay(30_000)
            engine.maxFPS = 1
            console.log("[FPS] Canvas unfocused for 30s; reducing maxFPS to 1")
        }
    }

    // Set up BabylonJS scene
    DisposableEffect(canvasId) {
        // Create a coroutine scope for async operations
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        
        // Get the canvas element
        val canvas = document.getElementById(canvasId) as HTMLCanvasElement

        // Create the engine
        val engine = Engine(
            canvas = canvas,
            antialias = false,
            options = object : lib.EngineOptions {
                override var useLargeWorldRendering: Boolean? = false // todo: we want true, but depth renderer has issues
                override var useHighPrecisionFloats: Boolean? = true
                override var useHighPrecisionMatrix: Boolean? = true
                override var audioEngine: Boolean? = true
            }
        ).also {
            engineRef = it
        }

        engine.maxFPS = initialMaxFps

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
        graphicsModeObserver()

        // Handle window resize
        val resizeHandler = { _: Event ->
            engine.resize()
        }
        window.addEventListener("resize", resizeHandler)

        // Create the scene
        val scene = Scene(engine).apply {
            imageProcessingConfiguration.isEnabled = true
            imageProcessingConfiguration.applyByPostProcess = true
            audioEnabled = true
            console.log("[Audio] Scene audio enabled: $audioEnabled")
        }

        // Unlock audio on first user interaction
        val getAudioEngine = {
            var ae = engine.audioEngine ?: Engine.audioEngine
            if (ae == null) {
                try {
                    console.log("[Audio] AudioEngine is null, attempting to create it...")
                    val newAe = lib.AudioEngine(canvas)
                    engine.audioEngine = newAe
                    ae = newAe
                } catch (t: Throwable) {
                    console.warn("[Audio] Failed to create AudioEngine: ${t.message}")
                }
            }
            ae
        }

        val initialAe = getAudioEngine()
        if (initialAe == null) {
            console.warn("[Audio] AudioEngine is null on initialization! (Instance: ${engine.audioEngine}, Static: ${Engine.audioEngine})")
        } else {
            console.log("[Audio] AudioEngine initialized. Unlocked: ${initialAe.unlocked}")
            initialAe.onAudioUnlockedObservable.add {
                console.log("[Audio] AudioEngine UNLOCKED via Observable. Status: ${initialAe.unlocked}")
            }
        }
        
        initialAe?.useCustomUnlockedButton = true
        
        val unlockAudio = { event: Event ->
            console.log("[Audio] User interaction detected (${event.type}), attempting to unlock audio...")
            val ae = getAudioEngine()
            if (ae != null) {
                if (!ae.unlocked) {
                    console.log("[Audio] Attempting to unlock AudioEngine via user interaction (Event: ${event.type})...")
                    ae.unlock()
                    
                    // Explicitly resume AudioContext if it exists and is suspended
                    try {
                        val ctx = ae.audioContext
                        if (ctx != null) {
                            val state = ctx.state
                            console.log("[Audio] AudioContext state: $state")
                            if (state == "suspended") {
                                console.log("[Audio] Resuming suspended AudioContext...")
                                ctx.resume()
                            }
                        }
                    } catch (t: Throwable) {
                        // Ignore errors here as audioContext might not be supported or accessible this way
                    }
                    
                    // Re-check after a short delay as it might be async
                    window.setTimeout({
                        console.log("[Audio] AudioEngine unlocked status after delay: ${ae.unlocked}")
                        if (ae.audioContext != null) {
                            console.log("[Audio] AudioContext state after delay: ${ae.audioContext.state}")
                        }
                    }, 500)
                }
            } else {
                console.warn("[Audio] Cannot unlock: AudioEngine is null (Instance: ${engine.audioEngine}, Static: ${Engine.audioEngine})")
            }

            // Ensure world music is also refreshed/unlocked on user interaction
            console.log("[Audio] Calling MusicManager.refreshPlayback() from unlockAudio")
            MusicManager.refreshPlayback()
        }
        window.addEventListener("click", unlockAudio)
        window.addEventListener("mousedown", unlockAudio)
        window.addEventListener("keydown", unlockAudio)
        window.addEventListener("touchstart", unlockAudio)

        // Register scene with SceneManager for global access
        SceneManager.setScene(scene)

        // Initialize BabylonJS loaders before loading the 3D model
        initializeBabylonJSLoaders()
        // Configure Draco after initializing loaders to ensure configuration objects are available
        try {
            configureBabylonDracoDecoder("/babylon/Draco")
        } catch (t: Throwable) {
            console.warn("[Scene] Failed to configure Babylon Draco decoder, falling back to default CDN URLs", t)
        }

        // Create controllers
        val cameraController = CameraController(scene)
        // Register camera controller with SceneManager for global access
        SceneManager.setCameraController(cameraController)

        val playerController = PlayerController(scene)
        // Register player controller with SceneManager for global access
        SceneManager.setPlayerController(playerController)

        // Create other players coordinator for multiplayer
        val otherPlayersCoordinator = OtherPlayersCoordinator(scene)
        // Register other players coordinator with SceneManager for global access
        SceneManager.setOtherPlayersCoordinator(otherPlayersCoordinator)

        val worldLoader = WorldLoader(scene)

        val worldObjectManager = WorldObjectManager(scene)
        val objectManager = SceneObjectManager(
            scene = scene,
            cameraController = cameraController,
            worldObjectManager = worldObjectManager
        )
        val savedPositionManager = SavedPositionManager(scene)

        // ------- Idle highlighting state -------
        val idleHighlighter = IdleObjectHighlighter(
            objectManager = objectManager,
            coroutineScope = coroutineScope
        )

        fun stopIdleHighlighting() {
            if (idleHighlighter.isActive()) {
                idleHighlighter.stop()
            }
        }

        fun triggerIdleHighlighting() {
            idleHighlighter.start()
        }

        // ------- Screen saver state -------
        var lastInteractionTime = window.performance.now()
        var isScreenSaverActive = false
        var screenSaverAnimationGroup: lib.AnimationGroup? = null
        val flippedCameras = mutableSetOf<lib.Camera>()
        var screenSaverFocusJob: Job? = null

        fun stopScreenSaver() {
            if (!isScreenSaverActive) return
            isScreenSaverActive = false
            screenSaverFocusJob?.cancel()
            screenSaverFocusJob = null
            screenSaverAnimationGroup?.stop()
            screenSaverAnimationGroup = null

            // Remove the screensaver camera from post-processing pipeline
            scene.activeCamera?.let {
                if (it != cameraController.getCamera()) {
                    cameraController.removeCameraFromPipeline(it)
                }
            }

            scene.activeCamera = cameraController.getCamera()
            worldLoader.getOcclusionManager()?.setEnabled(true)
            console.log("[ScreenSaver] Stopped")
        }

        fun triggerScreenSaver() {
            console.log("[ScreenSaver] triggerScreenSaver() called")
            if (isScreenSaverActive) {
                console.log("[ScreenSaver] Already active, returning")
                return
            }
            
            // Find the first camera that isn't the main player camera and has a long enough animation
            val mainCam = cameraController.getCamera()
            console.log("[ScreenSaver] Main camera: ${mainCam.name}")
            
            // Filter animation groups that last longer than 1 second
            val longAnimationGroups = scene.animationGroups.filter { group ->
                val durationFrames = kotlin.math.abs(group.to - group.from)
                // Use the FPS of the first targeted animation as a reference, or default to 60
                val fps = group.targetedAnimations.firstOrNull()?.animation?.framePerSecond?.toFloat() ?: 60f
                val durationSeconds = durationFrames / fps
                val isLong = durationSeconds > 1.0f
                console.log("[ScreenSaver] AnimationGroup: ${group.name}, duration: $durationSeconds s, isLong: $isLong")
                group.targetedAnimations.forEach { 
                    console.log("[ScreenSaver]   Target: ${it.target.asDynamic().name ?: it.target.toString()}")
                }
                isLong
            }
            console.log("[ScreenSaver] Found ${longAnimationGroups.size} long animation groups")

                // Find a camera that has an animation in one of these long groups or its ancestors
                val ssCam = scene.cameras.firstOrNull { cam ->
                    val isNotMain = cam != mainCam
                    val inLongGroup = longAnimationGroups.any { group ->
                        group.targetedAnimations.any { targetedAnim ->
                            val target = targetedAnim.target
                            val targetName = target.asDynamic().name as? String
                            var current: Node? = cam
                            var matched = false
                            while (current != null) {
                                if (target == current || (targetName != null && targetName == current.name)) {
                                    matched = true
                                    break
                                }
                                current = current.parent
                            }
                            matched
                        }
                    }
                    console.log("[ScreenSaver] Checking camera: ${cam.name}, parent: ${cam.parent?.name}, isNotMain: $isNotMain, inLongGroup: $inLongGroup")
                    isNotMain && inLongGroup
                }

                if (ssCam != null) {
                    console.log("[ScreenSaver] Found screen saver camera: ${ssCam.name}")
                } else {
                    console.log("[ScreenSaver] No suitable screen saver camera found")
                }

                if (ssCam != null) {
                    // Fix for horizontally flipped view in screen saver by modifying projection matrix
                    // We only want to flip it once to avoid toggling back and forth on subsequent screen saver starts
                    if (ssCam !in flippedCameras) {
                        try {
                            // Store original parent
                            val originalParent = ssCam.parent

                            // Create flip node with -1 z scaling
                            val flipNode = TransformNode("cameraFlipNode_${ssCam.name}", scene)
                            flipNode.position = ssCam.position
                            flipNode.scaling = lib.Vector3(-1f, 1f, 1f)

                            // Set flip node's parent to camera's original parent
                            flipNode.parent = originalParent

                            // Parent camera to flip node
                            ssCam.parent = flipNode

                            flippedCameras.add(ssCam)
                            console.log("[ScreenSaver] Flipped camera: ${ssCam.name}")
                        } catch (t: Throwable) {
                            console.warn("[ScreenSaver] Failed to modify projection matrix", t)
                        }
                    }

                    // Find first animation group that contains this camera or its ancestors
                    val animationGroup = longAnimationGroups.firstOrNull { group ->
                        group.targetedAnimations.any { targetedAnim ->
                            val target = targetedAnim.target
                            val targetName = target.asDynamic().name as? String
                            var current: Node? = ssCam
                            var matched = false
                            while (current != null) {
                                if (target == current || (targetName != null && targetName == current.name)) {
                                    matched = true
                                    break
                                }
                                current = current.parent
                            }
                            matched
                        }
                    }
                    
                    if (animationGroup == null) {
                        console.log("[ScreenSaver] No animation group found for ${ssCam.name}")
                        return
                    }
                
                isScreenSaverActive = true
                scene.activeCamera = ssCam

                // Add the screensaver camera to post-processing pipeline
                cameraController.addCameraToPipeline(ssCam)

                // Sync minZ and maxZ with main camera
                ssCam.minZ = mainCam.minZ
                ssCam.maxZ = mainCam.maxZ

                worldLoader.getOcclusionManager()?.setEnabled(false)
                animationGroup.play(loop = true)
                screenSaverAnimationGroup = animationGroup
                console.log("[ScreenSaver] Started with camera: ${ssCam.name}")
                console.log("[ScreenSaver]", ssCam)

                // Continuously update focal distance to center of view
                screenSaverFocusJob?.cancel()
                screenSaverFocusJob = coroutineScope.launch {
                    while (isScreenSaverActive) {
                        try {
                            val activeCam = scene.activeCamera
                            val pipeline = cameraController.getPipeline()
                            if (activeCam != null && pipeline.depthOfFieldEnabled) {
                                val renderWidth = engine.getRenderWidth()
                                val renderHeight = engine.getRenderHeight()
                                
                                // Read depth at center of screen
                                val depth = DepthPicking.readLinearDepthAt(renderWidth / 2, renderHeight / 2)
                                if (depth != null) {
                                    // Convert linear depth [0..1] to world distance
                                    // linearDepth = (z - zNear) / (zFar - zNear)
                                    // => z = linearDepth * (zFar - zNear) + zNear
                                    val distance = depth * (activeCam.maxZ - activeCam.minZ) + activeCam.minZ
                                    
                                    // Set focus distance in millimeters
                                    pipeline.depthOfField?.focusDistance = distance * 1000f
                                }
                            }
                        } catch (t: Throwable) {
                            console.warn("[ScreenSaver] Focus update failed", t)
                        }
                        delay(100)
                    }
                }
            }
        }

        // Set up shadow manager in the world object manager
        // This needs to be done after world loading since the shadow manager is created during world loading
        WorldLoader.onWorldLoaded {
            // Ensure occlusion manager state matches current screen saver state
            worldLoader.getOcclusionManager()?.setEnabled(!isScreenSaverActive)
            // Get the shadow manager from the world loader and set it on the world object manager
            worldLoader.getShadowManager()?.let { shadowManager ->
                worldObjectManager.setShadowManager(shadowManager)
                objectManager.setShadowManager(shadowManager)
                println("[DEBUG] Set shadow manager on world object manager")
            }
        }

        // Register object manager with SceneManager for global access
        SceneManager.setObjectManager(objectManager)
        SceneManager.setSavedPositionManager(savedPositionManager)

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

                // Get the player ID from the OtherPlayersCoordinator
                val otherPlayers = SceneManager.getOtherPlayersCoordinator()
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
            },
            onOpenInventory = {
                coroutineScope.launch {
                    requestPanel.emit(SidePanelState.Inventory)
                }
            }
        )


        inputHandler.setOnActivity {
            lastInteractionTime = window.performance.now()
            if (isScreenSaverActive) {
                stopScreenSaver()
            }
            if (idleHighlighter.isActive()) {
                stopIdleHighlighting()
            }
        }

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
            // Ensure the initial user session check completes first to avoid race conditions with tokens
            while (isCurrentUserPlayer.value == null) {
                delay(10)
            }
            
            try {
                // Call the suspend function
                val (worldMeshes, nodes) = worldLoader.loadWorld(
                    url = url,
                    onInfoLoaded = { world ->
                        currentWorld.value = world
                    }
                )

                // Store the current world again to ensure consistency if it changed
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
                playerController.setStartNodes(nodes)
                objectManager.setWorldMeshes(worldMeshes)
                otherPlayersCoordinator.setWorldMeshes(worldMeshes)

                // Find a valid starting position for the player
                val preferredStart = SceneManager.getAndClearPendingTeleport()
                val startPosition = if (preferredStart != null) {
                    playerController.findValidYPosition(preferredStart) ?: preferredStart
                } else if (currentWorld.value?.lastPosition != null) {
                    val last = currentWorld.value!!.lastPosition!!
                    val vec = lib.Vector3(last.x, last.y, last.z)
                    playerController.findValidYPosition(vec) ?: vec
                } else {
                    playerController.findValidStartPosition()
                }

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

                // Initialize locate object listener
                console.log("[DEBUG_LOG] Scene: Initializing locate object listener")
                playerController.initializeLocateObjectListener(router)
                console.log("[DEBUG_LOG] Scene: Locate object listener initialization completed")

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
                        console.log("[DEBUG] Player mesh registered as shadow caster and receiver")
                    }
                }

                // Set initial camera target position
                cameraController.setInitialTargetPosition(startPosition)

                // Initialize proximity-based cameras from the world
                cameraController.initializeProximityCameras()

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
                    otherPlayersCoordinator.sendJoinEvent(startPosition)

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

                    // Set up PLAY_SOUND listener
                    WebSocketApi.addListener(models.WebSocketAction.PLAY_SOUND) { message ->
                        try {
                            console.log("[Scene] Received PLAY_SOUND message:", message)
                            // Parse message as map
                            val data = ApiClient.json.decodeFromString<Map<String, String>>(message.data)
                            val url = data["url"] ?: ""
                            console.log("[Scene] Parsed sound URL:", url)
                            if (url.isNotBlank()) {
                                SoundManager.playSound(url)
                            } else {
                                console.warn("[Scene] PlaySound URL is blank")
                            }
                        } catch (e: Throwable) {
                            console.error("Error handling PLAY_SOUND message", e)
                        }
                    }
                } else {
                    console.warn("WebSocket connection not established after multiple attempts. Player movement may be limited.")
                }

                console.log("Setting WorldObjectManager in other players coordinator...")

                // Set the WorldObjectManager in the OtherPlayersCoordinator for username display
                otherPlayersCoordinator.setWorldObjectManager(worldObjectManager)

                // Provide HTTP worldId override (DB key) for snapshot fetches
                currentWorld.value?.key?.let { dbKey ->
                    otherPlayersCoordinator.setHttpWorldId(dbKey)
                }

                console.log("Initializing other players coordinator...")

                // Initialize other players coordinator for multiplayer
                otherPlayersCoordinator.initialize()


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
                val fps = if (dtMs > 0.0 && dtMs < 1_000.0) 1_000.0 / dtMs else 0.0

                if (fps > 0.0 && fps < engine.maxFPS / 2f) {
                    lowFpsAccumulatedMs += dtMs
                } else {
                    // Reset if performance recovers
                    lowFpsAccumulatedMs = 0.0
                }

                // If FPS has been < 30 for 5 seconds, degrade graphics
                if (lowFpsAccumulatedMs >= 60_000.0) {
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
            val speedMultiplier = if (inputHandler.isSprinting()) 4.0f else 1.0f

            // Update player position
            val newPosition = playerController.updatePlayerPosition(
                moveDirection = moveDirection,
                speedMultiplier = speedMultiplier,
            )

            // Update proximity camera state based on player position
            cameraController.checkProximity(playerController.getPlayerPosition() ?: lib.Vector3.Zero())

            if (newPosition == null) {
                // No movement this frame; zero out instantaneous velocity for sway
                SceneManager.setPlayerVelocity(null)
            }

            // Update camera target if player moved and Observer Mode is OFF
            if (newPosition != null && !GraphicsSettings.isObserverModeEnabled()) {
                cameraController.updateCameraTarget(newPosition)
            }

            // Update camera animation
            val isCameraAnimating = cameraController.updateAnimation()

            val currentTime = window.performance.now()
            val isPlayerMoving = moveDirection.length() > 0 || playerController.isMoving() || playerController.isJumping()
            
            if (isPlayerMoving || isCameraAnimating) {
                lastInteractionTime = currentTime
                if (isScreenSaverActive) {
                    stopScreenSaver()
                }
                if (idleHighlighter.isActive()) {
                    stopIdleHighlighting()
                }
            } else {
                // Check for inactivity
                if (!isScreenSaverActive && (currentTime - lastInteractionTime) > 60_000.0) {
                    triggerScreenSaver()
                }

                // Idle highlighting starts after 15s of inactivity
                if (!idleHighlighter.isActive() && (currentTime - lastInteractionTime) > 15_000.0) {
                    triggerIdleHighlighting()
                }
            }

            // Update world objects
            worldObjectManager.update(scene.deltaTime)
            objectManager.update(scene.deltaTime)

            // Update occlusion fading effect
            worldLoader.getOcclusionManager()?.update()

            // Update performance engine (culling small objects)
            worldLoader.getPerformanceEngine()?.update()

            // Update shadows based on camera distance (Task 2)
            worldLoader.getShadowManager()?.update()
        }

        // Start the render loop
        engine.runRenderLoop {
            scene.render()
        }

        // Clean up when the component is disposed
        onDispose {
            stopIdleHighlighting()
            // Cancel the coroutine scope
            coroutineScope.cancel()

            window.removeEventListener("resize", resizeHandler)
            window.removeEventListener("objectDrop", objectDropHandler)
            window.removeEventListener("click", unlockAudio)
            window.removeEventListener("mousedown", unlockAudio)
            window.removeEventListener("keydown", unlockAudio)
            window.removeEventListener("touchstart", unlockAudio)

            // Unregister the graphics mode observer
            GraphicsSettings.removeObserver(graphicsModeObserver)

            // Disconnect from WebSocket
            WebSocketConnectionManager.disconnectFromWorld(url)

            // Dispose controllers
            cameraController.dispose()
            playerController.dispose()
            otherPlayersCoordinator.dispose()
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

    // Add DialogueBox component for displaying dialogue messages
    DialogueBox {
        scope.launch {
            awaitAnimationFrame()
            canvasRef?.focus()
        }
    }

    // Add MinigameOverlay component for language learning games
    MinigameOverlay {
        scope.launch {
            awaitAnimationFrame()
            canvasRef?.focus()
        }
    }

    // Add TakeItems dialog for item transfer prompts
    TakeItemsDialog {
        scope.launch {
            awaitAnimationFrame()
            canvasRef?.focus()
        }
    }

    // Simple message dialog host
    if (showMessageDialog.value) {
        BaseDialog(
            onClose = { showMessageDialog.value = false },
            title = translated("Collect item"),
            content = {
                Text(messageDialogText.value)
            },
            actions = {
                DialogButton(
                    onClick = { showMessageDialog.value = false },
                    style = DialogButtonStyle.Secondary,
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
                    style = DialogButtonStyle.Primary,
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
            okLabel = translated("Drop"),
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
