package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.softwork.routingcompose.Router
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
import main.api.UserApi
import main.api.WebSocketConnectionManager
import main.components.camera.CameraController
import main.components.input.InputHandler
import main.components.`object`.SceneObjectManager
import main.components.player.OtherPlayersController
import main.components.player.PlayerController
import main.components.world.WorldLoader
import main.components.world.WorldObjectManager
import main.theme.Styles
import main.utils.createObjectDropEvent
import org.jetbrains.compose.web.dom.Canvas
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import kotlin.random.Random

/**
 * Helper function to dispatch an object drop event with the necessary data.
 * 
 * @param x The x coordinate of the drop
 * @param y The y coordinate of the drop
 * @param data The JSON data of the dropped object
 */
private fun dispatchObjectDropEvent(x: Int, y: Int, data: String) {
    // Create a custom event using the utility function
    val customEvent = createObjectDropEvent(x, y, data)
    
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
    
    // State to track low graphics mode
    val isLowGraphicsMode = remember { mutableStateOf(GraphicsSettings.isLowGraphicsModeEnabled()) }
    
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
    
    // Observe changes to graphics settings
    LaunchedEffect(Unit) {
        // Create a coroutine that watches for changes to the graphics mode
        while (true) {
            // Check if the graphics mode has changed
            val currentMode = GraphicsSettings.isLowGraphicsModeEnabled()
            if (isLowGraphicsMode.value != currentMode) {
                isLowGraphicsMode.value = currentMode
            }
            delay(100) // Check every 100ms
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
                it.focus()

                onDispose {}
            }

            // Add drag and drop event listeners
            onDragOver { event -> 
                event.preventDefault()
            }
            onDrop { event ->
                event.preventDefault()
                
                // Get the necessary data from the event
                val clientX = event.nativeEvent.asDynamic().clientX as Int
                val clientY = event.nativeEvent.asDynamic().clientY as Int
                val data = event.nativeEvent.asDynamic().dataTransfer.getData("application/json") as String
                
                // Use the helper function to dispatch the custom event
                dispatchObjectDropEvent(clientX, clientY, data)
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
        VisitorDialog(
            onClose = { 
                showLoginDialog.value = false 
            },
            onRegister = { username, password, yearOfBirth, sex ->
                // Create a coroutine scope
                val coroutineScope = CoroutineScope(Dispatchers.Main)
                
                // Handle registration using coroutines
                coroutineScope.launch {
                    try {
                        // First ensure we have a visitor token by getting the current user
                        val currentUser = UserApi.getCurrentUser()
                        
                        // Check if we have a token before proceeding
                        val token = UserApi.getToken()
                        if (token == null || token.isEmpty()) {
                            console.error("Registration error: No valid token available")
                            
                            try {
                                // Try to create a visitor again to get a token
                                val visitor = UserApi.createOrGetVisitor("")
                                console.log("Created new visitor: ${visitor.visitorId}")
                                
                                // Add a small delay to ensure token is saved
                                delay(500) // 500ms delay
                                
                                try {
                                    // Now upgrade the visitor to a player
                                    val user = UserApi.upgradeToPlayer(username, password, yearOfBirth, sex)
                                    console.log("User registered: ${user.username}")
                                    showLoginDialog.value = false
                                    // After successful registration, show the world edit panel
                                    showWorldEditPanel.value = true
                                } catch (error: Exception) {
                                    console.error("Registration error after retry: ${error.message}")
                                }
                            } catch (error: Exception) {
                                console.error("Error creating visitor: ${error.message}")
                            }
                        } else {
                            // We have a token, proceed with upgrade
                            try {
                                val user = UserApi.upgradeToPlayer(username, password, yearOfBirth, sex)
                                console.log("User registered: ${user.username}")
                                showLoginDialog.value = false
                                // After successful registration, show the world edit panel
                                showWorldEditPanel.value = true
                            } catch (error: Exception) {
                                console.error("Registration error: ${error.message}")
                            }
                        }
                    } catch (error: Exception) {
                        console.error("Error getting current user: ${error.message}")
                    }
                }
            },
            onLogin = { username, password ->
                // Create a coroutine scope
                val coroutineScope = CoroutineScope(Dispatchers.Main)
                
                // Handle login
                coroutineScope.launch {
                    try {
                        val user = UserApi.login(username, password)
                        console.log("User logged in: ${user.username}")
                        showLoginDialog.value = false
                        // After successful login, show the world edit panel
                        showWorldEditPanel.value = true
                    } catch (error: Exception) {
                        console.error("Login error: ${error.message}")
                    }
                }
            }
        )
    }
    
    // Add the UI overlay when the world is loaded
    if (isLoaded.value) {
        WorldUI()
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
        val objectManager = SceneObjectManager(scene, cameraController)
        val worldObjectManager = WorldObjectManager(scene)
        
        // Register object manager with SceneManager for global access
        SceneManager.setObjectManager(objectManager)
        
        // Create input handler after object manager is registered
        val inputHandler = InputHandler(scene, playerController, cameraController)

        // Set up object drop handler
        val objectDropHandler: (Event) -> Unit = { event ->
            val detail = event.asDynamic().detail
            val x = detail.x as Int
            val y = detail.y as Int
            val data = detail.data as String
            
            // Use the object manager to handle the drop
            objectManager.handleObjectDrop(x, y, data, currentWorld.value?.key ?: url)
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
                
                // Set world meshes in controllers
                playerController.setWorldMeshes(worldMeshes)
                objectManager.setWorldMeshes(worldMeshes)
                
                // Find a valid starting position for the player
                val startPosition = playerController.findValidStartPosition()
                
                // Create the player using the WorldObjectManager
                playerController.createPlayer(startPosition, worldObjectManager)
                
                // Initialize chat message listener
                playerController.initializeChatMessageListener()
                
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
                objectManager.setupWebSocketListeners()
                
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
                } else {
                    console.warn("WebSocket connection not established after multiple attempts. Player movement may be limited.")
                }
                
                console.log("Setting WorldObjectManager in other players controller...")
                
                // Set the WorldObjectManager in the OtherPlayersController for username display
                otherPlayersController.setWorldObjectManager(worldObjectManager)
                
                console.log("Initializing other players controller...")
                
                // Initialize other players controller for multiplayer
                otherPlayersController.initialize()
                
                // Set the shadow manager in the OtherPlayersController
                worldLoader.getShadowManager()?.let { shadowManager ->
                    otherPlayersController.setShadowManager(shadowManager)
                    println("[DEBUG] Shadow manager set in OtherPlayersController")
                }
                
                // Mark as loaded to hide loading screen
                isLoaded.value = true
            } catch (error: Exception) {
                console.error("Error loading world: ${error.message}")
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
