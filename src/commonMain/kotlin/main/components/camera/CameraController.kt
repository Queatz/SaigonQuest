package main.components.camera

import lib.ArcRotateCamera
import lib.ColorCorrectionPostProcess
import lib.CreateSphereOptions
import lib.DefaultRenderingPipeline
import lib.Mesh
import lib.MeshBuilder
import lib.SSAO2RenderingPipeline
import lib.Scene
import lib.Vector2
import lib.Vector3
import main.components.GraphicsSettings
import main.components.SceneManager
import kotlin.math.PI

/**
 * Controller for managing the camera in the 3D scene.
 * 
 * This class encapsulates all camera-related functionality including:
 * - Camera creation and configuration
 * - Camera target management
 * - Camera movement and positioning
 * - Depth of field updates
 * - Smooth camera animations
 */
class CameraController(private val scene: Scene) {
    // Initial FOV value when closest to target
    private val baseFov = 0.25f

    // Create the main camera
    private val camera: ArcRotateCamera = ArcRotateCamera(
        name = "camera",
        alpha = 0f,
        beta = 0f,
        radius = 24f,
        target = Vector3.Zero(),
        scene = scene
    ).apply {
        // Set camera constraints
        updateConstraints()
        this.targetScreenOffset = Vector2(0f, -.75f)

        // Reduce camera rotation sensitivity (higher value = less sensitive)
        // Default is 1000f. 500f is 2x more sensitive/faster.
        this.angularSensibilityX = 500f
        this.angularSensibilityY = 500f
        this.panningSensibility = 500f

        // Remove keyboard input once
        this.inputs.removeByType("keyboard")
        this.inputs.removeByType("ArcRotateCameraKeyboardMoveInput")

        this.wheelPrecision = 5f
        this.zoomToMouseLocation = true
        this.wheelDeltaPercentage = 0.01f
        // Set a narrow FOV (default is ~0.8 radians/45 degrees)
        this.fov = 0.25f // ~28 degrees - narrower telephoto-like view
        this.minZ = 0.1f
        this.maxZ = 500f

        // Set initial position
        this.alpha = (-PI / 2).toFloat()
        this.beta = upperBetaLimit
    }

    // Camera target - separate from player to prevent player moving with camera during panning
    private var cameraTarget: Mesh? = null
    
    // Animation properties
    private var targetPosition: Vector3? = null
    private var isAnimating: Boolean = false
    var isFollowActive: Boolean = true
    private var inputsAttached: Boolean = false // Track current attachment state
    private var isManuallyDisabled: Boolean = false // Track manual disabling to ignore proximity re-enabling
    private val animationSpeed: Float = 0.25f // Adjust this value to control animation speed (0.0-1.0)
    private val proximityAnimationSpeed: Float = 0.25f // Smooth speed for proximity transitions

    // Proximity camera locking
    private val proximityCameraManager = ProximityCameraManager(scene)

    // Pipeline manager for post-processing effects
    private val pipelineManager = CameraPipelineManager(scene, camera)

    /**
     * Observer for graphics settings changes.
     */
    private val graphicsSettingsObserver: () -> Unit = {
        pipelineManager.updatePipelineSettings()
        camera.updateConstraints()
    }

    init {
        console.log("[CameraController] Initializing camera controller")
        
        // Set as active camera for audio listener
        scene.activeCamera = camera
        
        // Create the camera target (will be positioned later when player is created)
        createCameraTarget()

        // Register an observer with GraphicsSettings to update pipeline settings when graphics settings change
        GraphicsSettings.addObserver(graphicsSettingsObserver)
    }
    
    /**
     * Creates an invisible camera target object.
     * This decouples camera panning from player movement.
     */
    private fun createCameraTarget() {
        // Create a separate camera target (invisible)
        cameraTarget = MeshBuilder.CreateSphere(
            name = "cameraTarget",
            options = object : CreateSphereOptions {
                override val diameter = 0.1f
            },
            scene = scene
        ).apply {
            isVisible = false
        }
        
        console.log("[DEBUG] Created camera target with depth-texture-friendly invisible material")
    }
    
    /**
     * Updates the camera target position.
     * Sets the target position for smooth animation.
     * 
     * @param position The new position for the camera target
     * @param useAnimation Whether to animate the transition
     * @param preserveSettings Whether to preserve current alpha, beta, and radius
     */
    fun updateCameraTarget(
        position: Vector3,
    ) {
        targetPosition = position.clone()
        isAnimating = true
    }
    
