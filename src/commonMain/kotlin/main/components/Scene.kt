package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.softwork.routingcompose.Router
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.serialization.json.Json
import lib.AbstractMesh
import lib.ArcRotateCamera
import lib.Color3
import lib.DefaultRenderingPipeline
import lib.DirectionalLight
import lib.Engine
import lib.HemisphericLight
import lib.KeyboardEventTypes
import lib.Matrix
import lib.Mesh
import lib.MeshBuilder
import lib.PBRMaterial
import lib.PointerEventTypes
import lib.Ray
import lib.Scene
import lib.SceneLoader
import lib.StandardMaterial
import lib.Vector3
import lib.initializeBabylonJSLoaders
import main.api.UserApi
import main.api.WebSocketApi
import main.api.WorldApi
import models.Object
import models.Position
import models.Rotation
import models.Scale
import models.World
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
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
    val currentWorld = remember { mutableStateOf<World?>(null) }
    
    // State to track if the world edit panel is visible
    val showWorldEditPanel = remember { mutableStateOf(false) }
    
    // State to track if the login dialog is visible
    val showLoginDialog = remember { mutableStateOf(false) }
    
    // Get the router for navigation
    val router = Router.current

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
                        Text(if (UserApi.getToken() != null) "[ Create this world ]" else "[ Login ]")
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
        WorldEditPanel(
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
            onRegister = { username, password ->
                // Handle registration
                // First ensure we have a visitor token by getting the current user
                UserApi.getCurrentUser()
                    .then { _ ->
                        // Check if we have a token before proceeding
                        val token = UserApi.getToken()
                        if (token == null || token.isEmpty()) {
                            console.error("Registration error: No valid token available")
                            // Try to create a visitor again to get a token
                            UserApi.createOrGetVisitor("")
                                .then { visitor ->
                                    console.log("Created new visitor: ${visitor.visitorId}")
                                    // Add a small delay to ensure token is saved
                                    window.setTimeout({
                                        // Now upgrade the visitor to a player
                                        UserApi.upgradeToPlayer(username, password)
                                            .then { user ->
                                                console.log("User registered: ${user.username}")
                                                showLoginDialog.value = false
                                                // After successful registration, show the world edit panel
                                                showWorldEditPanel.value = true
                                            }
                                            .catch { error ->
                                                console.error("Registration error after retry: ${error.message}")
                                            }
                                    }, 500) // 500ms delay
                                }
                                .catch { error ->
                                    console.error("Error creating visitor: ${error.message}")
                                }
                        } else {
                            // We have a token, proceed with upgrade
                            UserApi.upgradeToPlayer(username, password)
                                .then { user ->
                                    console.log("User registered: ${user.username}")
                                    showLoginDialog.value = false
                                    // After successful registration, show the world edit panel
                                    showWorldEditPanel.value = true
                                }
                                .catch { error ->
                                    console.error("Registration error: ${error.message}")
                                }
                        }
                    }
                    .catch { error ->
                        console.error("Error getting current user: ${error.message}")
                    }
            },
            onLogin = { username, password ->
                // Handle login
                UserApi.login(username, password)
                    .then { user ->
                        console.log("User logged in: ${user.username}")
                        showLoginDialog.value = false
                        // After successful login, show the world edit panel
                        showWorldEditPanel.value = true
                    }
                    .catch { error ->
                        console.error("Login error: ${error.message}")
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
        
        // Player variables
        var player: Mesh? = null
        var playerTargetPosition: Vector3? = null
        var moveCircle: Mesh? = null
        var worldMeshes: Array<AbstractMesh> = emptyArray()
        var isMoving = false
        
        // Object tracking
        val sceneObjects = mutableMapOf<String, Object>()
        val objectMeshes = mutableMapOf<String, Mesh>()
        var lastProximityCheck = 0.0
        val proximityCheckInterval = 500.0 // Check every 500ms
        
        // Movement speed
        val moveSpeed = 0.05f
        
        // Track pressed keys for continuous movement
        val pressedKeys = mutableSetOf<String>()
        
        // Track if pointer is being dragged
        var isPointerDown = false
        var pointerStartX = 0
        var pointerStartY = 0
        var isDragging = false
        
        // Track if camera target is being moved manually
        var isCameraTargetMovedManually = false

        // Create a camera with constraints (min 4, max 12)
        val camera = ArcRotateCamera(
            name = "camera",
            alpha = 0f,
            beta = 0f,
            radius = 8f, // Starting radius between min and max
            target = Vector3.Zero(),
            scene = scene
        ).apply {
            // Set camera constraints
            this.lowerRadiusLimit = 4f // Min constraint
            this.upperRadiusLimit = 12f // Max constraint

            // Set initial position
            this.alpha = (-PI / 2).toFloat()
            this.beta = (PI / 3).toFloat()

            // Attach camera controls
            attachControl(true)

            inputs.removeByType("ArcRotateCameraKeyboardMoveInput");
        }

        // Add a hemispheric light for ambient lighting
        val hemisphericLight = HemisphericLight(
            name = "hemisphericLight",
            direction = Vector3(0f, 1f, 0f),
            scene = scene
        ).apply {
            this.intensity = 0.5f
        }

        // Add a directional light for shadows
        val directionalLight = DirectionalLight(
            name = "directionalLight",
            direction = Vector3(0f, -1f, 1f),
            scene = scene
        ).apply {
            this.intensity = 0.8f
        }

        // Set up PBR rendering pipeline
        val pipeline = DefaultRenderingPipeline(
            name = "pbrPipeline",
            hdr = true,
            scene = scene,
            cameras = arrayOf(camera)
        ).apply {
            // Enable bloom for better lighting effects
            bloomEnabled = true
            bloomThreshold = 0.8f
            bloomWeight = 0.3f
            bloomKernel = 64f

            // Enable depth of field
            depthOfFieldEnabled = true
            depthOfField.focalLength = 50f
            depthOfField.fStop = 1.4f
            // Initial focus distance, will be updated in render loop
            depthOfField.focusDistance = 2000f
        }
        
        // Function to check if a position is valid (has ground below)
        fun isValidPosition(position: Vector3): Boolean {
            // Create a ray from slightly above the position pointing down
            val rayOrigin = Vector3(position.x, position.y + 1f, position.z)
            val rayDirection = Vector3(0f, -1f, 0f)
            val ray = Ray()
            ray.origin = rayOrigin
            ray.direction = rayDirection
            
            // Check if the ray intersects with any world mesh
            for (mesh in worldMeshes) {
                // Skip player and move circle meshes
                if (mesh == player || mesh == moveCircle) continue
                
                val hit = ray.intersectsMesh(mesh)
                if (hit.pickedPoint != null) {
                    return true
                }
            }
            
            return false
        }
        
        // Function to create a move circle at the target position
        fun createMoveCircle(position: Vector3) {
            // Remove existing move circle if any
            moveCircle?.dispose()
            
            // Create a new circle
            moveCircle = MeshBuilder.CreateDisc("moveCircle", 
                object {
                    val radius = 0.5f
                    val tessellation = 32
                }, scene)
            
            // Position the circle slightly above the ground to avoid z-fighting
            moveCircle?.position = Vector3(position.x, position.y + 0.01f, position.z)
            
            // Rotate the circle to be flat on the ground
            moveCircle?.rotation = Vector3(PI.toFloat() / 2f, 0f, 0f)
            
            // Create a material for the circle
            val material = StandardMaterial("moveCircleMaterial", scene)
            material.diffuseColor = Color3(0f, 0.8f, 1f)
            material.alpha = 0.7f
            material.backFaceCulling = false
            moveCircle?.material = material
            
            // Set up animation to scale down the circle
            var scaleValue = 1.0f
            scene.registerBeforeRender {
                if (moveCircle != null && scaleValue > 0) {
                    scaleValue -= 0.02f
                    if (scaleValue <= 0) {
                        moveCircle?.dispose()
                        moveCircle = null
                    } else {
                        moveCircle?.scaling = Vector3(scaleValue, scaleValue, scaleValue)
                        // Also fade out
                        (moveCircle?.material as? StandardMaterial)?.alpha = scaleValue * 0.7f
                    }
                }
            }
        }

        // Initialize BabylonJS loaders before loading the 3D model
        initializeBabylonJSLoaders()

        // Load the world data from the API based on the URL
        WorldApi.getWorldByUrl(url)
            .then { world ->
                // Store the world data
                currentWorld.value = world
                
                // Connect to WebSocket for real-time updates
                WebSocketApi.connect(url)
                
                // Determine the file to load
                val worldFile = if (world.file.isNotEmpty()) "${world.file}.glb" else "world.glb"
                
                // Load the 3D model
                SceneLoader.ImportMesh(
                    meshNames = "",
                    rootUrl = "/raw/data/",
                    sceneFilename = worldFile,
                    scene = scene,
                    onSuccess = { meshes, _, _, _ ->
                        // Store world meshes for collision detection
                        worldMeshes = meshes
                        
                        // Apply PBR materials to all meshes
                        meshes.forEach { mesh ->
                            // Skip if mesh is not visible or is a helper object
                            if (mesh.isVisible == false || mesh.name.startsWith("__")) {
                                return@forEach
                            }

                            // Create a PBR material
                            val material = PBRMaterial("pbr-${mesh.name}", scene).apply {
                                metallic = 0.3f
                                roughness = 0.6f

                                // Use the existing material's colors if available
                                val existingMaterial = mesh.material
                                if (existingMaterial is PBRMaterial) {
                                    albedoColor = existingMaterial.albedoColor
                                    emissiveColor = existingMaterial.emissiveColor
                                }
                            }

                            // Assign the PBR material to the mesh
                            mesh.material = material
                        }
                        
                        // Create player box
                        player = MeshBuilder.CreateBox("player", 
                            object {
                                val width = 0.5f
                                val height = 1.0f
                                val depth = 0.5f
                            }, scene)
                        
                        // Create player material
                        val playerMaterial = PBRMaterial("playerMaterial", scene).apply {
                            albedoColor = Color3(0.2f, 0.4f, 0.8f)
                            metallic = 0.1f
                            roughness = 0.2f
                            emissiveColor = Color3(0.1f, 0.1f, 0.3f)
                        }
                        player?.material = playerMaterial
                        
                        // Position player at a valid starting position
                        // Find a suitable position on the ground
                        var startPosition = Vector3.Zero()
                        for (x in -5..5) {
                            for (z in -5..5) {
                                val testPosition = Vector3(x.toFloat(), 0f, z.toFloat())
                                if (isValidPosition(testPosition)) {
                                    // Find the exact y position by raycasting
                                    val ray = Ray()
                                    ray.origin = Vector3(testPosition.x, 10f, testPosition.z)
                                    ray.direction = Vector3(0f, -1f, 0f)
                                    
                                    for (mesh in worldMeshes) {
                                        if (mesh == player) continue
                                        val hit = ray.intersectsMesh(mesh)
                                        if (hit.pickedPoint != null) {
                                            startPosition = Vector3(testPosition.x, hit.pickedPoint!!.y + 0.5f, testPosition.z)
                                            break
                                        }
                                    }
                                    if (startPosition.y > 0) break
                                }
                            }
                            if (startPosition.y > 0) break
                        }
                        
                        player?.position = startPosition
                        
                        // Update camera target to follow player (initial setup)
                        // This won't be affected by manual camera movement yet since we're just loading
                        camera.setTarget(player?.position ?: Vector3.Zero())
                        
                        // Reset the camera target moved manually flag to ensure proper initial state
                        isCameraTargetMovedManually = false
                        
                        // Mark as loaded to hide loading screen
                        isLoaded.value = true
                    },
                    onError = { _, message, exception ->
                        console.error("Error loading model: $message")
                        console.error("Exception details: $exception")
                        loadingFailed.value = true
                    }
                )
            }
            .catch { error ->
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
        
        // Set up keyboard controls
        scene.onKeyboardObservable.add { info ->
            val event = info.event
            val key = event.key.lowercase()
            
            // Only handle WASD and arrow keys
            if (key in listOf("w", "a", "s", "d", "arrowup", "arrowdown", "arrowleft", "arrowright")) {
                when (info.type) {
                    KeyboardEventTypes.KEYDOWN -> {
                        // Add key to pressed keys set
                        pressedKeys.add(key)
                        
                        // Cancel any click-to-move in progress
                        if (pressedKeys.isNotEmpty()) {
                            playerTargetPosition = null
                            isMoving = false
                        }
                    }
                    KeyboardEventTypes.KEYUP -> {
                        // Remove key from pressed keys set
                        pressedKeys.remove(key)
                    }
                    else -> { /* Ignore other keyboard events */ }
                }
            }
        }
        
        // Set up click/touch to move
        scene.onPointerObservable.add { info ->
            when (info.type) {
                PointerEventTypes.POINTERDOWN -> {
                    // Track pointer down state and position
                    isPointerDown = true
                    pointerStartX = scene.pointerX
                    pointerStartY = scene.pointerY
                    isDragging = false
                }
                
                PointerEventTypes.POINTERMOVE -> {
                    // Check if dragging (pointer is down and has moved more than a threshold)
                    if (isPointerDown) {
                        val deltaX = abs(scene.pointerX - pointerStartX)
                        val deltaY = abs(scene.pointerY - pointerStartY)
                        
                        // Consider it a drag if moved more than 5 pixels in any direction
                        if (deltaX > 5 || deltaY > 5) {
                            isDragging = true
                            
                            // Set flag to indicate camera target is being moved manually
                            isCameraTargetMovedManually = true
                            
                            // If dragging, cancel any movement
                            if (isMoving) {
                                isMoving = false
                                playerTargetPosition = null
                            }
                        }
                    }
                }
                
                PointerEventTypes.POINTERUP -> {
                    // If it was a click (not a drag) and player exists, handle movement
                    if (!isDragging && player != null) {
                        // Create a ray from the camera through the clicked point
                        val ray = scene.createPickingRay(
                            scene.pointerX,
                            scene.pointerY,
                            Matrix.Identity(),
                            camera
                        )
                        
                        // Check for intersection with world meshes
                        var closestDistance = Float.MAX_VALUE
                        var closestPoint: Vector3? = null
                        
                        for (mesh in worldMeshes) {
                            // Skip player and move circle meshes
                            if (mesh == player || mesh == moveCircle) continue
                            
                            val hit = ray.intersectsMesh(mesh)
                            if (hit.pickedPoint != null) {
                                val distance = Vector3.Distance(camera.position, hit.pickedPoint!!)
                                if (distance < closestDistance) {
                                    closestDistance = distance
                                    closestPoint = hit.pickedPoint
                                }
                            }
                        }
                        
                        // If we found a valid point, set it as the target
                        if (closestPoint != null) {
                            playerTargetPosition = Vector3(closestPoint.x, closestPoint.y + 0.5f, closestPoint.z)
                            isMoving = true
                            
                            // Create move circle at target position
                            createMoveCircle(closestPoint)
                        }
                    }
                    
                    // Reset pointer state
                    isPointerDown = false
                    isDragging = false
                    
                    // Reset camera target moved manually flag after a short delay
                    // This allows the camera to stabilize before resuming player tracking
                    window.setTimeout({
                        isCameraTargetMovedManually = false
                    }, 500) // 500ms delay
                }
                
                else -> { /* Ignore other pointer events */ }
            }
        }
        
        // Update player movement in the render loop
        scene.registerBeforeRender {
            if (player != null) {
                var moveDirection = Vector3.Zero()
                
                // Handle keyboard movement (if any keys are pressed)
                if (pressedKeys.isNotEmpty()) {
                    // Get camera's forward and right directions
                    val cameraForward = Vector3.Zero()
                    val cameraRight = Vector3.Zero()
                    
                    // Get forward direction from camera (ignoring Y component for flat movement)
                    val forward = camera.getDirection(Vector3.Forward())
                    cameraForward.x = forward.x
                    cameraForward.z = forward.z
                    cameraForward.normalize()
                    
                    // Get right direction (perpendicular to forward)
                    cameraRight.x = cameraForward.z
                    cameraRight.z = -cameraForward.x
                    cameraRight.normalize()
                    
                    // Calculate movement based on pressed keys
                    var moveX = 0f
                    var moveZ = 0f
                    
                    // WASD keys
                    if ("w" in pressedKeys) moveZ += 1f
                    if ("s" in pressedKeys) moveZ -= 1f
                    if ("a" in pressedKeys) moveX -= 1f
                    if ("d" in pressedKeys) moveX += 1f
                    
                    // Arrow keys (only control player, not camera)
                    if ("arrowup" in pressedKeys) moveZ += 1f
                    if ("arrowdown" in pressedKeys) moveZ -= 1f
                    if ("arrowleft" in pressedKeys) moveX -= 1f
                    if ("arrowright" in pressedKeys) moveX += 1f
                    
                    // Combine directions relative to camera
                    if (moveX != 0f || moveZ != 0f) {
                        // Forward/backward movement
                        if (moveZ != 0f) {
                            moveDirection = moveDirection.add(cameraForward.scale(moveZ))
                        }
                        
                        // Left/right movement
                        if (moveX != 0f) {
                            moveDirection = moveDirection.add(cameraRight.scale(moveX))
                        }
                        
                        // Normalize for consistent speed in diagonal movement
                        if (moveDirection.length() > 0) {
                            moveDirection.normalize()
                            moveDirection.scaleInPlace(moveSpeed)
                        }
                    }
                }
                
                // Handle click-to-move (if active and no keyboard movement)
                if (isMoving && playerTargetPosition != null && moveDirection.length() == 0f) {
                    val currentPos = player!!.position
                    val targetPos = playerTargetPosition!!
                    
                    // Calculate direction and distance
                    val direction = targetPos.subtract(currentPos)
                    val distance = direction.length()
                    
                    // If we're close enough, stop moving
                    if (distance < 0.1f) {
                        isMoving = false
                        playerTargetPosition = null
                    } else {
                        // Normalize direction and apply speed
                        direction.normalize()
                        direction.scaleInPlace(min(moveSpeed, distance))
                        moveDirection = direction
                    }
                } else if (moveDirection.length() > 0f) {
                    // If keyboard movement is active, cancel click-to-move
                    isMoving = false
                    playerTargetPosition = null
                }
                
                // Apply movement if there is any
                if (moveDirection.length() > 0f) {
                    // Calculate new position
                    val currentPos = player!!.position
                    val newPos = currentPos.add(moveDirection)
                    
                    // Check if the new position is valid
                    if (isValidPosition(newPos)) {
                        // Find the exact y position by raycasting
                        val ray = Ray()
                        ray.origin = Vector3(newPos.x, newPos.y + 1f, newPos.z)
                        ray.direction = Vector3(0f, -1f, 0f)
                        
                        for (mesh in worldMeshes) {
                            if (mesh == player || mesh == moveCircle) continue
                            val hit = ray.intersectsMesh(mesh)
                            if (hit.pickedPoint != null) {
                                newPos.y = hit.pickedPoint!!.y + 0.5f // Half the player height
                                break
                            }
                        }
                        
                        // Update player position
                        player!!.position = newPos
                        
                        // Send player position update via WebSocket
                        val userModel = UserApi.getCurrentUser().then { user ->
                            WebSocketApi.updatePlayerPosition(
                                position = Position(
                                    x = newPos.x,
                                    y = newPos.y,
                                    z = newPos.z
                                ),
                                isPlayer = user.isPlayer
                            )
                        }
                        
                        // Only update camera target if it's not being moved manually
                        if (!isCameraTargetMovedManually) {
                            // Update camera target and position to follow player
                            // Store current camera settings
                            val currentAlpha = camera.alpha
                            val currentBeta = camera.beta
                            val currentRadius = camera.radius
                            
                            // Update camera target to player position
                            camera.setTarget(newPos)
                            
                            // Restore camera settings to maintain relative position
                            camera.alpha = currentAlpha
                            camera.beta = currentBeta
                            camera.radius = currentRadius
                        }
                    } else if (isMoving) {
                        // If the position is invalid and we're using click-to-move, stop moving
                        isMoving = false
                        playerTargetPosition = null
                    }
                }
            }
        }

        // Start the render loop
        engine.runRenderLoop {
            // Update DOF focus distance to match distance to player
            if (player != null && pipeline.depthOfFieldEnabled) {
                // Calculate distance between camera and player
                val distanceToPlayer = Vector3.Distance(camera.position, player!!.position)
                // Update DOF focus distance
                pipeline.depthOfField.focusDistance = distanceToPlayer
            }
            
            scene.render()
        }

        // Function to create a cube for an object
        fun createObjectCube(obj: Object) {
            // Store the object in our map
            sceneObjects[obj.key] = obj
            
            // Remove existing mesh if any
            objectMeshes[obj.key]?.dispose()
            
            // Create a box mesh for the object
            val cube = MeshBuilder.CreateBox("object-${obj.key}", 
                object {
                    val width = 1.0f
                    val height = 1.0f
                    val depth = 1.0f
                }, scene)
            
            // Position the cube
            cube.position = Vector3(
                obj.position.x,
                obj.position.y,
                obj.position.z
            )
            
            // Create a material for the cube
            val material = StandardMaterial("material-${obj.key}", scene)
            material.diffuseColor = Color3(0.2f, 0.6f, 1.0f) // Blue color
            
            // Apply the material to the cube
            cube.material = material
            
            // Store the mesh in our map
            objectMeshes[obj.key] = cube
        }
        
        // Set up object drop handler
        val objectDropHandler: (Event) -> Unit = { event ->
            val detail = event.asDynamic().detail
            val x = detail.x as Int
            val y = detail.y as Int
            val data = detail.data as String
            
            try {
                // Parse the dropped object data
                val json = Json { 
                    ignoreUnknownKeys = true 
                    isLenient = true
                }
                
                // Safely deserialize the object, handling potential type mismatches
                val droppedObject = try {
                    json.decodeFromString<Object>(data)
                } catch (e: Exception) {
                    console.error("Error deserializing dropped object", e)
                    // Create a default object if deserialization fails
                    Object(
                        title = "Error Object",
                        categories = emptyList(),
                        data = "{}",
                        worldId = currentWorld.value?.key ?: url,
                        position = Position(0f, 0f, 0f),
                        rotation = Rotation(0f, 0f, 0f),
                        scale = Scale(1f, 1f, 1f)
                    )
                }
                
                // Create a ray from the camera through the drop point
                val ray = scene.createPickingRay(
                    x,
                    y,
                    Matrix.Identity(),
                    camera
                )
                
                // Check for intersection with world meshes
                var closestDistance = Float.MAX_VALUE
                var closestPoint: Vector3? = null
                
                for (mesh in worldMeshes) {
                    // Skip player and move circle meshes
                    if (mesh == player || mesh == moveCircle) continue
                    
                    val hit = ray.intersectsMesh(mesh)
                    if (hit.pickedPoint != null) {
                        val distance = Vector3.Distance(camera.position, hit.pickedPoint!!)
                        if (distance < closestDistance) {
                            closestDistance = distance
                            closestPoint = hit.pickedPoint
                        }
                    }
                }
                
                // If we found a valid point, create the object there
                if (closestPoint != null) {
                    // Create a new object with the position from the drop point
                    val newObject = droppedObject.copy(
                        position = Position(
                            x = closestPoint.x,
                            y = closestPoint.y + 0.5f, // Offset to place on top of the ground
                            z = closestPoint.z
                        )
                    )
                    
                    // Send the object creation event over WebSocket
                    WebSocketApi.addObject(newObject)
                    
                    // Create a cube to represent the object
                    createObjectCube(newObject)
                }
            } catch (e: Exception) {
                console.error("Error handling object drop", e)
            }
        }
        
        // Add event listener for object drop
        window.addEventListener("objectDrop", objectDropHandler)
        
        // Add WebSocket event listeners for object events
        WebSocketApi.addListener("addObject") { message ->
            try {
                val json = Json { 
                    ignoreUnknownKeys = true 
                    isLenient = true
                }
                
                // Safely deserialize the object, handling potential type mismatches
                try {
                    val obj = json.decodeFromString<Object>(message.data)
                    createObjectCube(obj)
                } catch (e: Exception) {
                    console.error("Error deserializing object from WebSocket", e)
                    // Create a default object if deserialization fails
                    val defaultObj = Object(
                        title = "Error Object",
                        categories = emptyList(),
                        data = "{}",
                        worldId = currentWorld.value?.key ?: url,
                        position = Position(0f, 0f, 0f),
                        rotation = Rotation(0f, 0f, 0f),
                        scale = Scale(1f, 1f, 1f)
                    )
                    createObjectCube(defaultObj)
                }
            } catch (e: Exception) {
                console.error("Error handling addObject event", e)
            }
        }
        
        WebSocketApi.addListener("removeObject") { message ->
            try {
                val objectId = message.data.trim('"') // Remove quotes if present
                
                // Remove the object from our maps
                sceneObjects.remove(objectId)
                
                // Remove and dispose the mesh
                objectMeshes[objectId]?.dispose()
                objectMeshes.remove(objectId)
                
                console.log("Object removed: $objectId")
            } catch (e: Exception) {
                console.error("Error handling removeObject event", e)
            }
        }
        
        // Clean up when the component is disposed
        onDispose {
            window.removeEventListener("resize", resizeHandler)
            window.removeEventListener("objectDrop", objectDropHandler)
            WebSocketApi.removeAllListeners("addObject")
            WebSocketApi.removeAllListeners("removeObject")
            WebSocketApi.disconnect()
            scene.dispose()
            engine.dispose()
        }
    }
}
