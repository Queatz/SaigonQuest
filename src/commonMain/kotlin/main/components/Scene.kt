package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import main.api.WebSocketApi
import main.components.camera.CameraController
import main.components.input.InputHandler
import main.components.`object`.ObjectManager
import main.components.player.PlayerController
import main.components.world.WorldLoader
import main.components.SceneManager
import main.components.WorldEdit
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.left
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.position
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.top
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Canvas
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import kotlin.random.Random
import org.jetbrains.compose.web.css.Position as PositionCss

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

    // Set up the canvas with CSS
    Canvas(
        attrs = {
            id(canvasId)
            style {
                width(100.percent)
                height(100.vh)
                display(DisplayStyle.Block)
            }
            // Add drag and drop event listeners
            attr("ondragover", "event.preventDefault();")
            attr("ondrop", "event.preventDefault(); window.dispatchEvent(new CustomEvent('objectDrop', {detail: {x: event.clientX, y: event.clientY, data: event.dataTransfer.getData('application/json')}}));")
        }
    )
    
    // Loading screen or error message
    if (!isLoaded.value && !showWorldEditPanel.value && !showLoginDialog.value) {
        Div(
            attrs = {
                style {
                    position(PositionCss.Absolute)
                    top(0.px)
                    left(0.px)
                    width(100.percent)
                    height(100.vh)
                    backgroundColor(Color.black)
                    color(Color.white)
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    justifyContent(JustifyContent.Center)
                    alignItems(AlignItems.Center)
                    property("z-index", "10")
                    padding(16.px)
                    textAlign("center")
                }
            }
        ) {
            if (loadingFailed.value) {
                // Error message when world loading fails
                H2 {
                    Text(errorMessage.value)
                }
                
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Row)
                            justifyContent(JustifyContent.Center)
                            alignItems(AlignItems.Center)
                            gap(16.px)
                            marginTop(24.px)
                        }
                    }
                ) {
                    // Create this world or Login button
                    Button(
                        attrs = {
                            style {
                                padding(12.px, 24.px)
                                backgroundColor(rgba(0, 120, 255, 0.7))
                                color(Color.white)
                                property("border", "0")
                                property("border-radius", "4px")
                                cursor("pointer")
                                fontSize(16.px)
                            }
                            onClick { 
                                // Check if user is logged in
                                if (UserApi.getToken() != null) {
                                    // User is logged in, show world edit panel
                                    showWorldEditPanel.value = true
                                } else {
                                    // User is not logged in, show login dialog
                                    showLoginDialog.value = true
                                }
                            }
                        }
                    ) {
                        // Show different text based on login status
                        Text(if (UserApi.getToken() != null) "Claim this World" else "Login and Claim this World")
                    }
                    
                    // Go back button
                    Button(
                        attrs = {
                            style {
                                padding(12.px, 24.px)
                                backgroundColor(rgba(255, 255, 255, 0.2))
                                color(Color.white)
                                property("border", "0")
                                property("border-radius", "4px")
                                cursor("pointer")
                                fontSize(16.px)
                            }
                            onClick { 
                                router.navigate("/")
                            }
                        }
                    ) {
                        Text("[ Go back ]")
                    }
                }
            } else {
                // Normal loading message
                Span {
                    Text("Loading Saigon.Quest world...")
                }
            }
        }
    }
    
    // Show world edit panel if requested
    if (showWorldEditPanel.value) {
        WorldEdit(
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

        // Handle window resize
        val resizeHandler = { _: Event ->
            engine.resize()
        }
        window.addEventListener("resize", resizeHandler)

        // Create the scene
        val scene = Scene(engine)
        
        // Initialize BabylonJS loaders before loading the 3D model
        initializeBabylonJSLoaders()
        
        // Create controllers
        val cameraController = CameraController(scene)
        // Register camera controller with SceneManager for global access
        SceneManager.setCameraController(cameraController)
        
        val playerController = PlayerController(scene)
        val inputHandler = InputHandler(scene, playerController, cameraController)
        val worldLoader = WorldLoader(scene)
        val objectManager = ObjectManager(scene, cameraController)
        
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
                
                // Create the player
                playerController.createPlayer(startPosition)
                
                // Register player mesh as shadow caster and receiver
                playerController.getPlayer()?.let { playerMesh ->
                    worldLoader.getShadowManager()?.let { shadowManager ->
                        shadowManager.addShadowCaster(playerMesh)
                        shadowManager.configureShadowReceiver(playerMesh)
                        println("[DEBUG] Player mesh registered as shadow caster and receiver")
                    }
                }
                
                // Set player mesh in object manager to exclude from collision detection
                objectManager.setPlayerMesh(playerController.getPlayer())
                objectManager.setMoveCircleMesh(playerController.getMoveCircle())
                
                // Set initial camera target position
                cameraController.setInitialTargetPosition(startPosition)
                
                // Set up input handling
                inputHandler.setupKeyboardHandling()
                inputHandler.setupPointerHandling()
                
                // Set up WebSocket listeners for objects
                objectManager.setupWebSocketListeners()
                
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
            
            // Dispose controllers
            cameraController.dispose()
            playerController.dispose()
            objectManager.dispose()
            worldLoader.dispose()
            
            // Clear SceneManager references
            SceneManager.clear()
            
            // Dispose scene and engine
            scene.dispose()
            engine.dispose()
        }
    }
}