    /**
     * Updates the camera animation in each frame.
     * Should be called in the render loop.
     * 
     * @return True if animation is in progress, false otherwise
     */
    fun updateAnimation(): Boolean {
        pipelineManager.updateDepthOfFieldFocus(cameraTarget)
        pipelineManager.updateVignetteWeight()

        // Handle proximity-based camera locking
        val influence = proximityCameraManager.getInfluence()
        val easedInfluence = proximityCameraManager.getEasedInfluence()
        val activePc = proximityCameraManager.getActiveCamera()
        
        // Determine normal FOV based on settings and radius
        val normalFov = if (GraphicsSettings.isWideAngleEnabled()) {
            val radiusRange = camera.upperRadiusLimit - camera.lowerRadiusLimit
            val radiusRatio = (camera.radius - camera.lowerRadiusLimit) / radiusRange
            baseFov * (1f + 1f * radiusRatio) // Scale from 1x to 4x base FOV
        } else {
            baseFov
        }

        // Determine intended FOV, blending between normal and locked if transitioning
        val blendPc = activePc ?: proximityCameraManager.getLastActiveCamera()
        val targetFov = if (blendPc != null) {
            val lockedFov = blendPc.targetCamera.fov
            normalFov * (1f - easedInfluence) + lockedFov * easedInfluence
        } else {
            normalFov
        }
        
        // Use faster speed for FOV during proximity transitions to feel snappier
        val currentPosition = cameraTarget?.position ?: Vector3.Zero()
        val pivotDist = Vector3.Distance(camera.target, currentPosition)
        val isFarFromPlayer = pivotDist > 1.0f
        val currentFovSpeed = if (influence > 0f) animationSpeed else if (isFarFromPlayer) proximityAnimationSpeed else animationSpeed
        
        // Smoothly lerp FOV
        camera.fov = camera.fov * (1 - currentFovSpeed) + targetFov * currentFovSpeed

        if (activePc != null) {
            val lockedCam = activePc.targetCamera
            val targetPos: Vector3 = lockedCam.globalPosition
            val forward: Vector3 = lockedCam.getForwardRay(1f).direction
            val targetLookAt: Vector3 = targetPos.add(forward)

            // Smoothly move the camera towards the locked camera's position and orientation.
            // We modulate the speed with easedInfluence to achieve an ease-in-out effect.
            val currentProximitySpeed = proximityAnimationSpeed * easedInfluence
            camera.setPosition(Vector3.Lerp(camera.position, targetPos, currentProximitySpeed))
            camera.setTarget(Vector3.Lerp(camera.target, targetLookAt, currentProximitySpeed))
            
            // Handle input enablement based on influence for a "soft" lock
            if (influence > 0.9f) {
                disableCameraInputs(manual = false)
            }
            
            updateSkybox()
            return true
        }

        // Re-enable camera inputs when not locked (only if not manually disabled)
        if (influence < 0.1f) {
            enableCameraInputs(manual = false)
        }

        // Check if we are still transitioning (FOV or Pivot)
        val fovCaughtUp = kotlin.math.abs(camera.fov - targetFov) < 0.001f
        val cameraCaughtUp = pivotDist < 0.01f
        val isTransitioning = !fovCaughtUp || !cameraCaughtUp

        if (!isAnimating || targetPosition == null || cameraTarget == null) {
            if (isTransitioning && cameraTarget != null) {
                if (isFollowActive && !cameraCaughtUp) {
                    // Continue lerping the camera target even if the mesh is stationary
                    updateCameraTargetImmediate(cameraTarget!!.position, useAnimation = true)
                }
                updateSkybox()
                return true
            }
            updateSkybox()
            return false
        }
        
        // Get current mesh position
        
        // Check if we're close enough to the target to end the animation
        val meshReachedTarget = Vector3.Distance(currentPosition, targetPosition!!) < 0.01f
        // cameraCaughtUp and fovCaughtUp are already calculated above

        if (meshReachedTarget) {
            // Set final position for mesh
            cameraTarget!!.position = targetPosition!!
            
            // Update camera to target the camera target object smoothly
            updateCameraTargetImmediate(targetPosition!!, useAnimation = true)
            
            // Only stop animating if the camera pivot and FOV have also caught up
            if (cameraCaughtUp && fovCaughtUp) {
                stopAnimating()
                updateSkybox()
                return false
            }
            
            updateSkybox()
            return true
        }
        
        // Interpolate between current position and target position for the mesh
        val newPosition = Vector3.Lerp(currentPosition, targetPosition!!, animationSpeed)
        
        // Update the camera target object's position
        cameraTarget!!.position = newPosition
        
        // Update camera to target the camera target object smoothly
        updateCameraTargetImmediate(newPosition, useAnimation = true)

        updateSkybox()
        
        return true
    }

    private fun updateSkybox() {
        // Update skybox
        SceneManager.getObjectManager()?.getSkyboxes()?.forEach { skybox ->
            camera.position.let { skybox.position.copyFrom(it) }
            skybox.position.x = -skybox.position.x
        }
    }
    
    /**
     * Updates the camera target position immediately or with animation.
     * Used internally and for initial positioning.
     * 
     * @param position The new position for the camera target
     * @param preserveSettings Whether to preserve current alpha, beta, and radius
     * @param useAnimation Whether to use a smooth transition (lerp)
     */
    fun updateCameraTargetImmediate(
        position: Vector3, 
        preserveSettings: Boolean = true,
        useAnimation: Boolean = false
    ) {
        var speed = if (useAnimation) {
            animationSpeed
        } else {
            1.0f
        }

        // Modulate speed when returning from a proximity lock for ease-in-out effect
        if (useAnimation && proximityCameraManager.getInfluence() > 0f && proximityCameraManager.getActiveCamera() == null) {
            val easedInfluence = proximityCameraManager.getEasedInfluence()
            speed *= (1f - easedInfluence)
        }

        if (preserveSettings) {
            // Store current camera settings
            val currentAlpha = camera.alpha
            val currentBeta = camera.beta
            val currentRadius = camera.radius
            
            // Update camera to target the camera target object
            if (speed >= 1.0f) {
                camera.setTarget(position)
            } else {
                camera.setTarget(Vector3.Lerp(camera.target, position, speed))
            }
            
            // Restore camera settings to maintain relative position
            camera.alpha = currentAlpha
            camera.beta = currentBeta
            camera.radius = currentRadius
        } else {
            // This is the "Auto Depth" / Navigation mode:
            // Set new pivot, camera stays put, radius/alpha/beta recompute naturally
            val currentPosition = camera.position.clone()
            if (speed >= 1.0f) {
                camera.setTarget(position)
            } else {
                camera.setTarget(Vector3.Lerp(camera.target, position, speed))
            }
            camera.setPosition(currentPosition)
        }

        // Also update the internal target mesh position
        cameraTarget?.position = position
    }
    
    
    /**
     * Gets the camera.
     * 
     * @return The camera
     */
    fun getCamera(): ArcRotateCamera {
        return camera
    }
    
    /**
     * Gets the camera target.
     * 
     * @return The camera target mesh
     */
    fun getCameraTarget(): Mesh? {
        return cameraTarget
    }
    
    /**
     * Sets the initial camera target position.
     * This is done immediately without animation.
     * Creates a separate copy of the position to decouple from player position.
     * 
     * @param position The initial position for the camera target
     */
    fun setInitialTargetPosition(position: Vector3) {
        // Create a clone of the position to decouple from player position
        val cameraTargetPosition = position.clone()
        cameraTarget?.position = cameraTargetPosition
        updateCameraTargetImmediate(cameraTargetPosition, useAnimation = false)
    }
    
    /**
     * Focuses the camera on a specific position in the scene.
     * This animates the camera movement to the target position.
     * 
     * @param position The position to focus on
     */
    fun focusOnPosition(position: Vector3) {
        // Ensure the camera target exists
        if (cameraTarget == null) {
            createCameraTarget()
        }
        
        // Reset animation state and disable player follow until they move again
        stopAnimating(manual = true)
        
        // Update the camera target with animation
        updateCameraTarget(position)
        
        // Set a fixed radius for consistent zoom level when focusing on objects
        // This ensures the camera is always at the same distance when locating objects
        camera.radius = 12.0f // Fixed distance that works well for viewing objects
        
        console.log("Camera focused on position (${position.x}, ${position.y}, ${position.z})")
    }
    
    /**
     * Gets the rendering pipeline.
     * 
     * @return The rendering pipeline
     */
    fun getPipeline(): DefaultRenderingPipeline {
        return pipelineManager.getPipeline()
    }

    fun stopAnimating(manual: Boolean = false) {
        isAnimating = false
        targetPosition = null
        if (manual) {
            isFollowActive = false
        }
        // Sync the internal target mesh position to the current camera target
        // This prevents the camera from snapping back to the previous target when animation stops
        cameraTarget?.position?.copyFrom(camera.target)
    }

    /**
     * Adds a camera to the post-processing pipeline.
     * This ensures the camera has the same effects as the main camera.
     * 
     * @param cameraToAdd The camera to add to the pipeline
     */
    fun addCameraToPipeline(cameraToAdd: lib.Camera) {
        pipelineManager.addCameraToPipeline(cameraToAdd)
    }

    /**
     * Removes a camera from the post-processing pipeline.
     * 
     * @param cameraToRemove The camera to remove from the pipeline
     */
    fun removeCameraFromPipeline(cameraToRemove: lib.Camera) {
        pipelineManager.removeCameraFromPipeline(cameraToRemove)
    }

    /**
     * Initializes proximity-based cameras from the given nodes.
     */
    fun initializeProximityCameras() {
        proximityCameraManager.initialize()
    }

    /**
     * Checks if the player is within range of any proximity cameras.
     * 
     * @param playerPosition The current position of the player
     */
    fun checkProximity(playerPosition: Vector3) {
        proximityCameraManager.update(playerPosition)
    }

    /**
     * Checks if the camera is currently locked to a proximity camera.
     * 
     * @return True if locked, false otherwise
     */
    fun isLocked(): Boolean = proximityCameraManager.isActive()
    
    
    /**
     * Updates camera constraints (limits) based on current settings.
     * Relaxed constraints in Observer Mode allow for Blender-style navigation.
     */
    fun ArcRotateCamera.updateConstraints() {
        if (GraphicsSettings.isObserverModeEnabled()) {
            lowerRadiusLimit = 8f
            upperRadiusLimit = 500f // Matches maxZ
            lowerBetaLimit = 0.01f
            upperBetaLimit = (PI/2f - 0.01f).toFloat()
        } else {
            lowerRadiusLimit = 12f
            upperRadiusLimit = 24f
            lowerBetaLimit = (57.5f * PI / 180f).toFloat()
            upperBetaLimit = (87.5f * PI / 180f).toFloat()
        }
    }

    /**
     * Updates the pipeline settings based on the pretty graphics setting.
     * This method can be called when the pretty graphics setting changes.
     */
    fun updatePipelineSettings() {
        pipelineManager.updatePipelineSettings()
    }

    /**
     * Updates the outline settings.
     */
    fun updateOutlineSettings(
        opacity: Float? = null,
        thicknessScale: Float? = null,
        colorMix: Float? = null,
    ) {
        pipelineManager.updateOutlineSettings(opacity, thicknessScale, colorMix)
    }

    /**
     * Enables camera inputs to allow camera movement.
     * 
     * @param manual Whether this is a manual request (e.g., from InputHandler)
     */
    fun enableCameraInputs(manual: Boolean = false) {
        if (manual) {
            isManuallyDisabled = false
        }
        
        if (isManuallyDisabled) {
            return
        }

        if (inputsAttached) return
        inputsAttached = true

        // Attach controls
        camera.attachControl(true)
        
        console.log("Camera inputs re-enabled")
    }
    
    /**
     * Disables camera inputs to prevent camera movement during object manipulation
     * 
     * @param manual Whether this is a manual request (e.g., from InputHandler)
     */
    fun disableCameraInputs(manual: Boolean = false) {
        if (manual) {
            isManuallyDisabled = true
        }

        if (!inputsAttached) return
        inputsAttached = false

        // Detach all controls
        camera.detachControl()
        
        console.log("Camera inputs disabled")
    }

    /**
     * Disposes of the camera controller resources.
     */
    fun dispose() {
        // Clear proximity cameras
        proximityCameraManager.clear()

        // Remove the observer when disposing
        GraphicsSettings.removeObserver(graphicsSettingsObserver)
        
        // Clean up the pipeline
        pipelineManager.dispose()
        
        // Clean up the camera target
        cameraTarget?.dispose()
        
        console.log("[DEBUG] Camera controller resources disposed")
    }
}

